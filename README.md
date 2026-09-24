# Village Deed

A [Thief](https://modrinth.com/mod/thief) addon for **Minecraft 1.21.1 / NeoForge 21.1.248**.
Buy a village and its law bends for you and the players you trust: you may take from its
chests, break its blocks, sleep in its beds and butcher its animals without the residents
holding it against you or the guards coming for you. Its people are not property: hurt a
villager and the village remembers, deed or no deed. Villages nobody has bought behave exactly
as Thief always made them behave.

By Chunkworks, AGPL-3.0-or-later. Grown from nfx's Village Deed 1.0.0 with his blessing.

## Buying

Sneak and use (right-click) any adult villager. That names the village, what the villagers would
sell it for and what you are carrying, and prints a clickable **[ Buy <village> ]** prompt;
clicking it runs `/deed buy` and completes the sale. Emeralds are taken first, then blocks of
emerald for the rest (nine each), and the overpayment comes back as change. The price you were
offered is the price you pay while the prompt lives (30 seconds by default).

The two steps are deliberate. Thief's gift is *also* sneak-use-on-villager with an emerald, so
sneak-use only ever offers; the gift still runs, and nobody buys a village while trying to
gift an emerald.

Every failed offer says why: no village the law recognises, already owned (and by whom), or
what it costs against what you carry.

## What the deed covers

| Thief crime | Owner and trusted | Everyone else |
|---|---|---|
| Breaking protected blocks (crops, beds, chests, job sites, the bell) | overlooked | punished |
| Opening chests and barrels | overlooked | punished |
| Sleeping in a villager's bed, kicking one out of bed | overlooked | punished |
| Looting an item frame | overlooked | punished |
| Picking up villagers or animals (CarryOn) | overlooked | punished |
| Killing the village's animals | overlooked | punished |
| Hurting or killing a villager | not a Thief crime: vanilla's gossip and the guards answer, for everyone |

The whole effect is one mixin at Thief's crime choke point, `Crime#commit`, returning the same
`Outcome.NONE` Thief's own Hero-of-the-Village check returns, so nothing downstream runs: no
gossip, no guard, no "you have been seen", no stat, no advancement, no event. Everything else in
the mod is the shop front.

## Owner and trusted

Whoever buys a village owns it. `/deed trust <player>` lets a friend use it as you do,
`/deed distrust <player>` takes that back, and `/deed transfer <player>` hands the village over
(the roster stays, the old owner is a stranger from then on). Ownership is per village: buy two
and trust different people in each. The server switch `everyone_is_immune` (off) restores the
1.0.0 rule where a bought village bends for every player.

## What a village costs

The price follows what the village holds, from a floor of 15 to a ceiling of 150 emeralds (both
server settings, with a multiplier). `/deed appraise` prints the lines. Every village starts at
15, then:

| Counted | Each |
|---|---|
| Homes (beds) | 1 |
| Adult residents | 2 |
| Each trade level above novice, per employed resident | 1 |
| Workstations (job-site blocks, worked or not) | 2 |
| Bells | 3 |
| Iron golems | 3 |
| Land, per chunk area of the village's footprint | 0.5 |
| Storage: chest, trapped chest, barrel | 1 |
| Crafting: crafting table, furnace 1; anvils 2; brewing stand 3 on top of its station; enchanting table 10; bookshelves 0.5 | as listed |
| Valuables: copper block 1, iron 3, gold 5, emerald 8, diamond 15, netherite 40; lanterns 0.25 | as listed |
| Loot in its containers: iron ingot 0.1, gold ingot 0.3, emerald 0.5, diamond 1, netherite ingot 5, enchanted book 2 | as listed |

The sum is rounded to the nearest five, multiplied, and kept within the floor and ceiling. A bare
hamlet lands at 15 to 25, a plains village around 75, a large fortified town at the ceiling. A
barrel is both a fisherman's workstation and storage and counts as both; so does a brewing stand
as a cleric's. Blocks are counted by chunk section over the village's bounding box, so a shop just
past the last house may count. Residents within eight blocks of the bounds count as the village's.

## Which villages

A village is whatever a registered **village provider** recognises (below). The built-in provider
recognises every worldgen structure in the tag `#villagedeed:villages`: Thief's own protected
structures (vanilla villages and Choice Theorem's Overhauled Villages) plus Terralith's
`fortified_village` and `fortified_desert_village`, which this mod also adds to `#thief:protected`
so Thief polices them. A village's territory is its structure's bounding box, roads and fields
included, keyed on the structure start's chunk, the identity the game itself keeps.

The village is found by the piece the villager stands in, then by any village's bounding box, then
as the nearest village within 48 blocks, so a villager wandering in the fields still offers their
village. Hand-built settlements have no structure and nothing to buy; Thief does not flag crimes
there either.

### Binding a village mod (the village protocol)

`com.chunkworks.villagedeed.api`: `Village` (id, name, bounds), `VillageProvider` (`at`, `near`,
`byId`) and `VillageProviders` (the ordered registry; first answer wins). A structure mod binds by
data alone: add its structure ids to `#villagedeed:villages` (to be buyable) and to
`#thief:protected` (to be policed) with a datapack or in its own data. A mod whose villages are not
structures registers a `VillageProvider` from its constructor; ids it issues carry its provider
name, and claims are keyed on them. Decision D-0002 in `knowledge/` has the spec.

## Commands

| Command | |
|---|---|
| `/deed buy` | Complete a purchase (what the chat prompt runs) |
| `/deed here` | Which village you are in or beside, its owner or its price, and whom you trust there |
| `/deed appraise` | The price line by line |
| `/deed list` | Every village bought in this dimension, with owner and roster size |
| `/deed trust <player>`, `/deed distrust <player>` | Owner only; offline players by name work |
| `/deed transfer <player>` | Owner only |
| `/deed revoke` | Tear up the deed for the village you are in (operators) |

## Config

`serverconfig/villagedeed-server.toml` in the world:

| Key | Default | |
|---|---|---|
| `price.floor` | 15 | The least a village costs |
| `price.ceiling` | 150 | The most, however rich; read as the floor when set below it |
| `price.multiplier` | 1.0 | Scales every appraisal before the bounds apply |
| `deed.everyone_is_immune` | false | Bought villages bend for every player, the 1.0.0 rule |
| `deed.clear_negative_gossip` | true | Wipe Major and Minor Negative gossip on purchase, so the residents trade with the new owner |
| `deed.confirm_window_seconds` | 30 | How long the prompt stays clickable |

## Diagnosing it

- "These villagers belong to no village the law recognises": no tagged structure within 48
  blocks of that villager. `/deed here` says the same; Thief's `/thief is_in_protected_structure`
  agrees for the spot you stand on.
- Bought a village but a chest still says "you have been seen": you are not the owner or on the
  roster (`/deed here` lists it), or the chest is outside the structure's bounding box (Thief
  would not flag it there either).
- The server log records every purchase ("<player> bought <village> (<id>) at <x, y, z> for N
  emeralds"), trust change, transfer and revocation with the village's id, so a dispute has a
  paper trail. Claims live in `data/villagedeed_claims.dat` of each dimension.
- A village bought on 1.0.0 that nobody owns: the file still holds it in 1.0.0's layout, and
  2.0.1 reads that ("N claims from Village Deed 1.0.0 carried over" in the log at first use, then
  one line per claim as its structure is surveyed). 2.0.0 skipped it; nothing was lost as long
  as no 2.0.0 purchase or trust change rewrote the file first.
- Prices look wrong: `/deed appraise` shows every line; the tariff is `Tariff.STANDARD` in the
  domain layer, the bounds and multiplier are config.

## Building

Java 21. Thief comes from the Modrinth maven (`maven.modrinth:thief:Zhr0tVOO`, the NeoForge
1.2.4 build the pack ships), compile-only for the mod and on the classpath of the gametest server.
`./gradlew test` runs the JDK-only domain tests (deed roster, census, tariff and appraisal,
payment, village names, offers). `./gradlew runGameTestServer` runs the real-server GameTests
with Thief loaded: the gametest datapack defines a jigsaw structure of its own, tags it as a
village and as Thief-protected, generates and registers it as worldgen would, and in it a theft
is punished, an owner and a trusted player are exempt while a stranger is not, the appraisal
follows what the hut holds, a purchase takes emeralds then blocks with change, and a villager in
the fields still offers the hut. `./gradlew build` produces `build/libs/villagedeed-<version>.jar`.

## Status

**2.0.1**: villages bought on 1.0.0 are their buyers' again. 2.0.0 read past 1.0.0's claims in
silence, so every earlier purchase vanished: no atlas marker, no exemption. They come back at
1.0.0's flat 45 emeralds, centred on their structure once the server has looked at it.
**2.0.0**: Chunkworks; the owner and the players they trust are exempt, not the whole server;
prices follow the village (15 to 150); honest messages; Terralith's fortified villages bought and
policed; every failed offer explains itself; emerald blocks with change; transfer; appraisal.
**1.0.0** (nfx): flat 45 emeralds, server-wide immunity. Download from
[GitHub Releases](https://github.com/the-rusty-shackleford/minecraft-village-deed/releases).
Verified: 19 JUnit tests and 6 real-server GameTests; see
[release verification](devtools/verification/).
