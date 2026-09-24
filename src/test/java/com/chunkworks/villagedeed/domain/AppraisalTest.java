/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import com.chunkworks.villagedeed.domain.Tariff.Category;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the empty census (the floor, no lines); each counter priced at its rate with its
 * own line; blocks and items grouped by category with counts summed and unpriced ids ignored;
 * rounding to five below and above a half; the ceiling and the floor clamping; the multiplier;
 * lines in category order with worthless categories left out; the tariff's invariants. */
final class AppraisalTest {
    private static final Tariff T = Tariff.STANDARD;

    @Test void emptyVillageIsWorthTheFloor() {
        var a = Appraisal.of(Census.EMPTY, T);
        assertEquals(15, a.price());
        assertEquals(15.0, a.raw());
        assertEquals(List.of(), a.lines());
    }
    @Test void eachCounterAddsItsRate() {
        var census = new Census(4, 3, 5, 2, 1, 2, 6, Map.of(), Map.of());
        var a = Appraisal.of(census, T);
        assertEquals(List.of(
                new Appraisal.Line(Category.HOMES, 4, 4.0), new Appraisal.Line(Category.PEOPLE, 3, 6.0),
                new Appraisal.Line(Category.TRADES, 5, 5.0), new Appraisal.Line(Category.WORKSTATIONS, 2, 4.0),
                new Appraisal.Line(Category.BELLS, 1, 3.0), new Appraisal.Line(Category.GUARDS, 2, 6.0),
                new Appraisal.Line(Category.LAND, 6, 3.0)), a.lines());
        assertEquals(15 + 31.0, a.raw());
        assertEquals(45, a.price());
    }
    @Test void blocksAndItemsGroupByCategory() {
        var census = Census.builder().block("minecraft:chest", 3).block("minecraft:barrel", 2).block("minecraft:enchanting_table", 1)
                .block("minecraft:diamond_block", 2).block("minecraft:stone", 500).item("minecraft:diamond", 4).item("minecraft:dirt", 64).build();
        var a = Appraisal.of(census, T);
        assertEquals(List.of(
                new Appraisal.Line(Category.STORAGE, 5, 5.0), new Appraisal.Line(Category.CRAFTING, 1, 10.0),
                new Appraisal.Line(Category.VALUABLES, 2, 30.0), new Appraisal.Line(Category.LOOT, 4, 4.0)), a.lines());
        assertEquals(15 + 49.0, a.raw());
        assertEquals(65, a.price());
    }
    @Test void roundingToFive() {
        assertEquals(25, Appraisal.of(new Census(12, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), T).price(), "27 rounds down");
        assertEquals(30, Appraisal.of(new Census(13, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), T).price(), "28 rounds up");
        assertEquals(30, Appraisal.roundToFive(27.5), "a half rounds up");
    }
    @Test void boundsAndMultiplier() {
        var rich = new Census(100, 100, 100, 50, 5, 10, 100, Map.of(), Map.of());
        assertEquals(150, Appraisal.of(rich, T).price(), "clamped to the ceiling");
        assertEquals(610, Appraisal.of(rich, T.withPrices(15, 1000, 1.0)).price(), "100 + 200 + 100 + 100 + 15 + 30 + 50, plus the base");
        assertEquals(15, Appraisal.of(Census.EMPTY, T.withPrices(15, 150, 0.1)).price(), "never below the floor");
        assertEquals(90, Appraisal.of(new Census(30, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), T.withPrices(15, 1000, 2.0)).price(), "45 doubled");
        assertThrows(IllegalArgumentException.class, () -> T.withPrices(150, 15, 1.0));
        assertThrows(IllegalArgumentException.class, () -> T.withPrices(15, 150, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tariff.Rate(Category.LOOT, -1));
    }
}
