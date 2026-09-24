/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.domain.Appraisal;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;
import java.util.Optional;

/** {@code /deed}: buy, here, appraise, list, trust, distrust, transfer, and revoke for operators. */
public final class DeedCommand {
    private DeedCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("deed")
                .then(Commands.literal("buy").executes(DeedCommand::buy))
                .then(Commands.literal("here").executes(DeedCommand::here))
                .then(Commands.literal("appraise").executes(DeedCommand::appraise))
                .then(Commands.literal("list").executes(DeedCommand::list))
                .then(Commands.literal("trust").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> trust(c, true))))
                .then(Commands.literal("distrust").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> trust(c, false))))
                .then(Commands.literal("transfer").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(DeedCommand::transfer)))
                .then(Commands.literal("revoke").requires(source -> source.hasPermission(2)).executes(DeedCommand::revoke)));
    }

    private static int buy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var outcome = DeedPurchase.buy(player);
        switch (outcome.result()) {
            case BOUGHT -> {
                context.getSource().getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.villagedeed.bought.broadcast", player.getDisplayName(), outcome.villageName(), outcome.price()).withStyle(ChatFormatting.GREEN), false);
                return 1;
            }
            case NOT_IN_VILLAGE -> fail(context, Component.translatable("message.villagedeed.fail.not_in_village"));
            case ALREADY_OWNED -> fail(context, Component.translatable("message.villagedeed.fail.already_owned", outcome.villageName(), outcome.ownerName()));
            case CANNOT_AFFORD -> fail(context, Component.translatable("message.villagedeed.fail.cannot_afford", outcome.price(), outcome.carrying()));
        }
        return 0;
    }
    /** effects: which village the player is in or beside, who owns it, and what it would cost. */
    private static int here(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var level = player.serverLevel();
        var village = DeedPurchase.villageAround(level, player.blockPosition());
        if (village.isEmpty()) { reply(context, Component.translatable("message.villagedeed.here.none").withStyle(ChatFormatting.GRAY)); return 0; }
        var v = village.get();
        var claim = Claims.get(level).get(v.id());
        if (claim == null) {
            reply(context, Component.translatable("message.villagedeed.here.unclaimed", v.name(), DeedPurchase.appraise(level, v).price()).withStyle(ChatFormatting.GRAY));
            return 1;
        }
        reply(context, Component.translatable("message.villagedeed.here.claimed", v.name(), claim.ownerName()).withStyle(ChatFormatting.GREEN));
        if (claim.deed().owner().equals(player.getUUID())) {
            var names = claim.trustedNames();
            reply(context, Component.translatable("message.villagedeed.here.trusted", names.isEmpty() ? Component.translatable("message.villagedeed.here.trusted.nobody") : Component.literal(String.join(", ", names))).withStyle(ChatFormatting.GRAY));
        }
        return 2;
    }
    /** effects: the appraisal of the village the player is in or beside, line by line. */
    private static int appraise(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var level = player.serverLevel();
        var village = DeedPurchase.villageAround(level, player.blockPosition());
        if (village.isEmpty()) { reply(context, Component.translatable("message.villagedeed.here.none").withStyle(ChatFormatting.GRAY)); return 0; }
        var v = village.get();
        var appraisal = DeedPurchase.appraise(level, v);
        reply(context, Component.translatable("message.villagedeed.appraise.header", v.name(), appraisal.price()).withStyle(ChatFormatting.GOLD));
        reply(context, Component.translatable("message.villagedeed.appraise.base", DeedPurchase.tariff().base()).withStyle(ChatFormatting.GRAY));
        for (var line : appraisal.lines()) reply(context, Component.translatable("message.villagedeed.appraise.line",
                Component.translatable("villagedeed.category." + line.category().name().toLowerCase(Locale.ROOT)), line.count(), String.format(Locale.ROOT, "%.1f", line.value())).withStyle(ChatFormatting.GRAY));
        var claim = Claims.get(level).get(v.id());
        if (claim != null) reply(context, Component.translatable("message.villagedeed.appraise.owned", claim.ownerName(), claim.pricePaid()).withStyle(ChatFormatting.DARK_GRAY));
        return appraisal.price();
    }
    private static int list(CommandContext<CommandSourceStack> context) {
        var claims = Claims.get(context.getSource().getLevel());
        if (claims.all().isEmpty()) { reply(context, Component.translatable("message.villagedeed.list.empty").withStyle(ChatFormatting.GRAY)); return 0; }
        reply(context, Component.translatable("message.villagedeed.list.header", claims.all().size()).withStyle(ChatFormatting.GOLD));
        for (var claim : claims.all()) reply(context, Component.translatable("message.villagedeed.list.entry",
                claim.name(), claim.centre().getX(), claim.centre().getZ(), claim.ownerName(), claim.deed().trusted().size()).withStyle(ChatFormatting.GRAY));
        return claims.all().size();
    }
    /** effects: puts the named player on or off the roster of the village the owner stands in. */
    private static int trust(CommandContext<CommandSourceStack> context, boolean trusted) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var owned = ownedVillage(context, player);
        if (owned.isEmpty()) return 0;
        var v = owned.get();
        var claims = Claims.get(player.serverLevel());
        var claim = claims.get(v.id());
        var target = target(context);
        if (target.getId().equals(claim.deed().owner())) { fail(context, Component.translatable("message.villagedeed.fail.trust_self")); return 0; }
        var deed = trusted ? claim.deed().trusting(target.getId()) : claim.deed().distrusting(target.getId());
        claims.put(claim.with(deed, target.getId(), target.getName()));
        VillageDeed.LOGGER.info("{} {} {} at {} ({})", player.getScoreboardName(), trusted ? "trusted" : "distrusted", target.getName(), v.name(), v.id());
        reply(context, Component.translatable(trusted ? "message.villagedeed.trusted" : "message.villagedeed.distrusted", target.getName(), v.name()).withStyle(ChatFormatting.GREEN));
        return 1;
    }
    /** effects: hands the village the owner stands in to the named player. */
    private static int transfer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var owned = ownedVillage(context, player);
        if (owned.isEmpty()) return 0;
        var v = owned.get();
        var claims = Claims.get(player.serverLevel());
        var claim = claims.get(v.id());
        var target = target(context);
        if (target.getId().equals(claim.deed().owner())) { fail(context, Component.translatable("message.villagedeed.fail.trust_self")); return 0; }
        claims.put(claim.with(claim.deed().transferredTo(target.getId()), target.getId(), target.getName()));
        VillageDeed.LOGGER.info("{} handed {} ({}) to {}", player.getScoreboardName(), v.name(), v.id(), target.getName());
        reply(context, Component.translatable("message.villagedeed.transferred", v.name(), target.getName()).withStyle(ChatFormatting.GREEN));
        var online = context.getSource().getServer().getPlayerList().getPlayer(target.getId());
        if (online != null) online.sendSystemMessage(Component.translatable("message.villagedeed.transferred.to_you", player.getScoreboardName(), v.name()).withStyle(ChatFormatting.GREEN));
        return 1;
    }
    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayerOrException();
        var level = player.serverLevel();
        var village = DeedPurchase.villageAround(level, player.blockPosition());
        if (village.isEmpty()) { fail(context, Component.translatable("message.villagedeed.fail.not_in_village")); return 0; }
        var removed = Claims.get(level).revoke(village.get().id());
        if (removed == null) { fail(context, Component.translatable("message.villagedeed.fail.not_claimed", village.get().name())); return 0; }
        VillageDeed.LOGGER.info("{} tore up the deed to {} ({}), owned by {}", player.getScoreboardName(), removed.name(), removed.id(), removed.ownerName());
        context.getSource().sendSuccess(() -> Component.translatable("message.villagedeed.revoked", removed.name()).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }
    /** effects: the village the player stands in or beside when they own it; otherwise a failure
     * message and empty. */
    private static Optional<Village> ownedVillage(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        var level = player.serverLevel();
        var village = DeedPurchase.villageAround(level, player.blockPosition());
        if (village.isEmpty()) { fail(context, Component.translatable("message.villagedeed.fail.not_in_village")); return Optional.empty(); }
        var claim = Claims.get(level).get(village.get().id());
        if (claim == null) { fail(context, Component.translatable("message.villagedeed.fail.not_claimed", village.get().name())); return Optional.empty(); }
        if (!claim.deed().owner().equals(player.getUUID())) { fail(context, Component.translatable("message.villagedeed.fail.not_owner", village.get().name(), claim.ownerName())); return Optional.empty(); }
        return village;
    }
    private static GameProfile target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(context, "player").iterator().next();
    }
    private static void reply(CommandContext<CommandSourceStack> context, Component text) { context.getSource().sendSuccess(() -> text, false); }
    private static void fail(CommandContext<CommandSourceStack> context, Component text) { context.getSource().sendFailure(text); }
}
