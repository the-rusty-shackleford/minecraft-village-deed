/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.gametest;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.villagedeed.Claims;
import com.chunkworks.villagedeed.DeedPurchase;
import com.chunkworks.villagedeed.ModItems;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.Payment;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** D-0006, Rusty's case: a village bought with emeralds that are only in a worn Backpacks+ bag,
 * with Backpacks+ loaded on the gametest server and the purchase counting and taking through
 * Carried. The bag is Backpacks+'s by registry id and filled through the vanilla container
 * component it keeps its cells in, so this mod compiles against nothing of Backpacks+.
 * <p>Partitions: the money only in the bag, split between the inventory and the bag, short in
 * total; emeralds and blocks of emerald; change due. Invariant in every case: what the player
 * carries is worth exactly the price less than before, and nothing lies on the ground.
 * <p>The buyer is a fake server player: not ticked and not tracked, so Backpacks+'s gear sync
 * never sends a worn bag's state to the other tests' players, whose mock connections never
 * negotiated its payloads. */
@GameTestHolder("villagedeed") @PrefixGameTestTemplate(false)
public final class CarriedPurchaseGameTests {
    public CarriedPurchaseGameTests() {}
    private static final int SETTLE = 5, CHEST = 38;

    private static ItemStack bag(ItemStack... cells) {
        var bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:expedition_backpack")));
        var all = new ArrayList<ItemStack>();
        for (var c : cells) all.add(c);
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(all));
        return bag;
    }
    private static ServerPlayer buyer(GameTestHelper h, BlockPos at, ItemStack worn) {
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "deed-buyer"));
        p.getInventory().clearContent();
        p.getInventory().setItem(CHEST, worn);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }
    private static int worth(ServerPlayer p) { return Payment.worth(Carried.count(p, Items.EMERALD), Carried.count(p, Items.EMERALD_BLOCK)); }
    private static List<ItemStack> bagCells(ServerPlayer p) {
        var contents = p.getInventory().getItem(CHEST).getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        var out = new ArrayList<ItemStack>();
        contents.stream().forEach(out::add);
        return out;
    }
    private static int inInventory(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (var s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n + (p.getInventory().offhand.get(0).is(item) ? p.getInventory().offhand.get(0).getCount() : 0);
    }

    @GameTest(template = "arena", timeoutTicks = 200) public void aVillageIsBoughtWithEmeraldsOnlyInAWornBag(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        Huts.villager(h, hut.at(5, 1, 5), VillagerProfession.FARMER, 2);
        var buyer = buyer(h, hut.at(3, 1, 3), bag(new ItemStack(Items.EMERALD, 5), new ItemStack(Items.EMERALD_BLOCK, 12), new ItemStack(Items.BREAD, 7)));
        h.runAfterDelay(SETTLE, () -> {
            var level = h.getLevel();
            var village = VillageProviders.at(level, hut.centre()).orElseThrow();
            int price = DeedPurchase.appraise(level, village).price();
            h.assertTrue(price > 5 && price <= 113, "a price the bag can pay, not with its emeralds alone: " + price);
            h.assertValueEqual(DeedPurchase.carrying(buyer), 5 + 12 * 9, "the offer counts what is in the bag");
            h.assertValueEqual(inInventory(buyer, Items.EMERALD) + inInventory(buyer, Items.EMERALD_BLOCK), 0, "nothing in the inventory");
            var plan = Payment.plan(price, 5, 12).orElseThrow();
            int before = worth(buyer);
            var outcome = DeedPurchase.buy(buyer);
            h.assertTrue(outcome.result() == DeedPurchase.Result.BOUGHT && outcome.price() == price, "bought: " + outcome);
            h.assertValueEqual(worth(buyer), before - price, "the player is poorer by exactly the price: nothing lost, nothing made");
            var cells = bagCells(buyer);
            h.assertTrue(cells.get(0).isEmpty(), "the bag's emeralds went first: " + cells.get(0));
            h.assertValueEqual(cells.get(1).getCount(), 12 - plan.blocks(), "then the blocks the plan takes");
            h.assertValueEqual(cells.get(2).getCount(), 7, "the bread untouched");
            h.assertValueEqual(Carried.count(buyer, Items.EMERALD), plan.change(), "the change came back");
            h.assertValueEqual(inInventory(buyer, ModItems.VILLAGE_DEED.get()), 1, "the deed is handed over");
            var claim = Claims.get(level).get(village.id());
            h.assertTrue(claim != null && claim.deed().owner().equals(buyer.getUUID()) && claim.pricePaid() == price, "the claim is the buyer's: " + claim);
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(buyer.blockPosition()).inflate(6)).isEmpty(), "nothing on the ground");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200) public void theInventoryPaysBeforeTheBag(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        Huts.villager(h, hut.at(5, 1, 5), VillagerProfession.FARMER, 2);
        var buyer = buyer(h, hut.at(3, 1, 3), bag(new ItemStack(Items.EMERALD, 64), new ItemStack(Items.EMERALD, 64)));
        buyer.getInventory().setItem(4, new ItemStack(Items.EMERALD, 10));
        buyer.getInventory().setItem(Carried.OFFHAND, new ItemStack(Items.EMERALD, 3));
        h.runAfterDelay(SETTLE, () -> {
            var level = h.getLevel();
            int price = DeedPurchase.appraise(level, VillageProviders.at(level, hut.centre()).orElseThrow()).price();
            h.assertTrue(price > 13, "more than the inventory and the offhand hold: " + price);
            var outcome = DeedPurchase.buy(buyer);
            h.assertTrue(outcome.result() == DeedPurchase.Result.BOUGHT, "bought: " + outcome);
            h.assertValueEqual(inInventory(buyer, Items.EMERALD), 0, "the inventory's ten and the offhand's three went first");
            var cells = bagCells(buyer);
            h.assertValueEqual(cells.get(0).getCount() + cells.get(1).getCount(), 128 - (price - 13), "the bag paid the rest, exactly");
            h.assertValueEqual(cells.get(0).getCount(), 64 - (price - 13), "from its first stack first");
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200) public void shortInTotalTakesNothing(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        Huts.villager(h, hut.at(5, 1, 5), VillagerProfession.FARMER, 2);
        var buyer = buyer(h, hut.at(3, 1, 3), bag(new ItemStack(Items.EMERALD, 4)));
        buyer.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
        h.runAfterDelay(SETTLE, () -> {
            var level = h.getLevel();
            var village = VillageProviders.at(level, hut.centre()).orElseThrow();
            var outcome = DeedPurchase.buy(buyer);
            h.assertTrue(outcome.result() == DeedPurchase.Result.CANNOT_AFFORD, "six emeralds buy no village: " + outcome);
            h.assertValueEqual(inInventory(buyer, Items.EMERALD), 2, "the inventory untouched");
            h.assertValueEqual(bagCells(buyer).get(0).getCount(), 4, "the bag untouched");
            h.assertTrue(Claims.get(level).get(village.id()) == null, "no claim");
            h.succeed();
        });
    }
}
