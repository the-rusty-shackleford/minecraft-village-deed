/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Who holds a village's deed and whom they let use the village. Immutable.
 * <p>AF: {@code owner} bought the village or was handed it; {@code trusted} are the players the
 * owner named since. The deed exempts the owner and the trusted from the village's law.
 * <p>RI: owner is non-null; trusted is unmodifiable and never holds the owner. */
public record Deed(UUID owner, Set<UUID> trusted) {
    public enum Tier { OWNER, TRUSTED, STRANGER }
    public Deed {
        Objects.requireNonNull(owner, "owner");
        trusted = Set.copyOf(trusted);
        if (trusted.contains(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
    }
    /** effects: a fresh deed in {@code owner}'s name with nobody else on it. */
    public static Deed of(UUID owner) { return new Deed(owner, Set.of()); }
    /** requires: who is not the owner; effects: this deed with {@code who} trusted. */
    public Deed trusting(UUID who) {
        if (who.equals(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
        var roster = new HashSet<>(trusted);
        roster.add(who);
        return new Deed(owner, roster);
    }
    /** effects: this deed with {@code who} off the roster; the same deed when they were not on it. */
    public Deed distrusting(UUID who) {
        var roster = new HashSet<>(trusted);
        roster.remove(who);
        return new Deed(owner, roster);
    }
    /** effects: this deed in {@code who}'s name. The roster stays, less the new owner; the old
     * owner is not put on it, so handing a village over is a clean break. */
    public Deed transferredTo(UUID who) {
        var roster = new HashSet<>(trusted);
        roster.remove(who);
        return new Deed(who, roster);
    }
    /** effects: the player's standing under this deed. */
    public Tier tier(UUID who) {
        return who.equals(owner) ? Tier.OWNER : trusted.contains(who) ? Tier.TRUSTED : Tier.STRANGER;
    }
    /** effects: whether the deed exempts the player: the owner and the trusted, nobody else. */
    public boolean permits(UUID who) { return tier(who) != Tier.STRANGER; }
}
