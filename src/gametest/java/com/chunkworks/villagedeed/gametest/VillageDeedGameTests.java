/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.gametest;

import com.chunkworks.villagedeed.Claims;
import com.chunkworks.villagedeed.DeedPurchase;
import com.chunkworks.villagedeed.ModItems;
import com.chunkworks.villagedeed.Surveyor;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.Appraisal;
import com.chunkworks.villagedeed.domain.Payment;
import io.github.mortuusars.thief.world.Crime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Map;

/** Real-server partitions, with Thief loaded and the hut registered as a protected village: a
 * theft in an unbought village is punished with the villager as witness; once bought, the owner
 * takes and butchers without consequence, a stranger is punished, a trusted player is not, and
 * trust withdrawn is punished again; the appraisal follows what the hut holds, counter by
 * counter, and equals the domain's answer for that census; a purchase takes emeralds first and
 * blocks of emerald for the rest with change back, records the claim, refuses a second buyer,
 * transfers and revokes; a villager standing in the fields outside the hut still offers it and
 * the offer can be bought from there. */
@GameTestHolder("villagedeed") @PrefixGameTestTemplate(false)
public final class VillageDeedGameTests {
    private static final int SETTLE = 5;

    private static Container chest(GameTestHelper h, BlockPos at) {
        h.getLevel().setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
        return (Container) h.getLevel().getBlockEntity(at);
    }
    private static void bed(GameTestHelper h, BlockPos foot) {
        var state = Blocks.RED_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        h.getLevel().setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), 3);
        h.getLevel().setBlock(foot.north(), state.setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

    @GameTest(template = "arena", timeoutTicks = 200) public void theftInAnUnboughtVillageIsACrime(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var chest = hut.at(2, 1, 2);
        chest(h, chest);
        Huts.villager(h, hut.at(5, 1, 5), VillagerProfession.NONE, 1);
        var thief = Huts.player(h, hut.at(3, 1, 2));
        Crimes.clear();
        h.runAfterDelay(SETTLE, () -> {
            h.assertTrue(thief.gameMode.destroyBlock(chest), "the chest broke");
            var seen = Crimes.by(thief.getUUID());
            h.assertTrue(seen.size() == 1 && seen.get(0).crime() == Crime.MEDIUM && seen.get(0).witnesses() == 1, "one medium crime with one witness, got " + seen);
            h.succeed();
        });
    }
    @GameTest(template = "arena", timeoutTicks = 200) public void ownerAndTrustedAreExemptStrangersAreNot(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var chests = new BlockPos[] { hut.at(2, 1, 2), hut.at(2, 1, 4), hut.at(2, 1, 6), hut.at(5, 1, 2) };
        for (var c : chests) chest(h, c);
        Huts.villager(h, hut.at(5, 1, 5), VillagerProfession.NONE, 1);
        var sheep = Huts.sheep(h, hut.at(4, 1, 6));
        var owner = Huts.player(h, hut.at(3, 1, 3), new ItemStack(Items.EMERALD, 64), new ItemStack(Items.EMERALD, 64), new ItemStack(Items.EMERALD, 64));
        var stranger = Huts.player(h, hut.at(3, 1, 4));
        h.runAfterDelay(SETTLE, () -> {
            var level = h.getLevel();
            var outcome = DeedPurchase.buy(owner);
            h.assertTrue(outcome.result() == DeedPurchase.Result.BOUGHT, "the owner buys the hut: " + outcome);
            var village = VillageProviders.at(level, hut.centre()).orElseThrow();
            var claims = Claims.get(level);
            Crimes.clear();
            h.assertTrue(owner.gameMode.destroyBlock(chests[0]), "the owner breaks a chest");
            h.assertTrue(Crimes.by(owner.getUUID()).isEmpty(), "the owner's theft is no crime");
            sheep.hurt(level.damageSources().playerAttack(owner), 1000f);
            h.assertTrue(sheep.isDeadOrDying(), "the sheep died");
            h.assertTrue(Crimes.by(owner.getUUID()).isEmpty(), "the owner's livestock is theirs to butcher");
            h.assertTrue(stranger.gameMode.destroyBlock(chests[1]), "the stranger breaks a chest");
            h.assertTrue(Crimes.by(stranger.getUUID()).size() == 1, "the stranger's theft is a crime, got " + Crimes.by(stranger.getUUID()));
            var claim = claims.get(village.id());
            claims.put(claim.with(claim.deed().trusting(stranger.getUUID()), stranger.getUUID(), "stranger"));
            h.assertTrue(stranger.gameMode.destroyBlock(chests[2]), "the trusted player breaks a chest");
            h.assertTrue(Crimes.by(stranger.getUUID()).size() == 1, "trusted, the same player's theft is no crime");
            claim = claims.get(village.id());
            claims.put(claim.with(claim.deed().distrusting(stranger.getUUID()), stranger.getUUID(), "stranger"));
            h.assertTrue(stranger.gameMode.destroyBlock(chests[3]), "the distrusted player breaks a chest");
            h.assertTrue(Crimes.by(stranger.getUUID()).size() == 2, "trust withdrawn, the theft is a crime again");
            h.succeed();
        });
    }
    @GameTest(template = "arena", timeoutTicks = 200) public void appraisalFollowsWhatTheVillageHolds(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var level = h.getLevel();
        var village = VillageProviders.at(level, hut.centre()).orElseThrow();
        var tariff = DeedPurchase.tariff();
        var bare = DeedPurchase.appraise(level, village);
        h.assertTrue(bare.price() == tariff.floor(), "a bare hut is worth the floor, got " + bare);
        bed(h, hut.at(1, 1, 2)); bed(h, hut.at(3, 1, 2));
        level.setBlock(hut.at(6, 1, 1), Blocks.BELL.defaultBlockState(), 3);
        level.setBlock(hut.at(6, 1, 3), Blocks.LECTERN.defaultBlockState(), 3);
        level.setBlock(hut.at(6, 1, 5), Blocks.SMITHING_TABLE.defaultBlockState(), 3);
        level.setBlock(hut.at(1, 1, 6), Blocks.ENCHANTING_TABLE.defaultBlockState(), 3);
        var stocked = chest(h, hut.at(3, 1, 6));
        stocked.setItem(0, new ItemStack(Items.DIAMOND, 5));
        stocked.setItem(1, new ItemStack(Items.EMERALD, 2));
        stocked.setItem(2, new ItemStack(Items.DIRT, 30));
        chest(h, hut.at(4, 1, 6)); chest(h, hut.at(5, 1, 6));
        Huts.villager(h, hut.at(2, 1, 4), VillagerProfession.LIBRARIAN, 3);
        Huts.villager(h, hut.at(4, 1, 4), VillagerProfession.TOOLSMITH, 5);
        Huts.villager(h, hut.at(5, 1, 3), VillagerProfession.NITWIT, 1);
        var baby = Huts.villager(h, hut.at(3, 1, 5), VillagerProfession.NONE, 1);
        baby.setBaby(true);
        h.runAfterDelay(SETTLE, () -> {
            var census = Surveyor.census(level, village, tariff);
            h.assertTrue(census.beds() == 2 && census.bells() == 1 && census.jobSites() == 2, "two beds, a bell, two job sites: " + census);
            h.assertTrue(census.villagers() == 3 && census.tradeLevels() == 6 && census.golems() == 0, "three adults with six levels above novice: " + census);
            h.assertTrue(census.footprint() == 1, "one chunk area of land: " + census);
            h.assertTrue(census.blocks().equals(Map.of("minecraft:chest", 3, "minecraft:enchanting_table", 1)), "three chests and an enchanting table: " + census.blocks());
            h.assertTrue(census.items().equals(Map.of("minecraft:diamond", 5, "minecraft:emerald", 2)), "the loot that is priced: " + census.items());
            var appraisal = DeedPurchase.appraise(level, village);
            h.assertTrue(appraisal.equals(Appraisal.of(census, tariff)), "the adapter appraises what the domain appraises");
            h.assertTrue(appraisal.price() == 55, "2 + 6 + 6 + 4 + 3 + 0.5 + 3 + 10 + 6 on a base of 15 rounds to 55, got " + appraisal);
            h.succeed();
        });
    }
    @GameTest(template = "arena", timeoutTicks = 200) public void buyingTakesEmeraldsThenBlocksAndGivesChange(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        chest(h, hut.at(2, 1, 2));
        Huts.villager(h, hut.at(5, 1, 5), VillagerProfession.FARMER, 2);
        var owner = Huts.player(h, hut.at(3, 1, 3), new ItemStack(Items.EMERALD, 4), new ItemStack(Items.EMERALD_BLOCK, 6));
        var other = Huts.player(h, hut.at(4, 1, 3), new ItemStack(Items.EMERALD_BLOCK, 8));
        h.runAfterDelay(SETTLE, () -> {
            var level = h.getLevel();
            var village = VillageProviders.at(level, hut.centre()).orElseThrow();
            int price = DeedPurchase.appraise(level, village).price();
            h.assertTrue(price > 4 && price <= 58, "a price emeralds alone cannot meet but blocks can: " + price);
            var plan = Payment.plan(price, 4, 6).orElseThrow();
            var outcome = DeedPurchase.buy(owner);
            h.assertTrue(outcome.result() == DeedPurchase.Result.BOUGHT && outcome.price() == price, "bought at the appraised price: " + outcome);
            h.assertTrue(owner.getInventory().countItem(Items.EMERALD) == 4 - plan.emeralds() + plan.change(), "emeralds taken first and the change returned: " + owner.getInventory().countItem(Items.EMERALD) + " vs " + plan);
            h.assertTrue(owner.getInventory().countItem(Items.EMERALD_BLOCK) == 6 - plan.blocks(), "blocks taken for the rest: " + plan);
            h.assertTrue(owner.getInventory().countItem(ModItems.VILLAGE_DEED.get()) == 1, "the deed is in hand");
            var claim = Claims.get(level).get(village.id());
            h.assertTrue(claim != null && claim.deed().owner().equals(owner.getUUID()) && claim.pricePaid() == price && claim.centre().equals(village.centre()), "the claim records owner, price and centre: " + claim);
            var again = DeedPurchase.buy(other);
            h.assertTrue(again.result() == DeedPurchase.Result.ALREADY_OWNED && again.ownerName().equals(owner.getScoreboardName()), "a second buyer is refused: " + again);
            Claims.get(level).put(claim.with(claim.deed().transferredTo(other.getUUID()), other.getUUID(), other.getScoreboardName()));
            h.assertTrue(Claims.get(level).get(village.id()).deed().owner().equals(other.getUUID()), "the village changed hands");
            h.assertTrue(Claims.get(level).revoke(village.id()) != null && Claims.get(level).get(village.id()) == null, "the deed was torn up");
            h.assertTrue(DeedPurchase.buy(other).result() == DeedPurchase.Result.BOUGHT, "a freed village sells again");
            h.succeed();
        });
    }
    @GameTest(template = "arena", timeoutTicks = 200) public void aVillagerInTheFieldsStillOffersTheirVillage(GameTestHelper h) {
        var hut = Huts.plant(h);
        Huts.build(h, hut);
        var level = h.getLevel();
        var outside = new BlockPos(hut.box().maxX() + 3, hut.box().minY() + 1, hut.box().minZ() + 3);
        level.setBlock(outside.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(outside.below().west(), Blocks.STONE.defaultBlockState(), 3);
        var villager = Huts.villager(h, outside, VillagerProfession.NONE, 1);
        var buyer = Huts.player(h, outside.west(), new ItemStack(Items.EMERALD, 64));
        h.runAfterDelay(SETTLE, () -> {
            h.assertTrue(VillageProviders.at(level, outside).isEmpty(), "the field is outside the hut");
            var near = VillageProviders.near(level, outside, DeedPurchase.NEAR_RADIUS);
            var village = VillageProviders.at(level, hut.centre()).orElseThrow();
            h.assertTrue(near.isPresent() && near.get().id().equals(village.id()), "the hut is the nearest village: " + near);
            DeedPurchase.offer(buyer, villager);
            var outcome = DeedPurchase.buy(buyer);
            h.assertTrue(outcome.result() == DeedPurchase.Result.BOUGHT && outcome.villageName().equals(village.name()), "bought from the field on the standing offer: " + outcome);
            h.assertTrue(Claims.get(level).get(village.id()) != null, "the hut is claimed");
            h.succeed();
        });
    }
}
