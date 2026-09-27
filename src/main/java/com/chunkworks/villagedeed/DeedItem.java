/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** The deed: a receipt, and a handle on the trust list. The claim lives in the world's saved data,
 * so losing, burning or giving away a deed changes nothing; using one opens its holder's own list
 * of whom they trust in all their villages (D-0004), whichever village it names. */
public final class DeedItem extends Item {
    public DeedItem(Properties properties) { super(properties); }
    /** effects: on the server, sends the user their trust list and opens the screen. */
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) TrustList.open(server);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
    /** effects: one line saying what using the deed does, on every deed, whenever it was bought. */
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.villagedeed.village_deed.use").withStyle(ChatFormatting.GRAY));
    }
}
