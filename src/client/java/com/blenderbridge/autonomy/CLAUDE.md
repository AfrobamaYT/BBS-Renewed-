<!-- Dossier: com/blenderbridge/autonomy — die BBS-autonomie-schicht (plan
     docs/superpowers/plans/2026-07-18-bbs-autonomie-schicht-plan.md). Eine KI (Claude) baut
     BBS-machinima-szenen autonom: LESEN (SceneDigest) → HANDELN (place/shot/path, spätere tasks)
     → SEHEN (render/video) → URTEILEN. Der rote faden: die KI plant in SEMANTIK, die engine rechnet
     die zahlen, ein regel-gate prüft billig vor dem render, das video-tool ist die letzte instanz.
     ALLE klassen hier sind NEU + LAZY-ladbar (kein boot-fenster; deploy-klassen-regel im dossier
     eine ebene höher, com/blenderbridge/CLAUDE.md). Kein BBS-source-edit (ARCHITEKTUR-invariante 2).
     Routes self-registrieren via StudioServer.route() — StudioServer.java wird NIE editiert. -->

<!-- verifiziert (ERSTELLEN-routes create_replay + keyframe: AutonomyWrite + AutonomyRoutes, AU/opus,
     2026-07-18) — die AUTONOMIE-LÜCKE geschlossen (user: "kannst du selbständig replays ERSTELLEN und
     bewegen und keyframen?"). COMPILE-GATE grün (compileClientJava, JAVA_HOME=../jbr-dcevm-home, BUILD
     SUCCESSFUL; AutonomyWrite.class 32.5K + $FilmRef + $SceneGeometry, AutonomyRoutes.class 8.7K). 2 neue
     routes (body-swap AutonomyRoutes: +2 route()-zeilen in register(), +2 handler, +parseDouble/parseInt-
     helper; +2 methoden auf der lazy AutonomyWrite): /bbs/create_replay (film.replays.addReplay() = BBS'
     factory, NICHT new Replay → uuid+shadow-kf+preNotify/add/postNotify; new ModelForm()+model.set()+
     replay.form.set() → form.model treibt getModels().getModel(); type by-class serialisiert, KEIN
     FormArchitect.fromData-MapType-pfad [= die EXEC/JShell BaseType/MapType-cast-falle aus der task-note,
     compiled new ModelForm() ist clean]; x/y/z-kf @tick0; EINZIGE route die die replay-LISTE ändert →
     handOverToBBSWithNewActor mit controller.createEntities() [sonst kein entity-spawn — recipe verbatim
     aus BBS' UIReplayList.addReplay(Vector3d,pitch,yaw)+updateFilmEditor]) und /bbs/keyframe (numericChannel
     via replay.keyframes.get(id)+KeyframeFactories.DOUBLE-gate → EIN keyframe @tick, mehrere=bewegung;
     item/slot-kanäle rejected). ⚠ MODEL-FALLE dokumentiert: player/alex = LEERES rig (kein bobj, rendert
     nichts) → emoticons/alex ist der sichtbare default (KK2/FT2-befund); route hardcoded KEINE substitution
     (rendert was sie kriegt, unbekanntes model = unsichtbarer aber echter actor — ehrlich wie der editor).
     Beide: FilmBackup vorher (commitBackup-muster). RUNTIME-CONFIRM + BOOT MACHT DER LEADER (self-confirm-
     hard-rule + deploy=boot, siehe block unten). Python: bbs_autonomy.py +bbs_create_replay/+bbs_keyframe
     (mcstudio), py-compile grün. Dies ist der compile+konstruierbarkeits-stand, NICHT der runtime-beweis. -->

<!-- verifiziert (tasks 0.3/1.1/1.2/2.1 write-routes: AutonomyWrite + AutonomyRoutes, W5/opus,
     2026-07-18) — COMPILE-GATE grün (compileClientJava, JAVA_HOME=../jbr-dcevm-home, BUILD SUCCESSFUL
     in 2s; AutonomyWrite.class 26.6K + $FilmRef + $SceneGeometry, AutonomyRoutes.class 6.4K in
     build/classes/java/client). NEUE lazy-klasse `AutonomyWrite.java` (die HANDELN/schreib-logik) +
     body-swap `AutonomyRoutes` (register() +5 route()-zeilen, +6 handler-methoden, +Readback/
     MinecraftClient-imports). 6 neue routes: /bbs/place (ZoneMapper→x/z-kf), /bbs/blocking_check
     (BlockingGate.checkStage aus szenen-bbox+camYaw — NICHT das nackte check(), W2-befund), /bbs/shot
     (ShotCompiler→camera-clip additiv), /bbs/camera_check (max-accel über die live-KeyframeClips),
     /bbs/path (ZoneMapper from/to→PathSolver.solve→toKeyframes→x/y/z-kf), /bbs/render (Readback-
     framebuffer-abgriff→PNG). Jede schreib-route: FilmBackup-snapshot VORHER (CameraClipWriter-
     commitBackup-muster via FilmBackup.backupFolder/enforceLimit public-helpers, KEIN protect() —
     das ist throttled für den add-on-push-pfad), keyframe-write (insert @playhead, mirror
     ActorImporter), handOverToBBS (undo/refresh/save). RUNTIME-CONFIRM MACHT DER LEADER (self-confirm-
     hard-rule): WRITE in user-film 'f' → leader testet live + render-beweis. Dies ist der
     compile+konstruierbarkeits-stand, NICHT der runtime-beweis.

     ⚠ DEPLOY = BOOT (nicht hotswap): AutonomyRoutes war beim client-init SCHON geladen UND register()
     + StudioServer.start() liefen SCHON. StudioServer.start() contextet die ROUTES-map EINMAL beim
     start (createContext-loop, dann `server != null`-guard) — eine route die NACH start in die map
     kommt wird NIE ge-createContext-et. Ein hotswap von AutonomyRoutes allein macht die 6 neuen
     routes also NICHT erreichbar (register() ist zudem idempotent-guarded, re-run = no-op). Sauberer
     weg: BOOT (gradlew runClient) — der frische init ruft register() mit allen 6 routes VOR
     StudioServer.start(), das contextet sie. Boot = deploy-fenster-mit-save (invariante 3). ALTERNATIV
     ohne boot (leader-EXEC, falls kein save-fenster): AutonomyWrite lädt lazy beim 1. call; die 6
     contexts per reflection auf das private StudioServer.server-feld nachziehen (`server.createContext
     (path, handler)` je route) — die handler sind `AutonomyRoutes::handleXxx` (private, per reflection
     oder ein EXEC-lambda das AutonomyWrite.place(...)/… direkt ruft). Boot ist der ehrliche default. -->

<!-- verifiziert (task 0.2 SceneDigest + AutonomyRoutes, W1/opus, 2026-07-18) — COMPILE-GATE grün
     (compileClientJava, JAVA_HOME=../jbr-dcevm-home, BUILD SUCCESSFUL; SceneDigest.class 13.4K +
     SceneDigest$FilmRef.class + AutonomyRoutes.class 1.9K in build/classes/java/client). LIVE-EXEC-
     roundtrip (SceneDigest.build() an film 'f' gegen die laufende JVM :7900/:9880) macht der LEADER
     — dies ist der compile+konstruierbarkeits-stand, NICHT der runtime-beweis. -->

# com/blenderbridge/autonomy — autonomie-schicht

## SceneDigest.java (task 0.2) — die LESEN-seite

`SceneDigest.build()` → hierarchisches JSON, der szenen-steckbrief des offenen BBS-films:

```
{
  ok, film, source ("editor"|"playing"), playhead (int tick), playing (bool), fps (20),
  actors: [{ uuid, name, isGroup, group, enabled, form, formType, formModel,
             pos:{x,y,z} | null, yaw, zone, zoneFrame }],
  cameraClips: [{ type, title, layer, tickStart, tickEnd, enabled }],
  channelCatalog: [{ name, type, semantik, bereich }],
  error
}
```

- **Der kern = channelCatalog**: pro kanal die SEMANTIK + der gültige bereich (x=weltposition-blöcke,
  yaw=blickrichtung-grad, health=0..20…), damit der LLM in BEDEUTUNG plant statt rohe zahlen zu raten
  (research kern-technik 1 / lehre 5). Statische tabelle (`buildChannelCatalog`) — die kanal-menge ist
  BBS-fest (`ReplayKeyframes.CURATED_CHANNELS`), also hand-kommentiert statt reflektiv.
- **REIN LESEND, wirft NIE**: wie ActorExporter; "kein film offen" → `{ok:false, error:"no film open"}`,
  jeder unerwartete throw → `ok:false`. Jeder contract-key immer präsent (LLM/python liest blind).
- **Film + playhead**: editor-film (mit UIFilmPanel für `getCursor()`/`isRunning()`) zuerst, dann ein
  bloß spielender film (playhead=0, playing=false — dokumentierte grenze). `peekDashboard()` NIE
  `getDashboard()` (letzteres baut ein dashboard als seiteneffekt).
- **Actor-pose am playhead**: keine felder — `replay.keyframes.x/y/z/yaw` sind `KeyframeChannel<Double>`,
  `.interpolate(tick)` liefert exakt was BBS an dem tick zeigt (wie ReplayPoseReader). Leer → 0 (BBS-fallback).
- **Clip-TYPE**: NICHT `clip.getId()` (das ist nach `Clips.sync()` nur der listen-index!) — sondern
  `film.camera.getFactory().getType(clip)` → `Link` → typ-key (idle/dolly/path/keyframe/orbit/...).
  tickEnd = tickStart + duration (exklusiv, BBS' isInside-konvention).

### ZONE = COARSE STUB bis task 1.1 (Regel 0, dokumentiert)
`zone` ist im schema (plan), aber die ECHTE 3×3-zone (kamera-RELATIV) ist `ZoneMapper` (task 1.1). Bis
dahin liefert `coarseZone()` ein WELT-ABSOLUTES bbox-gitter (vorne=kleineres z, links=kleineres x — eine
willkürliche welt-konvention; ohne kamera-blickrichtung gibt es kein echtes "vorne"). Markiert mit
`zoneFrame:"world-bbox-coarse"`. Task 1.1 ersetzt `coarseZone` durch den echten ZoneMapper-aufruf.

## AutonomyRoutes.java (task 0.2) — der self-register-anker

`AutonomyRoutes.register()` registriert `GET /bbs/scene` → `SceneDigest.build()` via
`StudioServer.route(path, handler)` (:7900). IDEMPOTENT (guard `registered`), LAZY. **AUFRUF-PUNKT:
`register()` MUSS EINMAL laufen VOR `StudioServer.start()`** — `route()` füllt nur die map, `start()`
verdrahtet sie (StudioServer-class-doc). Gehört in die studio-client-init unmittelbar vor `start()`,
dieselbe stelle wie see/ + compile_daemon/ ihre routes registrieren. StudioServer.java wird NIE editiert.

**RENDER-THREAD-HOP**: `SceneDigest.build()` liest live-state = MAIN THREAD ONLY. Der HTTP-handler
läuft auf dem server-thread → `RenderThread.submit(() -> SceneDigest.build().toString())` (exakt wie
StudioServers eigenes `/state`). Bei eingefrorenem render-thread → `{error}`-JSON statt hänger.

## ZoneMapper.java (task 1.1) — benannte zone → (x,z), kamera-relativ (der "echte-arbeit"-teil, AR1)

`ZoneMapper.resolve(String zone, float[] bboxMin, float[] bboxMax, float camYawDeg)` → `float[]{x,z}`.
REINE mathematik (kein film/reflektion/HTTP). Ersetzt `SceneDigest.coarseZone` als der ECHTE mapper.

- **3×3-gitter** `{vorne,mitte,hinten}×{links,mitte,rechts}` (Text-to-Stage). "vorne" = KAMERA-NAH.
  `parseZone` ist tolerant: reihenfolge egal, fehlender teil = mitte, unbekannt = mitte-mitte (nie throw).
- **KAMERA-RELATIV, nicht welt-absolut (AR1 "was hart bleibt" #3):** erst eine kanonische verschiebung
  IN DER BBOX (fraktion entlang tiefe/breite, `SPREAD=0.66` hält luft zum bbox-rand), dann in den
  KAMERA-FRAME rotiert (forward/right aus `camYaw`, MC-yaw: 0°=+Z, fwd=(−sin,+cos), right=(+cos,+sin)).
  "vorne" = kamera-nah = ENTGEGEN der blickrichtung vom zentrum (−forwardMag).
- **Defensiv:** null/degenerierte bbox → ursprung mit 1-block-basis; ausdehnung <1 → mindest-radius 1
  (sonst fallen bei 1 actor alle 9 zonen auf den zentroid).
- **BEWIESEN (leader-EXEC, standalone gegen build/classes + gson):** bbox 0,0..10,10 @ yaw0:
  vorne-mitte→(5.0, 1.70) [kamera-nah=kleines z], hinten-mitte→(5.0, 8.30), mitte-links→(1.70, 5.0),
  mitte-rechts→(8.30, 5.0). @ yaw90 (blick −X): vorne-mitte→(8.30, 5.0) = "vorne" in +X gedreht →
  BELEGT kamera-relativ.
- **TODO SceneDigest-rewire:** `coarseZone` → `ZoneMapper.resolve(zone, sceneBbox, camYaw)` (body-only
  auf SceneDigest, owner=W1). Dann `zoneFrame:"camera-3x3"`.

## BlockingGate.java (task 1.1) — die 5 Text-to-Stage-regeln als billiges pre-render-gate (AR1)

`BlockingGate.check(float[][] positions)` → `{ok, violations:[{regel,grund}]}`. REINE mathematik,
WIRFT NIE (null/leer → ok:true). Fängt ~90% peinlicher blockings OHNE render (billig-vor-teuer, R0c).

- **5 regeln (research-doc formeln):** R1 proxemik `s_prox=(1/k)Σ(1−d/2)<0.25` (keiner vorne) ·
  R2 balance `|schwerpunkt−mitte|>δ·breite`, δ=0.4 · R3 kolinear flachstes 3er-tupel `fläche<τ=1.0` ·
  R4 anti-thrash `Manhattan-delta>cap=12` (braucht vor-zustand) · R5 auftritt neuer actor `nx,nz<0.5`
  = poppt im zentrum (braucht vor-zustand). Mehrere regeln koexistieren im violations-array.
- **⚠ STAGE-FRAME (die falle, gemessen):** R1/R2 messen "vorne/hinten"/"links/rechts" — das hat NUR
  bedeutung relativ zu einer BÜHNE. Gegen die actor-EIGENE bbox sind sie SELBST-REFERENZIELL (immer
  steht jemand im vordersten drittel seiner eigenen spanne → R1 feuert nie; bei 2 actors liegt der
  schwerpunkt exakt auf der bbox-mitte → R2 feuert nie). Darum `checkStage(positions, previous,
  stageBboxMin, stageBboxMax, camYawDeg)`: projiziert in den kamera-frame (wie ZoneMapper) + misst
  gegen die STAGE-bbox. `check(...)` ohne stage = actor-bbox-fallback (fängt nur EXTREMES clustering).
  R3/R4/R5 sind frame-UNABHÄNGIG und laufen immer voll. **/bbs/blocking_check sollte `checkStage` mit
  der szenen-bbox+camYaw aus dem SceneDigest speisen, NICHT `check`** — sonst sind R1/R2 quasi tot.
- **BEWIESEN (leader-EXEC, standalone):** kolineare 3er (x=1,5,9@z5)→R3 (ok:false); dreieck
  (5,1)(2,8)(8,8)→ok:true; 1-links-11-rechts→R2 (imb 4.17>4.0) + R3 koexistent; teleport
  (5,1)→(25,25)→R4 (44>12); allBack-stage (z22..28 in bühne z0..30, yaw0)→R1 (s_prox 0.00), dieselbe
  anordnung OHNE stage → ok:true (belegt: R1 braucht das stage-frame); neuer-actor-im-zentrum→R5.

## AutonomyWrite.java (tasks 0.3/1.1/1.2/2.1) — die HANDELN/SEHEN-seite (W5)

`AutonomyWrite` ist die schreib-logik hinter den 6 neuen routes; `AutonomyRoutes` ist die dünne HTTP-
schicht davor. NEUE lazy-klasse (deploy-window-free) statt die schreib-methoden an das schon geladene
`AutonomyRoutes` zu hängen (member-add auf geladener klasse = deploy-risiko).

- **`place(actor, zone)`** → ZoneMapper.resolve(zone, szenen-bbox, camYaw) → x/z-keyframe @playhead
  auf `replay.keyframes.x/z`. FilmBackup vorher. Kein removeAll (einzel-keyframe-edit, nicht channel-
  besitz wie ActorImporter). Szenen-bbox+positions+camYaw kommen aus `sceneGeometry(film,tick)` (dieselbe
  geometrie die SceneDigest.coarseZone sammelt) — camYaw = yaw des ersten kamera-KeyframeClips an dessen
  start-tick, sonst 0 (default: kein kamera-clip = kein echtes "vorne", ZoneMapper-grob-frame).
- **`blockingCheck()`** → BlockingGate.**checkStage**(positions, null, bboxMin, bboxMax, camYaw) — NICHT
  `check()` (sonst R1/R2 tot, W2-befund). Rein lesend, kein snapshot.
- **`shot(actor, size, angle, movement)`** → ShotCompiler.ActorPose aus dem replay am playhead. ⚠ YAW:
  der actor-yaw (`replay.keyframes.yaw`) IST MC-entity-BODY-yaw in grad (channelCatalog: 0=süd/90=west) —
  GENAU was `ShotCompiler.forwardXZ` erwartet, also OHNE konvertierung durchgereicht (GEMESSEN: `Angle.
  angle(dx,dy,dz)` nutzt BBS-KAMERA-yaw `atan2(dz,dx)+90`, aber der actor-KANAL-yaw ist entity-yaw — die
  W3-doc-warnung "ggf. konvertieren" trifft NICHT zu, der digest liefert schon entity-yaw). over_shoulder
  nimmt den nächsten anderen performing-actor als schulter-partner. clips werden ADDITIV an `film.camera`
  gehängt (addClip+findFreeLayer+sortLayers+sync, CameraClipWriter-muster) — NICHT die alte fahrt ersetzt
  (LLM baut shot-für-shot). default-dauer 40 ticks (>CameraCheck-min 20). FilmBackup vorher.
- **`cameraCheck()`** → prüft NUR die max-beschleunigungs-regel über die live-KeyframeClips (x/y/z 2.
  differenz). ⚠ DOKUMENTIERTE GRENZE: `CameraCheck.check` nimmt `List<Compiled>`, und `Compiled` hat
  KEINEN public-ctor — seine geometrie (loi/size/angle) kennt nur der ShotCompiler der ihn IN DER SESSION
  baute; ein roher film-clip trägt keine shot-identität zum rekonstruieren (gleicher grund warum
  CameraClipWriter dense-baked statt Dolly/Path zu re-authoren). 180°/jump-cut/min-duration laufen also
  gegen die IN-SESSION kompilierten shots (über /bbs/shots Compiled-result), nicht gegen disk-clips. Die
  route nennt diese grenze im `note`-feld der antwort.
- **`path(actor, from, to)`** → ZoneMapper(from/to)→(x,z), PathSolver.solve(world, from, to) (⚠ MAIN-
  THREAD world-read, render-hop deckt es), toKeyframes(playhead, DEFAULT_SPEED) → x/y/z-keyframes.
  endpoint-Y = actor-Y am playhead (A* snappt eh auf den floor). FilmBackup vorher.
- **`/bbs/render`** (in AutonomyRoutes) → `Readback.captureMainFramebuffer` (glReadPixels in-JVM,
  fenster-fokus-unabhängig) → PNG `run/mcstudio-shots/bbs_render.png`. FRAME-ABGRIFF des laufenden
  renders, kein eigener durchlauf; `secs` wird geechot aber die zeit-getriebene playback-fahrt ist
  bewusst NICHT hier (braucht multi-tick main-thread-playback, leader/video-tool-getrieben).
- **`createReplay(formModel, zone, x, y, z)`** (ERSTELLEN, AU/opus) → `film.replays.addReplay()` (BBS'
  factory: uuid+shadow-kf+notify, NIE `new Replay`) + `new ModelForm(); form.model.set(model); replay.
  form.set(form)` + x/y/z-kf @tick0. Zone via ZoneMapper (wie place), sonst explizite x/y/z, sonst
  szenen-mittel-Y (`defaultY`). **EINZIGE route die die replay-LISTE ändert** → `handOverToBBSWithNewActor`
  (= handOverToBBS + `controller.createEntities()`, sonst spawnt der actor NICHT als entity — das ist die
  eine sache die place/shot/path NICHT tun). Recipe = BBS' `UIReplayList.addReplay(Vector3d,pitch,yaw)` +
  `updateFilmEditor` verbatim. ⚠ MODEL: `player/alex` ist ein LEERES rig (kein bobj → unsichtbar),
  `emoticons/alex` hat ein bobj (default in der python-tool-signatur); die route substituiert NICHT (kein
  invented behaviour — rendert das gegebene model, unbekannt=unsichtbarer aber echter actor). `new
  ModelForm()` DIREKT ist der clean-pfad (type by-class serialisiert); der `FormArchitect.fromData`-MapType-
  pfad ist die EXEC/JShell-cast-falle aus der task-note, in compiled java kein problem. FilmBackup vorher.
- **`keyframe(actor, channel, tick, value)`** (ERSTELLEN, AU/opus) → `numericChannel(replay, channel)`
  (= `replay.keyframes.get(id)` + `factory==KeyframeFactories.DOUBLE`-gate) → `insertDouble` @tick. Nur die
  double-kanäle (x/y/z/yaw/pitch/headYaw/bodyYaw/sneaking/...) schreibbar; item/slot (ItemStack/Integer)
  rejected (die gehören der Blender-strings-bridge). Mehrere calls über ticks = bewegungs-kurve. Replay-
  LISTE unverändert → normaler `handOverToBBS` (KEIN createEntities); `BaseFilmController.update()` re-
  applyt den kanal pro tick auf die live-entity, also sichtbar auch pausiert. FilmBackup vorher.

## Offen (nächste tasks, nicht hier)
- Runtime-EXEC-beweis SceneDigest an film 'f' (leader, W1-task). · **LIVE-CONFIRM der jetzt 8 write-routes
  am film 'f' (leader, self-confirm-hard-rule) + der deploy (BOOT — siehe dossier-kopf-block; register()
  contextet jetzt 8 /bbs/-routes: scene/place/blocking_check/shot/camera_check/path/create_replay/keyframe
  + render).** Der ERSTELLEN-confirm-loop: leerer/1-actor-film → create_replay (neuer actor da) → keyframe
  ×N (actor bewegt sich über zeit) → render zeigt den neuen actor. NIE film 'f' für destruktive tests;
  ⚠ actor-REMOVE aus dem offenen editor-film crasht startRenderFrame (IndexOutOfBounds) — ERSTELLEN ist
  sicher (kein remove), aber wegwerf-film für harte tests bevorzugen. · SceneDigest.coarseZone →
  ZoneMapper-rewire (W1, body-only). · camera_check gegen in-session Compiled (session-scoped Compiled-map,
  out-of-scope dieser welle). · zeit-getriebene render-fahrt (video-judge, plan stufe 1). ·
  python-plugin bbs_autonomy.py: LANDED (9 tools inkl. bbs_create_replay/bbs_keyframe).
