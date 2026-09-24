/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: a vanilla id; a nested id with several words; an id without the village_ prefix;
 * an id whose last segment is one word; empty and prefix-only paths. */
final class VillageNamesTest {
    @Test void names() {
        assertEquals("Plains Village", VillageNames.fromStructurePath("village_plains"));
        assertEquals("Desert Oasis Village", VillageNames.fromStructurePath("large/village_desert_oasis"));
        assertEquals("Fortified Desert Village", VillageNames.fromStructurePath("fortified_desert_village"));
        assertEquals("Desert", VillageNames.fromStructurePath("ctov/village/desert"));
        assertEquals("Village", VillageNames.fromStructurePath(""));
        assertEquals("Village", VillageNames.fromStructurePath("village_"));
    }
}
