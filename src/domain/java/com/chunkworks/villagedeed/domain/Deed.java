/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.Objects;
import java.util.UUID;

/** Who holds a village's deed. Immutable.
 * <p>AF: {@code owner} bought the village or was handed it. Whom else the deed lets use the
 * village is the owner's {@link Roster}, one list for every village they hold (D-0004).
 * <p>RI: owner is non-null. */
public record Deed(UUID owner) {
    public Deed { Objects.requireNonNull(owner, "owner"); }
    /** effects: a deed in {@code owner}'s name. */
    public static Deed of(UUID owner) { return new Deed(owner); }
    /** effects: this deed in {@code who}'s name. The village falls under the new owner's roster
     * and the old owner is a stranger to it, so handing a village over is a clean break. */
    public Deed transferredTo(UUID who) { return new Deed(who); }
}
