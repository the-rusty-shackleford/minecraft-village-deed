/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: a fresh deed; transfer to another player and to the owner themselves; the record's
 * invariant (owner non-null). Who else a deed lets in is the owner's roster (RosterTest). */
final class DeedTest {
    private static final UUID OWNER = new UUID(1, 1), OTHER = new UUID(2, 2);

    @Test void freshDeedIsTheOwners() {
        assertEquals(new Deed(OWNER), Deed.of(OWNER));
        assertEquals(OWNER, Deed.of(OWNER).owner());
    }
    @Test void transferIsACleanBreak() {
        assertEquals(new Deed(OTHER), Deed.of(OWNER).transferredTo(OTHER), "the deed is the new owner's alone");
        assertEquals(Deed.of(OWNER), Deed.of(OWNER).transferredTo(OWNER), "handing a village to its owner changes nothing");
    }
    @Test void invariants() {
        assertThrows(NullPointerException.class, () -> new Deed(null));
    }
}
