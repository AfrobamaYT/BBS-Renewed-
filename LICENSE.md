# Lizenzlage

Dieses Repository enthält Teile unter **verschiedenen** Lizenzen. Diese Datei sagt, welcher
Teil unter welcher steht. Der Kurzschluss „alles im Repo ist GPL" wäre **falsch** — siehe
besonders den Abschnitt zu den Assets.

---

## 1 · Komponentenlizenzen und Lizenz des Gesamtpakets

Die geerbten BBS-Dateien behalten ihre MIT-Lizenz und bleiben einzeln unter MIT nutzbar.
Fuer das **kombiniert ausgelieferte BBS-CML-Edition-Paket** wurde am 2026-09-15 bewusst
GPL-2.0-or-later gewaehlt. Das fruehere Ziel, den Blender-Port nur fuer ein MIT-Gesamtpaket
abzuspalten, wird nicht weiterverfolgt.

Grund ist der IK-Löser unter `mchorse/bbs_mod/cubic/ik/blender/`
(21 Java-Dateien, gemessen am 2026-09-15). Er ist **zeilenweise aus Blenders Quelle
übersetzt** — die Datei `IKMath.java` sagt es selbst: „Zeile für Zeile aus IK_Math.h".
Die betroffenen
Blender-Dateien tragen `GPL-2.0-or-later` (an der Quelle geprüft:
`intern/iksolver/intern/IK_Math.h` und `source/blender/blenkernel/intern/constraint.cc`,
beide „SPDX-FileCopyrightText: 2001-2002 NaN Holding BV"). Ein daraus abgeleitetes Werk
kann nicht schwächer lizenziert sein.

**Komponenten und Gesamtpaket:**

| Teil | Lizenz |
|---|---|
| Geerbte und eigenstaendig unter MIT lizenzierte Quelldateien | **MIT**, einzeln weiter nutzbar |
| `cubic/ik/blender/` — der aus Blender übersetzte Löser | **GPL-2.0-or-later** ([`COPYING`](COPYING)) |
| Kombiniert gebautes und verteiltes BBS-CML-Edition-Paket | **GPL-2.0-or-later** |

Direkte kuenftige Blender-Ports werden nicht pauschal als GPL-2.0-or-later angenommen.
Ihre exakten Quelldateien, Abhaengigkeiten und SPDX-Angaben entscheiden, ob fuer den
Gesamtrelease eine strengere GPL-Fassung gelten muss. Das verbindliche Verfahren steht in
[`docs/BLENDER-PORT-GATE.md`](docs/BLENDER-PORT-GATE.md).

Nicht aus Blender-Quellcode abgeleitet sind die Rig-Laufzeit (`cubic/ik/Rig*.java`)
und das Transform-Verhalten (`input/drag/`). Beide sind nach beobachtetem Verhalten
nachgebaut und gegen Blender **gemessen**, nicht aus dessen Quelltext übersetzt — die
Dateien belegen das selbst durchgehend mit Messwerten. Sie werden als Teil des kombinierten
Pakets GPL-kompatibel verteilt; das macht sie nicht nachtraeglich zu Blender-Derivaten.

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
| IK-Löser (Jacobi, SDLS, SVD, Pol-Zwang, Segmenttypen) | `mchorse/bbs_mod/cubic/ik/blender/` (21 Java-Dateien am 2026-09-15) | Aus Blenders `intern/iksolver` übersetzt — `IK_Math.h`, `IK_QJacobianSolver.cpp`, `IK_QSegment.cpp`, `IK_QJacobian.cpp`, `IK_QTask.cpp`, `IK_Solver.h` | **GPL-2.0-or-later** |
| Constraint-Auswertung und Rig-Mechanik | `mchorse/bbs_mod/cubic/ik/Rig*.java` | Eigenentwicklung nach gemessener Blender-Constraint-Semantik aus `constraint.cc`, `armature.cc` | Im Gesamtpaket **GPL-2.0-or-later** |
| Transform-Verhalten (Griff, Strähne, Achsenrasten, Präzision) | `ui/framework/elements/input/drag/`, `UITransformHandler` | Eigenentwicklung nach gemessenem Blender-Verhalten | Im Gesamtpaket **GPL-2.0-or-later** |
| BBS-Kern, UI-Grundgerüst, Formen, Film, Export | `mchorse/bbs_mod/…` (der Rest) | McHorse, Wemppy | MIT, siehe oben |
| Werkzeuge, Perf, Nano-Schicht | `com/afrobama/…`, `com/mcstudio/…` | in diesem Fork geschrieben | GPL-2.0-or-later |

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

Diese Datei beschreibt den Stand vom **2026-08-24**. Seit 2026-10-11 ist das Repository
öffentlich; offene Punkte zur Weitergabe stehen im [Changelog](CHANGELOG.md). Dies ist keine
Rechtsberatung, sondern die Dokumentation der Herkunft durch die Entwicklung.
