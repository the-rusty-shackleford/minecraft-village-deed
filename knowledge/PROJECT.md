# Village Deed

**2.2.0, built and verified 2026-09-28, unreleased**: a purchase counts and takes everything the
player carries, their Backpacks+ bags and the offhand included, through the Carried protocol, and
gives the change and the deed where the inventory would put them with the bags in it
([D-0006](decisions/D-0006.md)). Rusty's case is the first test: a village bought with emeralds
only in a worn bag. 12 GameTests (Backpacks+ 0.6.0 and Carried loaded), all green; the two new
purchase tests failed on 2.1.1. Ships with Carried 1.0.0 and Backpacks+ 0.6.0 as one pack on
Rusty's go. Not seen in play.

**2.1.0, built, verified and committed 2026-09-27, released 2026-09-28 in pack 1.67.0** (below): one
trusted list per player for all their villages, on a screen opened by using a deed or by the bare
`/deed`, open to every player, not only operators ([D-0004](decisions/D-0004.md)). Clean build
green: 25 JUnit (RosterTest's partitions, the screen's row order), 9 GameTests (one click
exempts a friend in two huts and another player's clicks cannot touch the list; 2.0.x's
per-village lists fold into the owner's; a player who is no operator may run `/deed`) and the
new booth (15 checks, 4 photos judged by eye: `/deed` opens it, a mouse click ticks a row,
`/deed trust WAXER_01` by name through the profile cache, the deed opens it through the client's
use-item path, the wheel scrolls). Jar `villagedeed-2.1.0.jar`, sha1 `2a4802a3…`, 94254 bytes.
The mod now has client code and a required network channel ("1"), so every client updates with
the pack. On the box, all six claims' per-village lists were empty (read 2026-09-27), so the fold
moves nobody there. Not seen live: Rusty's first use of the screen, and OtatopMalloy on it.
Trusting by name is not run in the GameTests: the GameTest server has no profile cache (the
booth covers it).

Version 2.0.0, built and released 2026-09-24 (pack 1.61.0). Minecraft 1.21.1, NeoForge 21.1.248,
Version 2.0.1 (2026-09-24 evening): claims bought on nfx's 1.0.0 carry over (D-0003). Released
as tag v2.0.1 and deployed as pack 1.62.2 at 20:51 UTC on Rusty's "Go, 2 min warning"; `deed
list` from the console then showed all five carried-over claims surveyed to their structures.

Java 21, a Thief 1.2.x addon. Chunkworks, AGPL-3.0-or-later; grown from nfx's 1.0.0 (MIT, folded in with
his blessing, D-0001). Public at github.com/the-rusty-shackleford/minecraft-village-deed.

Rusty on 2026-09-24: fold nfx's mod into the store under Chunkworks, make every village
purchasable, price by how nice a village is, stop the message claiming owners get away with
anything, and take the low-hanging fruit. Their calls: the owner and the players they name are
exempt (not the server); the deed covers property and livestock, never people; 15 to 150
emeralds; Terralith's fortified villages in, "the Rob Miller way" (a protocol, D-0002).

## Shape

- `src/domain` (JDK-only, plain JUnit, 19 tests with partitions at the top of each file):
  `Deed` (owner + roster, tiers, transfer), `Census` (what a village holds, with a builder the
  surveyor fills), `Tariff` (rates, bounds, multiplier; `STANDARD`), `Appraisal` (price and
  lines from a census under a tariff), `Payment` (emeralds first, blocks for the rest, change),
  `VillageNames` (structure path to a name), `Offer` (a price held for a player until a tick).
- `src/main/.../api`: the village protocol, `VillageId`, `Village`, `VillageProvider`,
  `VillageProviders` (D-0002). `village/StructureVillages`: the built-in provider over
  `#villagedeed:villages`.
- `src/main`: `VillageDeed` (entry; sneak-use offers), `DeedConfig` (SERVER: price floor,
  ceiling, multiplier; everyone_is_immune; clear_negative_gossip; confirm window), `Claims`
  (saved data `villagedeed_claims`: id → deed, names, centre, price, time), `Surveyor` (POIs,
  residents, palette counts per chunk section, container contents), `DeedPurchase` (offer, buy,
  appraise, the deed item), `Exemptions` (the mixin's one question), `DeedCommand`, `ModItems`,
  `mixin/CrimeMixin` (HEAD of Thief's `Crime.commit`, the only patch).
- Data: `data/villagedeed/tags/worldgen/structure/villages.json` (`#thief:protected` + the two
  Terralith ids, optional) and `data/thief/tags/worldgen/structure/protected.json` (the two
  Terralith ids, optional): the Terralith binding.
- `src/gametest`: a gametest mod with its own datapack (`villagedeed_gametest:hut`, a jigsaw
  structure over an 8x5x8 air template, tagged into both tags), `Huts` (generates and registers
  the hut as worldgen would, since `/place structure` registers nothing; survival mock players,
  because Thief's witnesses look away from creative ones and the framework's mock is creative),
  `Crimes` (records Thief's `CrimeCommitedEvent`), the GameTests, and `DeedBooth` (2.1.0), the
  silent client that drives and photographs the trust screen (`runPhotoBooth`; beside a live
  client through `tools/booth/run_iconified.sh`).
- 2.1.0 adds `domain.Roster` (one list per owner, the screen's row order), `Rosters` (overworld
  saved data `villagedeed_rosters`), `TrustList` (the payloads and the server side), `DeedItem`
  (use opens the list) and `client.TrustScreen` / `client.ClientSetup`; `Deed` keeps only the owner.

## Gotchas met

- Thief's `Witness.getWitnesses` returns nobody for a creative or spectator criminal. The
  framework's `makeMockServerPlayerInLevel` overrides `isCreative` to true, and a player joined
  by hand is creative too, because the gametest server's default game mode is creative and the
  join applies it. The tests join their own mock the way the framework does (embedded
  connection, `placeNewPlayer`) and then set it to survival. Found by a throwaway GameTest that
  asked Thief's pieces one at a time (protected structure, witnesses, factory, direct commit)
  rather than by reading bytecode.
- `/place structure` generates pieces but never calls `setStartForStructure` or
  `addReferenceForStructure`; a test that wants `getStructureWithPieceAt` to find a structure
  registers the start in its chunk and a reference in every chunk of its box.
- In a dev run the template manager reads `gameteststructures/*.snbt` by path for any id, so a
  jigsaw pool element can point at a test template; the hut's template is an air box and the
  test builds inside the registered bounding box.
- Thief's protected tag lists `#ctov:village` as optional; a datapack tag file with
  `"replace": false` merges into it.

## Status

Built 2026-09-24, JUnit and GameTests green (see
[release verification](../devtools/verification/release-2.0.0.md)), **released the same day on
Rusty's "Release it"**: tag v2.0.0, GitHub release (asset sha1 `46c6ba18…`), and on the box
`modhub drop-file mods/villagedeed-1.0.0.jar` (nfx's jar was an override from the adopted base
pack, both sides) then `add-file mods/villagedeed-2.0.0.jar`, `set-version 1.61.0`, `assemble`
at 17:17:59 UTC; the deployment is recorded in the server repo's
`knowledge/releases/pack-1.61.0.md`. That record said nobody had bought a village on 1.0.0, so no
claim migrated: **false, and never checked**. The box's `villagedeed_claims.dat` held five 1.0.0
purchases (three of Rusty's), which 2.0.0's loader skipped in silence; from 17:17 to the 2.0.1
fix the same evening nobody owned their village, the atlas showed none and Thief policed the
owners (D-0003). 2.0.1 reads both layouts and surveys the carried-over claims' centres on first
use. Before believing a data statement, read the file on the box.
Not yet seen live: the first thing to ask Rusty is their first purchase (sneak-use a villager in a
Terralith fortified village and in a CTOV village, read the appraisal, buy, open a chest, trust a
friend).

## Published and deployed — 2026-09-28, pack 1.67.0

Version 2.1.0 is [published](https://github.com/the-rusty-shackleford/minecraft-village-deed/releases/tag/v2.1.0)
(asset SHA-1 `2a4802a3c80618fc02a64b7da55a4ab22d60bfcc`, matching the clean-built jar) and deployed through Mod Hub in pack
**1.67.0**, replacing 2.0.1, on Rusty's "release with everything else after a 5 minute server
warning": restart 01:13:36 UTC at the end of the warning with nobody on, `Done` at 01:13:52,
"(2.0.1 -> 2.1.0)" in the log, 36 baseline errors, 20 TPS, parity clean. The server repo's
`knowledge/releases/pack-1.67.0.md` has the deployment. Not yet seen in play by Rusty.

## 2.1.1 — prices from 15 to 100 (2026-09-28, pack 1.67.2)

Rusty: 150 is too much, the best village should cost at most 100 and a basic one 15, and nearly
every village cost 150. [D-0005](decisions/D-0005.md) surveyed all 599 generated villages on the
box: 574 were at the ceiling, with a median raw of 295. Each point of D-0001's weights is now an
eighth of an emerald, and the ceiling is 100. The median village costs 50 and only the two
richest reach 100. The server's own `config/villagedeed-server.toml`, written by 2.0.0 with
`ceiling = 150`, is edited to 100 on release. The pack's stale 1.0.0 override of it is dropped.
Clean build: 27 JUnit tests and 9 GameTests. Released on Rusty's "Go" and deployed alone in pack
1.67.2 ([release verification](../devtools/verification/release-2.1.1.md)). Not yet seen: an
appraisal in play.
