/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import com.chunkworks.villagedeed.api.VillageProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/** The one question the mixin asks: does a deed on the village at this position exempt this
 * player from its law? */
public final class Exemptions {
    private Exemptions() {}
    /** effects: true when the position lies in a bought village whose deed names the criminal as
     * owner or trusted, or when the server lets everyone use bought villages. */
    public static boolean exempt(ServerLevel level, LivingEntity criminal, BlockPos pos) {
        var village = VillageProviders.at(level, pos);
        if (village.isEmpty()) return false;
        var claim = Claims.get(level).get(village.get().id());
        if (claim == null) return false;
        return DeedConfig.EVERYONE_IS_IMMUNE.get() || claim.deed().permits(criminal.getUUID());
    }
}
