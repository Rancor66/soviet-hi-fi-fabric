# Fabric 0.3.0 — cassette stacks and compact equipment

Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.160.0+26.2, Java 25.

Changes:
- Blank cassettes stack to 64. Recording creates exactly one unstackable recorded
  cassette; remaining blanks retain their metadata/count. Full inventory drops the
  result near the player. Both cached-track and new-upload completion use this path.
- Recorded cassettes from older releases gain the explicit stack-size component when
  ticked in a player's inventory.
- Right-clicking the top of a deck with an amplifier, or vice versa, combines the pair
  in one cell. Existing tape, playback position, settings and orientation are preserved.
- The combined block serves as both deck and amplifier for the existing link logic.
  Survival destruction drops the two separate devices and its cassette once.
- The six composite models reuse original geometry, textures and tape animations.
- Pressure plates and table supports are not changed, as requested in the revised scope.

Validation:
- Final `build` passed 23 JUnit tests and 8 server game tests (7 mod regressions plus
  Fabric's framework test). Log: stacking-build.log.
- Mod game tests exercise recording from a 64-stack, offhand recording, changed-hand
  cancellation, full inventory, both placement orders through GameTestHelper.useBlock
  (ordinary block/item interaction dispatch), save/load, and exact break drops.
- A separate dedicated server and client started successfully and connected. The
  client loaded the new blockstates/models without missing-model errors. Existing
  0.2.0 cassette data loaded in the test world.
- Native UI activation failed twice with `failed to activate captured window`.
  No in-game visual inspection is claimed. The gameplay interaction itself was
  checked by server game tests. No new subjective audio test was performed.
- Dedicated server was saved/stopped and the created test client closed. Automated
  game-test servers stopped on completion.

Install 0.3.0 on the server and all clients. The added block is
`soviet_hifi:stereo_stack`; break combined pairs before downgrading to an older mod.
