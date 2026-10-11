# Changelog

Alle nennenswerten Änderungen dieses Forks. Grundlage ist die geprüfte Feature-Aufnahme in
[`docs/FEATURES-RELEASE.md`](docs/FEATURES-RELEASE.md) — dort steht zu jedem Punkt der
Reifegrad und der Beleg.

**Ehrlichkeitsregel dieses Changelogs:** Was nicht fertig ist, steht nicht als fertig drin.
Punkte, die hinter einem Schalter liegen, per Vorgabe abgeschaltet sind oder deren Wirkung
nicht gemessen wurde, sind als solche markiert. Ein Feature, das kompiliert, aber im
laufenden Programm nichts tut, taucht hier gar nicht auf.

---

## [2.4] — unveröffentlicht, Ziel 2026-09-02

Erster Stand des Forks auf **Minecraft 26.2** (Fabric). Basis sind `bbs-fs` (Wemppy4) und
`bbs-mod-cml-edition` (ElGatoPro300), die ihrerseits McHorses BBS mod forken.

Umfang gegenüber den Upstreams: **202.884 eigene Java-Zeilen in 916 Dateien**, die es in
keinem Upstream-Zweig gibt.

### Rigging und IK — mit Blenders echtem Löser

Der größte Einzelblock dieses Forks. BBS' eigener IK-Löser wurde **entfernt** (3.619 Zeilen)
und durch Blenders echten Löser ersetzt — aus Blenders C++-Quelle übersetzt, nicht nachgebaut.

- IK rechnet mit Blenders Löser (Jacobi-Matrix, SDLS-Dämpfung, SVD, Pol-Zwang).
  Parität gegen Blender gemessen, größte Abweichung **7e-06**.
- Ein beliebiges Blender-Rig lässt sich importieren; BBS führt Blenders Constraint-Graph
  zur Laufzeit aus. **706 von 706 Knochen decken sich exakt** mit Blender.
- Das Control-Rig ist der Griff: Blenders Kontroll-Formen sind sichtbar und per Linksklick
  anwählbar. Sie folgen Bewegung und Drehung ihres Knochens, auch beim Abspielen.
- IK/FK pro Kette umschaltbar, Regler im N-Panel neben den Rig Layers.
- Snap IK↔FK setzt die andere Kette auf die aktuelle Pose, inklusive mittlerem Gelenk.
- Rig Layers blenden Control-Gruppen ein und aus, im Zeilen-Layout von Rigify.
- Gesichts-Steuerung über Shape Keys: ein Griff am Board treibt die Netzverformung über die
  ganze Kette.
- Ein Befehl macht aus einer Mixamo-FBX-Datei ein klickbares IK-Rig.
- Drei Rig-Systeme laufen durch: **Rigify, mixamo_rig, Auto-Rig Pro**.
- Blenders Constraint-Typen sind portiert: Copy Location/Rotation/Scale/Transforms,
  Track To, Damped/Locked Track, Child Of, Limit Location/Distance, Armature, Stretch To.
  *Teilweise:* 2 von 518 Constraints weichen ab (Fußrollen-Paar, bekannt und eingegrenzt).
- Treiber (Driver) werden exportiert und ausgewertet, statt eingefrorene Werte zu schreiben.

### Bedienung im Viewport — nach Blenders Vorbild

- Verschieben, Drehen und Skalieren rechnen wie Blender: Mausabstand zum Pivot, Spiegeln
  beim Überschreiten, Shift verlangsamt auf 1/30.
- Volle Blender-Rückmeldung: Maussymbol je Modus, gestrichelte Strähne vom Pivot,
  drei dicke Achslinien in Rot/Grün/Blau, orangefarbener Ursprungspunkt.
- Die Achslinien bleiben beim Ziehen am Startpunkt verankert, statt mit dem Objekt zu wandern.
- Achsen- und Sperrlinien zeichnen mit weicher Kante statt als pixelige Quader.
- Das ausgewählte Replay bekommt eine Outline wie in Blender.
- Der Bewegungspfad des gewählten Replays ist im Viewport sichtbar.
- Ein Skelett-Layer zeichnet jeden Knochen als Blender-Oktaeder.
- Die Welt lässt sich als Drahtgitter zeichnen (**Alt+Z**) — für Aufnahmen, bei denen ein
  Darsteller aus dem Boden kommt. *Mit Sodium und Iris nicht gemessen.*
- Ein Physik-Overlay zeigt die Kollisionskörper.
- Das HUD ist im Editor-Viewport sichtbar.

### Kameras

- Kamera-Clips lassen sich direkt im Replay-Editor sehen, auswählen und verschieben.
- Ein Tastenkürzel wählt eine bestimmte Kamera sofort aus, statt das Kameramenü zu öffnen.
- Shift+3 öffnet die Kameramodus-Auswahl.
- Der Flugmodus ändert die Kamera auch ohne ausgewählten Clip.
- Orbit- und Flugmodus ändern nur noch den Kameramodus, nicht mehr die Clip-Werte.
- Im Orbit zeigt ein Achsenkreuz wieder den Drehpunkt in der Bildmitte.
- Maustaste 4 fährt im Orbit auf das Replay zu und rahmt es nach seiner **echten
  Modellgröße** ein, nicht nach der Trefferbox.
- Ein bestehendes Replay lässt sich schnell auswählen — in der Welt wie im Dashboard.

### Zeitleiste

- Marker lassen sich setzen, anklicken und einfärben.
- Clips lassen sich deaktivieren bzw. verstecken, ohne sie zu löschen.
- Hotbar- und HUD-Keyframe-Spuren sind zu einer aufklappbaren Spur gebündelt.
- Eine Auswahl im Viewport tauscht die Zeitleiste nicht mehr aus.

### Export

- Export mit **Supersampling** (Faktor 1–4, Lanczos-Verkleinerung).
- Audio-Export schreibt **eine WAV je Clip**, schon auf Clip-Länge geschnitten und nach
  Zeitleisten-Position benannt — in der Schnittsoftware direkt in der richtigen Reihenfolge.
  <!-- SCHALTER-ZEILE AUDIO -->
  *Liegt hinter dem Schalter „Render audio into separate files" und ist per Vorgabe **aus**.*
- Der Video-Export läuft wieder: eine leere Ordner-Vorgabe ließ ffmpeg auf Java 25 sterben.

### Oberfläche

- Eine Vektor-Zeichenschicht (NanoVG) mit Farbtoken, Bausteinen, Symbolen und Bildern
  zeichnet Panels im Browser-Look statt in pixeligen Textur-Quads. Topbar, Statusleiste,
  Werkzeugleiste, Eigenschaften-Panel, Zeitleiste und Render-Reiter sind darauf umgebaut.
  216 SVG-Icons ersetzen den 16-px-Pixelatlas.
  <!-- SCHALTER-ZEILE VEKTOR -->
  *Liegt hinter den Schaltern `vector_font`, `vector_ui` und `vector_icons` und ist per
  Vorgabe **aus**.*
- Eine ausklappbare Seitenleiste mit senkrechten Reitern, nach Blenders Vorbild.
- Die Undo-Historie lässt sich per Ziehen andocken und wieder lösen und scrollt
  automatisch ans Ende. Ihre Einträge zeigen lesbar, was passiert ist, statt roher Pfade.
- Das Einstellungs-Overlay landet nicht mehr außerhalb des Bildes und fährt sich nicht fest.
- Der Texture Manager zeigt eine PNG-Vorschau statt nur den Dateinamen.
- Quick Search fügt eine Form direkt per Name ein, ohne den Form-Picker; ESC schließt sie.
- Die Audio-Suche findet importierte Sounds auch, wenn das Stichwort hinten steht.
- Strg+F sucht Knochen in den Pose-Eigenschaften.

### Einstellungen und Tastenbelegung

- Die Funktionen des Forks sind in den BBS-Einstellungen schaltbar, statt fest verdrahtet.
- Die Einstellungen zeigen lesbare Namen statt roher Schlüssel.
- Der Keybind-Editor nimmt **Maustaste 4 und 5** an.
- Jede Aktion kann eine **zweite Tastenbelegung** bekommen.
- Keybind-Konflikte werden nur gemeldet, wenn sie im selben Kontext wirklich kollidieren.
- Ein Einhand-Workflow für die linke Hand auf einer TKL-Tastatur.
- Räumlicher Klang als Schalter am Sound-Clip.
- Im Flugmodus lässt sich animieren.
- F12 versteckt das Debug-Fenster nicht mehr.

### Geschwindigkeit

- **Editor-Bildrate von 3 auf 43 Bilder je Sekunde** — der Bild-Cache wurde je Aufruf neu
  angelegt.
- IK-Ergebnisse werden gecacht; die Last liegt dadurch bei etwa 0 %.

### Behoben

- Die Hand streckt sich beim Rennen nicht mehr falsch (FOV wirkt korrekt auf die Hand).
- Der Hand-Bob läuft auch beim Gehen, nicht nur beim Sprinten.
- Die Hand übernimmt die Item-Benutzung vollständig, statt den Schlag halb zu zeigen.
- Die Dashboard-Verzerrung beim Clip-Wechsel ist weg.
- Der Crowd-Modus wirft keine Ausnahme mehr pro Bild.
- Minecraft stiehlt beim Start nicht mehr den Mausfokus. *Auf 26.2 nicht nachgemessen —
  siehe „Bekannte Lücken".*
- Icons verschwinden nicht mehr beim Herunterscrollen.
- Morphs sind in der Welt wieder sichtbar, mit offenem wie geschlossenem Panel.
- 32 Abnahme-Akten sind vollständig belegt; **kein bekannter kaputter Funktionsbefund ist
  offen.**

### Für Entwickler

- Änderungen an Mixin-Logik gehen **ohne Neustart** live: die Logik liegt in austauschbaren
  Klassen, der gewobene Handler ist ein Einzeiler. **122 von 128 Mixins (95 %)** laufen so.
- Auch gewobene Klassen lassen sich tauschen; neue statische Felder werden **vor** dem
  Tausch gemeldet statt danach.
- Ein Studio-Server steuert die laufende Instanz für Messläufe: echte Mausklicks durch die
  GLFW-Kette, virtuelle Tastatur, Zeiger-Züge, Zonenabfrage.
- Eine Konsole im Spiel mit Eingabehistorie, Live-Filter und Klick-Übernahme von Logzeilen.
- 40 Werkzeuge unter `tools/` — Rig-Export, FBX-Wandlung, Icon-Rasterung, Abnahme-Akten, Gates.
- GPU-Tracer (RenderDoc, apitrace) lassen sich in die Client-JVM laden.

### Bekannte Lücken

Offen ausgewiesen, nicht verschwiegen:

- **Der Replay-Zweig der Oberfläche steht in keiner Abnahme.** Er zeichnet nicht über die
  Vektor-Schicht und meldet keine Klickflächen; ein Prüflauf sieht ihn nicht. Ein Fehler
  dort wäre bis zum Release unsichtbar.
- **Ob ein echter Mauszug bei Ziehzonen ankommt, ist nicht belegt.** Betrifft jede
  Zieh-Geste im Editor.
- `WindowNoFocusStealMixin` fehlt auf dem 26.2-Zweig. Ob der Fokus-Diebstahl beim Start
  zurück ist, wurde nicht am Bildschirm nachgemessen.
- Der Drahtgitter-Modus ist mit Sodium und Iris nicht gemessen.
- Fracture, Ragdoll, Schematic und Hitbox sind aus dem älteren Fork portiert und verdrahtet,
  aber auf 26.2 nicht gemessen.
- `UIViewportHeaderNano` liegt als fertiger, aber nicht eingehängter Code im Baum.
- Ein stiller Rückfall in `getDopeSheetY` meldet bei eingeklappten Spuren die oberste Zeile.
  Alle vier Aufrufer wurden geprüft: sichtbar trifft es niemanden. Fix nach dem Release.

### Offene Punkte zur Rechtslage

1. Zwei Modelldateien nennen einen dritten Autor im Dateinamen; eine Weitergabeerlaubnis
   ist nicht belegt.
2. **Der IK-Löser ist nicht abgetrennt.** Er ist aus Blender übersetzt; solange er mit dem
   Mod in einem JAR steckt, gilt für das Ganze GPL-2.0-or-later (siehe [LICENSE.md](LICENSE.md)).
3. Das Paket enthält 111 geerbte Assets des BBS mod.

### Bewusst nicht gebaut

- **Kein eigenes Vulkan-Backend und kein eigener Raytracer.** Mojang liefert Vulkan selbst;
  ein Eigenbau kämpft gegen die Plattform.
- **Keine IK-Mathematik in-place.** Die Caches haben die Last bereits auf etwa 0 % gebracht.
- **Kein eigener Rig-Editor.** Das Rig wird in Blender gebaut und importiert, statt eine
  zweite Wahrheit zu erfinden.
