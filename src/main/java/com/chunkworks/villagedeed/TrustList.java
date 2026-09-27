/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.domain.Roster;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** The trust screen's wire (D-0004). The server sends a player their own roster as rows over every
 * player this world has seen, with how many villages it covers; each click comes back as one
 * toggle, applied to the sender's own roster and nobody else's, and answered with a fresh
 * listing. The bare {@code /deed} and using a deed open it; anyone may, operator or not, since the
 * list only ever edits its viewer's own villages. The screen is client code, handed each listing
 * through {@link Client}. */
public final class TrustList {
    private TrustList() {}

    /** The screen's contents: whether to open the screen (a refresh after a click only updates one
     * already open), how many villages the viewer holds, and the rows. */
    public record Listing(boolean open, int villages, List<Roster.Row> rows) implements CustomPacketPayload {
        public static final Type<Listing> TYPE = new Type<>(VillageDeed.id("roster"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Listing> CODEC = StreamCodec.of((buf, l) -> {
            buf.writeBoolean(l.open()); buf.writeVarInt(l.villages()); buf.writeVarInt(l.rows().size());
            for (var r : l.rows()) { buf.writeUUID(r.id()); buf.writeUtf(r.name()); buf.writeBoolean(r.online()); buf.writeBoolean(r.trusted()); }
        }, buf -> {
            boolean open = buf.readBoolean();
            int villages = buf.readVarInt(), n = buf.readVarInt();
            var rows = new ArrayList<Roster.Row>(n);
            for (int i = 0; i < n; i++) rows.add(new Roster.Row(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
            return new Listing(open, villages, rows);
        });
        public Listing { rows = List.copyOf(rows); }
        @Override public Type<Listing> type() { return TYPE; }
    }
    /** A click on a row: put the player on or off the sender's roster. */
    public record Toggle(UUID who, boolean trusted) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(VillageDeed.id("trust"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toggle> CODEC = StreamCodec.of(
                (buf, t) -> { buf.writeUUID(t.who()); buf.writeBoolean(t.trusted()); },
                buf -> new Toggle(buf.readUUID(), buf.readBoolean()));
        @Override public Type<Toggle> type() { return TYPE; }
    }
    /** Where the client takes each listing: the screen registers itself at client setup, so this
     * common class never names a client class. */
    public static final class Client {
        private static volatile Consumer<Listing> receiver = l -> {};
        private Client() {}
        public static void receiver(Consumer<Listing> r) { receiver = Objects.requireNonNull(r); }
        public static void accept(Listing listing) { receiver.accept(listing); }
    }

    /** effects: sends the player their listing and asks their client to open the screen. */
    public static void open(ServerPlayer player) { send(player, listing(player, true)); }
    /** effects: sends the listing when the player's connection took the channel at login; a
     * machine's fake player using a deed has no screen to open, and NeoForge refuses a payload to
     * a connection without the channel. */
    private static void send(ServerPlayer player, Listing listing) {
        if (player.connection != null && player.connection.hasChannel(Listing.TYPE)) PacketDistributor.sendToPlayer(player, listing);
    }
    /** effects: the player's listing: their roster over everyone this world has seen, and how many
     * villages they hold in every dimension. */
    public static Listing listing(ServerPlayer player, boolean open) {
        var server = player.server;
        var roster = Rosters.get(server).of(player.getUUID());
        var online = new HashSet<UUID>();
        for (var p : server.getPlayerList().getPlayers()) online.add(p.getUUID());
        return new Listing(open, villages(server, player.getUUID()), roster.rows(seen(server, roster), online));
    }
    /** effects: how many villages the player holds, over every dimension. */
    public static int villages(MinecraftServer server, UUID owner) {
        int n = 0;
        for (var level : server.getAllLevels()) n += Claims.get(level).ownedBy(owner).size();
        return n;
    }
    /** effects: applies the click to the sender's own roster and answers with a fresh listing. A
     * toggle naming the sender changes nothing, and neither does trusting a player this world has
     * never seen (the list offers only those it has). */
    public static void toggle(ServerPlayer player, Toggle toggle) {
        var server = player.server;
        var rosters = Rosters.get(server);
        var seen = seen(server, rosters.of(player.getUUID()));
        if (!toggle.trusted() || seen.containsKey(toggle.who())) {
            var name = seen.getOrDefault(toggle.who(), rosters.nameOf(toggle.who()));
            if (rosters.set(player.getUUID(), toggle.who(), name, toggle.trusted()))
                VillageDeed.LOGGER.info("{} {} {} in all their villages", player.getScoreboardName(), toggle.trusted() ? "trusted" : "distrusted", name != null ? name : toggle.who());
        }
        send(player, listing(player, false));
    }
    /** effects: every player this world has seen, with the best name the server knows (null when
     * it knows none): those with a player-data file, those online, and those on the roster; names
     * from the online player, else the server's profile cache, else the rosters' memory. */
    static Map<UUID, String> seen(MinecraftServer server, Roster roster) {
        var out = new HashMap<UUID, String>();
        var dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                files.forEach(f -> {
                    var name = f.getFileName().toString();
                    if (!name.endsWith(".dat")) return;
                    try { out.put(UUID.fromString(name.substring(0, name.length() - 4)), null); } catch (IllegalArgumentException ignored) {}
                });
            } catch (IOException e) { VillageDeed.LOGGER.warn("could not list {}", dir, e); }
        }
        for (var id : roster.trusted()) out.putIfAbsent(id, null);
        for (var p : server.getPlayerList().getPlayers()) out.put(p.getUUID(), p.getScoreboardName());
        var rosters = Rosters.get(server);
        var cache = server.getProfileCache();
        out.replaceAll((id, name) -> {
            if (name != null) return name;
            var cached = cache == null ? Optional.<GameProfile>empty() : cache.get(id);
            return cached.isPresent() ? cached.get().getName() : rosters.nameOf(id);
        });
        return out;
    }
}
