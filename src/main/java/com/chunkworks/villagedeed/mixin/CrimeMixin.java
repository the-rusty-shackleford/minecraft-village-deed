/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.mixin;

import com.chunkworks.villagedeed.Exemptions;
import io.github.mortuusars.thief.world.Crime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bends a bought village's law for the players its deed names. {@code Crime.commit} is the one
 * point every Thief crime passes through: breaking, opening, CarryOn pickup, beds, item frames and
 * killing livestock. Its own first statement is the Hero-of-the-Village early-out returning
 * {@code Outcome.NONE}; this returns the same from HEAD when the deed exempts the criminal, so
 * nothing downstream runs: no gossip, no guard, no "you have been seen", no stat, no event.
 * Hurting or killing a villager is not a Thief crime at all; vanilla's gossip and the guards
 * answer for that, deed or no deed. */
@Mixin(Crime.class)
abstract class CrimeMixin {
    @Inject(method = "commit", at = @At("HEAD"), cancellable = true)
    private void villagedeed$deedExempts(ServerLevel level, LivingEntity criminal, BlockPos pos, CallbackInfoReturnable<Crime.Outcome> cir) {
        if (Exemptions.exempt(level, criminal, pos)) cir.setReturnValue(Crime.Outcome.NONE);
    }
}
