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
 * lines in category order with worthless categories left out; the tariff's invariants; the
 * standard tariff as the weights at an emerald for eight points, and the prices it gives the
 * villages D-0005 surveyed. */
final class AppraisalTest {
    private static final Tariff T = Tariff.STANDARD;
    /** D-0001's weights paid a point an emerald, between its bounds: the arithmetic below reads in whole numbers. */
    private static final Tariff P = T.scaled(1 / Tariff.EMERALDS_PER_POINT).withPrices(15, 150, 1.0);

    @Test void emptyVillageIsWorthTheFloor() {
        var a = Appraisal.of(Census.EMPTY, P);
        assertEquals(15, a.price());
        assertEquals(15.0, a.raw());
        assertEquals(List.of(), a.lines());
    }
    @Test void eachCounterAddsItsRate() {
        var census = new Census(4, 3, 5, 2, 1, 2, 6, Map.of(), Map.of());
        var a = Appraisal.of(census, P);
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
        var a = Appraisal.of(census, P);
        assertEquals(List.of(
                new Appraisal.Line(Category.STORAGE, 5, 5.0), new Appraisal.Line(Category.CRAFTING, 1, 10.0),
                new Appraisal.Line(Category.VALUABLES, 2, 30.0), new Appraisal.Line(Category.LOOT, 4, 4.0)), a.lines());
        assertEquals(15 + 49.0, a.raw());
        assertEquals(65, a.price());
    }
    @Test void roundingToFive() {
        assertEquals(25, Appraisal.of(new Census(12, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), P).price(), "27 rounds down");
        assertEquals(30, Appraisal.of(new Census(13, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), P).price(), "28 rounds up");
        assertEquals(30, Appraisal.roundToFive(27.5), "a half rounds up");
    }
    @Test void boundsAndMultiplier() {
        var rich = new Census(100, 100, 100, 50, 5, 10, 100, Map.of(), Map.of());
        assertEquals(150, Appraisal.of(rich, P).price(), "clamped to the ceiling");
        assertEquals(610, Appraisal.of(rich, P.withPrices(15, 1000, 1.0)).price(), "100 + 200 + 100 + 100 + 15 + 30 + 50, plus the base");
        assertEquals(15, Appraisal.of(Census.EMPTY, P.withPrices(15, 150, 0.1)).price(), "never below the floor");
        assertEquals(90, Appraisal.of(new Census(30, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()), P.withPrices(15, 1000, 2.0)).price(), "45 doubled");
        assertThrows(IllegalArgumentException.class, () -> P.withPrices(150, 15, 1.0));
        assertThrows(IllegalArgumentException.class, () -> P.withPrices(15, 150, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tariff.Rate(Category.LOOT, -1));
        assertThrows(IllegalArgumentException.class, () -> P.scaled(0));
        assertThrows(IllegalArgumentException.class, () -> P.scaled(Double.NaN));
    }
    @Test void theStandardTariffIsTheWeightsAtAnEmeraldForEightPoints() {
        assertEquals(0.125, Tariff.EMERALDS_PER_POINT);
        assertEquals(15, T.base()); assertEquals(15, T.floor()); assertEquals(100, T.ceiling()); assertEquals(1.0, T.multiplier());
        assertEquals(1.0, P.perBed()); assertEquals(2.0, P.perVillager()); assertEquals(1.0, P.perTradeLevel()); assertEquals(2.0, P.perJobSite());
        assertEquals(3.0, P.perBell()); assertEquals(3.0, P.perGolem()); assertEquals(0.5, P.perFootprint());
        assertEquals(new Tariff.Rate(Category.CRAFTING, 10), P.blocks().get("minecraft:enchanting_table"));
        assertEquals(new Tariff.Rate(Category.VALUABLES, 0.25), P.blocks().get("minecraft:lantern"));
        assertEquals(new Tariff.Rate(Category.LOOT, 1), P.items().get("minecraft:diamond"));
        assertEquals(T, P.scaled(Tariff.EMERALDS_PER_POINT).withPrices(15, 100, 1.0), "and back: an eighth is exact");
    }
    /** The anchors are D-0005's survey of the 599 villages the server's world had generated, in points
     * above the base; a bed is a point, so a census of beds stands for a village of that many points. */
    @Test void theSurveyedVillagesSpreadFromTheFloorToTheCeiling() {
        assertEquals(15, Appraisal.of(Census.EMPTY, T).price(), "a bare hamlet: the floor");
        assertEquals(20, points(46), "the poorest surveyed, a vanilla taiga village of 61.5: 15 + 5.75");
        assertEquals(50, points(280), "the median, 295: 15 + 35");
        assertEquals(100, points(723), "the richest, 738: 15 + 90.4 held to the ceiling");
        assertEquals(new Appraisal.Line(Category.HOMES, 280, 35.0), Appraisal.of(beds(280), T).lines().get(0), "the lines read in emeralds");
    }
    private static int points(int n) { return Appraisal.of(beds(n), T).price(); }
    private static Census beds(int n) { return new Census(n, 0, 0, 0, 0, 0, 0, Map.of(), Map.of()); }
}
