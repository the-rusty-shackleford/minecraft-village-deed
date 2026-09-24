# Release verification — 2.0.1

2026-09-24 evening, after 2.0.0 went live in pack 1.61.0. Rusty: "my villages no longer show
up in my atlas, which is a big problem." Cause pinned on the box (D-0003): 2.0.0's loader
skipped every 1.0.0-layout entry in `villagedeed_claims.dat` in silence, and that file (last
written 03:10 UTC, before 2.0.0 shipped) holds five 1.0.0 purchases: Jdrum12's Taiga Village
and two Taiga Fortified Villages, WAXER_01's Plains Fortified Village, OtatopMalloy's Plains
Village. The 2.0.0 record's "nobody had bought a village on 1.0.0" was never checked.

Full `./gradlew clean build` on the release tree:

- JUnit: 19 tests, unchanged (the domain is untouched).
- `runGameTestServer` with Thief 1.2.4: 6 real-server GameTests. New:
  `claimsBoughtOnOneZeroZeroCarryOver`: a 1.0.0 entry for a planted hut (Buyer, BuyerName
  "Jdrum12", VillageId as the start chunk's long, VillageName, BoughtAt) plus an entry without a
  buyer loads as one claim on the hut's 2.0.0 id, owned by the buyer, named "Jdrum12", at the
  flat 45 emeralds with the stored tick, centred on the start chunk at y 0 and marked dirty; the
  survey centres and names it from the structure; saved in 2.0.0's layout it reads back the
  same and clean. The server log shows "1 claims from Village Deed 1.0.0 carried over" and
  "Jdrum12's claim on Hut (structure:…) carried over from 1.0.0, centred at …".
- The box's five `VillageId` longs decode as chunk positions (for instance −51539607674 is
  chunk −122, −12, blocks −1952, −192, near Rusty's base), the packing 2.0.0 keys villages by,
  so each carries over onto the village 2.0.0 itself would find there.
- Jar `villagedeed-2.0.1.jar` sha1 `afeaa591177fd754ce7380c8d37adf8162c606ce` (70476 bytes).
- Not verified: the five real claims on the box until the pack carries 2.0.1 there; the first
  `Claims.get` after the restart logs the carry-over and one survey line per village, which is
  the thing to read.
