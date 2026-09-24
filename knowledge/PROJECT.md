# Village Deed

Version 2.0.0, built 2026-09-24, unreleased. Minecraft 1.21.1, NeoForge 21.1.248, Java 21, a
Thief 1.2.x addon. Chunkworks, AGPL-3.0-or-later; grown from nfx's 1.0.0 (MIT, folded in with
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

Built 2026-09-24; JUnit and GameTests green (see
[release verification](../devtools/verification/release-2.0.0.md)). **Not released**: Rusty's go
is the release. Then: tag v2.0.0, GitHub release, and on the box `modhub drop-file
mods/villagedeed-1.0.0.jar` (an override jar from the adopted base pack, both sides) followed by
`add-file mods/villagedeed-2.0.0.jar <release url>`, `set-version`, `assemble`, restart when
empty. Nobody has bought a village on the box, so no claim migrates. First live check: Rusty
sneak-uses a villager in a Terralith fortified village and in a CTOV village, reads the
appraisal, buys one, opens a chest, trusts a friend.
