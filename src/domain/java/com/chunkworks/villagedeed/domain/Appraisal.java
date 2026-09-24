/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** What a village is worth under a tariff, and why. Immutable.
 * <p>AF: {@code price} is what the villagers ask; {@code lines} are the contributions that made
 * it, one per category in the tariff's order, categories worth nothing left out; {@code raw} is
 * the sum before rounding, the multiplier and the bounds.
 * <p>RI: price is a multiple of five within the bounds of the tariff that produced it; lines is
 * unmodifiable and ordered by category. */
public record Appraisal(int price, double raw, List<Line> lines) {
    /** One category's contribution: how many of it were counted and what they add. */
    public record Line(Tariff.Category category, int count, double value) {}
    public Appraisal { lines = List.copyOf(lines); }

    /** effects: the appraisal of the census under the tariff: base plus every counter and every
     * priced block and item, rounded to the nearest five, times the multiplier, kept within the
     * floor and the ceiling. */
    public static Appraisal of(Census census, Tariff tariff) {
        var counts = new EnumMap<Tariff.Category, Integer>(Tariff.Category.class);
        var values = new EnumMap<Tariff.Category, Double>(Tariff.Category.class);
        count(counts, values, Tariff.Category.HOMES, census.beds(), tariff.perBed());
        count(counts, values, Tariff.Category.PEOPLE, census.villagers(), tariff.perVillager());
        count(counts, values, Tariff.Category.TRADES, census.tradeLevels(), tariff.perTradeLevel());
        count(counts, values, Tariff.Category.WORKSTATIONS, census.jobSites(), tariff.perJobSite());
        count(counts, values, Tariff.Category.BELLS, census.bells(), tariff.perBell());
        count(counts, values, Tariff.Category.GUARDS, census.golems(), tariff.perGolem());
        count(counts, values, Tariff.Category.LAND, census.footprint(), tariff.perFootprint());
        for (var e : census.blocks().entrySet()) {
            var rate = tariff.blocks().get(e.getKey());
            if (rate != null) count(counts, values, rate.category(), e.getValue(), rate.value());
        }
        for (var e : census.items().entrySet()) {
            var rate = tariff.items().get(e.getKey());
            if (rate != null) count(counts, values, rate.category(), e.getValue(), rate.value());
        }
        var lines = new ArrayList<Line>();
        double sum = 0;
        for (var category : Tariff.Category.values()) {
            int n = counts.getOrDefault(category, 0);
            if (n == 0) continue;
            double v = values.getOrDefault(category, 0.0);
            sum += v;
            lines.add(new Line(category, n, v));
        }
        double raw = tariff.base() + sum;
        int price = Math.clamp(roundToFive(raw * tariff.multiplier()), tariff.floor(), tariff.ceiling());
        return new Appraisal(price, raw, lines);
    }
    private static void count(EnumMap<Tariff.Category, Integer> counts, EnumMap<Tariff.Category, Double> values, Tariff.Category category, int n, double rate) {
        if (n == 0) return;
        counts.merge(category, n, Integer::sum);
        values.merge(category, n * rate, Double::sum);
    }
    /** effects: the nearest multiple of five, halves rounding up. */
    static int roundToFive(double x) { return (int) Math.round(x / 5.0) * 5; }
}
