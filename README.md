# Portable Stereo (Fabric, Minecraft 26.3)

A boombox you carry around. Load any music disc and everyone within jukebox range (~64 blocks)
hears it, and the sound follows you as you move.

## How to use
Held:
- Right-click with a music disc in your other hand: loads it and starts playing.
- Right-click: pause / resume (picks up where it left off).
- Sneak + right-click a block: place it (keeps playing).
- Sneak + right-click the air: eject the disc.

Placed:
- Right-click holding a disc (when empty): loads it and plays.
- Right-click: pause / resume.
- Sneak + right-click with an empty hand: pick it up (keeps playing).
- Break it: drops the stereo, paused, disc still inside.

Everyone within ~64 blocks hears it, and people who walk up mid-song hear it from the right spot.
Volume follows the vanilla "Jukebox/Note Blocks" slider.

## Building
Requires JDK 25.

1. Open the folder in IntelliJ IDEA (it will import the Gradle project), or
2. From a terminal with Gradle 9.6+ installed, generate the wrapper once: `gradle wrapper`
   then build: `./gradlew build` (Windows: `gradlew.bat build`)

The mod jar lands in `build/libs/portable-stereo-1.0.0.jar`. Drop it in your `mods` folder
alongside Fabric API. It must be installed on the server AND on every client.

To test in a dev client: `./gradlew runClient`
