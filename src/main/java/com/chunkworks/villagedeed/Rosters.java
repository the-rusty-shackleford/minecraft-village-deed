/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.domain.Roster;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Every player's roster (D-0004): whom they let use their villages, one list for all of them,
 * kept in the overworld's saved data so one list covers villages in every dimension.
 * <p>AF: {@code rosters} maps a player to their roster; a player with no entry trusts nobody.
 * {@code names} holds the last name seen for players put on or off a roster, since names change;
 * only the trusted players' names are saved.
 * <p>RI: {@code rosters.get(p).owner()} is p; no stored roster is empty; no name is empty. */
public final class Rosters extends SavedData {
    private static final SavedData.Factory<Rosters> FACTORY = new SavedData.Factory<>(Rosters::new, Rosters::load);
    private final Map<UUID, Roster> rosters = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private Rosters() {}
    /** effects: the server's rosters. */
    public static Rosters get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, VillageDeed.ID + "_rosters");
    }
    /** effects: the rosters the tag holds; an entry that names no owner, and a trusted entry that
     * is the owner, are skipped. Public so a test can build one; the game reads through the factory. */
    public static Rosters load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new Rosters();
        var list = tag.getList("Rosters", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var r = list.getCompound(i);
            if (!r.hasUUID("Owner")) continue;
            var owner = r.getUUID("Owner");
            var trusted = r.getList("Trusted", Tag.TAG_COMPOUND);
            for (int j = 0; j < trusted.size(); j++) {
                var t = trusted.getCompound(j);
                if (t.hasUUID("Id")) data.set(owner, t.getUUID("Id"), t.getString("Name"), true);
            }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var roster : rosters.values()) {
            var r = new CompoundTag();
            r.putUUID("Owner", roster.owner());
            var trusted = new ListTag();
            for (var id : roster.trusted()) {
                var t = new CompoundTag();
                t.putUUID("Id", id);
                t.putString("Name", names.getOrDefault(id, ""));
                trusted.add(t);
            }
            r.put("Trusted", trusted);
            list.add(r);
        }
        tag.put("Rosters", list);
        return tag;
    }
    /** effects: the player's roster; an empty one when they trust nobody. */
    public Roster of(UUID owner) { return rosters.getOrDefault(owner, Roster.of(owner)); }
    /** effects: whether {@code owner}'s deeds let {@code who} use their villages. */
    public boolean permits(UUID owner, UUID who) { return of(owner).permits(who); }
    /** effects: the last name seen for a player who is or was on a roster, or null. */
    public String nameOf(UUID who) { return names.get(who); }
    /** effects: puts {@code who} on or off {@code owner}'s roster, remembering a non-empty name;
     * returns whether the roster changed. Putting the owner on their own roster changes nothing. */
    public boolean set(UUID owner, UUID who, String name, boolean trusted) {
        if (who.equals(owner)) return false;
        if (name != null && !name.isEmpty() && !name.equals(names.put(who, name))) setDirty();
        var before = of(owner);
        var after = trusted ? before.trusting(who) : before.distrusting(who);
        if (after.equals(before)) return false;
        if (after.trusted().isEmpty()) rosters.remove(owner); else rosters.put(owner, after);
        setDirty();
        return true;
    }
}
