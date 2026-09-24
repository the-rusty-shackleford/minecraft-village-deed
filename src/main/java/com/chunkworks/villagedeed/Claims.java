/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagedeed.domain.Deed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** The villages that have been bought, per dimension, in the level's saved data.
 * <p>AF: {@code claims} maps a village's id to its claim: the deed, the names last seen for the
 * players on it, where and for how much it was bought. RI: a claim's id is its key. */
public final class Claims extends SavedData {
    /** One bought village. Immutable. {@code names} holds the last-known name of the owner and of
     * every trusted player, since names change. */
    public record Claim(VillageId id, String name, Deed deed, Map<UUID, String> names, BlockPos centre, int pricePaid, long boughtAt) {
        public Claim { names = Map.copyOf(names); }
        public String ownerName() { return names.getOrDefault(deed.owner(), "?"); }
        /** effects: this claim with another deed, and the player's name remembered. */
        public Claim with(Deed newDeed, UUID who, String whoseName) {
            var n = new HashMap<>(names);
            n.put(who, whoseName);
            return new Claim(id, name, newDeed, n, centre, pricePaid, boughtAt);
        }
        /** effects: the trusted players' names, in no particular order. */
        public List<String> trustedNames() {
            var out = new ArrayList<String>();
            for (var id : deed.trusted()) out.add(names.getOrDefault(id, id.toString().substring(0, 8)));
            Collections.sort(out);
            return out;
        }
    }
    private static final SavedData.Factory<Claims> FACTORY = new SavedData.Factory<>(Claims::new, Claims::load);
    private final Map<VillageId, Claim> claims = new LinkedHashMap<>();
    private Claims() {}
    public static Claims get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY, VillageDeed.ID + "_claims"); }

    private static Claims load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new Claims();
        var list = tag.getList("Claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var c = list.getCompound(i);
            if (!c.hasUUID("Owner") || !c.contains("Id")) continue;
            VillageId id;
            try { id = VillageId.parse(c.getString("Id")); } catch (IllegalArgumentException e) { continue; }
            var names = new HashMap<UUID, String>();
            var owner = c.getUUID("Owner");
            names.put(owner, c.getString("OwnerName"));
            var deed = Deed.of(owner);
            var trusted = c.getList("Trusted", Tag.TAG_COMPOUND);
            for (int j = 0; j < trusted.size(); j++) {
                var t = trusted.getCompound(j);
                if (!t.hasUUID("Id") || t.getUUID("Id").equals(owner)) continue;
                deed = deed.trusting(t.getUUID("Id"));
                names.put(t.getUUID("Id"), t.getString("Name"));
            }
            var claim = new Claim(id, c.getString("Name"), deed, names, BlockPos.of(c.getLong("Centre")), c.getInt("Price"), c.getLong("BoughtAt"));
            data.claims.put(id, claim);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var claim : claims.values()) {
            var c = new CompoundTag();
            c.putString("Id", claim.id().toString());
            c.putString("Name", claim.name());
            c.putUUID("Owner", claim.deed().owner());
            c.putString("OwnerName", claim.ownerName());
            var trusted = new ListTag();
            for (var id : claim.deed().trusted()) {
                var t = new CompoundTag();
                t.putUUID("Id", id);
                t.putString("Name", claim.names().getOrDefault(id, ""));
                trusted.add(t);
            }
            c.put("Trusted", trusted);
            c.putLong("Centre", claim.centre().asLong());
            c.putInt("Price", claim.pricePaid());
            c.putLong("BoughtAt", claim.boughtAt());
            list.add(c);
        }
        tag.put("Claims", list);
        return tag;
    }
    /** effects: the claim on the village, or null. */
    public Claim get(VillageId id) { return claims.get(id); }
    public Collection<Claim> all() { return Collections.unmodifiableCollection(claims.values()); }
    /** effects: records the claim unless the village is already claimed; returns whether it did. */
    public boolean claim(Claim claim) {
        if (claims.containsKey(claim.id())) return false;
        claims.put(claim.id(), claim);
        setDirty();
        return true;
    }
    /** requires: the village is claimed; effects: replaces its claim. */
    public void put(Claim claim) {
        if (!claims.containsKey(claim.id())) throw new IllegalStateException("not claimed: " + claim.id());
        claims.put(claim.id(), claim);
        setDirty();
    }
    /** effects: tears up the claim; returns it, or null when there was none. */
    public Claim revoke(VillageId id) {
        var removed = claims.remove(id);
        if (removed != null) setDirty();
        return removed;
    }
}
