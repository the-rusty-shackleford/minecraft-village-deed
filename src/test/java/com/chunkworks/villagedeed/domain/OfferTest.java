/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: before, at and after the expiry tick; the invariants (empty names, a price that
 * is not positive). */
final class OfferTest {
    @Test void liveUntilTheTick() {
        var offer = new Offer("structure:42", "Plains Village", 45, 600);
        assertTrue(offer.live(0));
        assertTrue(offer.live(600));
        assertFalse(offer.live(601));
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> new Offer("", "Plains Village", 45, 600));
        assertThrows(IllegalArgumentException.class, () -> new Offer("structure:42", "", 45, 600));
        assertThrows(IllegalArgumentException.class, () -> new Offer("structure:42", "Plains Village", 0, 600));
    }
}
