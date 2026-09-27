/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import java.util.*;

/** Whom a player lets use their villages: one list for every village they hold, now and later
 * (D-0004). Immutable.
 * <p>AF: {@code owner} is the player whose list this is; {@code trusted} are the players they
 * named. A deed exempts its owner and the players on its owner's roster from the village's law.
 * <p>RI: owner is non-null; trusted is unmodifiable and never holds the owner. */
public record Roster(UUID owner, Set<UUID> trusted) {
    public enum Tier { OWNER, TRUSTED, STRANGER }
    /** One line of the trust screen: a player other than the owner, by the best name known. */
    public record Row(UUID id, String name, boolean online, boolean trusted) {}

    public Roster {
        Objects.requireNonNull(owner, "owner");
        trusted = Set.copyOf(trusted);
        if (trusted.contains(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
    }
    /** effects: {@code owner}'s roster with nobody on it. */
    public static Roster of(UUID owner) { return new Roster(owner, Set.of()); }
    /** requires: who is not the owner; effects: this roster with {@code who} on it. */
    public Roster trusting(UUID who) {
        if (who.equals(owner)) throw new IllegalArgumentException("the owner is not on their own roster");
        var list = new HashSet<>(trusted);
        list.add(who);
        return new Roster(owner, list);
    }
    /** effects: this roster with {@code who} off it; the same roster when they were not on it. */
    public Roster distrusting(UUID who) {
        var list = new HashSet<>(trusted);
        list.remove(who);
        return new Roster(owner, list);
    }
    /** effects: the player's standing with the owner. */
    public Tier tier(UUID who) {
        return who.equals(owner) ? Tier.OWNER : trusted.contains(who) ? Tier.TRUSTED : Tier.STRANGER;
    }
    /** effects: whether the owner's deeds exempt the player: the owner and the trusted, nobody else. */
    public boolean permits(UUID who) { return tier(who) != Tier.STRANGER; }
    /** requires: {@code seen} maps players to names (a null name means none is known), and
     * {@code online} is who is on the server now.
     * effects: the screen's rows: everyone seen and everyone on the roster, the owner excepted,
     * online players first, then by name ignoring case, then by id; a player with no known name
     * is shown by their id's first eight digits. */
    public List<Row> rows(Map<UUID, String> seen, Set<UUID> online) {
        var ids = new LinkedHashSet<UUID>(seen.keySet());
        ids.addAll(trusted);
        ids.remove(owner);
        var rows = new ArrayList<Row>(ids.size());
        for (var id : ids) {
            var name = seen.get(id);
            rows.add(new Row(id, name != null ? name : id.toString().substring(0, 8), online.contains(id), trusted.contains(id)));
        }
        rows.sort(Comparator.comparing((Row r) -> !r.online())
                .thenComparing(r -> r.name().toLowerCase(Locale.ROOT))
                .thenComparing(Row::id));
        return List.copyOf(rows);
    }
}
