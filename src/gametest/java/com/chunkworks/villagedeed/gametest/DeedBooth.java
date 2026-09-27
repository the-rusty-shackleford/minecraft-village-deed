/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.gametest;

import com.chunkworks.villagedeed.Claims;
import com.chunkworks.villagedeed.ModItems;
import com.chunkworks.villagedeed.Rosters;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagedeed.client.TrustScreen;
import com.chunkworks.villagedeed.domain.Deed;
import com.chunkworks.villagedeed.domain.Roster;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Hardware-client gate for the trust screen (D-0004), silent: the booth player holds two villages
 * and this world has seen sixteen other players, one of them online. The bare {@code /deed} opens
 * the screen (photographed); a click through the screen's own mouse path trusts the first row
 * (photographed); {@code /deed trust <name>} trusts by name through the server's profile cache;
 * using a deed through the client's use-item path opens the screen again, both ticked
 * (photographed); the wheel scrolls the long list (photographed). Screenshots need a human eye;
 * this fixture never ships. */
@EventBusSubscriber(modid = "villagedeed_gametest", value = Dist.CLIENT)
public final class DeedBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Village Deed booth");
    private static final List<String> SEEN = List.of("OtatopMalloy", "WAXER_01", "Bobandy_", "nfx", "Amberlight", "Birchwood_Ben", "Cobble", "Driftwood",
            "Emberly", "Fennick", "Gale_Rider", "Hollow_Oak", "Ivy", "Juniper", "Kestrel", "Lantern");
    private static int tick, firstRowTop;
    private static UUID first;
    private static ServerPlayer online;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("villagedeed.booth")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        mc.getToasts().clear();
        mc.gui.getChat().clearMessages(true);
        try {
            switch (++tick) {
                case 20 -> server(mc, p -> {
                    var l = p.serverLevel();
                    l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, l.getServer());
                    l.setDayTime(6000); l.setWeatherParameters(6000, 0, false, false);
                    p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent();
                    forget(p.server, p.getUUID());
                    for (var name : SEEN) seen(p.server, id(name), name);
                    var claims = Claims.get(p.server.overworld());
                    for (var village : new String[][] { { "structure:9001", "Taiga Village" }, { "structure:9002", "Plains Fortified Village" } })
                        check(claims.claim(new Claims.Claim(VillageId.parse(village[0]), village[1], Deed.of(p.getUUID()), Map.of(p.getUUID(), p.getScoreboardName()), BlockPos.ZERO, 45, 0)), "the booth player holds " + village[1]);
                    var deed = new ItemStack(ModItems.VILLAGE_DEED.get());
                    deed.set(DataComponents.CUSTOM_NAME, Component.literal("Deed to Taiga Village"));
                    p.setItemInHand(InteractionHand.MAIN_HAND, deed);
                    online = join(p.server, "OtatopMalloy");
                });
                case 40 -> mc.player.connection.sendCommand("deed");
                case 60 -> {
                    check(mc.screen instanceof TrustScreen, "the bare /deed opens the trust screen");
                    var screen = (TrustScreen) mc.screen;
                    var listing = screen.listing();
                    check(listing.villages() == 2, "the screen covers the two villages: " + listing.villages());
                    check(listing.rows().size() == SEEN.size() && listing.rows().stream().noneMatch(Roster.Row::trusted), "sixteen rows, none ticked: " + listing.rows());
                    check(listing.rows().get(0).name().equals("OtatopMalloy") && listing.rows().get(0).online() && listing.rows().stream().skip(1).noneMatch(Roster.Row::online),
                            "the online player first and alone in green: " + listing.rows().get(0));
                    photo(mc, "01-trust-screen");
                    first = listing.rows().get(0).id();
                    firstRowTop = screen.rowTop(0);
                    check(screen.mouseClicked(screen.panelLeft() + 20, screen.rowTop(0) + 6, 0), "a click lands on the first row");
                }
                case 80 -> {
                    var screen = (TrustScreen) mc.screen;
                    check(screen.listing().rows().get(0).trusted() && screen.listing().rows().stream().filter(Roster.Row::trusted).count() == 1, "the click ticked the first row and no other");
                    server(mc, p -> check(Rosters.get(p.server).of(p.getUUID()).trusted().equals(Set.of(first)), "the server holds the trust"));
                    photo(mc, "02-one-trusted");
                    mc.setScreen(null);
                }
                case 90 -> mc.player.connection.sendCommand("deed trust WAXER_01");
                case 100 -> server(mc, p -> check(Rosters.get(p.server).of(p.getUUID()).trusted().equals(Set.of(first, id("WAXER_01"))), "/deed trust by name, through the profile cache"));
                case 110 -> check(mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND).consumesAction(), "using the deed is an action");
                case 130 -> {
                    check(mc.screen instanceof TrustScreen, "using a deed opens the trust screen");
                    var rows = ((TrustScreen) mc.screen).listing().rows();
                    check(rows.stream().filter(Roster.Row::trusted).map(Roster.Row::name).toList().equals(List.of("OtatopMalloy", "WAXER_01")), "both ticked: " + rows);
                    photo(mc, "03-from-the-deed");
                    var screen = (TrustScreen) mc.screen;
                    check(screen.mouseScrolled(screen.panelLeft() + 20, firstRowTop + 20, 0, -3), "the wheel scrolls the list");
                    check(screen.rowTop(0) < firstRowTop, "scrolled down a row: " + screen.rowTop(0) + " vs " + firstRowTop);
                }
                case 140 -> { photo(mc, "04-scrolled"); mc.setScreen(null); }
                case 150 -> {
                    server(mc, p -> { if (online != null) p.server.getPlayerList().remove(online); });
                    LOG.info("villagedeed booth: COMPLETE");
                    mc.stop();
                }
            }
        } catch (Throwable failure) { LOG.error("villagedeed booth: FAIL", failure); mc.stop(); }
    }
    private static UUID id(String name) { return UUID.nameUUIDFromBytes(("booth:" + name).getBytes(StandardCharsets.UTF_8)); }
    /** effects: drops every player-data file but the booth player's own: the booth world is a copy
     * of the GameTest world and carries its mock players. */
    private static void forget(MinecraftServer server, UUID keep) {
        var dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!Files.isDirectory(dir)) return;
        try (var files = Files.list(dir)) {
            for (var f : files.toList()) if (!f.getFileName().toString().startsWith(keep.toString())) Files.delete(f);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    /** effects: makes the server count the player among those this world has seen: a player-data
     * file under their id and their name in the profile cache. */
    private static void seen(MinecraftServer server, UUID id, String name) {
        try {
            var dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            Files.createDirectories(dir);
            NbtIo.writeCompressed(new CompoundTag(), dir.resolve(id + ".dat"));
        } catch (IOException e) { throw new UncheckedIOException(e); }
        server.getProfileCache().add(new GameProfile(id, name));
    }
    /** effects: joins a player under that name over an embedded connection, so the list shows
     * someone online. */
    private static ServerPlayer join(MinecraftServer server, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(id(name), name), false);
        var player = new ServerPlayer(server, server.overworld(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        server.execute(() -> { try { action.accept(server.getPlayerList().getPlayer(id)); } catch (Throwable failure) { LOG.error("villagedeed booth: FAIL", failure); mc.execute(mc::stop); } });
    }
    private static void photo(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "villagedeed-" + name + ".png", mc.getMainRenderTarget(), m -> LOG.info("villagedeed booth: {}", m.getString()));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); LOG.info("villagedeed booth: PASS {}", message); }
}
