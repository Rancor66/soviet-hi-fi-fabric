# Fabric port verification, 17 September 2026

Version 0.2.0. Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.160.0+26.2,
Loom 1.17.21, Gradle 9.5.1, Java 25.

- Build passed all 23 tests: 16 storage/audio-math tests, 2 real MP3/OGG decoding tests,
  5 continuous playback/headroom regression tests inherited from Forge 0.1.3.
- Built JAR contains Fabric entrypoints, all 5 recipes, and embedded JLayer via Fabric
  jar-in-jar. No Forge mods.toml is packaged.
- A separate dedicated Fabric development server started on 127.0.0.1:25577.
  All equipment blocks were placed; block entity data serialized successfully.
- HiFiFabric development client connected. Cassette right-click opened the recording
  screen via Fabric networking. The original generated Dacha-90.mp3 was uploaded and
  stored on the server, and the cassette acquired its track metadata.
- A second independent development client HiFiListener connected with an empty cache.
  Inserting the cassette started deck animation and playback. Both clients received
  start tick 6275 / server tick 6280 and two speakers.
- HiFiListener automatically fetched and decoded 2118528 frames at 44100 Hz. Both
  clients opened Java Sound stereo outputs. Original file, server storage, and listener
  cache SHA-256 matched:
  cae67c1bf1d6bac5a5b85dab0e7d6e20cdb6da22e02dbe7cd9a699358d9793b0.
- Deck controls opened correctly, displayed the track and settings, and Stop returned
  the deck to idle. The recorded cassette remained stored with the original track id.
- Server saved and stopped through its local RCON endpoint; test clients were closed.

Evidence: fabric-build.log, fabric-server.log, fabric-client.log, fabric-listener.log,
build/test-results/test. Runtime tests used Loom development launches, not TLauncher.
This confirms transfer and audio output initialization, not subjective listening quality
or measured acoustic synchronization between separate physical computers. Full OGG
multiplayer playback and migration of an existing Forge world were not tested.

During setup, the initial listener nickname exceeded Minecraft's 16-character limit;
it was corrected to HiFiListener before the successful multiplayer test.
