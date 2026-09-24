/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** The rates an appraisal applies to a census, and the bounds it keeps the price within.
 * Immutable. The standard rates are the mod's; a server tunes the bounds and the multiplier.
 * <p>AF: {@code base} is what any village is worth before counting; the {@code per*} rates price
 * the census counters; {@code blocks} and {@code items} price the blocks standing in the village
 * and the items in its containers by id, each under a category the breakdown groups by; the price
 * is rounded to five, multiplied by {@code multiplier} and clamped to [floor, ceiling].
 * <p>RI: 0 < floor <= ceiling; base >= 0; multiplier > 0; no rate negative; maps unmodifiable. */
public record Tariff(int base, int floor, int ceiling, double multiplier,
                     double perBed, double perVillager, double perTradeLevel, double perJobSite, double perBell, double perGolem, double perFootprint,
                     Map<String, Rate> blocks, Map<String, Rate> items) {
    /** The lines of a breakdown, in the order they are listed. */
    public enum Category { HOMES, PEOPLE, TRADES, WORKSTATIONS, BELLS, GUARDS, LAND, STORAGE, CRAFTING, VALUABLES, LOOT }
    /** What one block or item is worth, and where it is listed. RI: value >= 0. */
    public record Rate(Category category, double value) {
        public Rate { if (value < 0) throw new IllegalArgumentException("value"); }
    }
    public Tariff {
        if (floor <= 0 || ceiling < floor) throw new IllegalArgumentException("floor and ceiling");
        if (base < 0 || multiplier <= 0) throw new IllegalArgumentException("base or multiplier");
        for (double r : new double[] { perBed, perVillager, perTradeLevel, perJobSite, perBell, perGolem, perFootprint })
            if (r < 0) throw new IllegalArgumentException("a rate is negative");
        blocks = Map.copyOf(blocks);
        items = Map.copyOf(items);
    }
    /** effects: this tariff with other bounds and multiplier. */
    public Tariff withPrices(int floor, int ceiling, double multiplier) {
        return new Tariff(base, floor, ceiling, multiplier, perBed, perVillager, perTradeLevel, perJobSite, perBell, perGolem, perFootprint, blocks, items);
    }

    /** The mod's rates. A job site is a workstation whether or not a villager works it; a block
     * that is also a job site (a barrel, a brewing stand) counts as both, on purpose. Anything not
     * named here is worth nothing. */
    public static final Tariff STANDARD;
    static {
        var blocks = new LinkedHashMap<String, Rate>();
        storage(blocks, "minecraft:chest", 1); storage(blocks, "minecraft:trapped_chest", 1); storage(blocks, "minecraft:barrel", 1);
        crafting(blocks, "minecraft:crafting_table", 1); crafting(blocks, "minecraft:furnace", 1);
        crafting(blocks, "minecraft:anvil", 2); crafting(blocks, "minecraft:chipped_anvil", 2); crafting(blocks, "minecraft:damaged_anvil", 2);
        crafting(blocks, "minecraft:enchanting_table", 10); crafting(blocks, "minecraft:brewing_stand", 3);
        crafting(blocks, "minecraft:bookshelf", 0.5); crafting(blocks, "minecraft:chiseled_bookshelf", 0.5);
        valuable(blocks, "minecraft:copper_block", 1); valuable(blocks, "minecraft:iron_block", 3); valuable(blocks, "minecraft:gold_block", 5);
        valuable(blocks, "minecraft:emerald_block", 8); valuable(blocks, "minecraft:diamond_block", 15); valuable(blocks, "minecraft:netherite_block", 40);
        valuable(blocks, "minecraft:lantern", 0.25); valuable(blocks, "minecraft:soul_lantern", 0.25);
        var items = new LinkedHashMap<String, Rate>();
        loot(items, "minecraft:copper_ingot", 0.05); loot(items, "minecraft:iron_ingot", 0.1); loot(items, "minecraft:gold_ingot", 0.3);
        loot(items, "minecraft:emerald", 0.5); loot(items, "minecraft:diamond", 1); loot(items, "minecraft:netherite_ingot", 5);
        loot(items, "minecraft:enchanted_book", 2);
        STANDARD = new Tariff(15, 15, 150, 1.0, 1, 2, 1, 2, 3, 3, 0.5, blocks, items);
    }
    private static void storage(Map<String, Rate> m, String id, double v) { m.put(id, new Rate(Category.STORAGE, v)); }
    private static void crafting(Map<String, Rate> m, String id, double v) { m.put(id, new Rate(Category.CRAFTING, v)); }
    private static void valuable(Map<String, Rate> m, String id, double v) { m.put(id, new Rate(Category.VALUABLES, v)); }
    private static void loot(Map<String, Rate> m, String id, double v) { m.put(id, new Rate(Category.LOOT, v)); }
}
