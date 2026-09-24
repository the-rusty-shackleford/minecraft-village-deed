/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

/** A village put up for sale to one player at a price the appraisal fixed, good until a tick, so
 * the price the player clicked is the price they pay. Immutable.
 * RI: village and name non-empty; price > 0. */
public record Offer(String village, String name, int price, long expiresAt) {
    public Offer {
        if (village.isEmpty() || name.isEmpty()) throw new IllegalArgumentException("village");
        if (price <= 0) throw new IllegalArgumentException("price");
    }
    /** effects: whether the offer still stands at the tick. */
    public boolean live(long now) { return now <= expiresAt; }
}
