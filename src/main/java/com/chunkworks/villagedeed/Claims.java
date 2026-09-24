/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.Deed;
import com.chunkworks.villagedeed.village.StructureVillages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
    /** What nfx's 1.0.0 charged for every village; the price a claim carried over from it records. */
    public static final int LEGACY_PRICE = 45;
    private final Map<VillageId, Claim> claims = new LinkedHashMap<>();
    /** Claims carried over from 1.0.0 whose centre is still the start chunk's middle at y 0,
     * until {@link #survey} finds their structure. */
    private final Set<VillageId> unsurveyed = new HashSet<>();
    private Claims() {}
    /** effects: the dimension's claims, every claim carried over from 1.0.0 surveyed first. */
    public static Claims get(ServerLevel level) {
        var claims = level.getDataStorage().computeIfAbsent(FACTORY, VillageDeed.ID + "_claims");
        claims.survey(level);
        return claims;
    }

    /** effects: the claims the tag holds, in 2.0.0's layout ({@code Owner}, {@code Id}, …) and in
     * nfx's 1.0.0 layout ({@code Buyer}, {@code BuyerName}, {@code VillageId} as the structure
     * start's chunk packed long, {@code VillageName}, {@code BoughtAt}), which 2.0.0 read past in
     * silence (D-0003). A 1.0.0 claim is its buyer's, on {@code structure:<chunk>}, the identity
     * 2.0.0 gives the same village, at 1.0.0's flat price, centred on the start chunk until
     * surveyed; anything carried over marks the data dirty so the next save writes 2.0.0's
     * layout. Public so a test can read a layout; the game reads through the factory. */
    public static Claims load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new Claims();
        var list = tag.getList("Claims", Tag.TAG_COMPOUND);
        int carried = 0;
        for (int i = 0; i < list.size(); i++) {
            var c = list.getCompound(i);
            if (c.hasUUID("Owner") && c.contains("Id")) {
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
            } else if (c.hasUUID("Buyer") && c.contains("VillageId", Tag.TAG_LONG)) {
                long packed = c.getLong("VillageId");
                var id = new VillageId(StructureVillages.ID, Long.toString(packed));
                var buyer = c.getUUID("Buyer");
                var name = c.getString("VillageName").isEmpty() ? "Village" : c.getString("VillageName");
                var centre = new BlockPos(ChunkPos.getX(packed) * 16 + 8, 0, ChunkPos.getZ(packed) * 16 + 8);
                var claim = new Claim(id, name, Deed.of(buyer), Map.of(buyer, c.getString("BuyerName")), centre, LEGACY_PRICE, c.getLong("BoughtAt"));
                if (data.claims.putIfAbsent(id, claim) == null) { data.unsurveyed.add(id); carried++; }
            }
        }
        if (carried > 0) {
            VillageDeed.LOGGER.info("{} claims from Village Deed 1.0.0 carried over; their centres are surveyed on first use", carried);
            data.setDirty();
        }
        return data;
    }
    /** effects: gives every claim carried over from 1.0.0 its structure's name and centre, loading
     * the start chunk to find the structure; a claim whose structure is not there any more keeps
     * the chunk's middle and is logged. Each claim is surveyed once. */
    public void survey(ServerLevel level) {
        if (unsurveyed.isEmpty()) return;
        for (var id : List.copyOf(unsurveyed)) {
            unsurveyed.remove(id);
            var claim = claims.get(id);
            if (claim == null) continue;
            long packed;
            try { packed = Long.parseLong(id.key()); } catch (NumberFormatException e) { continue; }
            level.getChunk(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            var village = VillageProviders.byId(level, id);
            if (village.isEmpty()) { VillageDeed.LOGGER.warn("no village structure at {} for {}'s claim on {}; its centre stays the chunk's middle", id, claim.ownerName(), claim.name()); continue; }
            claims.put(id, new Claim(id, village.get().name(), claim.deed(), claim.names(), village.get().centre(), claim.pricePaid(), claim.boughtAt()));
            VillageDeed.LOGGER.info("{}'s claim on {} ({}) carried over from 1.0.0, centred at {}", claim.ownerName(), village.get().name(), id, village.get().centre().toShortString());
        }
        setDirty();
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
