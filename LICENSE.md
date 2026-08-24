# Lizenzlage

Dieses Repository enthält Teile unter **verschiedenen** Lizenzen. Diese Datei sagt, welcher
Teil unter welcher steht. Der Kurzschluss „alles im Repo ist GPL" wäre **falsch** — siehe
besonders den Abschnitt zu den Assets.

> ⛔ **Vor einer Veröffentlichung oder Weitergabe:**
> [`docs/VOR-VEROEFFENTLICHUNG.md`](docs/VOR-VEROEFFENTLICHUNG.md) abarbeiten. Dort stehen
> drei Sperrpunkte, die diese Datei nicht auflöst — einer davon lässt sich nur von GitHub
> selbst erledigen und braucht deshalb Vorlauf.

---

## 1 · Der Code: GPL-2.0-or-later

**Der Quellcode dieses Forks steht unter der GNU General Public License, Version 2 oder
später** (`SPDX-License-Identifier: GPL-2.0-or-later`). Der vollständige Lizenztext liegt
in [`COPYING`](COPYING).

**Warum genau diese Lizenz:** Der IK-Löser und die Constraint-Auswertung sind zeilenweise
aus Blenders Quelle übersetzt, und die betroffenen Blender-Dateien tragen selbst
`GPL-2.0-or-later` (an der Quelle geprüft: `intern/iksolver/intern/IK_Math.h` und
`source/blender/blenkernel/intern/constraint.cc`, beide „SPDX-FileCopyrightText: 2001-2002
NaN Holding BV"). Ein daraus abgeleitetes Werk kann nicht schwächer lizenziert sein — die
Wahl folgt also aus der Quelle und ist keine Präferenz.

## 2 · ⛔ Assets sind ausdrücklich AUSGENOMMEN

**Nicht-Code fällt NICHT unter die GPL dieses Repositorys.** Das betrifft Texturen,
Modelle, Klänge, Schriften, Icons, Screenshots und alle sonstigen Mediendateien —
unabhängig davon, in welchem Ordner sie liegen.

Diese Dateien behalten die Rechte ihrer jeweiligen Urheber. Wer den Code unter der GPL
weiterverwendet, erwirbt damit **keine** Rechte an den Assets.

Konkret im Baum:

| Was | Herkunft | Anmerkung |
|---|---|---|
| Spielermodelle, Emoticon-Modelle, Banner- und Block-Texturen, Klänge (111 Dateien) | aus dem BBS mod geerbt | Rechte bei den ursprünglichen Urhebern, nicht bei diesem Fork. |
| `*_by_michaelcreeper_.bbmodel` (Alex/Steve Bends) | Dritt-Autor, im Dateinamen genannt | Eigener Urheber. Vor einer Weitergabe gesondert zu klären. |
| 216 SVG-Icons unter `icons/svg`, Nano- und Design-Icons | in diesem Fork erzeugt | |
| Screenshots unter `docs/`, `probleme/`, `design/` | in diesem Fork erzeugt | Zeigen Minecraft-Inhalte. |
| `inter-variable.ttf` | Inter | SIL Open Font License, Text liegt bei: `assets/fonts/LICENSE-Inter-OFL.txt` |
| `NotoSans-Regular.ttf`, `NotoSans-Bold.ttf` | Noto Sans | SIL Open Font License, Text liegt bei: `fonts/nano/LICENSE` |

## 3 · Geerbter Code: MIT bleibt MIT

Die von **McHorse** (BBS mod) und **Wemppy** (bbs-fs) übernommenen Teile stehen unter der
MIT-Lizenz. **Diese Vermerke bleiben an ihren Dateien stehen und werden nicht ersetzt.**
MIT ist mit der GPL verträglich; die MIT-Teile bleiben zusätzlich unter MIT nutzbar.

Der MIT-Text im Wortlaut:

> Copyright 2025 McHorse
> Copyright 2026 Wemppy
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

## 4 · Herkunft der Code-Teile

Damit nachvollziehbar bleibt, was woher stammt:

| Teil | Ort im Baum | Herkunft | Lizenz |
|---|---|---|---|
| IK-Löser (Jacobi, SDLS, SVD, Pol-Zwang, Segmenttypen) | `mchorse/bbs_mod/cubic/ik/blender/` (18 Dateien) | Aus Blenders `intern/iksolver` übersetzt — `IK_Math.h`, `IK_QJacobianSolver.cpp`, `IK_QSegment.cpp`, `IK_QJacobian.cpp`, `IK_QTask.cpp`, `IK_Solver.h` | **GPL-2.0-or-later** |
| Constraint-Auswertung und Rig-Mechanik | `mchorse/bbs_mod/cubic/ik/Rig*.java` | Blenders Constraint-Semantik aus `constraint.cc`, `armature.cc` | **GPL-2.0-or-later** |
| Transform-Verhalten (Griff, Strähne, Achsenrasten, Präzision) | `ui/framework/elements/input/drag/`, `UITransformHandler` | Blenders `transform_input.cc`, `transform_constraints.cc`, `transform_mode_resize.cc`, `transform_draw_cursors.cc` | **GPL-2.0-or-later** |
| BBS-Kern, UI-Grundgerüst, Formen, Film, Export | `mchorse/bbs_mod/…` (der Rest) | McHorse, Wemppy | MIT, siehe oben |
| Blender-Addon der Render-Brücke | `blender/addon/blender_bbs_bridge` | in diesem Fork geschrieben; läuft **in** Blender | GPL-2.0-or-later |

## 5 · Mitgelieferte Fremdbestandteile

Unter `libs/` und `resources/` liegen **fremde Binärdateien**, die weder uns gehören noch
unter unserer Lizenz stehen:

| Datei | Projekt | Lizenz laut Paket |
|---|---|---|
| `libs/acceleratedrendering-…-fabric.3.jar` | Accelerated Rendering (Argon4W) | MIT |
| `libs/veil-fabric-1.21.1-4.0.1.jar` | Veil (amo, Cappin, Ocelot) | LGPL-3.0 |
| `libs/worldedit-mod-7.3.8.jar` | WorldEdit (EngineHub) | GPL-3.0 |
| `resources/audio/soft_oal.dll` | OpenAL Soft | LGPL |
| `gradle/wrapper/gradle-wrapper.jar` | Gradle | Apache-2.0 |

⛔ **Nicht versioniert, absichtlich** — diese Dateien liegen unter `libs/`, sind aber
namentlich in `.gitignore` eingetragen und dürfen **nicht** mitverteilt werden:

| Datei | Projekt | Warum draußen |
|---|---|---|
| `libs/physics-mod-pro-*.jar` (2 Versionen) | Physics Mod Pro (Haubna) | „All rights reserved". Zusätzlich je ~130 MB und damit über GitHubs 100-MB-Grenze. |
| `libs/nvToolsExt64_1.dll` | NVIDIA Tools Extension | Vorkompilierte Bibliothek aus CUDA Toolkit / Nsight. Die NVTX-**API** ist offen (Apache-2.0 im nvtx3-Header, BSD-artig bei v1), die **Binärdatei** unterliegt aber NVIDIAs Redistributionsbedingungen. Aus der Versionierung genommen am 2026-08-24. |

Beide bleiben lokal unter `libs/` liegen, der Build findet sie. Fehlt die NVIDIA-DLL,
fällt `NvtxBridge` still auf No-Op zurück — es bricht nichts.

## 6 · Stand

Diese Datei beschreibt den Stand vom **2026-08-24**. Das Repository ist privat; die Fragen
der Weitergabe stellen sich erst bei einer Veröffentlichung. Dies ist keine
Rechtsberatung, sondern die Dokumentation der Herkunft durch die Entwicklung.
