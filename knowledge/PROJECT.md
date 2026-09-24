# Village Deed

Version 2.0.0, built and released 2026-09-24 (pack 1.61.0). Minecraft 1.21.1, NeoForge 21.1.248,
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
  `Crimes` (records Thief's `CrimeCommitedEvent`), five GameTests. No booth: no client UI.

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
`knowledge/releases/pack-1.61.0.md`. Nobody had bought a village on 1.0.0, so no claim migrated.
Not yet seen live: the first thing to ask Rusty is their first purchase (sneak-use a villager in a
Terralith fortified village and in a CTOV village, read the appraisal, buy, open a chest, trust a
friend).
