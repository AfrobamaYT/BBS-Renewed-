<!-- STALE-marker entfernt 2026-07-26: verwies auf commit 2b57eb1, der in KEINEM der drei repos mehr existiert (alter baum vor der umstrukturierung) — er war prinzipiell nicht abarbeitbar. Der post-commit-marker war ausserdem gar nicht installiert; seit heute laeuft er wieder und setzt echte, aufloesbare marker in docs/brain/.stale.json. -->
<!-- verifiziert (Task 1.0 identitäts-fundament, KK-1, 2026-07-18) — LIVE gegen die laufende JVM
     gemessen (hotswap BlenderBridgeClient REDEFINED; ActorImporter lud beim ersten applyActors-call
     frisch mit guard, KEIN restart; FilmIdentity NEU+lazy, lud beim ersten aufruf). NEUE lazy-klasse
     `FilmIdentity.java` (resolver, NICHT uuid-minter) + body-swap `ActorImporter.applyActors`
     (film-guard) + body-swap `BlenderBridgeClient` HELLO-case (film-id in der antwort). MESSFUND
     (EXEC :9880, DEFINITIV): `Film.getId()` IST die stabile persistente id in BBS' EIGENEM datenmodell
     — `BaseManager.create()` ruft `object.setId(id)` (BaseManager.java:36), `load("f").getId()`=="f",
     BBS keyt seine film-tabs/`.equals(filmId)`-vergleiche darauf (UIFilmPanel decompiled 1489/2056/
     2266/2320). KEINE erfundene uuid/sidecar (plan JA-zweig, invariante 2: nur gelesen).
     GATE 1 (0-writes-beweis): `applyActors({film:"OTHERFILM",replays:[]})` gegen offenen film 'f' →
     `{ok:false,applied:0,skipped:0,filmMismatch:true,expectedFilm:"OTHERFILM",film:"f",
     error:"film mismatch: … sync paused to protect your work"}`; ground-truth channels-json VOR==NACH
     byte-identisch (898 zeichen, 1 replay f526d136) = 0 writes in den falschen film. Guard greift VOR
     jeder mutation. GATE 2 (rename→bindung): actor-zuordnung war IMMER via `bbs_uuid` (nie name,
     `_find_actor_by_uuid`) — umbenannter replay behält uuid → kein empty. `currentFilmId()`=="f",
     `isForeignFilm("f")`=false / `isForeignFilm("OTHERFILM")`=true. Builder: opus (KK-1). -->
<!-- STALE seit commit bd2ebe4 — dateien unterhalb dieses dossiers geaendert; erst gegen code verifizieren, dann marker entfernen -->
<!-- verifiziert (Task 5.2 string/item-kanäle, HB-3, 2026-07-18) — LIVE gegen die laufende JVM
     gemessen (hotswap ActorExporter+ActorImporter body-only, 2/2 redefined 0 missed KEIN restart;
     StringChannels NEU+lazy). NEUE klasse `actors/StringChannels.java` (konstanten+codec) + je 1
     body-call in Exporter (exportReplay `out.add("strings",…)` wenn nicht leer) und Importer
     (applyReplay `StringChannels.apply`). GATE (:9880 EXEC/ACTORS_EXPORT, film 'f'): (a) leerer
     item-kanal → KEIN strings-key (byte-identisch alte payload, skippedChannels=[]); (b) EXEC-inject
     diamond_sword @tick5 in mainHand → re-export trägt strings.item_main_hand=[[5.0,
     "minecraft:diamond_sword"]] UND skippedChannels=["item_main_hand"] (additiv bewiesen);
     (c) ACTORS_IMPORT strings {item_main_hand:[[10,cobblestone],[20,air]]} → applied=1, re-export
     exakt zurück (full-replace, air→ItemStack.EMPTY→"minecraft:air"). Film 'f' RESTAURIERT: mainHand
     via EXEC geleert + panel.save() → skippedChannels=[]/kein strings wie vor dem test (disk
     f.dat neu geschrieben mit clean state). GRENZE: nur item-TYP (keine NBT), sichtbarkeit=HB-4/5.3b.
     addon-gegenstück actors.py `_apply_item_channels` (bbs_items-JSON-prop + bbs_item_*-index-fcurves).
     Builder: opus (HB-3). -->
<!-- verifiziert gegen commit dae96ae (W8-B4-fixes, koordinator-finalisiert) am 2026-07-17 —
     gates live gemessen: MAX_VOLUME-reject text 128^3 + coverage 0/0/[] (auch malformed),
     film-cam requestId auf allen 3 pfaden geechot, addon-gate fremd-id verworfen/pending intakt.
     RejectCoverage = neue lazy-klasse (stamp aufs reject-JSON, KEIN shared-state — arm im
     reject-zweig hätte laufende streaming-jobs gewiped, b7c81ed-lehre). -->
<!-- verifiziert gegen commit (W8-B3: task 3.6 v2, delta-mixin BOOT-PFLICHTIG committed, NICHT aktiv)
     am 2026-07-17 — EHRLICH: NUR compile-gate (compileClientJava grün, 3 frische .class) + json-gate
     (blenderbridge.mixins.json parst, ClientWorldBlockDeltaMixin registriert). KEIN runtime-gate —
     mixins weben beim class-load, die laufende JVM wurde NICHT angefasst (kein hotswap irgendeiner
     klasse in diesem task; tick-body-zeile + widening werden erst beim nächsten boot wirksam, bis
     dahin trägt W7-B4s poll das feature unverändert). MESSFUND (CFR 0.152 gegen das projekt-jar,
     yarn 1.21.1+build.3): es gibt KEINEN einzel-funnel für client-block-änderungen — handleBlockUpdate
     (beide server-packet-pfade, onBlockUpdate + onChunkDeltaUpdate per-pos) applied via
     super.setBlockState = INVOKESPECIAL und UMGEHT das ClientWorld.setBlockState-override; prediction
     (place/break) + processPendingUpdate laufen NUR durchs virtual setBlockState(4-arg) → mixin
     braucht BEIDE hooks. Full-chunk-(re)load (onChunkData) sieht KEINER der hooks (sektionen
     deserialisieren wholesale) → aktivierungs-plan sagt poll RUNTER nicht AUS. Aktivierungs-plan
     (weave-probe, same-tick-gate, budget-tuning) im BlockDeltaEvents-klassendoc. Task 2.5 NICHT
     mit-verankert (befund): dessen root-cause ist setBlockState+requestTeleport im SELBEN SERVER-task
     (EXEC-script-seite, kein committed java bündelt die calls — grep-bewiesen) — server-task-split,
     fällt NICHT in den client-mixin-scope. Builder: fable (W8-B3). -->
<!-- verifiziert gegen commit (task 3.6: BlockDeltaTracker, live-block-delta — das USP-novum, W7-B4)
     am 2026-07-17 — LIVE gemessen (hotswap WorldExportBridge+BlenderBridgeClient, danach
     BlockDeltaTracker-re-swap nach budget-retune [klasse war durch describe()-probe schon geladen —
     ChunkCoverage-lehre bestätigt], KEIN restart). ARCHITEKTUR-MESSFUND vorab (regel 6): es gibt
     KEIN mixin-freies client-block-change-event — fabric-api 0.116.7 lifecycle-jar gelistet (nur
     BE/chunk/entity/world/tick-events), BBS' ClientWorldMixin hookt nur addParticle; exakter hook
     (ClientWorld.handleBlockUpdate-mixin) wäre boot-pflichtig, bewusst NICHT gebaut → v1 =
     budgetierter snapshot-diff-poll. BENCH (main-thread, exakter scan-loop, 131072 chunk-cached
     reads, alle chunks geladen): 21.8ms kalt-JIT, 7.04/7.33ms warm (~54ns/read) → BUDGET_MAX von
     131072 auf 32768 gesenkt (~1.8ms worst case; 1.6M-region-sweep damit ~2.5s latenz, ehrlich
     dokumentiert). GATES (:9976-split, invariante 5b; export 33³ region 474,89,44..506,121,76 über
     den VOLLEN solicited+requestId-pfad, arm_from_meta las das ECHTE world_meta.json): setblock
     stone @ 493,107,63 → EIN BLOCK_DELTA auf dem draht (wire-capture: region exakt, changes
     ["493 107 63 minecraft:stone minecraft_block_stone"]) → würfel in :9976 EXAKT auf contract-
     koordinaten (x 493..494 / y −64..−63 / z 107..108), pool-material minecraft_block_stone via
     java-hint; abbauen → air-push OHNE material-token, patch weg; region-fremder setblock (514) →
     0 pushes im capture; addon-defense fremde region injiziert → ignore+1, nichts angewandt;
     idempotenz captured-fields 2× → noop 2×, objektliste unverändert (kein .001); reExport-probe →
     nur status-empfehlung, 0 objekte. Rückbau: beide setblocks → original air, je getblock-
     verifiziert; :9976 geräumt (MC_World/MC_World_Delta=None); tracker disarmed. Film des users
     nie berührt (kein actors/film-API im ganzen task). Builder: fable (W7-B4). -->
<!-- verifiziert gegen commits 2178b12+b7c81ed+d5bcd71 (end-review fixes 2/3/5-java) am 2026-07-17,
     deploy-fenster GELAUFEN (koordinator-freigabe, hotswap 4/4 klassen 0 missed KEIN restart;
     ExportCorrelation lazy-neu): FIX-3-GATE PASS — streaming-job lief (region 423..519/-60..111/
     -6..90, 140108/218843, 49/49 chunks), alt-request WÄHRENDDESSEN → ok=false "a streaming
     export is already running" MIT eigenem requestId; alt-export NACH job-ende → ok=true.
     FIX-2-ECHO live auf allen 3 wegen (sync-reject/sync-ok/async-result trugen je exakt die id
     ihres requests). FIX-5-KURZGATE (StreamMetaWriter-teardown) PASS: world_meta.json 0.6s frisch,
     region exakt, 895 emissive; 2. job (cull) erneut 895 BYTE-IDENTISCH → clear/fresh-queue
     verliert nichts. CO-GATE fund 4 (clamp×cullCaves, vertex-grep grenz-ebenen): seiten-wände
     halten (≈−1%/ebene), REGION-BODEN y=-60 verliert 66% (38392→12736) — SkyReachability cullt
     tiefen-blöcke samt boden-faces, dokumentierte grenze (register/SYMPTOME im workspace-repo);
     emissive-manifest cull-blind bestätigt (895==895 trotz −43% blocks). Fixer: fable. -->
<!-- verifiziert gegen commit (W7-B2: streaming-animated + clamp×cullCaves-co-gate-fixes) am
     2026-07-17 — LIVE gemessen (hotswap 3 klassen body-only: StreamingWorldExporter,
     StreamMetaWriter, SkyReachability — queued=3 missed=0, KEIN restart; 6 exports auf der
     referenz-region 423,-60,-6..519,111,90, export-fenster via main koordiniert). DREI dinge:
     (a) streaming-manifest hat die animated-sektion (TextureStripExporter-reuse, arm in
     maybeFinish, append in StreamMetaWriter.write). THREAD GEMESSEN statt angenommen:
     maybeFinish läuft via onClientTick auf dem RENDER thread (log: "[Render thread]
     (StreamMetaWriter)") — kein main-thread-hop nötig, gleicher thread wie extractTextures.
     TreeMap statt LinkedHashMap: job.materials-insertion ist worker-ANKUNFTSordnung (ohne
     sequencer nicht reproduzierbar) → sortiert = deterministisch. Gates: streaming==alt 5/5
     order-insensitiv, 2×streaming byte-exakt, strips MD5==alt; cull-export listet nur die 4
     im OBJ referenzierten (magma ganz weggecullt), MTL-konsistent. (b) recordIfEmissive NACH
     dem cull-entscheid (fund 4): cull-manifest 79 statt 895, 0 gelistete lichter ohne
     geometrie im c1-OBJ (cell-check), ohne cull 895==alt unverändert; 64/816 gedroppte sind
     geometrie-ANGRENZEND (lava-innenzellen, selbst nie gerendert — konservativ, ok). (c)
     boden-lücke zu: shouldCullFace cullt NIE faces gegen die geclampte grenze
     (StreamRegionClamp.isBoundaryAir, existierende API — keine neue klasse nötig). HULL-GATE
     100.00%: in-plane-faces s1==c1 exakt auf allen 5 grenz-ebenen (floor 9390, x0 12118,
     x1 13268, z0 12314, z1 12195 = 59.285). MESSLEHRE fürs nächste hull-gate: fixer-fs
     "wände −1%/boden −66%" zählte vertex-BERÜHRUNGEN (fängt senkrechte cave-innenwand-faces
     an der plane-kante, korrekt gecullt) — in-plane-faces zählen, nicht vertex-treffer.
     OBJ-MD5 zwischen wiederholten exports differiert weiter (BE-write-race, bekannt&dokumentiert;
     sortiert-inhalts-MD5 identisch beide paare), world_meta beider paare identisch ex time/sky.
     Builder: fable (W7-B2). -->
<!-- verifiziert gegen commit (task 4.1 wire-anschluss ENTITY_EXPORT, W7-B1) am 2026-07-17 — LIVE
     gemessen (hotswap EntityExporter/ActorExporter/BlenderBridgeClient, 3 klassen 0 missed, KEIN
     restart; EntityExportBridge+EntityExportTarget lazy). Fixture BRIDGE_TEST_MOB (villager,
     headYaw 0/30/-20, bodyYaw 0/-40/15, pitch 0/10/-5 @ ticks 0/10/20) selbst angelegt+entfernt,
     film 'f' restauriert (byte-identisch bis auf totalTimeWorked+sessions-time — dieselbe
     dokumentierte ausnahme wie beim 4.3a-gate). GATES: (1) ENTITY_EXPORT(replayUuid) →
     OBJ+MTL+PNG+rig.json, rig-bones == die EXAKTEN 10 capturePose-pfade des villagers (root,
     /head, /head/hat, /head/hat/hat_rim, /head/nose, /left_leg, /right_leg, /arms, /body,
     /body/jacket), arms-bindRotation -0.75 rad aufgezeichnet; (2) requestId-echo korreliert
     (2 verschiedene ids nacheinander, addon akzeptierte nur die eigene); (3) addon-kette e2e:
     armature==partliste, 3-tick-winkel-gate via ReplayPoseReader auf dem NEUEN rig exakt
     (worst 2.5e-08 rad, ALLE 10 bones gematcht — auf alex waren es 4); (4) export+pose-reads
     film-read-only (snapshot-vergleich). ZERO-ROTATION-CAPTURE-entscheid (wire-pfad captured
     mit genullten part-rotationen, pivots intakt): absolute pose-results doppelrotieren sonst
     bind-rotierte parts (villager-arme) — EntityExportTarget-klassendoc trägt die herleitung;
     direkte EntityExporter.export-caller (EXEC) kriegen weiter echte bind-pose. Builder: fable. -->
<!-- verifiziert gegen commit (end-review fix 2 java-hälfte: requestId-echo im WORLD_EXPORT_RESULT)
     am 2026-07-17 — WICHTIGER MESSFUND des reviews: das result echote bisher NICHTS vom request
     (envelope = frisches STATE.nextOutboundSeq + konstante uuid "world", BridgeServer broadcastet
     an ALLE clients) — das addon konnte sein eigenes result nicht von fremden unterscheiden
     (fund 2, gate-inversion im 120s-fenster). Jetzt: request trägt optional fields.requestId,
     echo auf DREI wegen: (a) handleWorldExport stampt ALLE synchronen replies (alt-erfolg/-fehler,
     streaming-start-fehler, busy-reject) mit der id des JEWEILIGEN requests (ein abgelehnter
     request kriegt SEINE id, nie die des laufenden jobs); (b) neue lazy-klasse ExportCorrelation
     (RegionClamp-muster, kein feld auf geladenen klassen) wird im streaming-zweig NACH dem
     isActive()-guard gearmt; (c) StreamingWorldExporter.sendResult (der einzige terminale
     async-sender) liest sie zurück. Leere/fehlende id = byte-identisches altes reply-shape
     (alter addon-stand/EXEC-caller). Addon-gegenstück im workspace-repo (ce2d139). Compile
     grün; live-echo-beweis am fix-3-gate mitgemessen. Fixer: fable. -->
<!-- verifiziert gegen commit (task 3.2, "laden ehrlich machen", W6-B1) am 2026-07-17 — LIVE gemessen,
     BEIDE export-pfade (alt-pfad synchron UND streaming). Neue lazy-klasse world/ChunkCoverage.java
     (arm/record/read, RegionClamp-muster) + body-edits (WorldExporter.exportRegion arm + scan-loop
     record, StreamingWorldExporter.start arm + processColumnOnMainThread record, WorldExportBridge
     .payload appendet 3 neue felder — ExportResult-record UNANGETASTET, siehe ChunkCoverage-klassendoc
     "WHY A NEW SEPARATE CLASS"). WORLD_EXPORT_RESULT trägt jetzt IMMER zusätzlich
     "chunksTotal"/"chunksLoaded"(int)/"missingChunks"(string-array "chunkX chunkZ", gecappt 20).
     **MESSFUND (überraschung, kostete einen Fehlversuch): `World.isChunkLoaded(BlockPos)` LÜGT auf
     dem client** — lieferte `true` für eine chunk 2000 blöcke vom spieler entfernt (view distance 12).
     Live gegen die laufende JVM geprüft: `world.getChunk(cx,cz)` an der fernen position ist eine
     `EmptyChunk`-instanz (vanilla-platzhalter), an der spieler-position ein echter `WorldChunk` —
     `isChunkLoaded` antwortete für BEIDE `true`. Ground truth stattdessen:
     `!(world.getChunk(cx,cz) instanceof EmptyChunk)`. GATE gemessen (spieler bei 490,105,60,
     dim=overworld, view distance 12): region weit weg (2490,90,60..2493,93,63) → chunksTotal=1,
     chunksLoaded=0, missingChunks=["155 3"], blocks=0/faces=0 (vorher: stilles ok:true+blocks:0 ohne
     erklärung); region am spieler (486,80,56..494,106,64) → chunksTotal=2, chunksLoaded=2,
     missingChunks=[], blocks=58/faces=210 (echte geometrie, beweist "leer"≠"leer weil ungeladen");
     mischregion am view-distance-rand (656,60,48..719,90,63, gemessene EmptyChunk-grenze bei chunk
     44,3) → chunksTotal=4, chunksLoaded=3, missingChunks=["44 3"] — exakt die unabhängig per
     EXEC/instanceof-probe gemessene grenze, blocks=4216/faces=10837. Streaming-pfad (gleiche 3
     regionen wiederholt mit fields.streaming=true) lieferte IDENTISCHE zahlen in allen 3 fällen
     (selbe payload()-methode, gleiche ChunkCoverage-instanz) — Task 3.2 ist NICHT alt-pfad-only,
     beide pfade sind ehrlich. Hotswap: 4 klassen redefiniert (WorldExporter, StreamingWorldExporter,
     WorldExportBridge, ChunkCoverage — letztere neu&lazy, brauchte trotzdem einen eigenen
     hotswap-aufruf nach dem isChunkLoaded-Fix, weil sie zwischen den beiden compile-läufen schon
     einmal geladen war), 0 missed, KEIN restart. -->
<!-- verifiziert gegen commit (task "animierte texturen" schema-erweiterung "order", B2, contract-owner
     sign-off) am 2026-07-17 — LIVE gemessen. Neues feld world_meta.json "animated"[]."order":
     [[tileIndex,timeTicks],...] via AnimationResourceMetadata.forEachFrame — grund: lava_still/fire
     haben ping-pong-frames-listen mit per-tile-zeiten, die ein bloßes "frametime" nicht abbilden
     kann (Blender würde linear statt ping-pong abspielen = sichtbare abweichung vom game).
     MESSFUND (überraschung, nicht angenommen): forEachFrame liefert 0 callbacks wenn die .mcmeta
     KEINE explizite "frames"-liste hat (water_still, per reflection auf das private "frames"-feld
     bestätigt: leere ImmutableList, nicht null) — vanilla synthetisiert den linear-default NICHT in
     dieser klasse (das passiert eine schicht höher, in SpriteContents/atlas, die wir bewusst nicht
     anfassen wollen, gleiche wand wie extractTextures). FIX: bei leerem forEachFrame-ergebnis
     synthetisieren wir selbst [[0,frametime],...,[frames-1,frametime]] aus der schon gemessenen
     strip-frame-zahl. GATE (region 423,-60,-6..519,111,90, 2x export): water_still.order ==
     [[0,2],[1,2],...,[31,2]] exakt (linear-default korrekt), lava_still.order-länge 38 > frames=20
     (ping-pong bestätigt), alle 5 materialien: jeder tileIndex < frames. **HOTSWAP-LEHRE (neu,
     kostete einen fehlgeschlagenen ersten export):** TextureStripExporter.AnimatedTexture ist eine
     NESTED klasse → eigene .class-datei (`TextureStripExporter$AnimatedTexture.class`) → braucht
     ihren EIGENEN hotswap-aufruf, wenn sich ihr konstruktor ändert (neues feld `order`) — die
     äußere klasse redefinieren reicht NICHT, symptom war `NoSuchMethodError` auf den alten
     5-arg-konstruktor trotz erfolgreichem redefine der äußeren klasse. -->
<!-- verifiziert gegen commit (task "animierte texturen", B2, TextureStripExporter) am 2026-07-17 — LIVE
     gemessen (hotswap auf WorldExporter, KEIN restart; TextureStripExporter lazy-geladen). region
     423,-60,-6..519,111,90 (139.871 blöcke/218.029 faces, alt-pfad), 2x export: world_meta.json
     "animated"-array (material/frames/frametime/interpolate/file) enthält water_still/water_flow/
     lava_still/lava_flow/magma (5/5, magma war in der region zusätzlich vorhanden), alle 5
     strip-PNGs höhe==frames×breite exakt (water_still 16x512=32f, lava_still 16x320=20f, water_flow
     32x1024=32f, lava_flow 32x512=16f, magma 16x48=3f), 0 nicht-animierte materialien im array (435
     obj-materialien gesamt geprüft), animated-array 2x export byte-identisch (timeOfDay/skyAngle
     änderten sich zwischen läufen — daylight cycle lief weiter — animated-sektion ist davon
     unabhängig, wie gefordert). MESSFUND (nicht aus gedächtnis übernommen, gegen laufende JVM UND
     rohe .mcmeta im client-jar geprüft): water_still.png.mcmeta hat KEINEN "interpolate"-key →
     default false, NICHT true wie ursprünglich angenommen; magma/prismarine setzen interpolate:true
     explizit, water_still nicht. -->
<!-- verifiziert gegen commit (task 3.5-streaming: StreamMetaWriter) am 2026-07-17 — SCHLIESST die
     "LIVE-gate offen" notiz von b4fdeae unten: streaming-pfad schreibt world_meta.json jetzt LIVE
     bewiesen. region 423..519/-60..111/-6..90 (140.108 blöcke/218.843 faces streaming vs 139.871/
     218.029 alt-pfad, divergenz erwartet siehe StreamRegionClamp-notiz): world_meta.json mtime
     FRISCH direkt nach export, 895 emissive einträge, sortiert BYTE-IDENTISCH zwischen streaming
     und alt-pfad auf derselben region; skyAngle 0.61579067 (streaming) vs 0.6484556 (alt, ~677
     ticks später gemessen) — minimale differenz erwartet, kein bug. body-only-hotswap auf
     StreamingWorldExporter (1 klasse redefiniert, 0 missed, KEIN restart), neue klasse
     StreamMetaWriter lazy-geladen. -->
<!-- verifiziert gegen commit 7f90f23 (task 3.3c streaming: StreamRegionClamp) am 2026-07-17 —
     LIVE gemessen: openBounds=true reproduziert unclamped EXAKT (84.881/159.814); clamped
     streaming vs alt +237/+814 = boundary-FLUID-faces (alt-RegionClamp deckt emitFluid nicht).
     world_meta LIVE (895 emissive, skyAngle echt), sonnen-vorzeichen gegen in-game-sonne PASS. -->
<!-- verifiziert gegen commit b4fdeae (task 3.5: writeWorldMeta) am 2026-07-17 — compile-gate gruen;
     LIVE-gate offen (game war zu): naechstes deploy-fenster exportieren + world_meta.json pruefen. -->
<!-- verifiziert gegen commit 081fd12 (task 4.3a: ReplayPoseReader, replay-gebundene entity-pose)
     am 2026-07-17 — live gegen laufende JVM gemessen (hotswap, KEIN restart). Scope NUR
     entity/ReplayPoseReader.java (NEU) + BlenderBridgeClient.dispatch (body-only, 1 raw-string-
     case "REPLAY_POSE_EXPORT") — camera/world/actors/mixin unangetastet, deren ältere
     verifikationen unten bleiben gültig. Gemessen gegen fixture BRIDGE_TEST_MOB (villager,
     headYaw 0/30/-20, bodyYaw 0/-40/15, pitch 0/10/-5 @ ticks 0/10/20, koordinator-bereitgestellt):
     frames==angeforderte tick-zahl (20 UND 21 beide bestätigt); head-bone yaw/pitch gegen
     hand-berechnete ground-truth interpoliert — schlechteste abweichung 2.36e-08 (toleranz war
     1e-4); REPLAY_POSE_EXPORT_RESULT-schema feld-für-feld identisch zu einem echten
     ENTITY_BAKE_RESULT (selbe top-level-keys/-typen, selbe pro-frame-form, IDENTISCHE bone-namen-
     menge — gegen einen echten live-villager gebaked); film 'f' nach den reads unverändert
     (re-export zeigt byte-identische keyframes, beide replays intakt — read-only bestätigt, nicht
     nur behauptet). GEFUNDENER+GEFIXTER BUG während dieser verifikation: `EntityExporter.poseEntity`
     zieht `bodyYaw` bereits selbst von `entity.headYaw` ab (erwartet dort den ABSOLUTEN wert) —
     eine frühere version dieser klasse zog `bodyYaw` VORHER schon selbst ab (kopierte
     `MobFormRenderer.applyMorphRotation`s konvention, die aber `poseEntity` gar nicht durchläuft)
     und subtrahierte damit zweimal; am fixture live gemessen (tick 10 lieferte 110° statt 70°)
     BEVOR der fix lief — siehe `ReplayPoseReader`-klassendoc "POSE MATH" für die vollen zahlen.

     ÄLTER — verifiziert gegen commit 86d5628 (task 3.3-nachtrag: arm-state-ordering-fix + BE-write-race-
     untersuchung) am 2026-07-17 — live gegen "New World" gemessen: StreamingWorldExporter.isActive()
     jetzt VOR jedem armNextJob/resetWriteSequencer/FaceMerger.armNextJob geprüft (echter bug: ein
     REQUEST WÄHREND ein job läuft konnte bisher trotzdem den arm-zustand+sequencer des laufenden
     jobs zerstören, obwohl er selbst korrekt abgelehnt wurde). BE-write-race (von face-merge
     gefunden): exportBlockEntitiesForColumn schreibt NICHT über den write-sequencer (bewusst NICHT
     gefixt — BlockEntityExporter.emit ist MAIN-THREAD-ONLY/GL-gebunden, der sequencer kann auf
     BELIEBIGEM thread drainen; ein erster versuch das zu fixen wurde vor landung verworfen, siehe
     StreamingWorldExporter-klassendoc). Scheinbare determinismus-regression aufgeklärt: KEIN
     sequencer-bug, KEIN klassen-skew, KEINE agent-interferenz (alle 3 geprüft, sogar mit
     team-weit bestätigtem ruhe-fenster reproduziert) — die test-region enthielt UNBEMERKT 3
     block-entities (427,-17,20 / 430,-17,20 / 431,-17,22), traf also die schon bekannte BE-race
     nur zufalls-abhängig. Auf gescannt-BE-freier sub-region: 2× export byte-identisch (diff/cmp/MD5
     stimmen überein). LEHRE: vor einem determinismus-gate die region AKTIV scannen (nicht von
     "kenne keine BE dort" auf "BE-frei" schließen) — siehe FAEHIGKEITEN.md für die volle zahlen-basis.

     ÄLTER — verifiziert gegen commit 6d3f448 (task 3.4, FaceMerger face-merge) am 2026-07-17 —
     live gegen "New World" gemessen (laufende instanz, hotswap, KEIN restart): losslessness
     total-OBJ-flächen 0.000000-diff über alle materialien (2 regionen), face-reduktion 17.0%
     (16-chunk-region mit struktur) / 18.8% (16-chunk natur-region), determinismus 2×export→
     MD5-identisch (mergeFaces allein + kombiniert mit cullCaves, region ohne block-entities —
     write-sequencer musste auf mergeFaces erweitert werden, siehe FaceMerger-klassendoc),
     kombinierbar cullCaves+mergeFaces < cullCaves-allein (beide regionen), Blender-render-
     silhouette-diff 4/6 kamerawinkel nahe-null-RMS, die 2 laub-lastigen matchen einer
     rendert-dasselbe-file-zweimal-kontrollmessung (EEVEE-eigenes rausch, kein merge-defekt).
     ENTDECKT (aus scope, nicht gefixt): regionen MIT block-entities bleiben trotz obigem fix
     byte-verschieden (aber inhalts-identisch) zwischen wiederholten exports — BlockEntityExporter
     schreibt main-thread direkt, umgeht den spalten-write-sequencer komplett; betrifft auch
     Task 3.3s cullCaves-allein-pfad, nicht im 3.4-dateimanifest.
     verifiziert gegen commit 855b16f (task 3.3, SkyReachability cave-culling) am 2026-07-17 —
     live gegen "New World" gemessen (frischer boot nach crash, fenster FOKUSSIERT): regression
     cullCaves=false==alt-pfad exakt (16.806 blöcke/28.680 faces), cull-effekt 20-spalten-region
     35.382→11.437 blöcke/54.121→17.702 faces (-67%), korrektheit 8/8 real gescannte oberflächen/
     höhlen-positionen (direkt + OBJ-vertex-grep), fps nie <30 (20-spalten min118, 1024-spalten-
     stresstest min69 — FENSTER MUSS FOKUSSIERT SEIN, unfokussiert maß ich vorher fälschlich 0/17fps),
     determinismus 0-diff/gleicher MD5 nach write-sequencer-fix (siehe FAEHIGKEITEN.md für alle zahlen) -->


Bridge BBS↔Blender. TCP :9880, null-byte-JSON `{type,origin,uuid,seq,fields}`. KEIN BBS-source
anfassen — alles lebt hier + 3 mixins (der dritte, ClientWorldBlockDeltaMixin, ist committed
aber erst nach dem nächsten boot gewoben).

## WO IST WAS (kuratiert, nicht generiert)

| datei | verantwortung | fallen |
|---|---|---|
| `BlenderBridgeClient` | mod-init, tick (inbox→dispatch→`detectBbsCameraGrab`→applyCam→pushCam) | dispatch unknown-type = no-op (schema-reservierungen ok). **Task 1.0 (KK-1, 2026-07-18):** HELLO-case antwortet jetzt mit einem HELLO-envelope `fields.film=FilmIdentity.currentFilmId()` (raw `film`-key, KEIN BridgeProtocol-konstante/methode — geladene klasse, body-only-swap; addon bindet daran + erkennt filmwechsel). Leere id = kein film offen → addon bindet aus der ersten ACTORS_EXPORT_RESULT nach |
| `BridgeServer` | NIO-selector :9880 | `closeConnection`: authority-release NUR bei 0 verbleibenden (EXEC-clients kommen/gehen pro call!) |
| `BridgeProtocol` | typen+konstanten+envelopes | `envelope()` hat 4 args; fields danach via `getFields(msg).addProperty` |
| `FilmIdentity` | NEU 2026-07-18 (Task 1.0, KK-1): DER film-id-resolver — `currentFilmId()` (editor-first/playing-fallback, gibt `Film.getId()`) + `isForeignFilm(expected)` | KEINE erfundene uuid/sidecar: `Film.getId()` IST die stabile persistente id in BBS' EIGENEM datenmodell (BaseManager.create→setId(id), BBS keyt film-tabs darauf — decompiled UIFilmPanel). NEUE lazy-klasse (deploy-window-free, `RegionClamp`-muster) statt methoden an geladene ActorImporter/BlenderBridgeClient. Reiner READ (invariante 2). `isForeignFilm` ist NUR true wenn BEIDE ids nicht-leer sind und differieren: leere erwartung (alt-addon/EXEC-caller) UND leerer current (kein/ungespeicherter film) sind NIE mismatch (backward-compatible). MAIN-THREAD-only wie die actor-bridges |
| `BridgeState` | seq/LWW, echo, pendingCamera | HELLO → `resetAllSeq()` PFLICHT (sonst stirbt sync nach Blender-reload) |
| `CameraBridge` | cam lesen/schreiben/pushen | rotation RADIAN intern, `writeCamera()` nimmt GRAD, `fov` RADIAN. `writeCamera` parkt controller-wunsch VOR overwrite |
| `LiveCameraOverride` | geparkte Blender-pose + armed-flag | `isActive()` ist owner-gated (BLENDER only) |
| `camera/CameraAuthority` | DER kamera-besitzer (NOBODY/BBS/BLENDER) + controllerWanted + grabDetected | NEUE klasse = lazy = felder wachsen ohne boot. wire-strings englisch (NOBODY) |
| `camera/FilmCameraExporter` | Task 1.3: film-kamera-CLIP-TIMELINE (nicht die live-pose) → dichte pro-tick-frames | sampelt via `CameraClipContext` (derselbe evaluator wie Runner/Play-controller) statt clip-typ-für-typ zu übersetzen — deckt Idle/Dolly/Path/Keyframe/layer/envelope gleich ab; raw-string message-type `FILM_CAMERA_EXPORT`/`_RESULT` (kein neuer `BridgeProtocol.TYPE_*`, dokumentiert in der klasse); **Task 1.3b (W7-B5, 2026-07-17): `CameraClipContext.entities` wird jetzt offline befüllt** — `OfflineEntityResolver.arm()` vor der loop, `applyTick()` pro sample-tick, per-clip try/catch (werfender clip = warnung + pose hält, NIE ganz-export-abbruch — VOR 1.3b hätte er den export gekillt), `finally resolver.finish()` (tracker-statics-restore); result trägt additiv `warnings:[{type,tickStart,tickEnd,reason}]` (immer präsent, auch im failure-pfad). GEMESSEN: regression film 'f' 31 frames bit-identisch vor/nach hotswap; LookClip auf bewegtes test-replay 30 ticks worst-delta 5.5e-6 (toleranz 1e-4) gegen channel-ground-truth; selector=-1 → 1 warnung + frames bit-identisch zur baseline (clip no-opt wie live); tick==duration hält pose von tick-1 (getClips leer dort — engine-konvention, baseline identisch). **HAT KEINEN live-runner-zugriff** (offline-sampler) — `film/PlayheadSync` braucht den echten playhead und holt ihn NICHT von hier |
| `camera/OfflineEntityResolver` | NEU 2026-07-17 (Task 1.3b, W7-B5): replay→`StubEntity`-map für den offline-sampler, EXAKT das playback-rezept (`BaseFilmController.createEntities` 1486-1500: enabled+group-chain-check, form-COPY, `keyframes.apply`, index==selector-raum; `Films.playFilm:78` teilt dieselbe map) + stufe-1-warnungen (selector<0 / ziel deaktiviert-außer-range / riding-ziel / werfender clip, 1×/clip via identity-set) | BEWUSST NICHT repliziert (klassendoc "WHAT IS NOT REPLICATED"): `applyClientActions` (sounds/partikel), `updateEntityAndForm`, `MorphMountSync` (fasst LIVE render-entities an — mounted ziel = warnung statt still falsch), `MobCemPoseCapture`. **prev==current-collapse NACH JEDEM apply** — transition=0-reads (`lerp(prev,cur,0)`) lieferten sonst tick-1 (createEntities-hygiene, hier pro tick). **`TrackerClientClip`-statics-klammer** (renderHand/renderHud/currentEntity save/restore) — deren `applyClip` setzt sie als eval-nebenwirkung, pre-1.3b-sampler konnte schon `renderHand` flippen (latenter 1-frame-hand-flash, mitgefixt). package-private, instanz pro export, keine statics |
| `film/PlayheadSync` | Task 1.4: BBS↔Blender playhead-sync (`UIFilmPanel.getCursor()`/`setCursor(int)`/`isRunning()` == `RunnerCameraController.ticks`/playing) | eigener, unabhängiger owner-wert (nur `CameraAuthority.Owner`-ENUM-TYP wiederverwendet, keine geteilte instanz mit der kamera); `owner` reist optional im selben raw-string `PLAYHEAD.fields` statt einem 2. message-typ (toggle-an=claim+sofort-handshake, toggle-aus=reiner release-ping OHNE `tick` — sonst bleibt owner für immer BLENDER und BBS' default-richtung ist tot); inbound-apply-gate (`owner==BLENDER`) IST zugleich der echo-schutz, kein separates baseline-flag nötig |
| `BridgeExec` | JShell in-JVM (KnotClassLoader-parent) | fallen: siehe unten "EXEC" |
| `actors/ActorExporter` | film→JSON (replays, kanäle) | sendet `formModel` (echter key) UND `form` (displayName — NIE zum auflösen nutzen). **W7-B1:** zusätzlich raw-key `formMob` (`MobForm.mobID`, "minecraft:villager") — addon-mob-zweig braucht die registry-id für den ENTITY_EXPORT-cache; raw-literal statt `ActorProtocol`-konstante (neues static-feld auf geladener klasse = hotswap-verbot). **Task 5.2 (HB-3, 2026-07-18):** EINE body-zeile am ende von `exportReplay` — `out.add(StringChannels.STRINGS, StringChannels.serialize(replay))` NUR wenn nicht leer. Serialisiert die 15 ItemStack-kanäle als item-ids (siehe `StringChannels`). `skippedChannels` unverändert (strings = ADDITIV). Leere item-kanäle → kein `strings`-key = byte-identisch alte payload. |
| `actors/ActorImporter` | JSON→film (keyframes, undo, UI-refresh) | `updateChannelsList()` detacht editor VOR early-outs — preconditions spiegeln. **Task 1.0 (KK-1, 2026-07-18) FILM-GUARD (datenverlust-seal):** `applyActors` liest `fields.film` (erwartete film-id, vom addon getaggt) und verwirft mit `filmMismatch(...)` **VOR jeder mutation**, wenn `FilmIdentity.isForeignFilm(expected)` — d.h. BBS hat inzwischen einen ANDEREN film offen. Reject-payload trägt `ok:false/applied:0/filmMismatch:true/expectedFilm/film(=offener)` (maschinenlesbar, addon setzt daraufhin seine sync-pause). Leere/fehlende `film` = backward-compatible (alt-addon/EXEC-caller schreibt in den offenen film wie bisher). GEMESSEN: fremd-film-write → filmMismatch, ground-truth VOR==NACH byte-identisch = 0 writes in den falschen film. **Task 5.2 (HB-3, 2026-07-18):** `applyReplay` ruft nach dem numerik-loop `StringChannels.apply(replayObject, replay)` (item-kanäle zurück, full-replace je kanal). Der alte `return false` bei fehlendem `channels`-objekt ist WEG — ein payload darf NUR `strings` tragen (item-edit ohne kurven-edit) und zählt dann als applied (`touched`-flag). |
| `actors/StringChannels` | NEU 2026-07-18 (Task 5.2, HB-3): die 15 ItemStack-kanäle (6 equipment + 9 hotbar_slot_1..9) als item-registry-ids in beide richtungen — `serialize(Replay)`→`"strings"`-objekt, `apply(JsonObject,Replay)`←, `itemId(ItemStack)`/`itemStack(String)` | NEUE lazy-klasse (deploy-window-free, `RegionClamp`-muster) statt konstante/methode an den GELADENEN Exporter/Importer (hotswap-verbot). Wählt kanäle über `channel.getFactory()==KeyframeFactories.ITEM_STACK` (kein hardcode je id). id via `Registries.ITEM.getId(item).toString()` (BlockDeltaTracker-präzedenz), leerer stack→`"minecraft:air"` (round-trippt zu `ItemStack.EMPTY`, `Registries.ITEM.get(unbekannt)`=AIR). GRENZE: nur item-TYP, KEINE NBT/components; kein interp (item=stufenfunktion). GATE LIVE (:9880): export leer→kein strings-key; diamond_sword injiziert→`strings.item_main_hand=[[5,"minecraft:diamond_sword"]]`; import cobblestone+air→applied=1, air→EMPTY→air; film 'f' restauriert |
| `actors/ActorPushTracker` | Task 1.6: push statt 0.5s-poll — fingerprintet den offenen film JEDEN tick (billig, keine JSON), pusht unsolicited ACTORS_EXPORT_RESULT bei diff über den bestehenden `ActorBridge.export`-weg | tick-anbindung wie `EntityBakeHandler` (onClientTick-body-zeile, kein neuer anker nötig); echo-schutz via `markSynced()` (von `ActorBridge.importActors` gerufen, NICHT `BridgeState` — die klasse hält ihre eigene baseline). GEMESSEN 2026-07-16: latenz median 2.3ms/5, echo delta=+0, idle 30s=0 pushes |
| `actors/FilmBackup` | Task 1.5: write-safety-net — `protect(Supplier<JsonObject>)` wrapt `ActorImporter.applyActors` in `ActorBridge.importActors` (1 zeile), snapshotet den film (`toData()`) VOR dem import, committet erst NACH dem import und nur bei `applied>0` nach `run/blenderbridge_backups/<filmId>/<timestamp>.json` | throttle 1/film/30s ist ABSICHTLICH nur die SICHERUNG (der import selbst läuft immer, throttle skippt nie eine mutation); rotation max 20/film via `enforceLimit()` (public, EXEC-testbar ohne echte 30s-wartezeit/21-imports — fake-timestamped dateien reichen, filename-sort==zeit-sort); `resolveOpenFilm()` ist BEWUSST dupliziert (nicht geteilt) aus `ActorImporter` — muss bei änderung dort MITGEZOGEN werden, sonst backupt es den falschen film; `restore(File, Film)` ruft nur `Film.fromData()`, KEIN save/undo/UI-refresh (restore-UI war nicht gefordert) |
| `world/WorldExporter` | region→OBJ (render-thread, synchron — ALT-pfad, bleibt als referenz/gate) | nur GELADENE chunks (sonst ok:true+blocks:0!), MAX_VOLUME 128³. **Rand-schließung via `RegionClamp` (2026-07-17, user-beschwerde "untergrund fehlt"):** nachbarn AUSSERHALB der region sind geladen+solid → shouldDrawSide cullte alle rand-faces → export war offene hülle (gemessen: 1.26M solid im game, nur 85k emittiert sichtbar). Fix: `RegionClamp.isBoundaryAir()` gewinnt VOR shouldDrawSide → wände+boden zu (139.871 blöcke/218k faces nachher). Streaming-pfad hat das NOCH NICHT (task 3.3c-resthälfte) — dort erledigt der rim das culling, gleiche lücke. **Task 3.5 (b4fdeae):** `writeWorldMeta` schreibt `world_meta.json` (exakter vanilla-`getSkyAngle(1f)`, timeOfDay, dimension, alle emissive-blöcke pos+luminance+id aus dem scan-loop) neben die OBJ — addon-consumer: `world_light.py`. Streaming-pfad schreibt das manifest NICHT (offen). **3.3c streaming-hälfte GELANDET (7f90f23):** `StreamRegionClamp` (eigene lazy-klasse, volatile — worker-threads!) + ColumnSnapshot.getBlockState-clamp VOR rim-buchführung + arm in WorldExportBridge; `fields.openBounds=true` = alte offene hülle. Bekannte divergenz: alt-clamp deckt emitFluid NICHT (+237/+814 an dieser region) — streaming ist der korrektere pfad. **Task "animierte texturen" (2026-07-17, B2):** `writeWorldMeta` schreibt jetzt zusätzlich `"animated": [...]` (material/frames/frametime/interpolate/file) — logik komplett in NEUER klasse `TextureStripExporter` (body-edit hier nur 2 zeilen: `armForExport(...)` in `runExport` VOR dem `writeWorldMeta`-call, `appendAnimatedJson(meta)` in `writeWorldMeta`s body). Streaming-pfad (`StreamMetaWriter`, B1, 50380f9) hat die animated-sektion NOCH NICHT — dokumentierte lücke, folge-task, NICHT selbst reinfassen. GATE gemessen (region 423,-60,-6..519,111,90, 139.871 blöcke/218.029 faces, 2× export): water_still/water_flow/lava_still/lava_flow + magma alle erkannt (5/5), alle strip-höhen==frames×breite exakt (water_still 16x512=32f, lava_still 16x320=20f, lava_flow 32x512=16f, magma 16x48=3f), keine nicht-animierten materialien im array (435 obj-materialien gesamt), animated-array 2×export byte-identisch (bei wechselndem timeOfSonne/skyAngle — daylight cycle lief). **MESSFUND:** `water_still.png.mcmeta` hat KEIN `interpolate`-key (default false) — live gegen laufende JVM UND rohe .mcmeta im client-jar geprüft, `interpolate=false`, NICHT true (magma/prismarine setzen `interpolate:true` explizit, water_still nicht). **Task 3.2 (2026-07-17, W6-B1, "laden ehrlich machen"):** `exportRegion` armt `ChunkCoverage.armForExport()`, die scan-loop ruft `ChunkCoverage.record(world,pos)` PRO POSITION VOR dem air-check (eine unloaded chunk liest sonst signallos als air) — dedupt intern auf 1 probe/chunk. `ExportResult`-record UNANGETASTET (siehe `ChunkCoverage`-klassendoc). Streaming-pfad bekam es GLEICHZEITIG (nicht nur alt-pfad-only): `StreamingWorldExporter.start` armt, `processColumnOnMainThread` recordet 1×/spalte (main-thread, vor dem worker-handoff) — beide pfade teilen sich `WorldExportBridge.payload`, also automatisch dieselben 3 neuen felder. |
| `world/TextureStripExporter` | NEU 2026-07-17 (B2): animierte block-texturen (wasser/lava/magma/feuer) — voller strip (alle frames) + timing für Blender, ergänzt `WorldExporter.extractTextures`s single-frame-crop | erkennt "animiert" über die RESSOURCE (`.mcmeta` via `AnimationResourceMetadata.READER`), NICHT über `SpriteContents`' package-private animation-feld (gleiche wand wie `extractTextures`-klassendoc). frame-count kommt aus der STRIP-DATEI selbst (höhe/breite, quadratische frames vorausgesetzt — vanilla-format), NICHT aus `AnimationResourceMetadata.forEachFrame` (das ist playback-reihenfolge, kann frames wiederholen). schreibt `<outDir>/textures/anim/<sprite-name>_strip.png` (byte-copy, kein re-encode). **STATIC-HOLDER-UMWEG (leader-entscheid, kein echter parameter):** `writeWorldMeta` lebt auf `WorldExporter` (geladen) → parameter-änderung dort wäre riskanter als reiner body-edit, also `armForExport(...)` (aufgerufen aus `runExport`s body) füllt einen static `pending`, `appendAnimatedJson(meta)` liest+LEERT ihn (leak-schutz wie `RegionClamp.clear`). Alt-pfad = render-thread-only, plain static reicht. TODO nächstes boot-fenster: zu echtem parameter zurückbauen. Schema (material/frames/frametime/interpolate/file/**order**) ist LEADER-FIXIERT — B3 baut den Blender-consumer dagegen, änderung NUR nach SendMessage-rücksprache. **`order` (schema-erweiterung 2026-07-17, contract-owner sign-off):** `[[tileIndex,timeTicks],...]`, die AUFGELÖSTE playback-sequenz via `AnimationResourceMetadata.forEachFrame` — grund: lava_still/fire spielen PING-PONG (mehr order-einträge als `frames`!) mit per-tile-zeiten, die `frametime` allein nicht abbildet. **GEMESSEN:** `forEachFrame` liefert 0 callbacks wenn die `.mcmeta` KEINE explizite `frames`-liste hat (water_still) — kein vanilla-freebie, WIR synthetisieren dann selbst den linear-default `[[0,frametime],...,[frames-1,frametime]]` aus der strip-frame-zahl. `frames` bleibt reine COUNT (unverändert, B3 baut schon dagegen). **HOTSWAP-FALLE:** `AnimatedTexture` ist eine NESTED klasse (eigene `.class`-datei) — bei konstruktor-änderung (neues feld) braucht sie ihren EIGENEN hotswap-aufruf zusätzlich zur äußeren klasse, sonst `NoSuchMethodError` am alten konstruktor trotz erfolgreichem äußeren redefine (live erlebt, siehe dossier-kopf) |
| `world/RegionClamp` | export-scoped "region-grenze=luft"-regel (set/clear/isBoundaryAir) | BEWUSST eigene klasse: WorldExporter ist geladen (felder dort = boot), neue klasse lazy-loadet vom dev-classpath + exporter braucht nur body-swaps. clear() im finally (leck-schutz). NUR render-thread (plain fields) |
| `world/ChunkCoverage` | NEU 2026-07-17 (Task 3.2, W6-B1): "laden ehrlich machen" — zählt distinct chunks/region, wie viele davon ECHT geladen waren, liste fehlender chunk-koordinaten (gecappt 20) — `WORLD_EXPORT_RESULT` trägt jetzt IMMER `chunksTotal`/`chunksLoaded`/`missingChunks` (beide export-pfade, via `WorldExportBridge.payload`) | **`World.isChunkLoaded(BlockPos)` LÜGT auf dem client** (live gemessen: `true` für eine chunk 2000 blöcke vom spieler entfernt) — ground truth ist `!(world.getChunk(cx,cz) instanceof EmptyChunk)`, siehe klassendoc "MEASURED TRAP". BEWUSST eigene lazy-klasse statt neues `ExportResult`-feld (dessen konstruktor ist auf einer bereits geladenen nested class — gleiche falle wie `TextureStripExporter.AnimatedTexture`). arm/record/read im `RegionClamp`-rhythmus; `record` dedupt selbst auf 1 chunk-probe, aufrufer muss nicht selbst dedupen |
| `world/StreamingWorldExporter` | off-thread-export: worker-pool + tick-getriebene snapshots + async progress/result | aktivieren via `fields.streaming=true`; face-count == alt-pfad (gate, exakt bewiesen); antwortet SELBST (handleWorldExport null-check). Task 3.3: `fields.cullCaves=true` optional — signatur/felder UNANGETASTET (bereits geladene klasse), gate/wiring lebt in `SkyReachability`. Task 3.4: `fields.mergeFaces=true` optional, gate/wiring lebt in `FaceMerger` — `meshColumn`s body macht PendingQuad↔FaceMerger.Quad copy-in/copy-out (PendingQuad ist `private` zu dieser klasse, FaceMerger kann sie nicht direkt anfassen) und speist das ergebnis in den GLEICHEN write-sequencer-zweig wie cullCaves (bedingung jetzt `cullCaves \|\| mergeFaces` — merge's eigene pro-spalte-kosten streuen genug um dieselbe worker-schreibt-race sichtbar zu machen, siehe FaceMerger-klassendoc). **`isActive()`** (2026-07-17): `WorldExportBridge.export` MUSS das VOR jedem arm/reset-call prüfen — sonst zerstört ein (selbst korrekt abgelehnter) request während eines laufenden jobs dessen arm-zustand+sequencer. **UND im ALT-zweig (end-review fix 3):** ein request OHNE `streaming:true` (default!) lief sonst synchron in DASSELBE outDir/dateinamen (`world.obj` trunkiert während der job sie offen hält = interleaved-korrupt, `world_meta.json` last-writer-wins) und wischte via `ChunkCoverage.armForExport` die coverage-zählung des laufenden jobs — beide zweige rejecten jetzt mit "a streaming export is already running". **BE-write-race BEWUSST NICHT GEFIXT**: `exportBlockEntitiesForColumn` schreibt UNSEQUENCED (main-thread, sofort) — `BlockEntityExporter.emit` ist main-thread/GL-gebunden, der sequencer kann aber auf beliebigem thread drainen; ein versuch das über den sequencer zu leiten wurde vor landung verworfen (echtes crash-/GL-risiko, kein cosmetic-fix). Region MIT block-entities → content-identische, byte-verschiedene wiederholungs-exports bleiben möglich, dokumentiert. **Task 3.5-streaming (2026-07-17, body-only + neue klasse `StreamMetaWriter`): schreibt jetzt AUCH `world_meta.json`** — `start()` armt `StreamMetaWriter.armNextJob(world)`, `meshColumn`s block-loop ruft `StreamMetaWriter.recordIfEmissive(pos,state)` (kein zweiter scan, selber block schon in der hand), `maybeFinish` ruft `StreamMetaWriter.write(...)` nach `writeMtl`. LIVE gemessen: region 423..519/-60..111/-6..90, 895 emissive einträge, sortiert BYTE-IDENTISCH zum alt-pfad (`WorldExporter.writeWorldMeta`) auf derselben region; skyAngle differierte minimal (0.6158 vs 0.6485, zeit lief zwischen den zwei exports weiter) — erwartet, kein bug. |
| `world/StreamMetaWriter` | NEU 2026-07-17 (Task 3.5-streaming): `world_meta.json` fürs streaming-pipeline-gegenstück zu `WorldExporter.writeWorldMeta`, byte-schema-identisch (region/timeOfDay/skyAngle via `world.getSkyAngle(1.0F)`/dimension/emissive "x y z luminance block-id") | NEUE klasse (deploy-window-free, gleiches muster wie `SkyReachability`/`FaceMerger`/`StreamRegionClamp`) statt felder an `StreamingWorldExporter` anzufassen; `ConcurrentLinkedQueue` statt geteilter liste, weil emissive-sammlung auf WORKER-threads läuft (viele spalten gleichzeitig) — kein lock-kontention mit `Job#writerLock`. **`write()` SORTIERT die gedrainte liste (`Collections.sort`, natürliche string-order) VOR dem schreiben (leader-review-fund 2026-07-17)** — drain-reihenfolge ist worker-scheduling-abhängig (welche spalte zuerst fertig wird), NICHT nur ein vergleichs-detail: das projekt hat eine GEMESSENE byte/MD5-determinismus-garantie für 2×-export (write-sequencer, siehe `SkyReachability` "DETERMINISTIC WRITE ORDER"), unsortiert hätte `world_meta.json` als einzige datei im export-verzeichnis diese garantie gebrochen. LIVE gemessen: 2× streaming-export derselben region hintereinander → `world_meta.json` diff-identisch/MD5-identisch (`84b779dadae0bb5cdfe927f7399c88ab`) BIS AUF `timeOfDay`/`skyAngle` (zeit lief: 6822→7054 ticks) — 895/895 emissive-einträge, in-reihenfolge identisch. Alt-pfad (`WorldExporter.writeWorldMeta`) braucht dieses sortieren NICHT (scan-order ist selbst schon deterministisch) — dort nichts anfassen. **`armedWorld`-hygiene (end-review fix, fund 5):** der doc-behauptete same-world-sanity-check existiert jetzt WIRKLICH (`write()` warnt bei armed≠write-world — welt-wechsel mid-job hieße emissive der alten + time/sky der neuen welt); `write()`s finally UND `sendResult`s teardown (`armNextJob(null)` — deckt abort-pfade die write() nie erreichen) leeren world-ref+queue — vorher pinnte die `ClientWorld` (kompletter welt-graph) bis zum NÄCHSTEN streaming-export, und die queue behielt die emissive-strings des letzten jobs. **W7-B2 (2026-07-17):** `write()` ruft jetzt auch `TextureStripExporter.appendAnimatedJson` (animated-sektion, arm passiert in `maybeFinish` — RENDER thread, log-gemessen) und `recordIfEmissive` wird von `meshColumn` NACH dem cull-entscheid gerufen (nur blöcke die faces behielten, wenn cullCaves armed; ohne cull unverändert) — cull-manifest = nur geometrie-existente lichter (79 statt 895 an der referenz-region, 0 einträge ohne OBJ-geometrie). |
| `world/BlockDeltaTracker` | NEU 2026-07-17 (task 3.6, W7-B4, das USP-novum): live-block-delta — budgetierter snapshot-diff-sweep (int[] raw-state-ids, y-innerste index-achse = chunk-column-kohärente chunk-cache-hits) über die region des LETZTEN erfolgreichen exports, pusht unsolicited raw-string `BLOCK_DELTA` (`"x y z block-id [particle-sprite-material-hint]"`, region+count+reExport). Armen NUR via `WorldExportBridge.export`-body (alt-pfad nach `result.ok`, streaming nach `start()==null`); addon-consumer `world_delta.py` | KEIN mixin-freies client-block-event existiert (GEMESSEN: fabric-api-jar gelistet, BBS-mixins geprüft) — exakter mixin = boot, bewusst nicht gebaut, poll ist der restart-freie weg. Unloaded chunks werden GESKIPPT (`EmptyChunk`-instanceof, ChunkCoverage-falle — sonst delta-sturm beim chunk-unload). `WeakReference`-welt (StreamMetaWriter-lehre), welt-wechsel=disarm. BATCH_CAP 256 mit SHORT-CIRCUIT im scan-loop (paste dirtied sonst budget×string+model-lookup in einem tick) → `reExport:true` statt mega-message. Budget GEMESSEN: ~54ns/read warm → MAX 32768 (~1.8ms/tick worst case), latenz ehrlich bis ~1 sweep (~1s normal, ~2.5s bei 1.6M). Eigene region-kopie — NIE RegionClamp/StreamRegionClamp kapern (export-scoped, finally-cleared; tracker überlebt den export). **W8-B3 (2026-07-17, boot-cargo):** region/snapshot-statics + `changeEntry`/`push` jetzt package-private (BlockDeltaEvents-reuse, kein neues member) + EINE tick()-body-zeile `BlockDeltaEvents.flush(world)` (no-op bis mixin gewoben). ⚠ committed version ≠ geladene ffc7c5f — NICHT ohne not force-sync-hotswappen vor dem boot-fenster (harmlos by design, aber nichts zu gewinnen) |
| `world/BlockDeltaEvents` | NEU 2026-07-17 (W8-B3, task 3.6 v2, ⛔ BOOT-PFLICHTIG/schlafend): notify-brücke mixin→v1-poll-apparat — `notifyExternal` (guard+enqueue, cap 1024, main-thread) vom `ClientWorldBlockDeltaMixin`, `flush` (tick-grenze, aus `BlockDeltaTracker.tick()`) difft gegen DASSELBE snapshot + pusht durch DENSELBEN `BLOCK_DELTA`-pfad — event- und sweep-funde wire-identisch, kein doppel-report (flush adoptiert snapshot). Overflow >1024/tick = paste-scale → reExport-empfehlung | Läuft HEUTE NICHT (nur compile+json-gate, ehrlich): beide caller (mixin, neue tick-body) werden erst beim nächsten boot wirksam. AKTIVIERUNGS-PLAN im klassendoc: boot → weave-bytecode-probe → same-tick-runtime-gate → poll RUNTER NICHT AUS (BUDGET_MAX 32768→4096 bzw. TARGET_SWEEP_TICKS 20→200; sweep bleibt netz für die GEMESSENE full-chunk-reload-lücke, die kein block-hook sieht). Stale-queue-einträge self-healen (bounds-recheck + UNSEEN-adopt) |
| `world/FaceMerger` | Task 3.4 (face-merge-hälfte von Task 3.3, R1: Mineways −89%): pro-säule 2D-sweep-greedy-merge koplanarer, gleich-texturierter unit-quads zu maximal-rechtecken, UV-REPEAT (nicht stretch) | NEUE klasse (SkyReachability-muster: eigenes `Quad`, da `StreamingWorldExporter.PendingQuad` `private` ist) — nur axis-aligned/unit-size/voll-[0,1]-UV quads mergen JE (slabs/stairs/fences/cross-modelle/sloped-fluid fallen automatisch durch classify() raus, PASSTHROUGH unverändert); UV-zu-achse-mapping wird PRO LAYER aus der ersten quad GEMESSEN (kein winding hardcoded); v1 bewusst NUR pro säule (keine spalten-übergreifende mergung); merge() ist eine reine, zustandslose funktion (kein ThreadLocal nötig, anders als SkyReachability) |
| `world/ColumnSnapshot` | thread-sicherer säulen-snapshot +1 rand +biome | `outOfRimReads` MUSS 0 sein AUSSER region berührt welt-boden/-decke (Y=-64/319 → kleiner positiver zähler, kein bug — gemessen 1536, geclippt 0); BEs bewusst null |
| `world/SkyReachability` | Task 3.3: pro-säule BFS/flood-fill (Bret-B `LightConnectedPathfinder`-muster) für höhlen-culling, seeds = säulen-oberkante + region-rand-echtlicht, propagation durch `getOpacity()==0`-zellen | NEUE klasse, trägt ALLE 3.3-zustände (arm-flag, ThreadLocal pro worker, write-sequencer) statt `StreamingWorldExporter`/`Job`/`WorldExportBridge` (bereits geladen) anzufassen — siehe klassen-doc "DEPLOY-WINDOW-FREE WIRING". Grenzen: pro-säule-scope (höhle >1 block über nachbar-spalte offen wird falsch geculled), wasser=opak (binär, keine dämpfung), keine dimension-awareness (nur overworld-artig sinnvoll). **GEMESSEN (streaming-builder-note oben "canAddMethod=false" ist UNVOLLSTÄNDIG:** DCEVM erlaubte auf DIESER JVM tatsächlich add-method/-field via hotswap (SkyReachability bekam 2 hotswap-runden mit neuen static-methoden+feldern, beide erfolgreich) — der JDI-flag lügt, das eigentliche verhalten muss man PROBIEREN, nicht am flag ablesen; die `<clinit>`-falle (unten) ist trotzdem real und die eigentliche gefahr, unabhängig vom flag. **W7-B2 boundary-ausnahme (2026-07-17, body-only):** `shouldCullFace` cullt NIE eine face deren eine zelle laut `StreamRegionClamp.isBoundaryAir` außerhalb der geclampten region liegt — die clamp erklärt außen zu offener luft, also ist eine grenz-face per definition von außen sichtbar; schließt die 66%-boden-lücke des co-gates (hull 100.00% exakt, in-plane-faces gemessen; openBounds=aus → ausnahme inaktiv) |
| `entity/EntityExporter` | EINE entity → bind-pose OBJ+MTL+komposit-textur (forEachCuboid + capturing consumer) | **2026-07-17: villager-textur ist jetzt der VOLLE layer-stack** (delegiert an `VillagerTextureLayers`, body-only-change, hotswapped) — nicht mehr nur basis. `resolveTexture`/`getTexture` liefern weiterhin nur die BASIS-identifier (konstante, siehe `VillagerTextureLayers`-doc), `doExport` reicht sie an die neue klasse weiter statt selbst zu kopieren. non-SinglePartEntityModel-fallback weiterhin offen (unverändert, außerhalb dieses tasks). **W7-B1 (task 4.1 wire): 2 BODY-only-hooks** — `resolveEntity` konsultiert zuerst `EntityExportTarget.peekTarget()` (throwaway-entity eines replays ist NICHT in der welt), `resetBindPose` ruft pro part `EntityExportTarget.neutralizeRotation` (no-op wenn nicht armed). Direkte caller unverändert |
| `entity/VillagerTextureLayers` | NEU 2026-07-17: villager textur-LAYER-STACK auflösen (base/type/profession/profession_level, ground-truth aus decompiled `VillagerClothingFeatureRenderer`) + alpha-over-komposit (java.awt BufferedImage) in EINE PNG | `EntityExporter.getTexture()`-doc-korrektur beachten: die "biome-spezifische basis"-annahme war FALSCH, `getTexture` ist konstant, "type" ist der immer-aktive biome-layer. NUR base+type für unbeschäftigte villager (kein profession/profession_level ohne job); NITWIT bekommt kein level-badge (deckt sich mit vanilla: keine "none.png"/"nitwit-level"-datei existiert). Hat-geometrie-toggle (`setHatVisible` je layer) bleibt UNGELÖST (dokumentierte grenze, betrifft geometrie nicht textur) |
| `entity/EntityBakeHandler` | ENTITY_BAKE: pose pro tick samplen (echtes setAngles), result mit frames-array | tick-anbindung via bestehendem onClientTick-body; idle-mobs liefern legitim 0-deltas |
| `entity/EntityProtocol` | payload-keys für bake-messages | typ-konstanten leben in BridgeProtocol (W1-reservierung) |
| `entity/EntityExportBridge` | NEU 2026-07-17 (W7-B1, task 4.1 wire): `ENTITY_EXPORT`(-`_RESULT`) — replay(`MobForm`)/live-entity → treibt `EntityExporter` (rig-neutral, s.u.) + schreibt `rig.json` (part-baum: path/name/objGroup/parent/pivotPx/bindRotation/visible) neben die OBJ; result echot `requestId` (solicited-korrelation VON ANFANG AN, ce2d139-lehre) + `mobId`/`replayUuid`/`rigPath`/`boneCount` | raw-string-case in dispatch (kein `TYPE_*`); film/replay-resolution BEWUSST dupliziert (haus-konvention); throwaway-entity MIT mobNBT (`ensureEntity`-spiegel — texturen brauchen NBT, pose nicht); `sanitizeGroupName` MUSS `EntityExporter`s privates original spiegeln; pivotPx sind ROHE pixel (÷16 = blöcke, addon macht das); EIN export-dir pro entity-TYP (2 professions = letzter gewinnt, dokumentierte grenze) |
| `entity/EntityExportTarget` | NEU 2026-07-17 (W7-B1): static-holder (RegionClamp-muster) — armed target-entity + zeroRotation-flag für die 2 `EntityExporter`-body-hooks | arm→export→clear IN EINEM dispatch (main-thread, plain statics); clear IMMER im finally (leak würde den nächsten ENTITY_BAKE-resolve kapern). **ZERO-ROTATION-CAPTURE (klassendoc trägt herleitung):** wire-OBJ wird mit genullten part-rotationen captured — absolute pose-results (capturePose-radians) doppelrotieren sonst bind-rotierte parts; mit R_bind==0 ist die addon-konjugation P=H⁻¹RH für JEDE bone-rest-orientierung exakt |
| `entity/ReplayPoseReader` | Task 4.3a: replay-gebundene mob-actors DIREKT aus keyframe-kanälen posen (`headYaw`/`bodyYaw`/`pitch`, `KeyframeChannel.interpolate()` — kein tick-sampling-job wie 4.3b, ganze performance existiert schon), raw-string `REPLAY_POSE_EXPORT`/`_RESULT` (kein neuer `BridgeProtocol.TYPE_*`), schema absichtlich byte-identisch zu `ENTITY_BAKE_RESULT` (reuse der `EntityProtocol`-key-konstanten) | NUR replays mit `MobForm`-form (nicht `ModelForm`/"alex"-rig — genau das task-ziel: mob-rig statt alex-rig); throwaway-posier-entity via `Registries.ENTITY_TYPE.get(id).create(world)` (NIE gespawnt/geticked, gleicher trick wie `MobFormRenderer.ensureEntity()`) — limb-swing bleibt daher 0 (kein `.tick()`, bewusst keine erfundene walk-mathematik); **`posingEntity.headYaw` MUSS die ABSOLUTE kanal-yaw sein, NICHT selbst vorher minus `bodyYaw`** — `EntityExporter.poseEntity` zieht `bodyYaw` intern schon ab, doppelt abziehen war ein echter, live gemessener bug (siehe dossier-kopf) |
| `mixin/CameraControllerLiveOverrideMixin` | stempelt override pro frame | BYTE-IDENTISCH LASSEN (hotswap kann keine mixins) |
| `mixin/ClientWorldBlockDeltaMixin` | NEU 2026-07-17 (W8-B3, ⛔ schlafend bis boot): event-signal für BlockDeltaTracker — ZWEI hooks, beide → `BlockDeltaEvents.notifyExternal` | GEMESSEN warum zwei (CFR gegen projekt-jar): `handleBlockUpdate` (alle server-pfade) applied via `super.setBlockState`=INVOKESPECIAL → umgeht das override; prediction+`processPendingUpdate` laufen NUR durchs virtual `setBlockState(4-arg)`. `onChunkData` (full-chunk-reload) sieht KEINER — poll bleibt netz. setBlockState-hook feuert nur bei return true. Nach boot: BYTE-IDENTISCH LASSEN wie die anderen mixins |
| `importers/ObjPivotSidecar` | NEU 2026-07-22 (OBJ-pivot-task): rettet beim OBJ-drop die pivots — kopiert die gleichnamige `.bbs.json`-sidecar als `model.bbs.json` (CubicModelLoader merged sie per gruppen-name mit den OBJ-meshes = pivots überleben), dazu `model.mtl` + die `map_Kd`-textur der MTL als `model.png`; validiert die sidecar VOR dem kopieren (parent-ohne-group = NPE-falle Model.fromData:374, von CubicLoader.load VERSCHLUCKT → wäre still pivot-los) | matching TRIMMED+case-insensitive (Blender-export heißt real `verity .bbs.json`); wiring = 2 body-only-edits in `ModelDropImporter` (isObjSidecar-skip in importFiles, installCompanions in installModel); einheiten geprüft: OBJ-verts ×16 (ModelData.fill), origin schon in px, render ÷16, UV ×tw÷tw=neutral. LIVE bewiesen (Cube.origin==(32,24,0) px, kontrolle ohne sidecar ==(0,0,0), users echte verity-dateien inkl. textur). Code-ebene: bbs-wiki INDEX §8 |
| `mixin/ModelPartChildrenAccessor` | backup-zugriff auf ModelPart.children | **ENTDECKT 737d81a: children ist via bbs.accesswidener SCHON PUBLIC** (bbstools-eintrag 07-15) — direkt lesen, mixin bleibt nur als fallback |

## DIE REGELN DIE HEUTE ZEIT KOSTETEN (2026-07-16 gemessen)

- **EXEC/JShell**: EIN fehler rejected den GANZEN block · nur ERSTE expression liefert wert ·
  lambda-vars eindeutig (2× `f` = fehler) · mixin-methoden UNSICHTBAR (reflection!) · NEUE
  klassen nach compile: `Class.forName`, nie direktes symbol (analyzer-cache) · `execute()`
  + warten im selben call = DEADLOCK (submit-then-poll über 2 calls) · main-thread via
  `MinecraftClient.getInstance().execute(...)`, server via `getServer().execute(...)`.
- **Block setzen + teleport im SELBEN server-task = paket verloren** (client sieht air).
- **Einheiten**: BOBJ 1:1 blöcke · `.bbs.json` PIXEL (×16) · contract `bbs_y=bl_z, bbs_z=-bl_y`.
- **Modelle**: `getModels().reload()` ist ASYNC — `getModel()` erst null, log sagt "was loaded!".
  Animation namens **"idle"** spielt automatisch. Texturen via `config.json` link
  (`assets:models/<name>/<file>.png`). glTF NIE (skinning kollabiert), BOBJ/OBJ ja.
- **Boot/deploy**: neue klasse = lazy (kein boot) · body-ÄNDERUNG = JDI-hotswap
  (`java hotswap-tool/HotSwap.java 5005 "cml-fresh/build/classes/java/client" <fqcn>`) ·
  **GEMESSEN (streaming-builder): `canAddMethod=false` auf dieser JVM** — auch NEUE
  methoden/felder an GELADENEN klassen brauchen boot, nicht nur mixins. Bei bedarf:
  private→package-private relaxen ist ok (kein neues member), oder logik in NEUE klasse
  auslagern und nur bestehende bodies umbiegen. **KORREKTUR (task 3.3, 2026-07-17): der
  `canAddMethod=false`-flag LOG — auf DIESER JVM ging add-method/-field per hotswap tatsächlich
  durch (SkyReachability bekam 2× neue static-methoden/-felder via hotswap, beide erfolgreich).
  Der flag ist keine verlässliche DCEVM-diagnose, PROBIEREN statt am flag glauben. Trotzdem:
  private→package-private/neue-klasse bleibt der SICHERE default, weil die `<clinit>`-falle
  (unten) real ist und genau bei "hotswap fügt doch ein feld hinzu" zuschlägt.**
- **⛔ `<clinit>`-FALLE (2× passiert 2026-07-17, 2. mal = GAME-CRASH):** wenn ein hotswap
  einen neuen static-field MIT initializer doch durchbringt, wird `<clinit>` NIE erneut
  ausgeführt → field bleibt am bytecode-default (null!) → NPE später, im schlimmsten fall
  auf dem render-thread (crash-2026-07-17_01.59.12). REGEL: per hotswap NIE felder mit
  initializern einführen — **lazy-holder-muster**: `synchronized(Klasse.class)`-double-checked
  init eines instanz-objekts, KEIN static-initializer. Gilt auch für ThreadLocal/locks. Boot IMMER `JAVA_HOME=../jbr-dcevm-home` +
  `-Pdebug -Pworld` (Temurin stirbt an AllowEnhancedClassRedefinition). Windows: `gradlew.bat`
  oder bash `./gradlew` — NIE `cmd /c gradlew`.
- Ports: :9880 bridge · :9876 Blender-MCP · :5005 JDWP (nur mit -Pdebug).
- **FLOTTEN-REGEL export-gates (2026-07-17 nach determinismus-fehlalarm):** (1) vor jedem
  determinismus-/MD5-gate eine "export-gate-window"-ansage per SendMessage an main — kein
  paralleler export/compile während des fensters. (2) KLASSEN-VERSIONS-SKEW: jedes
  `compileClientJava` irgendeines agents baut ALLE dirty sources — die live-JVM kann danach
  ältere bodies tragen als build/classes. Vor gates: eigene klassen frisch re-hotswappen
  (force-sync), erst dann messen.
