/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.api;

/** The identity of a village, stable for the life of the world within one dimension: the provider
 * that recognises it and the key the provider gives it. Immutable.
 * <p>RI: provider is non-empty and holds no colon; key is non-empty. */
public record VillageId(String provider, String key) {
    public VillageId {
        if (provider.isEmpty() || provider.indexOf(':') >= 0) throw new IllegalArgumentException("provider");
        if (key.isEmpty()) throw new IllegalArgumentException("key");
    }
    /** effects: the id written by {@link #toString}; throws IllegalArgumentException otherwise. */
    public static VillageId parse(String text) {
        int colon = text.indexOf(':');
        if (colon <= 0) throw new IllegalArgumentException("village id: " + text);
        return new VillageId(text.substring(0, colon), text.substring(colon + 1));
    }
    @Override public String toString() { return provider + ":" + key; }
}
