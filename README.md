# BBS — Minecraft 26.2 fork

A fork of the BBS animation mod for Minecraft (Fabric), ported to **26.2** and extended with
tools that bring animation work in Minecraft closer to what Blender and traditional sequencers
can do.

<!-- Screenshots go here, e.g.  ![The film editor](docs/images/editor.png)  — files in docs/images/ -->

---

## What sets this fork apart

**Blender's real IK solver instead of a home-grown one.** BBS's own solver was removed
(3,619 lines) and replaced with Blender's, translated from its C++ source. Any Blender rig can
be imported; BBS runs Blender's constraint graph at runtime. Measured: **706 of 706 bones match
Blender exactly**, largest IK deviation 7e-06. Rigify, mixamo_rig and Auto-Rig Pro all pass.

**Controls modelled on Blender.** Move, rotate and scale compute the way Blender does — mouse
distance to the pivot, mirroring when you cross it, Shift slowing to 1/30 — with the full visual
feedback: a cursor per mode, a dashed line from the pivot, anchored axis lines, an orange origin
point.

**The editor runs smoothly.** From 3 to 43 frames per second.

The full list is in the [changelog](CHANGELOG.md).

## Honesty about the state

The tree keeps an audited feature inventory: **[`docs/FEATURES-RELEASE.md`](docs/FEATURES-RELEASE.md)**.
Every feature is marked individually — *done*, *partial*, *skeleton only* or *unverified* — with
the evidence behind it.

As of 2026-08-23, measured against the tree rather than estimated:

| | |
|---|---|
| Features inventoried | **91** — 71 done, 8 partial, 1 skeleton only, 10 unverified |
| Own code on top of the upstreams | **202,884 lines of Java** across 916 files |
| Methods with an empty body | 57 of 5,833 (**1.0 %**) |
| Own classes with no caller | **3 of 804** |
| Acceptance records | 32, all evidenced, **0 open symptoms** |

Anything not measured is listed as *unverified* there and is not sold as finished here. Known
gaps are in the [changelog](CHANGELOG.md#bekannte-lücken).

## Repository layout

| Folder | Contents |
|---|---|
| `src/main`, `src/client` | The mod. Own code lives under `com.afrobama.*` and `com.mcstudio.*`; extensions to the original under `mchorse.bbs_mod.*`. |
| `src/gametest` | Screenshot comparison tests via Fabric's comparison API. They never run in the shipped mod. |
| `blender/io_export_bobj` | The Blender add-on that exports models and rigs to BBS (BOBJ). |
| `icons/` | The fork's own SVG icons, source of the vector interface. |
| `docs/` | Feature inventory and images. |

## ffmpeg

Video export runs through [ffmpeg](https://ffmpeg.org/download.html), which is not bundled with
the mod. Install it once, then point BBS at it under **Settings → Video recording → Encoder
path**:

- **Windows:** download a build from the ffmpeg download page and unpack it. As the encoder path,
  enter either `ffmpeg.exe` itself or the unpacked folder — BBS also finds `bin\ffmpeg.exe`
  inside it.
- **Linux:** install the `ffmpeg` package of your distribution. The default path `ffmpeg` then
  works as is.
- **macOS:** `brew install ffmpeg`, then enter the path that `which ffmpeg` prints.

## Building

Minecraft 26.2 requires **Java 25**, so Gradle itself has to run on it:

```
./gradlew build
```

If your default JDK is older, point Gradle at a Java 25 installation without editing the
repository — either export `JAVA_HOME`, or add a line to your personal
`~/.gradle/gradle.properties`:

```
org.gradle.java.home=/path/to/jdk-25
```

The key values live in `gradle.properties`: `minecraft_version=26.2`, `loader_version`,
`fabric_version`, `mod_version`.

**Optional dependency.** Three mixins and one bridge class compile against Physics Mod Pro,
which is "all rights reserved" and therefore not part of this repository. Without it the build
simply leaves those four files out and drops their mixin configuration — everything else builds
and runs unchanged. With the jar in `libs/`, the physics integration compiles in as well.

## Origins and licence

This fork builds on:

- **BBS mod** by [McHorse](https://github.com/mchorse) — the original mod
- **bbs-fs** by [Wemppy4](https://github.com/Wemppy4)
- **bbs-mod-cml-edition** by [ElGatoPro300](https://github.com/ElGatoPro300)

**The combined BBS CML Edition package is released under GPL-2.0-or-later.** The IK solver
under `cubic/ik/blender/` is translated line by line from Blender's GPL source. On
2026-09-15 the project therefore deliberately chose a GPL-compatible combined release instead
of separating the solver merely to preserve an MIT licence for the whole package.

Inherited MIT files retain their MIT notices and remain reusable individually under MIT.
Any new direct Blender source port must pass the provenance and licensing gate in
[`BLENDER-PORT-GATE.md`](BLENDER-PORT-GATE.md); the SPDX licence of those exact sources may
require a stricter GPL version for a future release.

Rights holders are McHorse, Wemppy, ElGatoPro300 and Afrobama; further contributors are in the
git history (`git shortlog -sn`). All **MIT** notices stay where they are — removing them
forfeits the licence.

> ⛔ **Assets are explicitly excluded.** Textures, models, sounds, fonts and icons do **not**
> fall under this repository's GPL. They keep the rights of their respective authors; reusing
> the code under the GPL grants **no** rights to the assets.

Which part is under which licence, which Blender files were translated and which third-party
components ship along is set out in full in [`LICENSE.md`](LICENSE.md).
