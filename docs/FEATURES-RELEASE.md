# Features des Forks — Aufnahme für den Release (Ziel 2026-09-02)

**Erstellt 2026-08-23 auf Zweig `port-26.2`, HEAD `5e1192ce9`.**

⛔ **Gezogen, nicht erinnert.** Reihenfolge wie beauftragt: Git-Historie gegen die
Upstreams, dann Quelltext, dann `docs/`. Jede Behauptung aus einer Notizdatei wurde am
Quelltext nachgeprüft, bevor sie hier steht — **vier Stellen waren überholt**, sie stehen
unten unter „Korrekturen an der vorhandenen Doku".

## Wie die Reifegrade zu lesen sind

| Marke | Bedeutung |
|---|---|
| **fertig** | Im Baum, verdrahtet (Aufrufer im Quelltext belegt), und es existiert ein gemessener Beleg — Abnahme-Akte, Pixel-/Zahlenmessung oder Laufzeitprotokoll. |
| **teilweise** | Kern läuft und ist belegt, ein benannter Teil fehlt oder ist per Vorgabe abgeschaltet. |
| **nur Gerüst** | Übersetzt und kompiliert, aber kein Aufrufer im Baum — tut im laufenden Programm nichts. |
| **ungeprüft** | Code vorhanden und verdrahtet, aber ich habe keine Messung gefunden und keine gemacht. Kein Urteil über Funktion. |

**Grundlage der Reifegrade** — drei Messungen am Baum, nicht am Gedächtnis:

- **202.884 eigene Java-Zeilen** in 916 Dateien, die es in keinem Upstream-Zweig gibt;
  5.833 Methoden, davon **57 mit leerem Körper (1,0 %)**, 1 TODO, 4 `UnsupportedOperationException`.
  Der Baum ist gebaut, nicht Skelett.
- **3 von 804 eigenen Nicht-Mixin-Klassen haben keinen einzigen Aufrufer** (550 Zeilen).
  Sie stehen unten namentlich.
- **`tools/probleme.py liste` meldet 32 Akten, alle belegt, 0 offene Symptome.**
  Kein bekannter kaputter Funktionsbefund ist offen.

---

## Kameras und Aufnahme

| Feature | Stand |
|---|---|
| Kamera-Clips lassen sich direkt im Replay-Editor sehen, auswählen und verschieben, statt nur in der Kameraspur. | fertig |
| Ein Tastenkürzel wählt eine bestimmte Kamera sofort aus, statt erst das Kameramenü zu öffnen. | fertig |
| Shift+3 öffnet die Kameramodus-Auswahl, statt die Pose umzuschalten. | fertig |
| Der Flugmodus ändert die Kamera auch dann, wenn kein Clip ausgewählt ist. | fertig |
| Orbit- und Flugmodus ändern nur noch den Kameramodus, nicht mehr die Clip-Werte. | fertig |
| Im Orbit-Modus zeigt ein Achsenkreuz wieder den Drehpunkt in der Bildmitte an. | fertig |
| Maustaste 4 fährt im Orbit auf das ausgewählte Replay zu und rahmt es nach seiner echten Modellgröße ein, nicht nach der Trefferbox. | fertig |
| Ein bestehendes Replay lässt sich schnell auswählen — in der Welt wie im Dashboard. | fertig |
| Mehrere Kamera-Vorschaufenster zeigen die aktiven Kamera-Clips des laufenden Films gleichzeitig. | ungeprüft |
| Das Kamera-Wackeln ist als eigener Effekt einstellbar. | ungeprüft |

## Zeitleiste und Clips

| Feature | Stand |
|---|---|
| Marker auf der Zeitleiste lassen sich setzen, anklicken und einfärben. | fertig |
| Clips lassen sich deaktivieren bzw. verstecken, ohne sie zu löschen. | fertig |
| Hotbar- und HUD-Keyframe-Spuren sind zu einer aufklappbaren Spur gebündelt, statt die Liste zu fluten. | fertig |
| Keyframes setzen und löschen hängt im „Key"-Menü der Zeitleiste. | fertig |
| Clips lassen sich mit der Maus ziehen. | fertig |
| Das Dope Sheet meldet seine Klickflächen an die Abnahme, sodass ein Zonenlauf es sieht. | teilweise — Schnitt 1 von mehreren; der Replay-Zweig meldet noch keine Zonen |
| Eine Auswahl im Viewport tauscht die Zeitleiste nicht mehr aus. | fertig |
| Die Kanalsymbole im Dope Sheet stehen in der Textfarbe statt in vollem Weiß. | fertig |
| Die neue Zeitleiste ist auf die Vektor-Zeichenschicht umgebaut (1.729 Zeilen, im Film-Panel verdrahtet). | ungeprüft — im Baum und eingehängt, keine Abnahme gefunden |

## Rigging, IK und Blender-Treue

Der größte Einzelblock des Forks: **BBS' eigener IK-Löser wurde entfernt (3.619 Zeilen)
und durch Blenders echten Löser ersetzt** — übersetzt aus Blenders C++-Quelle, nicht
nachgebaut.

| Feature | Stand |
|---|---|
| BBS rechnet IK mit Blenders eigenem Löser (Jacobi-Matrix, SDLS-Dämpfung, SVD, Pol-Zwang), statt mit einer Eigenkonstruktion. | fertig — Parität gegen Blender gemessen, größte Abweichung 7e-06 |
| BBS importiert ein beliebiges Blender-Rig samt Constraint-Graph und führt ihn zur Laufzeit aus, statt eigene Rig-Mechanik zu erfinden. | fertig — 706 von 706 Knochen decken sich exakt mit Blender |
| Das Control-Rig ist der Griff: Blenders Kontroll-Formen sind in BBS sichtbar und per Linksklick anwählbar. | fertig |
| Die Kontroll-Formen folgen Bewegung und Drehung ihres Knochens, auch beim Abspielen. | fertig |
| IK/FK lässt sich pro Kette umschalten; der Regler sitzt im N-Panel neben den Rig Layers. | fertig |
| Snap IK↔FK setzt die andere Kette auf die aktuelle Pose, inklusive mittlerem Gelenk. | fertig |
| Rig Layers blenden Control-Gruppen ein und aus, im Zeilen-Layout von Rigify. | fertig |
| Ein Regler-Panel folgt der Auswahl wie Blenders N-Panel und zeigt Blenders Beschriftungen statt Rohnamen. | fertig |
| Gesichts-Steuerung über Shape Keys: ein Griff am Board treibt die Netzverformung über die ganze Kette. | fertig — headless am Prüfstück gemessen, linear und exakt |
| Ein Befehl macht aus einer Mixamo-FBX-Datei ein klickbares IK-Rig in BBS. | fertig |
| Drei Rig-Systeme laufen durch: Rigify, mixamo_rig und Auto-Rig Pro. | fertig — zehn Rigs im Laufzettel, neun exakt |
| Blenders Constraint-Typen sind portiert (Copy Location/Rotation/Scale/Transforms, Track To, Damped/Locked Track, Child Of, Limit Location/Distance, Armature, Stretch To). | teilweise — 2 von 518 Constraints weichen ab (Fußrollen-Paar, bekannt und eingegrenzt) |
| Treiber (Driver) werden exportiert und zur Laufzeit ausgewertet, statt eingefrorene Werte zu schreiben. | fertig |
| Ein Skelett-Layer zeichnet jeden Knochen als Blender-Oktaeder im Editor. | fertig |

## Rendern im Viewport

| Feature | Stand |
|---|---|
| Die Welt lässt sich als Drahtgitter zeichnen (Alt+Z), damit man Aufnahmen planen kann, bei denen ein Darsteller aus dem Boden kommt. | teilweise — gemessen im DEV-Client; mit Sodium+Iris in der CF-Instanz **nicht** gemessen |
| Das ausgewählte Replay bekommt eine Outline wie in Blender. | fertig |
| Der Bewegungspfad des gewählten Replays ist im Viewport sichtbar. | fertig |
| Achsen- und Sperrlinien zeichnen mit weicher Kante statt als pixelige Quader. | fertig |
| Blender-Rückmeldung beim Verschieben/Drehen/Skalieren: Maussymbol je Modus, gestrichelte Strähne vom Pivot, drei dicke Achslinien in Rot/Grün/Blau, orangefarbener Ursprungspunkt. | fertig — 9 Abnahme-Akten, pixelgemessen |
| Verschieben, Drehen und Skalieren rechnen wie Blender (Mausabstand zum Pivot, Spiegeln beim Überschreiten, Shift verlangsamt auf 1/30). | fertig |
| Die Achslinien bleiben beim Ziehen am Startpunkt verankert, statt mit dem Objekt zu wandern. | fertig |
| Das HUD ist im Editor-Viewport sichtbar. | fertig |
| Ein Physik-Overlay zeigt die Kollisionskörper. | fertig — Pixel-Beweis erbracht |
| Die Hand streckt sich beim Rennen nicht mehr falsch (FOV wirkt korrekt auf die Hand), und der Hand-Bob läuft auch beim Gehen. | fertig |
| Die Hand übernimmt die Item-Benutzung vollständig, statt den Schlag halb zu zeigen. | fertig |

## Rendern und Export

| Feature | Stand |
|---|---|
| Ein Render-Reiter in der Seitenleiste wählt die Engine — BBS, Blender Eevee oder Blender Cycles — und startet den Lauf mit einem Knopf. | fertig — E2E gemessen, Klick bis mp4 in 24 s für 30 Frames |
| BBS rendert den ganzen Film über Blenders echte Engine, statt sie nachzubauen: Kamera, Darsteller und ein Weltausschnitt werden je Auftrag frisch nach Blender gebacken. | fertig — Ende-zu-Ende auf 26.2 bewiesen |
| Das Blender-Addon richtet sich selbst ein: Bildrate, Importe und Asset-Pfad kommen aus BBS, nichts wird von Hand gepflegt. | fertig — beide Selbstheilungen gegengeprobt |
| Export mit Supersampling (Faktor 1–4, Lanczos-Verkleinerung). | fertig |
| Audio-Export schreibt eine WAV je Clip, schon auf Clip-Länge geschnitten und nach Zeitleisten-Position benannt. | teilweise — gebaut und an Dateien belegt, Schalter steht per **Vorgabe aus** |
| Der Video-Export läuft wieder, nachdem eine leere Ordner-Vorgabe ffmpeg auf Java 25 sterben ließ. | fertig |
| Ein Welt-Frame wird im normalen Frame gefangen statt außerhalb des Zeitplans gerendert. | fertig |

## Oberfläche

⛔ **Wichtig für den Changelog:** Der Vektor-Umbau liegt hinter drei Schaltern, die per
Vorgabe **aus** stehen (`vector_font`, `vector_ui`, `vector_icons` in `BBSSettings`).
Wer den neuen Look sehen will, muss ihn einschalten. Die `*Nano`-Panels selbst hängen
**nicht** an diesen Schaltern und laufen immer.

| Feature | Stand |
|---|---|
| Eine Vektor-Zeichenschicht (NanoVG) mit Farbtoken, Bausteinen, Symbolen und Bildern zeichnet Panels im Browser-Look statt in pixeligen Textur-Quads. | teilweise — Schicht gebaut und verdrahtet, Vektor-Schrift/UI/Icons per Vorgabe aus |
| Topbar, Statusleiste, Werkzeugleiste, Eigenschaften-Panel, Zeitleiste und Render-Reiter sind auf die neue Schicht umgebaut. | ungeprüft — alle sechs im Baum und eingehängt, Abnahme nur für einzelne Teile |
| Ein Viewport-Header setzt die Werkzeugreihe nach oben, wie Blender es macht. | **nur Gerüst** — 216 Zeilen, vollständig ausprogrammiert, **kein Aufrufer im Baum** |
| Eine ausklappbare Seitenleiste mit senkrechten Reitern, nach Blenders Vorbild. | fertig |
| Die Auswahl-Liste zeigt Kacheln statt reiner Namen. | ungeprüft |
| Ein Design-System mit 18 Komponentenkarten und einer Token-Leiter liegt im Baum. | fertig — als Entwurfsgrundlage, nicht als Laufzeit-Code |
| 216 SVG-Icons ersetzen den 16-px-Pixelatlas. | teilweise — Icons erzeugt, Vektor-Weg per Vorgabe aus |
| Das Einstellungs-Overlay landet nicht mehr außerhalb des Bildes und fährt sich nicht fest. | fertig |
| Die Undo-Historie lässt sich per Ziehen andocken und wieder lösen, und scrollt automatisch ans Ende. | fertig — 12 An-/Abdock-Zyklen ohne Fehler |
| Undo-Einträge zeigen lesbar, was passiert ist, statt roher Pfade. | fertig |
| Der Texture Manager zeigt eine PNG-Vorschau statt nur den Dateinamen. | fertig |
| Quick Search fügt eine Form direkt per Name ein, ohne den Form-Picker; ESC schließt die Box. | fertig |
| Die Audio-Suche findet importierte Sounds auch, wenn das Stichwort hinten steht. | fertig |
| Strg+F sucht Knochen in den Pose-Eigenschaften. | fertig |

## Einstellungen und Tastenbelegung

| Feature | Stand |
|---|---|
| Die Funktionen des Forks sind in den BBS-Einstellungen schaltbar, statt fest verdrahtet zu sein. | fertig |
| Die Einstellungen zeigen lesbare Namen statt roher Schlüssel. | fertig |
| Der Keybind-Editor nimmt Maustaste 4 und 5 an. | fertig |
| Jede Aktion kann eine zweite Tastenbelegung bekommen. | fertig |
| Keybind-Konflikte werden nur gemeldet, wenn sie im selben Kontext wirklich kollidieren. | fertig |
| Ein Einhand-Workflow für die linke Hand auf einer TKL-Tastatur ist entworfen und belegt. | fertig |
| Räumlicher Klang ist als Schalter am Sound-Clip verfügbar. | fertig |
| Im Flugmodus lässt sich animieren (die Flug-Animationssperre ist umgangen). | fertig |
| F12 versteckt das Debug-Fenster nicht mehr. | fertig |
| Minecraft stiehlt beim Start nicht mehr den Mausfokus. | teilweise — auf 1.21.1 gebaut, das zuständige Mixin fehlt auf `port-26.2` (siehe unten) |

## Darsteller, Menge und Effekte

| Feature | Stand |
|---|---|
| Ein Darsteller zerbricht zu einem bestimmten Film-Tick in physikalische Bruchstücke — jeder Knochen wird ein eigener Körper. | ungeprüft |
| Ein Ragdoll-Modus lässt einen Darsteller ab Befehl unter Schwerkraft zusammensacken. | ungeprüft |
| Ein Massen-Modus rendert große Menschenmengen; Dorfbewohner können nach Keyframe-Muster hüpfen. | teilweise — Crowd-Modus warf pro Frame eine Ausnahme, das ist behoben; Rest ungeprüft |
| Bauarbeiter-Darsteller setzen einen Bauplan Schritt für Schritt um. | ungeprüft — aus dem alten Fork übernommen |
| Trefferflächen lassen sich per Befehl setzen und verursachen Schaden. | ungeprüft — aus dem alten Fork übernommen |
| Ein Partikel-Effekt setzt seinen Minecraft-Klang selbst. | **offen** — Aufgabe #68, nicht gebaut |

## Werkzeuge für die Entwicklung

Nicht changelog-relevant für Endnutzer, aber der zweitgrößte Bauteil des Forks.

| Feature | Stand |
|---|---|
| Änderungen an Mixin-Logik gehen ohne Neustart live: die Logik liegt in austauschbaren Klassen, der gewobene Handler ist ein Einzeiler. | fertig — **122 von 128 Mixins (95 %)** auf der Brücke, live gemessen |
| Auch gewobene Klassen lassen sich tauschen, und neue statische Felder werden vor dem Tausch gemeldet statt danach. | fertig |
| Ein Studio-Server steuert die laufende Instanz für Messläufe: echte Mausklicks durch die GLFW-Kette, virtuelle Tastatur, Zeiger-Züge, Zonenabfrage. | fertig |
| Eine Konsole im Spiel mit Eingabehistorie, Live-Filter und Klick-Übernahme von Logzeilen. | fertig |
| Blenders eigene Debug- und Vergleichswerkzeuge sind angebunden; BBS misst gegen eine eigene Blender-Instanz statt gegen die des Nutzers. | fertig |
| 40 Werkzeuge unter `tools/` — Rig-Export, FBX-Wandlung, Icon-Rasterung, Fensterverwaltung, Abnahme-Akten, Gates. | fertig |
| GPU-Tracer (RenderDoc, apitrace) lassen sich in die Client-JVM laden. | fertig |

## Geschwindigkeit

| Feature | Stand |
|---|---|
| Der Editor läuft mit 43 statt 3 Bildern je Sekunde — der Bild-Cache wurde je Aufruf neu angelegt. | fertig — gemessen |
| Der Film-Controller rechnet Darsteller parallel und cacht pausierte Matrizen. | ungeprüft |
| IK-Ergebnisse werden gecacht; die Last liegt dadurch bei ~0 %. | fertig — deshalb wurde die In-Place-Mathematik bewusst nicht gebaut |

---

## Korrekturen an der vorhandenen Doku

Vier Stellen behaupteten etwas, das der Quelltext widerlegt. Sie sind hier korrigiert,
**im Original aber nicht angefasst** (reine Aufnahme, kein Umbau):

1. **`docs/AUFGABEN.md`, Aufgabe #56** sagt „53 offen" bei den Mixins.
   `python tools/mixin-bruecke-gate.py` meldet live **6 offen, 122 von 128 auf der Brücke**.
   Die Aufgabe steht auf `pending`, ist aber zu 95 % erledigt.
2. **`docs/AUFGABEN.md` ist seit 2026-08-12 nicht mehr fortgeschrieben.** Die gesamte
   Arbeit vom 13. bis 23.08. — Blender-IK-Port, Rig-System, Render-Brücke, NanoVG-Umbau,
   Design-System — steht in **keiner** Aufgabe. Der Spiegel-Hook prüft nur die
   Übereinstimmung mit dem Aufgaben-Speicher, nicht die Vollständigkeit gegenüber dem Baum:
   Arbeit ohne Aufgabe fällt still durch.
3. **`docs/PROFI-UMBAU.md`, Abschnitt „Lagebild"** führt die Kern-IK mit „~4000 Z. dediziert,
   `ModelIKApplier` 1514" als vorhanden. `ModelIKApplier` existiert nicht mehr — der eigene
   Löser wurde am 2026-08-14 entfernt (3.619 Zeilen). Das Lagebild kündigt den Ersatz an,
   sein Ist-Stand ist aber überholt.
4. **`docs/AUFGABEN.md`, Aufgabe #70** behauptet „62 Klassen aus dem alten Fork fehlen".
   Diese Zahl ließ sich nicht nachmessen — siehe „Nicht einordenbar", Punkt 1.

## Nur Gerüst — gebaut, aber ohne Wirkung

Gemessen: **3 von 804 eigenen Nicht-Mixin-Klassen haben keinen Aufrufer im ganzen Baum.**

| Klasse | Zeilen | Was fehlt |
|---|---|---|
| `UIViewportHeaderNano` | 216 | Der Viewport-Header ist fertig ausprogrammiert, wird aber nirgends instanziiert. Kam am 2026-08-22 im Sammelcommit „UI-Umbau" in den Baum. Die alte Werkzeugreihe (`UIWerkzeugleisteNano`) läuft weiter. |
| `GlFaehigkeiten` | 177 | Testwerkzeug, nie eingehängt. |
| `CrowdMassVeilDebugCommand` | 157 | Debug-Befehl, nirgends registriert. |

## Nicht einordenbar — mit Grund

1. **Aufgabe #70: „62 Klassen aus dem alten Fork fehlen."** Der Vergleichsbaum
   `BBSTools-Fabric` liegt nicht auf dieser Maschine, und das Repo hat keinen Remote
   darauf. Gegen den Geschwisterzweig `origin/1.21.1` gemessen fehlt auf `port-26.2`
   genau **eine** Klasse (`WindowNoFocusStealMixin`), 67 sind neu dazugekommen — aber das
   beantwortet #70 nicht, weil `origin/1.21.1` bereits die Brücke enthält und damit **kein**
   Vorgänger, sondern ein paralleler Zweig ist. Ohne den alten Baum bleibt die Frage offen.
2. **`WindowNoFocusStealMixin` fehlt auf `port-26.2`.** Die zugehörige Aufgabe („MC-Boot
   stiehlt den Mausfokus") ist als erledigt verbucht, und ein Ersatz ist im Quelltext nicht
   auffindbar. Ob der Fokus-Diebstahl auf 26.2 zurück ist, ist **nicht gemessen** — das
   braucht einen Boot am Bildschirm.
3. **Die sechs Nano-Panels** sind im Baum und eingehängt, aber es gibt keine Abnahme-Akte
   für sie als Ganzes. Ich habe sie als „ungeprüft" geführt, statt aus der Anwesenheit des
   Codes auf Funktion zu schließen.
4. **Fracture, Ragdoll, Schematic, Hitbox** stammen laut Quelltext-Kommentar aus dem alten
   NeoForge-Fork („Ported from NeoForge BBSTools", 15 Dateien). Sie sind portiert und
   verdrahtet, aber es gibt keine Messung auf 26.2 — deshalb durchweg „ungeprüft".
5. **Der Replay-Zweig der Oberfläche steht in keiner Abnahme.** Er zeichnet nicht über die
   Vektor-Schicht und meldet keine Zonen; ein Zonenlauf sieht ihn nicht. Damit ist ein
   erheblicher Teil der Oberfläche vor dem Release ungemessen. Das ist Punkt 1 der
   Release-Liste vom 2026-08-23 und **kein Befund, sondern ein blinder Fleck**.
6. **Ob ein echter Mauszug bei Ziehzonen ankommt, ist nicht belegt** — `/ui/ziehfeld` ruft
   die Logik direkt. Betrifft jede Zieh-Geste im Editor. Punkt 2 der Release-Liste.

## Befunde, die beim Aufnehmen aufgefallen sind

Gemeldet, nicht behoben — dies ist eine Aufnahme, kein Umbau.

1. **Das Mixin-Brücken-Gate hat seine Klinke verloren.** `tools/mixin-bruecke-gate.py`
   meldet „kein stand hinterlegt"; `tools/mixin-bruecke-stand.json` existiert nicht.
   Das Gate misst noch, kann aber keine neue Schuld mehr blocken — genau die Konstruktion,
   an der laut Aufgabe #56 schon einmal ein Register gestorben ist.
2. **`UIViewportHeaderNano` ist tot im Baum** (siehe oben). Entweder einhängen oder entfernen.
3. **Der Aufgaben-Spiegel deckt 11 Tage Arbeit nicht ab** (siehe Korrektur 2). Der Hook
   erzwingt Gleichstand mit dem Aufgaben-Speicher, nicht Vollständigkeit gegenüber dem Baum.
