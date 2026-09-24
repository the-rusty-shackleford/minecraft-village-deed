/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the actor is the owner / trusted / a stranger; trusting a stranger, the owner, a
 * player already trusted; distrusting a trusted and an absent player; transfer to a stranger and
 * to a trusted player; the record's invariants (owner on the roster, roster immutability). */
final class DeedTest {
    private static final UUID OWNER = new UUID(1, 1), FRIEND = new UUID(2, 2), STRANGER = new UUID(3, 3);
    private static final Deed DEED = Deed.of(OWNER).trusting(FRIEND);

    @Test void tiersAndPermission() {
        assertEquals(Deed.Tier.OWNER, DEED.tier(OWNER));
        assertEquals(Deed.Tier.TRUSTED, DEED.tier(FRIEND));
        assertEquals(Deed.Tier.STRANGER, DEED.tier(STRANGER));
        assertTrue(DEED.permits(OWNER));
        assertTrue(DEED.permits(FRIEND));
        assertFalse(DEED.permits(STRANGER));
    }
    @Test void rosterChanges() {
        assertEquals(new Deed(OWNER, Set.of()), Deed.of(OWNER));
        assertEquals(DEED, DEED.trusting(FRIEND), "trusting twice is the same roster");
        assertEquals(Set.of(FRIEND, STRANGER), DEED.trusting(STRANGER).trusted());
        assertEquals(Set.of(), DEED.distrusting(FRIEND).trusted());
        assertEquals(DEED, DEED.distrusting(STRANGER), "distrusting an absent player changes nothing");
        assertThrows(IllegalArgumentException.class, () -> DEED.trusting(OWNER));
    }
    @Test void transferIsACleanBreak() {
        var toStranger = DEED.transferredTo(STRANGER);
        assertEquals(new Deed(STRANGER, Set.of(FRIEND)), toStranger);
        assertFalse(toStranger.permits(OWNER), "the old owner is a stranger now");
        var toFriend = DEED.transferredTo(FRIEND);
        assertEquals(new Deed(FRIEND, Set.of()), toFriend, "the new owner leaves the roster");
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> new Deed(OWNER, Set.of(OWNER)));
        assertThrows(NullPointerException.class, () -> new Deed(null, Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> DEED.trusted().add(STRANGER));
    }
}
