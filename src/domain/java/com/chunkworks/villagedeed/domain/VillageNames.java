/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.Locale;

/** A readable name for a village from the path of its structure id, since structures carry no
 * display name: {@code village_plains} is "Plains Village", {@code large/village_desert_oasis} is
 * "Desert Oasis Village", {@code fortified_village} is "Fortified Village". */
public final class VillageNames {
    private VillageNames() {}
    /** effects: the last path segment, title-cased word by word, with a leading {@code village_}
     * moved to the end; "Village" when nothing is left. */
    public static String fromStructurePath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) path = path.substring(lastSlash + 1);
        if (path.startsWith("village_")) path = path.substring("village_".length()) + "_village";
        var name = new StringBuilder();
        for (var word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return name.isEmpty() ? "Village" : name.toString();
    }
}
