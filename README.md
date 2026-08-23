# BBS — Fork auf Minecraft 26.2

Ein Fork des BBS-Animationsmods für Minecraft (Fabric), portiert auf **26.2** und um
Werkzeuge erweitert, die Animationsarbeit in Minecraft näher an das heranbringen, was
Blender und klassische Sequencer können.

> **Privates Repository.** Dieser Baum ist nicht veröffentlicht. Er ist die Arbeits- und
> Sicherungsablage des Forks.

![Der Film-Editor mit dem Render-Reiter](design/bbs-design-system/briefing/ist-editor-mit-nano-panel.png)

*Der Film-Editor mit dem Render-Reiter: Engine wählen — BBS, Blender Eevee oder Blender
Cycles — und der Film rendert mit einem Knopf. Das Panel zeigt die neue Vektor-Oberfläche;
der Rest des Bildes ist noch der bisherige Look.*

---

## Was diesen Fork ausmacht

**Blenders echter IK-Löser statt eines Eigenbaus.** BBS' eigener Löser wurde entfernt
(3.619 Zeilen) und durch Blenders Löser ersetzt, aus dessen C++-Quelle übersetzt. Ein
beliebiges Blender-Rig lässt sich importieren; BBS führt Blenders Constraint-Graph zur
Laufzeit aus. Gemessen: **706 von 706 Knochen decken sich exakt** mit Blender, größte
IK-Abweichung 7e-06. Rigify, mixamo_rig und Auto-Rig Pro laufen durch.

**Ein Klick rendert den Film mit Blender.** Kamera, Darsteller und ein Weltausschnitt
werden je Auftrag frisch nach Blender gebacken und dort mit Eevee oder Cycles gerendert.
Ende-zu-Ende gemessen: Klick bis fertiges mp4 in 24 Sekunden für 30 Bilder. Das
mitgelieferte Addon richtet sich selbst ein.

**Bedienung nach Blenders Vorbild.** Verschieben, Drehen und Skalieren rechnen wie in
Blender — Mausabstand zum Pivot, Spiegeln beim Überschreiten, Shift verlangsamt auf 1/30 —
mit der vollen visuellen Rückmeldung: Maussymbol je Modus, gestrichelte Strähne vom Pivot,
verankerte Achslinien, orangefarbener Ursprungspunkt.

**Der Editor läuft flüssig.** Von 3 auf 43 Bilder je Sekunde.

Die vollständige Liste steht im [Changelog](CHANGELOG.md).

## Ehrlichkeit über den Stand

Der Baum führt eine geprüfte Feature-Aufnahme:
**[`docs/FEATURES-RELEASE.md`](docs/FEATURES-RELEASE.md)**. Dort ist jedes Feature einzeln
markiert — *fertig*, *teilweise*, *nur Gerüst* oder *ungeprüft* — mit dem Beleg dazu.

Stand 2026-08-23, am Baum gemessen und nicht geschätzt:

| | |
|---|---|
| Aufgenommene Features | **91** — 71 fertig, 8 teilweise, 1 nur Gerüst, 10 ungeprüft |
| Eigener Code gegenüber den Upstreams | **202.884 Java-Zeilen** in 916 Dateien |
| Methoden mit leerem Körper | 57 von 5.833 (**1,0 %**) |
| Eigene Klassen ohne Aufrufer | **3 von 804** |
| Abnahme-Akten | 32, alle belegt, **0 offene Symptome** |

Was nicht gemessen ist, steht als *ungeprüft* dort und wird hier nicht als fertig verkauft.
Die bekannten Lücken stehen im [Changelog](CHANGELOG.md#bekannte-lücken).

## Aufbau des Repositorys

| Ordner | Inhalt |
|---|---|
| `src/gametest` | Bildvergleichs-Tests über Fabrics Vergleichs-API. Laufen nie im ausgelieferten Mod. |
| `blender/addon` | Das Blender-Addon der Render-Brücke (`blender_bbs_bridge`) samt BOBJ-Exporter. |
| `tools/` | 40 Werkzeuge: Rig-Export, FBX-Wandlung, Abnahme-Akten, Hotswap, Gates. Übersicht in [`tools/WERKZEUGE.md`](tools/WERKZEUGE.md). |
| `docs/` | Feature-Aufnahme, Umbau-Programm, Buglisten, Messprotokolle. |
| `design/`, `icons/` | Design-System, Icon-Entwürfe, Briefing-Screenshots. |
| `probleme/` | Die Abnahme-Akten — je Befund ein Symptom mit Beleg. |

### Zweige

| Zweig | Stand |
|---|---|
| `port-26.2` | Der aktive Zweig. Minecraft 26.2, Fabric. |
| `1.21.1` | Der Stand vor dem Port. |

Die Upstreams sind als Remotes eingehängt: `cmlup` (bbs-mod-cml-edition) und `fsgh` (bbs-fs).
Beide Push-URLs sind absichtlich unbrauchbar gesetzt — es sind fremde Repositories.

## Bauen

Gebaut wird mit Gradle gegen eine JBR mit DCEVM (Pfad in `gradle.properties`):

```
./gradlew build
```

Die wichtigsten Werte stehen in `gradle.properties`: `minecraft_version=26.2`,
`loader_version`, `fabric_version`, `mod_version`.

## Herkunft und Lizenz

Dieser Fork baut auf:

- **BBS mod** von [McHorse](https://github.com/mchorse) — der ursprüngliche Mod
- **bbs-fs** von [Wemppy4](https://github.com/Wemppy4)
- **bbs-mod-cml-edition** von [ElGatoPro300](https://github.com/ElGatoPro300)

**Der Code steht unter GPL-2.0-or-later** ([`COPYING`](COPYING)). Das folgt aus der Quelle,
nicht aus einer Vorliebe: der IK-Löser und die Constraint-Auswertung sind zeilenweise aus
Blender übersetzt, und die betroffenen Blender-Dateien tragen selbst `GPL-2.0-or-later`.

Die von McHorse und Wemppy geerbten Teile behalten ihre **MIT**-Vermerke — MIT ist
GPL-verträglich, es wird nichts ersetzt.

> ⛔ **Assets sind ausdrücklich ausgenommen.** Texturen, Modelle, Klänge, Schriften und
> Icons fallen **nicht** unter die GPL dieses Repositorys. Sie behalten die Rechte ihrer
> jeweiligen Urheber; wer den Code unter der GPL weiterverwendet, erwirbt damit **keine**
> Rechte an den Assets.

Welcher Teil unter welcher Lizenz steht, welche Blender-Dateien übersetzt wurden und welche
Fremdbestandteile mitliegen, steht vollständig in [`LICENSE.md`](LICENSE.md).
