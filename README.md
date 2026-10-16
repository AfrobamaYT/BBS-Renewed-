# BBS Renewed — Minecraft 26.3

BBS Renewed is a Fabric fork of McHorse's BBS animation mod, built on BBS FS and BBS CML
Edition. It extends the film editor, animation tools, rigs and rendering workflow.

## Install

1. Install Minecraft **26.3**, Java **25** and Fabric Loader **0.19.5 or newer**.
2. Put **Fabric API for 26.3** and `bbs-2.5.0-26.3.jar` in your `mods` directory.
3. For video export, install [ffmpeg](https://ffmpeg.org/download.html) and select its
   executable or containing directory in BBS's video recording settings.

Use the matching Minecraft version. Keep a backup of existing worlds and films when moving
an animation project to a new Minecraft version. The development companion is not required.

## Changes and verification

The current 26.3 snapshot includes OpenGL/Vulkan rendering, the Regie workspace, texture
editor, updated animation controls, held-prop recording fixes, Blender integration and the
Resolve link. See the [changelog](CHANGELOG.md) and [release record](docs/RELEASE-26.3.md)
for the exact scope and verification. The older feature inventory is historical.

## Build from source

Run Gradle with Java 25:

```sh
./gradlew build
```

The public build includes all six frozen native renderer libraries and verifies their source
fingerprints and checksums. Native sources and build instructions are in
[`native/nanovg-vulkan`](native/nanovg-vulkan). An optional Physics Mod Pro integration is
compiled only when its separately obtained JAR is present; that JAR is not distributed here.

## Credits and licence

- [McHorse](https://github.com/mchorse/bbs-mod): the original BBS mod.
- [Wemppy](https://github.com/Wemppy4/bbs-fs): BBS FS.
- [ElGatoPro300](https://github.com/ElGatoPro300/bbs-mod-cml-edition): BBS CML Edition.
- MichaelCreeper_: the inherited Alex/Steve Bends models.
- Afrobama and the other contributors credited in the source.

The combined mod is **GPL-2.0-or-later**. Original MIT notices, Blender source provenance,
third-party licences and asset credits remain in place. See [LICENSE.md](LICENSE.md),
[COPYING](COPYING) and the [release record](docs/RELEASE-26.3.md).
