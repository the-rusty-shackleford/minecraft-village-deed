/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: emeralds alone suffice; blocks needed with change; blocks landing exactly; blocks
 * alone; the price exactly met; too poor; a price that is not positive; negative counts. */
final class PaymentTest {
    @Test void emeraldsFirst() {
        assertEquals(Optional.of(new Payment.Plan(45, 0, 0)), Payment.plan(45, 50, 3));
        assertEquals(Optional.of(new Payment.Plan(45, 0, 0)), Payment.plan(45, 45, 0), "exactly met");
    }
    @Test void blocksCoverTheRestWithChange() {
        assertEquals(Optional.of(new Payment.Plan(4, 5, 4)), Payment.plan(45, 4, 6));
        assertEquals(Optional.of(new Payment.Plan(0, 5, 0)), Payment.plan(45, 0, 5), "blocks alone, landing exactly");
        assertEquals(Optional.of(new Payment.Plan(0, 2, 3)), Payment.plan(15, 0, 2));
    }
    @Test void tooPoor() {
        assertEquals(Optional.empty(), Payment.plan(45, 4, 4));
        assertEquals(40, Payment.worth(4, 4));
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> Payment.plan(0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> Payment.plan(10, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> Payment.worth(0, -1));
        assertThrows(IllegalArgumentException.class, () -> new Payment.Plan(1, 1, 9));
    }
}
