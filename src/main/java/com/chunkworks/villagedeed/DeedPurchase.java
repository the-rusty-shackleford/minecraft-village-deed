/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Buying a village: the offer a sneak-use on a villager prints, the purchase the prompt's click
 * runs, and everything both need. The offer never charges: Thief's emerald gift is the same
 * gesture, so sneak-use only names the price and the click confirms. The price the player was
 * offered is the price they pay while the offer lives. */
public final class DeedPurchase {
    /** How far from a wandering villager their village may lie. */
    public static final int NEAR_RADIUS = 48;
    /** Villagers wander outside the bounds; pad them when forgiving grudges. */
    private static final int GOSSIP_MARGIN = 24;
    private static final Map<UUID, Offer> OFFERS = new HashMap<>();
    private DeedPurchase() {}

    public enum Result { BOUGHT, NOT_IN_VILLAGE, ALREADY_OWNED, CANNOT_AFFORD }
    /** What a purchase attempt came to; the fields past {@code result} are filled where they apply. */
    public record Outcome(Result result, String villageName, String ownerName, int price, int carrying) {
        static Outcome of(Result result) { return new Outcome(result, "", "", 0, 0); }
    }

    /** effects: the tariff under the server's price settings. */
    public static Tariff tariff() {
        int floor = DeedConfig.FLOOR.get();
        return Tariff.STANDARD.withPrices(floor, Math.max(floor, DeedConfig.CEILING.get()), DeedConfig.MULTIPLIER.get());
    }
    /** effects: what the village is worth now. */
    public static Appraisal appraise(ServerLevel level, Village village) {
        var tariff = tariff();
        return Appraisal.of(Surveyor.census(level, village, tariff), tariff);
    }
    /** effects: the village the position is in, else the nearest within reach. */
    public static Optional<Village> villageAround(ServerLevel level, BlockPos pos) {
        return VillageProviders.at(level, pos).or(() -> VillageProviders.near(level, pos, NEAR_RADIUS));
    }

    /** effects: tells the player what the villager's village would cost, or why there is nothing
     * to buy, and keeps the offer open for the confirm window. Never charges. */
    public static void offer(ServerPlayer player, Villager villager) {
        var level = player.serverLevel();
        var village = VillageProviders.at(level, villager.blockPosition())
                .or(() -> VillageProviders.at(level, player.blockPosition()))
                .or(() -> VillageProviders.near(level, villager.blockPosition(), NEAR_RADIUS));
        if (village.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.villagedeed.offer.no_village").withStyle(ChatFormatting.GRAY));
            return;
        }
        var v = village.get();
        var claim = Claims.get(level).get(v.id());
        if (claim != null) {
            var key = claim.deed().owner().equals(player.getUUID()) ? "message.villagedeed.offer.yours" : "message.villagedeed.offer.owned";
            player.sendSystemMessage(Component.translatable(key, v.name(), claim.ownerName()).withStyle(ChatFormatting.GRAY));
            return;
        }
        var appraisal = appraise(level, v);
        int carrying = carrying(player);
        OFFERS.put(player.getUUID(), new Offer(v.id().toString(), v.name(), appraisal.price(), level.getGameTime() + DeedConfig.CONFIRM_WINDOW_SECONDS.get() * 20L));
        player.sendSystemMessage(Component.translatable("message.villagedeed.offer", v.name(), appraisal.price(), carrying).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("message.villagedeed.offer.button", v.name()).withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/deed buy"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.villagedeed.offer.hover", appraisal.price())))));
    }

    /** effects: buys the village the player stands in, or the one they were offered when they
     * stand between its pieces, at the offered price while the offer lives and at today's
     * appraisal otherwise: takes emeralds then blocks of emerald, returns the change, records
     * the claim in the player's name, forgives the residents' grudges and hands over the deed. */
    public static Outcome buy(ServerPlayer player) {
        var level = player.serverLevel();
        var offer = liveOffer(player, level);
        var village = VillageProviders.at(level, player.blockPosition());
        if (village.isEmpty() && offer != null) village = VillageProviders.byId(level, VillageId.parse(offer.village()));
        if (village.isEmpty()) return Outcome.of(Result.NOT_IN_VILLAGE);
        var v = village.get();
        var claims = Claims.get(level);
        var existing = claims.get(v.id());
        if (existing != null) return new Outcome(Result.ALREADY_OWNED, v.name(), existing.ownerName(), 0, 0);
        int price = offer != null && offer.village().equals(v.id().toString()) ? offer.price() : appraise(level, v).price();
        int emeralds = count(player, Items.EMERALD), blocks = count(player, Items.EMERALD_BLOCK);
        var plan = Payment.plan(price, emeralds, blocks);
        if (plan.isEmpty()) return new Outcome(Result.CANNOT_AFFORD, v.name(), "", price, Payment.worth(emeralds, blocks));
        take(player, Items.EMERALD, plan.get().emeralds());
        take(player, Items.EMERALD_BLOCK, plan.get().blocks());
        if (plan.get().change() > 0) player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, plan.get().change()));
        var claim = new Claims.Claim(v.id(), v.name(), Deed.of(player.getUUID()), Map.of(player.getUUID(), player.getScoreboardName()), v.centre(), price, level.getGameTime());
        claims.claim(claim);
        OFFERS.remove(player.getUUID());
        if (DeedConfig.CLEAR_NEGATIVE_GOSSIP.get()) clearNegativeGossip(level, v);
        player.getInventory().placeItemBackInInventory(makeDeed(claim));
        level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        VillageDeed.LOGGER.info("{} bought {} ({}) at {} for {} emeralds", player.getScoreboardName(), v.name(), v.id(), v.centre().toShortString(), price);
        return new Outcome(Result.BOUGHT, v.name(), player.getScoreboardName(), price, 0);
    }
    private static Offer liveOffer(ServerPlayer player, ServerLevel level) {
        var offer = OFFERS.get(player.getUUID());
        if (offer == null) return null;
        if (offer.live(level.getGameTime())) return offer;
        OFFERS.remove(player.getUUID());
        return null;
    }
    /** effects: what the player carries in emeralds and blocks of emerald, as emeralds. */
    public static int carrying(ServerPlayer player) { return Payment.worth(count(player, Items.EMERALD), count(player, Items.EMERALD_BLOCK)); }
    static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (var stack : player.getInventory().items) if (stack.is(item)) total += stack.getCount();
        return total;
    }
    private static void take(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (var stack : player.getInventory().items) {
            if (remaining <= 0) break;
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        player.getInventory().setChanged();
    }
    /** effects: wipes every grudge the village holds, about everyone: the deed forgives the
     * village as a whole, so a friend the owner trusts is not locked out of trades over old ones. */
    private static void clearNegativeGossip(ServerLevel level, Village village) {
        for (var villager : level.getEntitiesOfClass(Villager.class, AABB.of(village.bounds()).inflate(GOSSIP_MARGIN))) {
            villager.getGossips().remove(GossipType.MAJOR_NEGATIVE);
            villager.getGossips().remove(GossipType.MINOR_NEGATIVE);
        }
    }
    static ItemStack makeDeed(Claims.Claim claim) {
        var deed = new ItemStack(ModItems.VILLAGE_DEED.get());
        deed.set(DataComponents.CUSTOM_NAME, Component.translatable("item.villagedeed.village_deed.named", claim.name()).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
        deed.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.villagedeed.village_deed.lore.location", claim.centre().getX(), claim.centre().getZ()).withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("item.villagedeed.village_deed.lore.buyer", claim.ownerName(), claim.pricePaid()).withStyle(ChatFormatting.DARK_GRAY),
                Component.translatable("item.villagedeed.village_deed.lore.covers").withStyle(ChatFormatting.DARK_GREEN),
                Component.translatable("item.villagedeed.village_deed.lore.limit").withStyle(ChatFormatting.DARK_GREEN))));
        return deed;
    }
    /** effects: drops the player's open offer, if any; offers are a session's convenience. */
    public static void forget(UUID player) { OFFERS.remove(player); }
}
