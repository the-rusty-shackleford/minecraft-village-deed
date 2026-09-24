# Release verification — 2.0.0

2026-09-24. Village Deed folded into the store as a Chunkworks mod (D-0001) with the village
protocol (D-0002). Built on Rusty's approval of the plan and **released the same day on their
"Release it"** as pack 1.61.0 (the server repo's `knowledge/releases/pack-1.61.0.md` has the
deployment).

- `./gradlew test`: 19 JUnit tests over the domain (deed roster and transfer; census invariants
  and builder; appraisal per counter, per category, rounding, bounds, multiplier; payment in
  emeralds and blocks with change; village names; offers).
- `./gradlew runGameTestServer`: 5 real-server GameTests with Thief 1.2.4 loaded from the Modrinth
  maven, each in the gametest datapack's own jigsaw hut, generated and registered as worldgen
  would and tagged as a village and as Thief-protected:
  - a survival mock player breaking a chest with a villager 3.6 blocks away is punished with a
    MEDIUM crime and that one witness (Thief's `CrimeCommitedEvent`, recorded by the gametest mod);
  - after buying the hut the owner breaks a chest and kills a sheep with no crime, a stranger's
    theft is a crime, trusted the same player's theft is not, distrusted it is again;
  - a bare hut appraises at the floor (15); with two beds, a bell, a lectern and a smithing table,
    an enchanting table, three chests (five diamonds and two emeralds in one, dirt ignored), a
    librarian at level 3, a toolsmith at level 5, a nitwit and a baby, the census counts exactly
    that (2 beds, 1 bell, 2 job sites, 3 adults, 6 trade levels, 1 chunk area, 3 chests, 1
    enchanting table, 5 diamonds, 2 emeralds) and the price is 55, equal to the domain's answer;
  - with 4 emeralds and 6 blocks of emerald against an appraised price of 20, the purchase takes
    the 4 emeralds and 2 blocks and returns 2 emeralds of change, records owner, price and centre,
    refuses a second buyer with the owner's name, transfers, revokes and sells again;
  - a villager 3 blocks outside the hut's box has the hut as nearest village, the offer names it
    and the buyer in the field buys it on the standing offer.
- `./gradlew clean build`: green; jar `villagedeed-2.0.0.jar` sha1
  `46c6ba18182639edeebbb02825fb178ff146c1eb` (68980 bytes): domain, api, mixin, both tag files,
  no Thief class; `mods.toml` version 2.0.0, authors Chunkworks, AGPL-3.0-or-later.
- Found on the way: Thief's witnesses look away from creative criminals, and both the framework's
  mock player and any player joined on the gametest server are creative (its default game mode);
  the tests' mock is set to survival after joining. A throwaway GameTest that asked Thief's pieces
  one at a time found it in one run.
- Not verified: a live server with real villages (vanilla, CTOV, Terralith fortified) and real
  players; the offer's chat prompt and the appraisal lines as a player reads them; Guard
  Villagers' guards as witnesses; the config file's regeneration on the box (the 1.0.0 `price`
  key is dropped). Rusty's first purchase on the box is the live test.
