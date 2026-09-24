/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.village;

import com.chunkworks.villagedeed.VillageDeed;
import com.chunkworks.villagedeed.api.Village;
import com.chunkworks.villagedeed.api.VillageId;
import com.chunkworks.villagedeed.api.VillageProvider;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.VillageNames;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;

/** The villages that are worldgen structures: every structure in {@code #villagedeed:villages},
 * which holds Thief's protected structures (vanilla and CTOV villages) and Terralith's fortified
 * villages. A village's key is its structure start's chunk, the identity the game itself keeps
 * for it, and its bounds are the start's bounding box, so a village covers its roads and fields
 * as well as its houses. */
public final class StructureVillages implements VillageProvider {
    public static final String ID = "structure";
    public static final TagKey<Structure> VILLAGES = TagKey.create(Registries.STRUCTURE, VillageDeed.id("villages"));
    /** A structure as a village. Immutable. */
    public record StructureVillage(VillageId id, String name, BoundingBox bounds) implements Village {}

    @Override public String id() { return ID; }
    @Override public Optional<Village> at(ServerLevel level, BlockPos pos) {
        var start = level.structureManager().getStructureWithPieceAt(pos, VILLAGES);
        if (start.isValid()) return Optional.of(of(level, start));
        // Between the pieces: a road, a field, the yard between two houses.
        for (var e : level.structureManager().getAllStructuresAt(pos).entrySet()) {
            if (!inTag(level, e.getKey())) continue;
            var starts = new ArrayList<StructureStart>();
            level.structureManager().fillStartsForStructure(e.getKey(), e.getValue(), starts::add);
            for (var s : starts) if (s.isValid() && s.getBoundingBox().isInside(pos)) return Optional.of(of(level, s));
        }
        return Optional.empty();
    }
    @Override public Optional<Village> near(ServerLevel level, BlockPos pos, int radius) {
        if (radius < 0) throw new IllegalArgumentException("radius");
        Village best = null;
        double bestDistance = (double) radius * radius;
        int reach = (radius >> 4) + 1;
        var centre = new ChunkPos(pos);
        var seen = new HashSet<Long>();
        for (int cx = centre.x - reach; cx <= centre.x + reach; cx++) for (int cz = centre.z - reach; cz <= centre.z + reach; cz++) {
            var chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) continue;
            for (var e : chunk.getAllReferences().entrySet()) {
                if (!inTag(level, e.getKey())) continue;
                for (long reference : e.getValue()) {
                    if (!seen.add(reference)) continue;
                    var home = level.getChunkSource().getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference));
                    if (home == null) continue;
                    var start = home.getStartForStructure(e.getKey());
                    if (start == null || !start.isValid()) continue;
                    double d = VillageProviders.distanceSquared(start.getBoundingBox(), pos);
                    if (d <= bestDistance) { bestDistance = d; best = of(level, start); }
                }
            }
        }
        return Optional.ofNullable(best);
    }
    @Override public Optional<Village> byId(ServerLevel level, VillageId id) {
        if (!ID.equals(id.provider())) return Optional.empty();
        long packed;
        try { packed = Long.parseLong(id.key()); } catch (NumberFormatException e) { return Optional.empty(); }
        var chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
        if (chunk == null) return Optional.empty();
        for (var e : chunk.getAllStarts().entrySet())
            if (e.getValue().isValid() && inTag(level, e.getKey())) return Optional.of(of(level, e.getValue()));
        return Optional.empty();
    }
    private static boolean inTag(ServerLevel level, Structure structure) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(structure).is(VILLAGES);
    }
    /** effects: the village a structure start is. */
    public static StructureVillage of(ServerLevel level, StructureStart start) {
        var key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
        var name = key == null ? "Village" : VillageNames.fromStructurePath(key.getPath());
        return new StructureVillage(new VillageId(ID, Long.toString(start.getChunkPos().toLong())), name, start.getBoundingBox());
    }
}
