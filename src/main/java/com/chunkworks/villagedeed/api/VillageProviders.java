/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** The registered providers, asked in registration order: the first to recognise a village at a
 * position answers for it, so a mod that wants precedence registers before the deed mod does
 * (mods load in dependency order; declare the ordering in your mods.toml). */
public final class VillageProviders {
    private static final List<VillageProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    private VillageProviders() {}
    /** effects: adds the provider last; throws IllegalStateException on a second provider with the same id. */
    public static void register(VillageProvider provider) {
        for (var p : PROVIDERS) if (p.id().equals(provider.id())) throw new IllegalStateException("a village provider named " + p.id() + " is already registered");
        PROVIDERS.add(provider);
    }
    public static List<VillageProvider> all() { return List.copyOf(PROVIDERS); }
    /** effects: the first provider's village whose bounds hold the position. */
    public static Optional<Village> at(ServerLevel level, BlockPos pos) {
        for (var p : PROVIDERS) { var v = p.at(level, pos); if (v.isPresent()) return v; }
        return Optional.empty();
    }
    /** effects: the nearest village any provider knows within the radius. */
    public static Optional<Village> near(ServerLevel level, BlockPos pos, int radius) {
        Village best = null;
        double bestDistance = Double.MAX_VALUE;
        for (var p : PROVIDERS) {
            var v = p.near(level, pos, radius);
            if (v.isEmpty()) continue;
            double d = distanceSquared(v.get().bounds(), pos);
            if (d < bestDistance) { bestDistance = d; best = v.get(); }
        }
        return Optional.ofNullable(best);
    }
    /** effects: the village the id names, from the provider it belongs to. */
    public static Optional<Village> byId(ServerLevel level, VillageId id) {
        for (var p : PROVIDERS) if (p.id().equals(id.provider())) return p.byId(level, id);
        return Optional.empty();
    }
    /** effects: the squared distance from the position to the nearest point of the box, zero inside. */
    public static double distanceSquared(BoundingBox box, BlockPos pos) {
        double dx = Math.max(0, Math.max(box.minX() - pos.getX(), pos.getX() - box.maxX()));
        double dy = Math.max(0, Math.max(box.minY() - pos.getY(), pos.getY() - box.maxY()));
        double dz = Math.max(0, Math.max(box.minZ() - pos.getZ(), pos.getZ() - box.maxZ()));
        return dx * dx + dy * dy + dz * dz;
    }
}
