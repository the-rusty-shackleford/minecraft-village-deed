/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;

/** A source of villages: what recognises a village at a position and resolves a saved id back
 * to it. Register one with {@link VillageProviders#register} from a mod constructor; the deed mod
 * ships {@code structure}, which recognises every worldgen structure in the tag
 * {@code #villagedeed:villages}. Answers must be consistent: the village {@link #at} returns for
 * a position contains it, and {@link #byId} on that village's id returns an equal village while
 * its chunks are loaded. */
public interface VillageProvider {
    /** effects: this provider's name, the first half of every id it issues; no colon. */
    String id();
    /** effects: the village whose bounds hold the position, when this provider knows one. */
    Optional<Village> at(ServerLevel level, BlockPos pos);
    /** requires: radius >= 0; effects: the nearest village whose bounds lie within {@code radius}
     * blocks of the position, for a villager who has wandered into the fields; empty when none. */
    Optional<Village> near(ServerLevel level, BlockPos pos, int radius);
    /** effects: the village a saved id names, or empty when the id is not this provider's or the
     * village cannot be resolved right now (its chunks unloaded); never a guess. */
    Optional<Village> byId(ServerLevel level, VillageId id);
}
