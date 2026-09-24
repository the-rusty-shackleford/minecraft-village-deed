/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.HashMap;
import java.util.Map;

/** What a village holds, counted on the ground: the input to an {@link Appraisal}. Immutable.
 * <p>AF: {@code beds} are its homes, {@code villagers} its adult residents and {@code tradeLevels}
 * the sum of their trade levels above novice, {@code jobSites} its workstations, {@code bells} its
 * meeting points, {@code golems} its guards, {@code footprint} its ground in chunk areas;
 * {@code blocks} maps a block id to how many stand in it and {@code items} an item id to how many
 * sit in its containers.
 * <p>RI: no count is negative; the maps are unmodifiable and every value in them is positive. */
public record Census(int beds, int villagers, int tradeLevels, int jobSites, int bells, int golems, int footprint,
                     Map<String, Integer> blocks, Map<String, Integer> items) {
    public static final Census EMPTY = new Census(0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of());
    public Census {
        for (int n : new int[] { beds, villagers, tradeLevels, jobSites, bells, golems, footprint })
            if (n < 0) throw new IllegalArgumentException("a count is negative");
        blocks = Map.copyOf(blocks);
        items = Map.copyOf(items);
        for (var n : blocks.values()) if (n <= 0) throw new IllegalArgumentException("a block count is not positive");
        for (var n : items.values()) if (n <= 0) throw new IllegalArgumentException("an item count is not positive");
    }
    public static Builder builder() { return new Builder(); }

    /** A census being taken, one finding at a time. */
    public static final class Builder {
        private int beds, villagers, tradeLevels, jobSites, bells, golems, footprint;
        private final Map<String, Integer> blocks = new HashMap<>(), items = new HashMap<>();
        private Builder() {}
        public Builder bed() { beds++; return this; }
        /** requires: level >= 1; effects: one more adult resident whose trade level is {@code level}. */
        public Builder villager(int level) {
            if (level < 1) throw new IllegalArgumentException("level");
            villagers++; tradeLevels += level - 1; return this;
        }
        public Builder jobSite() { jobSites++; return this; }
        public Builder bell() { bells++; return this; }
        public Builder golem() { golems++; return this; }
        /** requires: chunkAreas >= 0; effects: sets the footprint. */
        public Builder footprint(int chunkAreas) {
            if (chunkAreas < 0) throw new IllegalArgumentException("footprint");
            footprint = chunkAreas; return this;
        }
        /** requires: n > 0; effects: {@code n} more blocks of that id. */
        public Builder block(String id, int n) {
            if (n <= 0) throw new IllegalArgumentException("n");
            blocks.merge(id, n, Integer::sum); return this;
        }
        /** requires: n > 0; effects: {@code n} more items of that id in the village's containers. */
        public Builder item(String id, int n) {
            if (n <= 0) throw new IllegalArgumentException("n");
            items.merge(id, n, Integer::sum); return this;
        }
        public Census build() { return new Census(beds, villagers, tradeLevels, jobSites, bells, golems, footprint, blocks, items); }
    }
}
