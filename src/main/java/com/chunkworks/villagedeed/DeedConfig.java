/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side switches, in {@code serverconfig/villagedeed-server.toml} of each world. */
public final class DeedConfig {
    private DeedConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue FLOOR, CEILING, CONFIRM_WINDOW_SECONDS;
    public static final ModConfigSpec.DoubleValue MULTIPLIER;
    public static final ModConfigSpec.BooleanValue EVERYONE_IS_IMMUNE, CLEAR_NEGATIVE_GOSSIP;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("price");
        FLOOR = builder.comment("The least a village costs, in emeralds; a bare hamlet.", "Default: 15").defineInRange("floor", 15, 1, 100000);
        CEILING = builder.comment("The most a village costs, in emeralds, however rich; read as the floor when set below it.", "Default: 150").defineInRange("ceiling", 150, 1, 100000);
        MULTIPLIER = builder.comment("Scales every appraisal before the floor and ceiling apply. 2.0 doubles prices.", "Default: 1.0").defineInRange("multiplier", 1.0, 0.01, 100.0);
        builder.pop();
        builder.push("deed");
        EVERYONE_IS_IMMUNE = builder.comment("Whether a bought village's law bends for every player, not only the owner and the players they trust.", "Default: false").define("everyone_is_immune", false);
        CLEAR_NEGATIVE_GOSSIP = builder.comment("On purchase, wipe Major and Minor Negative gossip from every villager in the bought village.",
                "Without this you can own a village whose residents still refuse to trade and whose guards are still hostile over old grudges.", "Default: true").define("clear_negative_gossip", true);
        CONFIRM_WINDOW_SECONDS = builder.comment("How long the [Buy] prompt stays clickable after sneak-using on a villager.", "Default: 30").defineInRange("confirm_window_seconds", 30, 5, 300);
        builder.pop();
        SPEC = builder.build();
    }
}
