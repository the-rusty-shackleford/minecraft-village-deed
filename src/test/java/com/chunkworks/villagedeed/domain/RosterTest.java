/* Copyright (C) 2026 Chunkworks. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.villagedeed.domain;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions.
 * <ul>
 * <li>tier and permits: the player is the owner / trusted / a stranger;</li>
 * <li>trusting: a stranger, a player already trusted, the owner (refused);</li>
 * <li>distrusting: a trusted player, an absent player, the owner (no change);</li>
 * <li>rows: seen players online and offline, a trusted player never seen, the owner among the
 * seen, names that differ only in case, two players with one name, a player with no known name,
 * nobody seen;</li>
 * <li>the record's invariants: owner non-null, owner not on the roster, the roster unmodifiable.</li>
 * </ul> */
final class RosterTest {
    private static final UUID OWNER = new UUID(1, 1), FRIEND = new UUID(2, 2), STRANGER = new UUID(3, 3), LOST = new UUID(4, 4);
    private static final Roster ROSTER = Roster.of(OWNER).trusting(FRIEND);

    @Test void tiersAndPermission() {
        assertEquals(Roster.Tier.OWNER, ROSTER.tier(OWNER));
        assertEquals(Roster.Tier.TRUSTED, ROSTER.tier(FRIEND));
        assertEquals(Roster.Tier.STRANGER, ROSTER.tier(STRANGER));
        assertTrue(ROSTER.permits(OWNER));
        assertTrue(ROSTER.permits(FRIEND));
        assertFalse(ROSTER.permits(STRANGER));
    }
    @Test void trusting() {
        assertEquals(new Roster(OWNER, Set.of()), Roster.of(OWNER));
        assertEquals(Set.of(FRIEND, STRANGER), ROSTER.trusting(STRANGER).trusted());
        assertEquals(ROSTER, ROSTER.trusting(FRIEND), "trusting twice is the same roster");
        assertThrows(IllegalArgumentException.class, () -> ROSTER.trusting(OWNER));
    }
    @Test void distrusting() {
        assertEquals(Set.of(), ROSTER.distrusting(FRIEND).trusted());
        assertFalse(ROSTER.distrusting(FRIEND).permits(FRIEND), "trust withdrawn, a stranger again");
        assertEquals(ROSTER, ROSTER.distrusting(STRANGER), "distrusting an absent player changes nothing");
        assertEquals(ROSTER, ROSTER.distrusting(OWNER), "the owner was never on it");
    }
    @Test void rowsPutOnlinePlayersFirstThenNamesIgnoringCase() {
        var a = new UUID(5, 5); var b = new UUID(6, 6); var c = new UUID(7, 7);
        var seen = new HashMap<UUID, String>();
        seen.put(OWNER, "Jdrum12"); seen.put(a, "bobandy_"); seen.put(b, "OtatopMalloy"); seen.put(c, "WAXER_01"); seen.put(FRIEND, "Alpha");
        var rows = ROSTER.rows(seen, Set.of(OWNER, b, c));
        assertEquals(List.of("OtatopMalloy", "WAXER_01", "Alpha", "bobandy_"), rows.stream().map(Roster.Row::name).toList(),
                "online first (the owner excepted), then by name ignoring case");
        assertEquals(List.of(true, true, false, false), rows.stream().map(Roster.Row::online).toList());
        assertEquals(List.of(false, false, true, false), rows.stream().map(Roster.Row::trusted).toList());
    }
    @Test void rowsKeepATrustedPlayerNobodyHasSeenAndNameThemByTheirId() {
        var rows = Roster.of(OWNER).trusting(LOST).rows(Map.of(), Set.of());
        assertEquals(List.of(new Roster.Row(LOST, LOST.toString().substring(0, 8), false, true)), rows);
        var unnamed = new HashMap<UUID, String>(); unnamed.put(STRANGER, null);
        assertEquals(STRANGER.toString().substring(0, 8), Roster.of(OWNER).rows(unnamed, Set.of()).get(0).name(), "a null name falls back to the id");
    }
    @Test void rowsBreakATieOnNameByIdAndAreEmptyWhenNobodyElseWasSeen() {
        var first = new UUID(8, 1); var second = new UUID(8, 2);
        var rows = Roster.of(OWNER).rows(Map.of(second, "Steve", first, "steve"), Set.of());
        assertEquals(List.of(first, second), rows.stream().map(Roster.Row::id).toList(), "equal names ignoring case order by id");
        assertEquals(List.of(), Roster.of(OWNER).rows(Map.of(OWNER, "Jdrum12"), Set.of(OWNER)));
    }
    @Test void invariants() {
        assertThrows(NullPointerException.class, () -> new Roster(null, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new Roster(OWNER, Set.of(OWNER)));
        assertThrows(UnsupportedOperationException.class, () -> ROSTER.trusted().add(STRANGER));
        assertThrows(UnsupportedOperationException.class, () -> ROSTER.rows(Map.of(), Set.of()).add(null));
    }
}
