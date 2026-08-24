# Lizenzlage

Dieses Repository enthält Teile unter **verschiedenen** Lizenzen. Diese Datei sagt, welcher
Teil unter welcher steht. Der Kurzschluss „alles im Repo ist GPL" wäre **falsch** — siehe
besonders den Abschnitt zu den Assets.

> ⛔ **Vor einer Veröffentlichung oder Weitergabe:**
> [`docs/VOR-VEROEFFENTLICHUNG.md`](docs/VOR-VEROEFFENTLICHUNG.md) abarbeiten. Dort stehen
> drei Sperrpunkte, die diese Datei nicht auflöst — einer davon lässt sich nur von GitHub
> selbst erledigen und braucht deshalb Vorlauf.

---

## 1 · Zwei Lizenzen, und warum

**Das Ziel dieses Forks ist MIT** — wie beim ursprünglichen BBS mod und wie bei
praktisch allen anderen BBS-Forks. Damit kann die Community übernehmen, was sie
gebrauchen kann, und dieser Fork gibt zurück, statt nur zu nehmen.

Dem steht ein Teil entgegen: Der IK-Löser unter `mchorse/bbs_mod/cubic/ik/blender/`
(18 Dateien, 3.341 Zeilen) ist **zeilenweise aus Blenders Quelle übersetzt** — die
Datei `IKMath.java` sagt es selbst: „Zeile für Zeile aus IK_Math.h". Die betroffenen
Blender-Dateien tragen `GPL-2.0-or-later` (an der Quelle geprüft:
`intern/iksolver/intern/IK_Math.h` und `source/blender/blenkernel/intern/constraint.cc`,
beide „SPDX-FileCopyrightText: 2001-2002 NaN Holding BV"). Ein daraus abgeleitetes Werk
kann nicht schwächer lizenziert sein.

**Angestrebte Aufteilung:**

| Teil | Lizenz |
|---|---|
| Der Mod — alles außer dem IK-Löser | **MIT** |
| `cubic/ik/blender/` — der aus Blender übersetzte Löser | **GPL-2.0-or-later** ([`COPYING`](COPYING)) |

Das Paket ist dafür günstig geschnitten: Es hat genau **eine** Anbindung an den Rest
(`RigIKKetten.java`).

⛔ **Ist-Zustand, ehrlich:** Diese Trennung ist **dokumentiert, aber noch nicht gebaut.**
Solange Löser und Mod in einem JAR ausgeliefert werden, sind sie ein Werk — und für
dieses Gesamtwerk gilt dann **GPL-2.0-or-later**, nicht MIT. Die MIT-Teile bleiben davon
unberührt und einzeln weiter unter MIT nutzbar; betroffen ist nur das ausgelieferte
Ganze. Was zu tun ist, steht in
[`docs/VOR-VEROEFFENTLICHUNG.md`](docs/VOR-VEROEFFENTLICHUNG.md).

Ob ein separat ausgeliefertes JAR juristisch als eigenständiges Werk gilt oder als
abgeleitetes, ist bei Java umstritten. Diese Datei dokumentiert die Absicht und die
Herkunft — sie entscheidet die Frage nicht.

Nicht abgeleitet und daher **nicht** betroffen: die Rig-Laufzeit (`cubic/ik/Rig*.java`)
und das Transform-Verhalten (`input/drag/`). Beide sind nach beobachtetem Verhalten
nachgebaut und gegen Blender **gemessen**, nicht aus dessen Quelltext übersetzt — die
Dateien belegen das selbst durchgehend mit Messwerten. Funktionalität als solche ist
nicht urheberrechtlich geschützt, nur konkreter Code.

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

## 3 · Der MIT-Teil und wem er gehört

Der weit überwiegende Teil dieses Repositorys steht unter der **MIT-Lizenz** — der geerbte
Code ebenso wie das, was in diesem Fork dazugeschrieben wurde. MIT ist mit der GPL
verträglich; die MIT-Teile bleiben zusätzlich unter MIT nutzbar.

**Rechteinhaber**, gemessen an den Beiträgen auf `port-26.2` (hinzugefügte Zeilen, ohne
Merges, alle Dateitypen):

| | Zeilen gesamt | davon Massen-Commits* | Handgeschrieben |
|---|---|---|---|
| **McHorse** — der ursprüngliche BBS mod | 335.115 | 178.021 | **157.094** |
| **Afrobama** — dieser Fork | 203.052 | 56.804 | **146.248** |
| **ElGatoPro300** — bbs-mod-cml-edition | 528.586 | 392.808 | **135.778** |
| **Wemppy** — bbs-fs (`Wemppy4` + `wemppyy`) | 102.289 | 0 | **102.289** |

\* Commits über 5.000 Zeilen — Mappings-Umstellungen, Massenumbenennungen, generierte
Dateien. Sie zählen urheberrechtlich mit, sagen aber wenig über den Umfang der eigentlichen
Arbeit. Deshalb steht die Handarbeit daneben.

Weitere Beitragende der Upstreams — darunter **SIRSYP** (467.782 Zeilen, davon 88 % in
Massen-Commits), ElRedstoniano, Fanyel, Matias Neto und andere — sind in der Git-Historie
namentlich verzeichnet. `git shortlog -sn` listet sie vollständig.

⛔ **Die Copyright-Vermerke aller Genannten bleiben an ihren Dateien stehen und werden
nicht ersetzt.** MIT verlangt das ausdrücklich: Wer den Vermerk entfernt, verliert damit
die Lizenz — seine Nutzung ist dann unerlaubt. Das gilt in beide Richtungen, für den
geerbten Code wie für den dieses Forks.

Der MIT-Text im Wortlaut:

> Copyright 2025 McHorse
> Copyright 2026 Wemppy
> Copyright 2026 ElGatoPro300
> Copyright 2026 Afrobama
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
