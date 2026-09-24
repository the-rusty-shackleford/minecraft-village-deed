/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** A village a deed can be sold for: a named territory with a stable identity. Implementations
 * are immutable values; two villages with equal ids are the same village.
 * <p>Spec: {@link #bounds} holds every block the village's law covers and contains
 * {@link #centre}; {@link #contains} is exactly bounds membership. */
public interface Village {
    VillageId id();
    /** effects: a readable name, never empty. */
    String name();
    BoundingBox bounds();
    default boolean contains(BlockPos pos) { return bounds().isInside(pos); }
    default BlockPos centre() { return bounds().getCenter(); }
}
