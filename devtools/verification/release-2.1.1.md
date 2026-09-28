# Release verification — 2.1.1

2026-09-28. Rusty asked for prices from 15 to at most 100, having found nearly every village at
150; on the result, "Go". D-0005 has the survey and the rule.

The gate was a full `clean build` through `tools/booth/run_iconified.sh`, beside Rusty's running
client with `disableConfigWatcher` set. It passed:

- 27 JUnit tests: 25 before, plus the standard tariff's weights at an eighth and the survey's
  anchors (15, 20, 50, 100).
- 9 GameTests: the hut's appraisal is now 20, from 40.5 points at an eighth on a base of 15.
- All 15 booth checks.

The jar holds no docs or test classes; the amend after the gate changed only `knowledge/`.

Artifact: `villagedeed-2.1.1.jar` (94842 bytes).
SHA-1: `c25a342f7bc61043af2fc591babcd964c3301f94`. The GitHub asset, downloaded, matches.

Deployed alone in pack 1.67.2 (the Hub printed players d12deb7a…, server 88167adc…).

- **Config:** the server's `config/villagedeed-server.toml` was 2.0.0's (`ceiling = 150`). It
  was set to 100 before the restart (the old file is kept as `.pre-2.1.1`). NeoForge then
  corrected the file's comments to the new defaults and kept 100.
- **Stale override:** the pack's override of that file, nfx's 1.0.0 config (`price = 45`),
  left the pack through `drop-file`.
- **Restart:** nobody was online, so it restarted at once. The log shows "villagedeed
  (version 2.1.0 -> 2.1.1)", the 36 baseline errors and 20 TPS; Mod Hub reports parity.

Not verified: an appraisal on the box. `/deed appraise` needs a player standing in a village,
and nobody was on; the prices above come from the survey's census run through the new tariff.
