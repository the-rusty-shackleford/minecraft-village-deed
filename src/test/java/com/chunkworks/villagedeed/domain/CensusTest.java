/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the empty census; a builder accumulating every kind of finding, with villager
 * levels at novice and above; the invariants (a negative counter, a non-positive map count, map
 * immutability, a builder given a bad level or count). */
final class CensusTest {
    @Test void emptyHoldsNothing() {
        assertEquals(new Census(0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), Census.EMPTY);
        assertEquals(Census.EMPTY, Census.builder().build());
    }
    @Test void builderAccumulates() {
        var census = Census.builder().bed().bed().villager(1).villager(3).villager(5).jobSite().bell().golem().footprint(4)
                .block("minecraft:chest", 2).block("minecraft:chest", 1).item("minecraft:diamond", 3).build();
        assertEquals(new Census(2, 3, 6, 1, 1, 1, 4, Map.of("minecraft:chest", 3), Map.of("minecraft:diamond", 3)), census);
    }
    @Test void invariants() {
        assertThrows(IllegalArgumentException.class, () -> new Census(-1, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Census(0, 0, 0, 0, 0, 0, 0, Map.of("minecraft:chest", 0), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Census(0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of("minecraft:diamond", -2)));
        assertThrows(UnsupportedOperationException.class, () -> Census.EMPTY.blocks().put("minecraft:chest", 1));
        assertThrows(IllegalArgumentException.class, () -> Census.builder().villager(0));
        assertThrows(IllegalArgumentException.class, () -> Census.builder().block("minecraft:chest", 0));
        assertThrows(IllegalArgumentException.class, () -> Census.builder().footprint(-1));
    }
}
