package com.afrobama.bbstools.client;

import com.afrobama.bbstools.BBSToolsMod;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entry. UI panels (Keybind Editor, Quick Replay Picker), the
 * model-editor URL-paste hook, and the keybind override system are
 * registered from here once they're migrated.
 */
public final class BBSToolsModClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        BBSToolsMod.LOG.info("BBSTools client init");

        /* AUTONOMIE 2026-07-27: zwei fertige, aber nie gerufene werkzeuge.
         * F12 oeffnet die in-game log-konsole (BBSLogConsoleScreen), das HUD zeigt
         * perf-zahlen (aus mit /bbs-renewed hud). Beide haben ihr register() seit
         * jeher — es hat sie nur niemand aufgerufen, also gab es sie im spiel nicht. */
        com.afrobama.bbstools.client.console.BBSLogConsoleHotkey.register();
        com.afrobama.bbstools.client.console.BBSRenewedHud.register();

        /* AUTONOMIE 2026-07-27: BBSToolsAddon von hand am event-bus anmelden.
         * Frueher lief das ueber BBS' custom-entrypoint "bbs-addon-client" (CML-fork), das
         * es im fs-kern nicht gibt — fs postet seine 5 register-events einfach ueber
         * BBSMod.events. Ohne diese zeile wird kein einziger @Subscribe-hook je gerufen
         * (dashboard-panel + sprachdatei fehlen dann still). */
        mchorse.bbs_mod.BBSMod.events.register(new com.afrobama.bbstools.client.addon.BBSToolsAddon());

        /* Blender-transform-kern bei BBS anmelden. UIPropTransform kennt seit 2026-07-26 nur
         * noch das interface mchorse…input.BlenderModalKernel — ohne diese zeile bleibt die
         * fabrik null und G/R/S fällt auf BBS' alten ray-pfad zurück (kein crash, aber der
         * kernel ist dann einfach nicht da). */
        /* Blender-transform entfernt 2026-07-26 (user-order). */

        /* Master Debugger Modules 41+42 — crash-safe writers init FIRST,
         *  before UncaughtExceptionCapture, so the very first exception
         *  (if any) lands in bbs/debugger/exceptions.ndjson. r38 2026-05-18 —
         *  survives JVM native AVs because the writer uses
         *  FileChannel.force(false).
         *
         *  ⚠ bbs/debugger/recording.jfr entsteht seit 2026-07-28 NICHT mehr von
         *  selbst: die JFR-dauer-aufnahme schaltet die erweiterte klassen-
         *  redefinition der JVM ab (gemessen) und damit strukturelles hotswap.
         *  Wer die .jfr fuer eine crash-jagd braucht: BBS-einstellung
         *  bbstools.jfr_auto_record — siehe JfrAutostart. */
        com.afrobama.bbstools.client.debug.master.crashsafe.CrashSafeExceptionLog.init();
        com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.init();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.crashsafe.CrashSafeExceptionLog.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.INSTANCE);

        /* Master Debugger Modules 43-46 — external + zero-install GL
         *  inspection. Each init is a one-shot probe; no-op if the
         *  corresponding tool isn't present in the process. Order:
         *    43 RenderDoc (open source, MIT — needs MC launched via
         *       RenderDoc to attach)
         *    44 NVTX (NVIDIA Tools Extension — needs nvToolsExt DLL,
         *       ships with CUDA Toolkit / Nsight Graphics)
         *    45 APITrace (open source MIT — needs `apitrace trace ...`)
         *    46 GlIntrospect (zero-install — just OpenGL queries) */
        com.afrobama.bbstools.client.debug.master.external.RenderDocBridge.init();
        com.afrobama.bbstools.client.debug.master.external.NvtxBridge.init();
        com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.init();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.external.RenderDocBridge.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.external.NvtxBridge.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.gl.GlIntrospect.INSTANCE);

        /* Per-tick heartbeat pulse — tells external watchers MC's tick
         *  thread is still alive. Cheap (single AtomicLong store). */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            client ->
            {
                com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.onTick();

                /* ⭐ TAKTMARKE FUER NSIGHT (2026-08-04). Ohne einen Marker, der IMMER
                 * feuert, bleibt die Nsight-Zeitachse in aller Regel leer: der zweite
                 * Marker sitzt am Auswahlpuffer, und der laeuft ausschliesslich beim
                 * Bone-Hover-Picking (BoneHoverPickRunner) -- also nur mit Maus ueber der
                 * Kameravorschau. Gemessen: 25 s Aufzeichnung im offenen Film-Editor
                 * ergaben NULL Bereiche.
                 *
                 * Mit dieser Marke hat jede Aufzeichnung ein Taktraster, an dem sich alles
                 * andere einordnen laesst -- und Tick-Aussetzer werden als Luecke sichtbar.
                 * Kosten: ein Funktionsaufruf je Tick (20/s), und er faellt sofort zurueck,
                 * wenn NVTX nicht angehaengt oder das Feature aus ist. */
                com.afrobama.bbstools.client.debug.master.external.NvtxBridge.mark("tick");

                /* afrobama — mirror the BBS top-bar "Bake" menu flags into perf config. */
                com.afrobama.bbstools.client.qol.BakeMenuSync.tick();

                /* AUTONOMIE 2026-07-27: kamera-preview-overlays HIER synchronisieren,
                 * nicht in ihrem render(). Dort aenderten sie die kinder-liste, waehrend
                 * UIElement.render() sie iterierte -> ConcurrentModificationException,
                 * drei crash-reports. Im tick iteriert niemand. */
                com.afrobama.bbstools.client.campreview.UICameraPreviewOverlay.tickAlle();
                com.afrobama.bbstools.client.campreview.UIClipPreviewLayer.tickAlle();
            });
        /*
         * Real-frame id fuer den playback-sim-decouple.
         *
         * ⛔ FRUEHER AM HudRenderCallback — DAS WAR DER FLACKER-BUG (2026-07-27, gemessen).
         * Der HUD-pass laeuft NICHT, solange ein screen offen ist, also im BBS-editor nie.
         * Die id stand dort fest, und PlaybackDecoupleLogik.coalescePerFrame bricht jeden
         * updateEntities ab, dessen id gleich der letzten ist — im editor also ALLE ausser
         * dem ersten. Die actors froren auf dem ersten tick ein und bekamen keine
         * keyframe-transforms mehr; der nutzer sah "70% szene, 30% falsche position".
         *
         * LevelRenderEvents.END_MAIN feuert pro welt-render, auch bei offenem screen. Damit
         * zaehlt die id im editor wieder mit, das gate ist wieder self-gating wie gedacht.
         *
         * ⚠ Iris/shaderpacks koennen die welt mehrfach pro frame zeichnen (schatten-pass).
         * Mehr id-spruenge sind harmlos: das gate wird dadurch nur DURCHLAESSIGER (mehr
         * ticks laufen durch), nie strenger — es kann also nie wieder transforms verschlucken.
         */
        // PHASE NICHT BELEGT GLEICHWERTIG: alt war LAST/END/AFTER_SETUP. 26.2 hat dafuer kein benanntes Gegenstueck -- ob diese Phase an derselben Stelle liegt, ist am laufenden Bild zu pruefen (Export gegen Fenster).
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.END_MAIN.register(
            kontext ->
            {
                com.afrobama.bbstools.perf.BBSPerfConfig.bbstoolsRenderFrameId++;

                /* ⛔ ZWEITER ANTRIEB FUER DEN HERZSCHLAG — der HUD-Antrieb unten steht still,
                 * sobald ein Screen offen ist, und offen ist er in JEDER Editor-Sitzung.
                 * Gemessen 2026-08-04: frame blieb 45 Minuten auf 11930 und fps auf 30,
                 * waehrend die Instanz mit 85–140 fps lief. Ein eingefrorener Wert, der wie
                 * ein Messwert aussieht, ist teurer als gar keiner: er beendet die Suche.
                 * Hier laeuft es je Weltdurchgang, also auch hinter dem Editor. */
                com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.onFrameWelt();
                try
                {
                    com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.setFps(
                        net.minecraft.client.Minecraft.getInstance().getFps());
                }
                catch (Throwable ig) {}
            });

        /* Per-frame counter for the heartbeat's "frame" + "fps" fields.
         *
         * 26.2: HudRenderCallback ist gestrichen, Nachfolger HudElementRegistry (siehe
         * BBSModClient). Dieses Element ZEICHNET nichts, es zaehlt nur -- der Zeichner wird
         * gar nicht angefasst. addLast, weil der alte Rueckruf am Ende des HUD lief; fuer
         * einen reinen Zaehler ist die Ebene ohnehin egal. */
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.afrobama.bbstools.BBSToolsMod.MOD_ID, "herzschlag"),
            (drawContext, tickDelta) ->
            {
                com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.onFrameHud();
                try
                {
                    com.afrobama.bbstools.client.debug.master.crashsafe.BBSHeartbeat.setFps(
                        net.minecraft.client.Minecraft.getInstance().getFps());
                }
                catch (Throwable ig) {}
                /* Benchmark harness: once-per-real-frame driver (Iris-safe; WorldRenderEvents fires per pass). */
                try { com.afrobama.bbstools.client.debug.master.bench.BenchHarness.onFrame(); }
                catch (Throwable ig) {}
            });

        /* Master Debugger Module 3a — install global uncaught-exception
         *  handler FIRST so anything thrown by later init lines lands in
         *  our [uncaught] log instead of falling through to vanilla MC. */
        com.afrobama.bbstools.client.debug.master.jvm.UncaughtExceptionCapture.install();

        /* UI Overhaul Phase 1: editor transparency keybind (Ctrl+Alt+T). */
        com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency.register();
        BBSToolsMod.LOG.info("UI Overhaul ready — theme={}, mixins=[toggle, button, scrollbar, icon, bg-alpha]",
            com.afrobama.bbstools.client.ui.theme.BBSToolsBrand.NAME);
        BBSToolsMod.LOG.info("UI Overhaul shortcuts: Ctrl+Alt+T transparency · Ctrl+, settings · Ctrl+H undo · Ctrl+Shift+R replay dock · Ctrl+Shift+Z/Y undo/redo step · Shift+F9 keybind editor");
        BBSToolsMod.LOG.info("UI Overhaul commands: /bbsdebug ui-info · transparency · overlays · undo-history · replay-dock · settings");

        /* UI Overhaul Phase 3: align BBS-CML's accent color with our
         *  ACCENT_PRIMARY so selected list items, dashboard tab
         *  highlight, etc. all pull from our palette. Persists to
         *  BBS's config so it stays after relaunch — user can
         *  override via BBS settings if they want vanilla cyan back. */
        try
        {
            int desiredAccent = com.afrobama.bbstools.client.ui.theme.BBSToolsTheme.ACCENT_PRIMARY & 0x00FFFFFF;
            mchorse.bbs_mod.BBSSettings.primaryColor.set(Integer.valueOf(desiredAccent), 0);
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.warn("Failed to set BBS primary color: {}", t.toString());
        }

        /* In-game profiler dashboard /bbsdebug. */
        com.afrobama.bbstools.client.debug.BBSDebug.register();

        /* Crowd-form applier: client-tick scan + Form injection per villager UUID. */

        /* PhysicsMod ragdoll bridge — reflection-gated, safe to call
         *  whether or not physics-mod-pro is installed. */
        com.afrobama.bbstools.client.ragdoll.BBSPhysicsModBridge.init();

        /* Flashback (by Moulberry) recording bridge — license-clean
         *  reflective shim. Flashback is "All rights reserved" so we
         *  can't vendor or mixin into it; instead the user installs it
         *  alongside BBSTools and we call its public static recorder
         *  API via reflection. Lets the BBS editor scrub + re-shoot
         *  inside Flashback's IntegratedServer playback. The
         *  isAvailable() call here primes the cached probe + logs the
         *  one-time "detected"/"missing" banner at boot. */
        com.afrobama.bbstools.client.flashback.FlashbackBridge.isAvailable();
        com.afrobama.bbstools.client.flashback.FlashbackBridgeCommand.register();

        /* Load keybind presets from disk so UIKeybindEditor's preset
         *  selector + filtered list have data. The BBS jar ships its
         *  own KeybindOverrides under mchorse.bbs_mod.ui.utils.keys
         *  but our editor reads from OUR registry — which the
         *  KeyComboMixin populates as BBS combos construct. Without
         *  this call, presets map stays empty and the editor's
         *  preset row is blank. */
        com.afrobama.bbstools.client.keybinds.KeybindOverrides.loadFromDisk();

        /* TrackerClip override DISABLED — see prior session notes:
         *  BBS jar's compiled mixins reference its own TrackerClientClip
         *  by class name; replacing breaks hand/HUD render. Re-enable
         *  block on the BBS Refreshed switch only. */

        /* === Fracture feature: PhysicsMod-driven model destruction === */
        com.afrobama.bbstools.client.fracture.FracturePhysicsBridge.init();
        if (com.afrobama.bbstools.client.qol.QuickWinKeybinds.DEV_ENV) com.afrobama.bbstools.client.fracture.FractureTestCommand.register();
        com.afrobama.bbstools.client.debug.BBSDebugEverything.register();

        /* /bbstools skin <username> — pulls a player's current Mojang
         *  skin PNG into bbs/skins/<username>.png so the user can drop
         *  it onto a replay model without leaving the game. */
        com.afrobama.bbstools.client.skin.SkinFetchCommand.register();

        /* /bbstools fov save|load|list — named FOV presets for the BBS
         *  tracker clip, persisted to bbs/fov-presets.json. Probes the
         *  active tracker clip's fov value (falls back to MC options),
         *  applies on load by mutating the clip's ValueFloat. */
        com.afrobama.bbstools.client.tracker.TrackerFovPresets.register();

        /* Live-tunable BBSPerfConfig.MAX_RENDER_DISTANCE_SQ for cinematic
         *  shots — replays were disappearing >96 b during the 1m
         *  pillager-manhunt cinematic; static default is 512² now but
         *  per-shot framing sometimes needs more. */
        com.afrobama.bbstools.client.perf.CinematicDistanceCommand.register();
        /* Manual sprint FOV override — fallback for when BBS replay
         *  entity sprint-state isn't being read correctly. */
        com.afrobama.bbstools.client.tracker.SprintFovManualCommand.register();

        /* === BBSTools FX pipeline DISABLED iter 64 ===
         *  User report 2026-05-16: MC crashed on startup after enabling
         *  ComplementaryReimagined shaderpack in Iris. Stack trace shows
         *  the crash is in Iris's own ShadowRenderer (NullPointerException
         *  at IrisRenderSystem.restorePlayerProjection) — not in our
         *  code. But Veil + Iris coexistence is the trigger, and the
         *  user wants to run pure Iris-shaderpack visuals (Grox-style
         *  Complementary Unbound / SEUS Renewed) without our post-FX
         *  interference.
         *
         *  Quote: "disable unsere pipeline ich brauche die nicht"
         *
         *  So all BBSFx registrations are commented out for now. The
         *  BBSFxPipelineRegistrar's first-stage callback was calling
         *  Veil's enableBuffers + auto-adding 7 post-FX pipelines which
         *  registers framebuffer attachments + shader hooks that Iris
         *  is unhappy about during its shadow pass. Removing all of
         *  that gives Iris an uncontested render path.
         *
         *  /bbstools fx commands also disabled (the registrar that drives
         *  them is gone). Re-enable this whole block once we have a way
         *  to coexist with Iris shaderpacks without crashing it. */
        /* iter 65 — re-enable ONLY the glitch path (user request: "ah ich
         *  fand aber glitch cool, mach das kurz rein"). Other catastrophe
         *  effects + the shadowmap pre-pass (suspected crash cause when
         *  Iris shaderpack was active — VeilLevelPerspectiveRenderer.render
         *  interfering with Iris's projection-matrix save/restore) stay
         *  disabled. The PipelineRegistrar's iris-shaderpack-gating (iter 63)
         *  means our pipelines auto-back-off when a pack is active, so this
         *  should be safe with Complementary Reimagined enabled. */
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        com.afrobama.bbstools.client.fx.effects.GlitchEffect.register();
        /* ⛔ P3-26.2-AUS — /bbstools fx ist AUS (Veil-nachbearbeitung: bloom, supernova,
         * grain; auch das an/aus des glitch-pfads lief ueber diesen befehl).
         * Hier stand:  com.afrobama.bbstools.client.fx.BBSFxCommand.register();
         *
         * Der befehl bedient nur die Veil-pipelines, und deren registrar
         * (BBSFxPipelineRegistrar, BBSFxShadowmapPrePass, BBSFxUniformPusher) ist im
         * ausschluss-block von build.gradle stillgelegt: Veil hat keinen 26.2-bau. Ohne
         * pipelines hat der befehl nichts zu schalten.
         *
         * ZURUECK KOMMT ES MIT VEIL, NICHT DURCH PORTIEREN: Veil-abhaengigkeit anschalten,
         * die drei ausschluss-zeilen streichen, diese zeile wieder einsetzen. */
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        /* Other FX kept disabled — uncomment if/when their conflicts with
         *  Iris + shaderpack are resolved:
         *  - SupernovaEffect, NukeEffect, MeteorEffect, AntimatterEffect,
         *    CrystalEffect, RevealLightEffect, DemoSequencer
         *  - BBSFxShadowmapPrePass (primary crash suspect — does a full
         *    world re-render via VeilLevelPerspectiveRenderer.render
         *    inside Iris's render frame which clobbers the projection
         *    matrix Iris is trying to save) */
        /* One-shot cinematic perf profile: /bbstools cinematic on|off.
         *  Flips four BBSPerfConfig knobs at once (draw dist, shadow
         *  dist, LOD enable, LOD radius) so the user doesn't have to
         *  remember each value mid-shoot. */
        com.afrobama.bbstools.client.perf.CinematicProfileCommand.register();
        /* /bbstools tod <0-24000>|night|noon|dawn|dusk: instant world-time
         *  snap on the integrated-server world. Bypasses vanilla /time
         *  set's command-dispatch overhead so cinematic re-takes don't
         *  pick up 2-4 ticks of day-cycle jitter between shots. */
        com.afrobama.bbstools.client.cinematic.TimeOfDayCommand.register();
        /* Tier 1d — per-replay Crowd Mode toggle in the BBS Replays
         *  overlay panel. Adds a "Crowd Mode" checkbox next to the
         *  existing actor/fp/relative toggles so the user can flip
         *  individual replays into crowd-render mode from the editor
         *  UI without touching /bbstools crowdmode commands. */
        com.afrobama.bbstools.client.ui.ReplayCrowdModeToggle.install();

        /* /bbstools crowdmode all on|off|status: flips crowdMode flag
         *  on every replay in every active film. crowdMode replays
         *  skip server-side ActorEntity spawn (no AI/collision tick
         *  cost) AND become eligible for the Veil GPU-instanced batch
         *  path. Designed for 200+ replay cinematic crowd shots.
         *
         *  iter 82 fix: registered CLIENT-side because other BBSTools
         *  modules already register `/bbstools` as a client literal
         *  root — server-side subcommands get rejected by Fabric's
         *  client dispatcher as "Incorrect argument" and never
         *  forwarded. */
        if (com.afrobama.bbstools.client.qol.QuickWinKeybinds.DEV_ENV) com.afrobama.bbstools.client.cinematic.CrowdModeCommand.register();
        /* /bbstools fastpath on|off|status: toggle the CrowdIris
         *  Sodium fast-path (54-byte direct Iris-extended push). ON =
         *  30-40 FPS but transparent villagers (extension-byte bug
         *  unresolved). OFF = 7 FPS visible. Default OFF for safe
         *  rendering. */
        if (com.afrobama.bbstools.client.qol.QuickWinKeybinds.DEV_ENV) com.afrobama.bbstools.client.cinematic.FastPathCommand.register();
        /* /bbstools accelrender on|off|status: Tier 1a — toggle the
         *  AcceleratedRendering GPU-compute crowd path. Requires
         *  AccelRender mod installed. Default OFF until tested. */

        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        /* Tier 1 — replay fast-path measure-mode toggle. Coded by Afrobama/Claude. */
        if (com.afrobama.bbstools.client.qol.QuickWinKeybinds.DEV_ENV) com.afrobama.bbstools.client.cinematic.ReplayFastPathCommand.register();
        /* Tier 2-h — deferred GPU-instanced replay draw (AFTER_TRANSLUCENT,
         *  outside Iris gbuffers pass). Coded by Afrobama/Claude. */
        com.afrobama.bbstools.client.bbs.fastpath.Tier2hInstancedDraw.register();
        com.afrobama.bbstools.client.bbs.fastpath.CacheBarRenderer.register();
        /* Decoupled per-unique-animation baker — runs on client tick so the
         * cache fills AFK (the bypass only ran during playback). */
        com.afrobama.bbstools.client.bbs.fastpath.BackgroundBaker.register();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.ReplayFastPathDebugger.INSTANCE);
        /* Opt-in frustum cull (default OFF) — toggle via /bbsdebug frustum-cull on
         *  or http://localhost:7777/feature/frustum-cull/on. Big GPU win when a
         *  shot frames only part of the crowd (off-screen instances skip Iris's
         *  deferred shading); zero visual change. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.FrustumCullDebugger.INSTANCE);
        /* Diagnostic crowd-draw toggle — A/B the crowd's true cost at a fixed view
         *  (off = no instanced crowd, camera/world identical). */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.CrowdDrawDebugger.INSTANCE);
        /* A/B PhysicsMod's render-thread cost WITHOUT removing the jar (disabling
         *  features in PhysicsMod's GUI does NOT unload its renderMain/renderLiquid
         *  hooks). /bbsdebug physics-suppress on|off or .../feature/physics-suppress/on. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.PhysicsSuppressDebugger.INSTANCE);
        /* Whole-world-render GPU timer (GL_TIMESTAMP) — settles GPU-bound vs CPU-bound
         *  directly, since the cpu-sampler's leaf attribution is unreliable on a
         *  busy-waiting render thread. .../feature/world-gpu/dump -> worldGpuUs. */
        com.afrobama.bbstools.client.perf.WorldGpuTimer.register();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.WorldGpuDebugger.INSTANCE);
        /* Parked-editor dip fix toggle (re-apply only selected, skip 3000-crowd full). */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.ParkReapplyDebugger.INSTANCE);
        /* /bbstools crowdgpu on|off|status: Option B — GPU-instanced
         *  crowd renderer powered by iris-fork's custom crowd shader. */


        /* Tier framework registration (2026-05-17 mega-session):
         *  Wire every new tier feature's IFeatureDebugger into
         *  BBSDebugRegistry. Each gets /bbsdebug <name> for free.
         *  Skeletons + safety-default OFF until in-game verification.
         *  Some have additional commands shipped here too. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.VeilGroupAuditor.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.perf.BBSPerfHud.Debugger());
        /* Tier 4b — visible HUD renderer wired to BBSPerfHud.ENABLED.
         *  Toggle via /bbsdebug perfhud on (flips the same flag the
         *  Debugger above owns). HudRenderCallback early-returns when
         *  disabled so the OFF path is a single volatile read. */
        com.afrobama.bbstools.client.hud.BBSPerfHudRenderer.register();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.render.texture.BBSToolsTextureEnhancer.INSTANCE);
        com.afrobama.bbstools.client.render.texture.BBSToolsTextureEnhancer.register();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.audit.GlAuditRingBuffer.INSTANCE);
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.BBSEntityWorkerPool.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.BBSCoarseOccluder.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.BBSUploadThread.INSTANCE);
        /* Tier 2e DH-style upload thread — hidden 64×48 GLFW window
         *  sharing the MC main context, dedicated worker for
         *  glBufferSubData uploads. Default OFF; opt in via /bbsdebug
         *  upload-thread on. Init AFTER MC window exists (CLIENT_STARTED). */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
            .register(client ->
            {
                long handle = client.getWindow().handle();
                com.afrobama.bbstools.perf.BBSUploadThread.initOnClient(handle);
            });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
            .register(client -> com.afrobama.bbstools.perf.BBSUploadThread.shutdown());
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.AsyncMeshLoader.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.PausedReplayCache.INSTANCE);
        /* Tier 3g — paused-replay bone-matrix cache. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.PausedReplayBoneCache.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.PausedReplayMatrixCache.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.MolangComputeDispatcher.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.CrowdTextureAtlas.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.BindlessTextureRegistry.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.jfr.BBSJfrRecorder.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.shader.ShaderHotReload.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.ShadowSkipStats.INSTANCE);
        /* Tier 3f — lazy keyframe channel allocation. /bbsdebug keyframes
         *  prints the touched-vs-total ratio + heap savings ceiling. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.perf.LazyKeyframeChannel.INSTANCE);

        /* Master Debugger Module 14 — mixin conflict detector + bytecode
         *  dumper. Both default OFF (one-shot triggers, not toggles).
         *  - mixin-conflict: parses every loaded mod's *.mixins.json +
         *    ASM-reads the @Mixin classes to find cross-mod injector
         *    collisions on the same target.method. Run via /bbsdebug
         *    feature mixin-conflict on.
         *  - bytecode-dump: writes a class's pre-mixin bytecode to
         *    <instance>/bbstools_bytecode/<class>-<ts>.class so it can
         *    be inspected with `javap -c`. Run via /bbsdebug bytecode-dump
         *    <fqcn>. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.mixin.MixinConflictDetector.INSTANCE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.mixin.BytecodeDumper.INSTANCE);

        /* Master Debugger Module 18 — HTML report generator. Parses
         *  bbstools-console.log/latest.log into a single-file HTML with
         *  collapsible sections per capture/exception/dump. Easy to share
         *  with Claude (HTML can be pasted as text). Default OFF; trigger
         *  via /bbsdebug feature html-report generate (or generate-from). */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.client.debug.master.report.HtmlReportGenerator.Feature());

        /* Master Debugger Module 21 — disk + file-handle tracker. 30s
         *  background poll of <instance>/'s FileStore.getUsableSpace(),
         *  warn line when <5GB free. Pack + bbsmod tree scans on-demand
         *  via /bbsdebug feature disk-monitor packs|bbs|dump. Default
         *  ON for poll (one syscall / 30s is free), scans are explicit. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.client.debug.master.disk.DiskMonitor.Feature());
        /* Der poll-thread laesst sich nachtraeglich nicht mehr ungestartet machen — also
         * hier fragen statt ihn zu starten und im start-pass wieder abzuschalten. */
        if (com.afrobama.bbstools.client.debug.StartPositivliste.istStandardAn("disk-monitor"))
        {
            com.afrobama.bbstools.client.debug.master.disk.DiskMonitor.startPolling();
        }

        /* Master Debugger Module 22 — mod compatibility suite. Logs load
         *  order, Fabric event hook fire order, refmap status, and the
         *  full dependency graph. Helps diagnose "mod X breaks mod Y".
         *  Default-ON for the lightweight load-order capture (cached, one
         *  scan); toggling ON via /bbsdebug feature mod-compat on triggers
         *  the full dumpAll. Sub-aliases: dump | load-order | hooks <evt>
         *  | refmaps | deps (wired in BBSDebugClientCommand). */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.client.debug.master.modcompat.ModCompatDebugger.Feature());

        /* Master Debugger Module 30 — HTTP debug endpoint. Tiny JDK HttpServer
         *  on 127.0.0.1:7777 exposing /state /threads /heap /gpu /film
         *  /log/tail/N /screenshot /eval /feature/{name}[/on|/off] /triggers.
         *  Lets Claude WebFetch the live state instead of grepping logs.
         *  Bound to loopback only (security). Default ON per spec (sandbox).
         *  Conflict-detect tries 7778..7799 if port busy. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.client.debug.master.http.HttpDebugServer.Feature());
        try { com.afrobama.bbstools.client.debug.master.http.HttpDebugServer.start(); }
        catch (Throwable t) { /* log line emitted inside; never fatal */ }

        /* Master Debugger Module 26 — window + input state inspector.
         *  Lazy/on-command snapshot (cursor mode, monitor index, refresh
         *  rate, keybind conflicts, currently-held GLFW keys, mouse
         *  capture state). Default ON — ENABLED gates only whether
         *  `dump` does anything; no per-frame cost. Use
         *  /bbsdebug feature input-window dump to fire a full snapshot. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.input.InputWindowDebugger.INSTANCE);

        /* Master Debugger Module 33 — on-screen DebugOverlayHud.
         *  Top-left 10-row panel rendered via HudRenderCallback, updated
         *  every 500ms via ClientTickEvents — shows FPS, frame ms, CPU%,
         *  RAM/GC, bound GL program, render-thread state, mod count,
         *  last F11 capture age, IFeatureDebugger active count, live-log
         *  MB, HTTP API port. Default ON; F12 toggles or
         *  /bbsdebug feature debug-overlay on|off. */
        com.afrobama.bbstools.client.debug.master.hud.DebugOverlayHud.register();


        com.afrobama.bbstools.client.debug.BBSDebugClientCommand.register();

        /* Bridge legacy renderer ENABLED flags into BBSDebugRegistry so
         *  /bbsdebug crowdgpu on AND /bbstools crowdgpu on both work. */
        /* 2026-07-31 WIEDER DA. Der Aufruf war entfernt, weil LegacyRendererDebuggers vom
         * Compile ausgeschlossen ist -- und daran hingen nicht nur die drei Crowd-Schalter,
         * sondern die SAMMELANMELDUNG VON RUND 40 DEBUGGERN: eval (der JShell-Kanal, ueber den
         * unser Messwerkzeug dev.py/beweis.py laeuft), live-log, catch-track, jdwp,
         * parallel-animator. Zwei Huellen kosteten achtunddreissig Werkzeuge.
         *
         * Die zwei stehen jetzt allein in LegacyCrowdDebuggers, nur die bleibt ausgeschlossen.
         * crowdgpu und accelrender sind damit weiterhin aus -- der Rest ist zurueck. */
        com.afrobama.bbstools.client.debug.LegacyRendererDebuggers.registerAll();
        /* /bbstools sky freeze|unfreeze: locks daylight + weather cycles
         *  and forces clear sky so lighting stays constant across the
         *  dozens of takes per hero shot. Bundles two /gamerule toggles
         *  + a /weather clear into one toggle. Server-authoritative on
         *  the integrated-server side via CommandRegistrationCallback. */
        com.afrobama.bbstools.client.cinematic.SkyFreezeCommand.register();
        /* /bbstools deletegrass [radius]: clears SHORT_GRASS + TALL_GRASS
         *  in a sphere around the player. Cinematic grass is distracting
         *  in hero shots and snags PhysicsMod debris colliders around
         *  ragdoll feet — this lets the user clean a 64-256 b zone in
         *  one tick before rolling without /fill replace gymnastics. */
        com.afrobama.bbstools.client.level.DeleteGrassCommand.register();
        /* DISABLED — BBS Renewed (the renamed BBS-CML fork) ships the
         *  same editor-cam handler at source level. Running both = double
         *  cycling per F8 press. The BBS-Renewed jar's
         *  mchorse.bbs_mod.client.editorcam.EditorCamHandler owns this
         *  feature now. Re-enable only if user runs BBSTools without
         *  BBS Renewed (e.g. on stock BBS-CML). */
        if (!isBBSRenewedPresent())
        {
            com.afrobama.bbstools.client.editorcam.EditorCamHandler.register();
        }

        /* Phase 16: F7 "record from cursor" hotkey — vanilla V records
         *  from tick 0, F7 records from the editor's current scrub
         *  cursor. Works inside or outside the editor. */
        com.afrobama.bbstools.client.film.RecordFromCursorHandler.register();

        /* Phase 16b: F6 "record from current keyframes" — like F7, but
         *  teleports the player to the keyframed pose at the cursor
         *  first so the user picks up exactly where the existing
         *  animation leaves them. Outside-editor equivalent of the
         *  in-editor "Record replay (all keyframes)" mode. */
        com.afrobama.bbstools.client.film.RecordFromKeyframesHandler.register();

        /* Quick-win one-press hotkeys (F4 cinematic toggle, F5 marker-
         *  at-cursor, F2 Veil-crowd A/B). Registered via Fabric
         *  KeyBindingHelper into "category.bbstools" so the user can
         *  rebind them from vanilla MC's Controls screen. */
        com.afrobama.bbstools.client.qol.QuickWinKeybinds.register();

        /* Editor shortcuts BBS doesn't ship: Ctrl+D duplicate selected
         *  clip, Ctrl+Shift+M marker at cursor, Space play/pause,
         *  Home/End jump to film start/end. Gated on a BBS UIScreen
         *  being open + film loaded so they never trip in-world. Pure
         *  reflection so BBS-Renewed renames can't break compile. */
        com.afrobama.bbstools.client.ui.EditorShortcuts.register();

        /* Timeline: /bbstools ripple-delete — NLE-style ripple delete
         *  on the BBS film timeline. Deletes the currently selected
         *  clip and shifts every clip on every track (camera +
         *  screen + per-replay actions) that starts at or after the
         *  deleted clip's start tick LEFT by the deleted clip's
         *  duration so the gap closes. Pure reflection so it
         *  survives BBS-CML / BBS-Renewed field renames. */
        com.afrobama.bbstools.client.timeline.RippleDeleteCommand.register();

        /* Coded by Afrobama/Claude — timeline markers (scaffolding):
         *  named pins on the film timeline persisted to
         *  config/bbs/markers.json. Commands:
         *  /bbstools marker add|list|clear|jump. The planned "M"
         *  shortcut lands in a follow-up once the keybind editor's
         *  category for marker hotkeys exists — for now everything
         *  goes through the command. */

        /* (Removed 2026-05-31) The old TAB-hold "Axiom-style" block-selection mode was
         *  inaccurate/annoying and is superseded by the BBS-side WorldBlockCapture
         *  (drag-box marquee → StructureForm/BlockForm replays). Dead code deleted. */

        /* Phase 2 Editor: Power Bins folder structure. Creates default
         *  preset bins under bbs/presets/ on first launch so save-preset
         *  actions have somewhere to put files. */
        com.afrobama.bbstools.client.powerbins.PowerBins.ensureFolders();

        /* Power Bins thumbnails: 1 Hz ring-buffer capture of world-only
         *  frame, used when MASPresetIO.captureThumbnail() fires from
         *  the dashboard right-click handler (where the live framebuffer
         *  would contain UI chrome). See WorldFrameCapture javadoc. */
        com.afrobama.bbstools.client.powerbins.WorldFrameCapture.register();


        
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */

        




        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */

        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */



        com.afrobama.bbstools.client.schematic.SchematicPreview.register();


        /* Phase 2 Editor: Block Animation. Captures Block-Selection region,
         *  animates per-block transforms over time. Primary trigger via
         *  bbs:block_animation BBS clip (registered below). /bbsanim
         *  command also kept for quick out-of-editor tests. */
        com.afrobama.bbstools.client.blockanim.BlockAnimationCommands.register();
        com.afrobama.bbstools.client.blockanim.BlockAnimationRenderer.register();

        /* Block-Spawn Animation FX: per-block staggered drop-in for the
         *  1m pillager-manhunt cinematic. Pure render-side ghost overlay
         *  via LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN — does not modify
         *  world blocks. Triggered via /bbstools blockspawn <r> [dur]. */
        com.afrobama.bbstools.client.blockspawn.BlockSpawnAnimEffect.register();
        com.afrobama.bbstools.client.blockspawn.BlockSpawnAnimCommand.register();

        /* MCEF HTML-UI prototype: /bbstools web [url] opens a Chromium browser screen
         *  (renders the Claude HTML mockups 1:1). No-op-safe if the MCEF mod is absent. */
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */

        /* Register the BlockAnimation action clip type in BBS so it
         *  appears in the clip-type picker alongside other action clips. */
        try
        {
            mchorse.bbs_mod.BBSMod.getFactoryActionClips().register(
                mchorse.bbs_mod.resources.Link.bbs("block_animation"),
                com.afrobama.bbstools.client.clip.BlockAnimationClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(
                    mchorse.bbs_mod.ui.utils.icons.Icons.BLOCK, 0xffaa22));
            BBSToolsMod.LOG.info("Block-animation action clip registered (bbs:block_animation)");
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("Failed to register block_animation clip type", t);
        }

        /* Drop-in zombie renderer that honours the BBS
         *  RagdollTextureRegistry. BBS-CML already ships these for
         *  Villager and Pillager — Zombie was missing. /ragdollswap
         *  Player substitutes to Zombie and registers the player skin
         *  via the same registry, so this wrapper is what actually
         *  surfaces the player skin on the resulting PhysicsMod
         *  ragdoll. For zombies WITHOUT an override, falls through
         *  to vanilla — every other zombie in the world unaffected. */
        try
        {
            net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                net.minecraft.world.entity.EntityTypes.ZOMBIE,
                com.afrobama.bbstools.client.ragdoll.BBSTexturedZombieRenderer::new);
            BBSToolsMod.LOG.info("Registered BBSTexturedZombieRenderer — /ragdollswap Player will carry BBS skin onto the zombie ragdoll.");
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.warn("Failed to register BBSTexturedZombieRenderer: {}", t.toString());
        }

        /* MAS/Veil Phase 0 — Veil feature-gate. Detects Veil + watched
         *  mods at boot, logs full diagnostic banner, runs sanity
         *  probes on key Veil API classes. Foundation for every Veil-
         *  dependent feature we'll register downstream. Per project
         *  policy: log-first so compat issues surface in
         *  bbstools-console.log on first launch, not after debugging. */
        try
        {
            /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("VeilFeatureGate.init failed — Veil features will be unavailable. {}", t.toString(), t);
        }

        /* MAS/Veil Phase 1 — first user-visible feature: bloom toggle.
         *  Activates Veil's built-in veil:core/bloom post-processing
         *  pipeline via /mas bloom on. Proof of end-to-end integration. */
        try
        {
            /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("MASCommands.register failed: {}", t.toString(), t);
        }

        /* MAS/Veil Phase 5b — per-frame uniform pusher. Veil 4.0.1 has
         *  no auto-bound time uniform; we push MAS_GameTime ourselves
         *  before each animated pipeline runs so supernova / black-
         *  hole / grain / flare can animate. */
        try
        {
            /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("MASShaderUniformPusher.register failed: {}", t.toString(), t);
        }

        /* Auto-enable destroying-mc-style vibrancy (bloom_dm + post) on
         * world join. Mirrors destroying-mc's ShaderType.CRACKS default. */
        try
        {
            /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("MASAutoEnableVibrancy.register failed: {}", t.toString(), t);
        }

        /* In-game log console: install Log4j ring-buffer appender FIRST
         *  so it captures every event from now on, then register the
         *  F12 toggle handler. */
        com.afrobama.bbstools.client.console.LogConsoleBuffer.install();

        /* Master Debugger Module 24 — system-wide live log interceptor.
         *  Installs a separate Log4j AbstractAppender that captures every
         *  SLF4J/Log4j event from every mod, filters to our preferred tag
         *  set + bbstools/* loggers, and writes them to a dedicated file
         *  at <instance>/bbstools_debug_live.log. Default ON so Claude has
         *  a clean grep target. Installed AFTER LogConsoleBuffer so both
         *  ring-buffer + live file capture in parallel. */
        com.afrobama.bbstools.client.debug.master.log.LiveLogAppender.install();

        /* Master Debugger Module 31 — crash-report enrichment + GL fence
         *  monitor. Both DEFAULT ON; both expose IFeatureDebugger so
         *  `/bbsdebug crash-enrich` and `/bbsdebug gl-fence` work.
         *  - 31a (crash-enrich): MixinCrashReport.<init> adds a "BBSTools
         *    Debug State" section to every CrashReport. Zero overhead
         *    until a crash fires. Section pulls last-100 live-log lines,
         *    GL ring state, all feature toggles, BBS film summary.
         *  - 31b (gl-fence): wraps glFenceSync / glClientWaitSync calls
         *    so we can spot CPU stalls on GPU. Callers route through
         *    GlFenceMonitor.recordInsert / recordWait. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.crash.CrashEnricher.FEATURE);
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.gl.GlFenceMonitor.FEATURE);

        /* Master Debugger Module 35 — inline tracepoint via persisted
         *  config (Instrumentation API fallback). User issues
         *  /bbsdebug feature tracepoint add <class.method>; if the target
         *  matches one of MethodTracer's 5 pre-baked keys it activates
         *  immediately, otherwise it's persisted to
         *  <instance>/bbstools_tracepoints.json and re-applied on next
         *  launch (with a clear "needs -javaagent" notice). DEFAULT ON
         *  — only reads the config file once, no per-frame cost. */
        com.afrobama.bbstools.client.debug.master.tracepoint.TracepointManager.loadConfig();
        com.afrobama.bbstools.client.debug.master.tracepoint.TracepointManager.applyAll();
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.tracepoint.TracepointManager.FEATURE);

        /* Master Debugger Module 34 — time-series metrics DB.
         *  In-memory 1-hour ring per metric (3600 samples × 1Hz × 12 metrics
         *  = ~340 KB). Daemon thread polls existing module APIs (fps, frame-time,
         *  cpu%, heap, gc, draws...) every 1s. Exposes HTTP endpoints
         *  /metrics/all, /metrics/{name}, /metrics/{name}.csv so Claude can
         *  WebFetch trend data and compare "before bug" vs "after bug".
         *  Registered AFTER HttpDebugServer (line ~306) so its
         *  installHttpEndpoints() reflective hook finds a live server.
         *  DEFAULT ON. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.client.debug.master.timeseries.TimeSeriesMetrics.Feature());
        /* enable() hier entfernt (2026-07-28): der start-pass entscheidet. Sonst laeuft der
         * 1-Hz-poll-thread ab mod-init und wird eine sekunde spaeter wieder abgeschaltet. */

        /* Master Debugger Module 38 — Jaeger-style distributed tracing.
         *  Lock-free 1000-span rolling buffer + ThreadLocal stack for
         *  auto-nested parents. Spans are AutoCloseable: try-with-resources
         *  is the canonical pattern. Registered AFTER HttpDebugServer so
         *  its installHttpEndpoints() reflective hook adds /traces to the
         *  live server. DEFAULT ON; overhead is ~200-400ns per traced span,
         *  ~10ns when disabled (single volatile read short-circuits). */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            new com.afrobama.bbstools.client.debug.master.tracing.SpanTracer.Feature());
        /* enable() hier entfernt (2026-07-28) — start-pass entscheidet. */

        /* Master Debugger Module 39 — time-travel debugger (state snapshot
         *  rewind). NOT rr-style instruction-level replay (impossible
         *  without -agentlib): instead a daemon thread captures a frozen
         *  view of every BBSPerfConfig field + headline runtime counters
         *  every 10s into a rolling 360-slot ring. 1 hour of history at
         *  ~1.8 MB heap. User can `/bbsdebug feature time-travel diff 0 30`
         *  to see what flag changed in the last 5 minutes. Registered
         *  AFTER HttpDebugServer so its installHttpEndpoints() reflective
         *  hook adds /time-travel/* to the live server. DEFAULT ON. */
        com.afrobama.bbstools.client.debug.BBSDebugRegistry.register(
            com.afrobama.bbstools.client.debug.master.timetravel.StateTimeMachine.FEATURE);
        /* enable() hier entfernt (2026-07-28) — start-pass entscheidet. Der snapshot-thread
         * lief sonst ab mod-init und schrieb alle 10 s in einen 360-slot-ring. */

        /* Start-zustand der debugger. DEFERRED auf CLIENT_STARTED — einige
         *  Feature.setEnabled-impls fassen GL-zustand an (bindless-probe, GLFW
         *  input-grab), der beim ModInitializer noch nicht existiert (r36: das
         *  hat den boot zerlegt).
         *
         *  ⛔ NICHT MEHR "alles an ausser verbotsliste" (2026-07-28): das hatte 57
         *  von 87 debuggern scharf, kostete gemessen ~136 ms voll-GC pro minute und
         *  flutete das log so, dass ein crash-fenster nichts mehr hergab. Jetzt
         *  entscheidet die POSITIVLISTE in StartPositivliste — begruendung und die
         *  liste selbst stehen dort, damit sie an einer stelle wachsen. */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
            .register(client ->
            {
                try
                {
                    com.afrobama.bbstools.client.debug.StartPositivliste.anwenden();
                }
                catch (Throwable t)
                {
                    BBSToolsMod.LOG.error("[master-debug] start-zustand fehlgeschlagen", t);
                }

                /* JFR-dauer-aufnahme: seit 2026-07-28 nur noch auf ausdruecklichen wunsch.
                 * Sie schaltet die erweiterte klassen-redefinition der JVM ab (gemessen),
                 * und damit strukturelles hotswap — begruendung in JfrAutostart. */
                com.afrobama.bbstools.client.debug.master.jfr.JfrAutostart.starteWennErlaubt();
            });

        com.afrobama.bbstools.client.console.LogConsoleHandler.register();
        com.afrobama.bbstools.client.fracture.GravityGunHandler.register();
        com.afrobama.bbstools.client.fracture.GravityGunHud.register();
        com.afrobama.bbstools.client.fracture.BlockDestructionWireframe.register();

        /* Coded by Afrobama/Claude — Camera Preview Phase 1-3 ported
         *  from BBS-Renewed source. World-render-to-framebuffer per
         *  preview, draggable/resizable Blender-style windows in the
         *  dashboard, tracker-mode following Replay keyframes. */
        com.afrobama.bbstools.client.campreview.CameraPreviewRegistry.register();
        /* Phase 10 redesign: clip-driven viewports replace this */
        /* com.afrobama.bbstools.client.campreview.CameraPreviewCommands.register(); */
        /* Phase 10 redesign: clip-driven viewports replace this */
        /* com.afrobama.bbstools.client.campreview.CameraReplayWatcher.register(); */

        /* Resolve F8 conflict — BBS's axes-toggle moves to Ctrl+F8 so
         *  our F8 editor-cam can take the bare F8 binding without the
         *  dashboard silently flipping axes off when the user presses F8. */
        /* Deferred to the first client tick — touching BBS Keys at init poisons UIKeys against
         * this BBS-CML jar (L10n not up yet); see AxesToggleKeyFix.register(). */
        com.afrobama.bbstools.client.keybinds.AxesToggleKeyFix.register();

        /* Register the fracture action clip type. Both data class
         *  (server-side bbs:model_fracture in factoryActionClips) and
         *  client subclass (UIClip mapping for the editor inspector)
         *  must be registered. */
        try
        {
            mchorse.bbs_mod.BBSMod.getFactoryActionClips().register(
                mchorse.bbs_mod.resources.Link.bbs("model_fracture"),
                com.afrobama.bbstools.client.fracture.ModelFractureClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(
                    mchorse.bbs_mod.ui.utils.icons.Icons.BLOCK, 0xff6633));

            mchorse.bbs_mod.ui.film.clips.UIClip.register(
                com.afrobama.bbstools.client.fracture.ModelFractureClientClip.class,
                com.afrobama.bbstools.client.fracture.UIModelFractureClip::new);

            BBSToolsMod.LOG.info("Fracture action clip registered (bbs:model_fracture)");
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("Failed to register fracture clip type", t);
        }

        /* Register the physics-block-destruction clip type. Lets the
         *  user mark a region of MC blocks + an impact point and
         *  destroys them via PhysicsMod debris on clip start. */
        try
        {
            mchorse.bbs_mod.BBSMod.getFactoryActionClips().register(
                mchorse.bbs_mod.resources.Link.bbs("physics_block_destruction"),
                com.afrobama.bbstools.client.fracture.PhysicsBlockDestructionClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(
                    mchorse.bbs_mod.ui.utils.icons.Icons.BLOCK, 0xff9900));

            mchorse.bbs_mod.ui.film.clips.UIClip.register(
                com.afrobama.bbstools.client.fracture.PhysicsBlockDestructionClientClip.class,
                com.afrobama.bbstools.client.fracture.UIPhysicsBlockDestructionClip::new);

            BBSToolsMod.LOG.info("Block-destruction clip registered (bbs:physics_block_destruction)");
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("Failed to register block-destruction clip type", t);
        }

        /* Register the entity-proxy ragdoll clip type. Spawns +
         *  kills an invisible Villager at the actor's position to
         *  trigger PhysicsMod's ragdoll system; our hook captures
         *  the ragdoll and overrides BBS bone matrices each frame. */
        try
        {
            mchorse.bbs_mod.BBSMod.getFactoryActionClips().register(
                mchorse.bbs_mod.resources.Link.bbs("model_ragdoll_proxy"),
                com.afrobama.bbstools.client.fracture.ModelRagdollProxyClientClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(
                    mchorse.bbs_mod.ui.utils.icons.Icons.LIMB, 0xff5577));

            mchorse.bbs_mod.ui.film.clips.UIClip.register(
                com.afrobama.bbstools.client.fracture.ModelRagdollProxyClientClip.class,
                com.afrobama.bbstools.client.fracture.UIModelRagdollProxyClip::new);

            BBSToolsMod.LOG.info("Ragdoll-proxy clip registered (bbs:model_ragdoll_proxy)");
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("Failed to register ragdoll-proxy clip type", t);
        }

        /* Coded by Afrobama/Claude — Phase 10: register the new
         *  "Add Camera" CAMERA-clip type. Same properties as
         *  TrackerClip (target replay + offset + angle etc.) but a
         *  distinct class identity so Phase 11+ can pick it out of the
         *  active-clip list and drive a SEPARATE viewport instead of
         *  modifying the main camera. */
        try
        {
            mchorse.bbs_mod.BBSMod.getFactoryCameraClips().register(
                mchorse.bbs_mod.resources.Link.bbs("bbstools_add_camera"),
                com.afrobama.bbstools.clip.BBSToolsAddCameraClip.class,
                new mchorse.bbs_mod.camera.clips.ClipFactoryData(
                    mchorse.bbs_mod.ui.utils.icons.Icons.CAMERA, 0x4ec1ff));

            mchorse.bbs_mod.ui.film.clips.UIClip.register(
                com.afrobama.bbstools.clip.BBSToolsAddCameraClip.class,
                (clip, editor) -> new com.afrobama.bbstools.client.clip.UIBBSToolsAddCameraClip(
                    (com.afrobama.bbstools.clip.BBSToolsAddCameraClip) clip, editor));

            /* AUTONOMIE 2026-07-27: UI-editor fuer den damage-hitbox-clip. Der clip-typ
             * selbst wird serverseitig in BBSToolsMod registriert; ohne DIESE zeile gaebe
             * es ihn zwar in der clip-liste, aber ohne editor-panel. */
            mchorse.bbs_mod.ui.film.clips.UIClip.register(
                com.afrobama.bbstools.hitbox.DamageHitboxClip.class,
                (clip, editor) -> new com.afrobama.bbstools.client.hitbox.UIDamageHitboxClip(
                    (com.afrobama.bbstools.hitbox.DamageHitboxClip) clip, editor));

            BBSToolsMod.LOG.info("Add Camera clip registered (bbs:bbstools_add_camera)");
        }
        catch (Throwable t)
        {
            BBSToolsMod.LOG.error("Failed to register Add Camera clip type", t);
        }

        /* Phase 11: aktive Add-Camera-Clips der laufenden Film-Timeline; Phase 12s
         *  Pre-Pass-Mixin liest die Liste je Frame (ein Viewport je aktivem Clip). */
        com.afrobama.bbstools.client.campreview.ActiveCameraClipRegistry.register();
        com.afrobama.bbstools.client.campreview.ClipViewportFramebuffers.register();
        com.afrobama.bbstools.client.campreview.CameraFrustumMeshRenderer.register();
        com.afrobama.bbstools.client.campreview.CameraMeshToggleHandler.register();

        /* Per-tick polling for the rewind detector + collision/velocity
         *  watchers. Uses the END_CLIENT_TICK event so the film
         *  controllers have already updated their tick counter for
         *  this game tick by the time we read it. */
        final int[] lastTickHolder = { -1 };
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
        {
            try
            {
                java.util.List<mchorse.bbs_mod.film.BaseFilmController> controllers =
                    mchorse.bbs_mod.BBSModClient.getFilms().getControllers();

                int representativeTick = 0;

                for (mchorse.bbs_mod.film.BaseFilmController controller : controllers)
                {
                    int tick = controller.getTick();
                    representativeTick = tick;

                    /* Per-actor rewind detection. Iterate the controller's
                     *  entities (IntObjectMap, not List) and forward
                     *  to FractureRegistry. */
                    Object entitiesMap = controller.getEntities();
                    if (entitiesMap != null)
                    {
                        try
                        {
                            java.lang.reflect.Method valuesMethod = entitiesMap.getClass().getMethod("values");
                            Object values = valuesMethod.invoke(entitiesMap);
                            if (values instanceof Iterable<?> iter)
                            {
                                for (Object actor : iter)
                                {
                                    if (actor instanceof mchorse.bbs_mod.forms.entities.IEntity ie)
                                    {
                                        com.afrobama.bbstools.client.fracture.FractureRegistry.onPlaybackTick(ie, tick);
                                    }
                                }
                            }
                        }
                        catch (Throwable ignored)
                        {
                            /* IntObjectMap API may differ; skip rewind detect for this controller. */
                        }
                    }
                }

                /* Drive the collision and velocity-drop watchers with the
                 *  most recent representative tick. Watchers are tied to
                 *  the replay they were started with, so the tick they
                 *  see is approximate but good enough for cinematic
                 *  fracture timing. */
                com.afrobama.bbstools.client.fracture.CollisionWatcher.onClientTick(representativeTick);
                com.afrobama.bbstools.client.fracture.VelocityDropWatcher.onClientTick(representativeTick);

                /* Track latest tick but DO NOT auto-restore on rewind.
                 *
                 * Removed the auto-restore path (was: restore-all + clear-events
                 * whenever representativeTick < lastTickHolder). It triggered on
                 * every loop-back of the film (tick 47 → 0 every ~2.4 sec) which
                 * caused the destroyed wall to repeatedly re-appear, then re-fire
                 * at the clip's start tick spawned a fresh batch of debris on top.
                 * Net effect: wall stayed visually intact most of the time while
                 * debris piled up = user-reported "structure still standing but
                 * tons of duplicate blocks shot in front".
                 *
                 * Restoration on retake is still handled by the clip's own
                 * applyClientAction (calls BlockSnapshotRegistry.restoreAll +
                 * BlockShatterPropagator.clearAll on every refire). That keeps
                 * "press play again to retake" working without the loop-induced
                 * pulsing of the original blocks.
                 *
                 * For explicit cleanup outside playback the user has the
                 * /bbstools fracture-help command + world unload restore. */
                lastTickHolder[0] = representativeTick;

                /* Phase B: integrate every active bone-fracture body
                 *  one tick (gravity, damping, world-block collision,
                 *  rotation tumble). Cheap; runs zero work when no
                 *  fracture is active. */
                com.afrobama.bbstools.client.fracture.BonePhysicsSimulator.onClientTick();

                /* Impact-point block-shatter propagation — radial wave
                 *  expansion from impact origin. Cheap; no work when
                 *  no events active. */
                com.afrobama.bbstools.client.fracture.BlockShatterPropagator.onClientTick();

                /* Drive the post-fire body-count diagnostic — fires
                 *  delayed reports scheduled by the destruction clip. */
                com.afrobama.bbstools.client.fracture.BlockShatterPropagator.tickBodyCountReports();
            }
            catch (Throwable t)
            {
                /* Defensive: never let a fracture-tick exception nuke
                 *  the entire client tick loop. Log and move on. */
                BBSToolsMod.LOG.debug("Fracture tick hook error: {}", t.toString());
            }
        });

        /* Helper for both CLIENT_STOPPING (game closes) and DISCONNECT
         *  (user leaves a world but keeps the game running) so state
         *  doesn't leak across worlds. Without the disconnect path,
         *  rejoining a world would inherit stale fracture / snapshot /
         *  watcher entries from the previous session. */
        Runnable wipeFractureState = () ->
        {
            com.afrobama.bbstools.client.fracture.FractureRegistry.clearAll();
            com.afrobama.bbstools.client.fracture.CollisionWatcher.clearAll();
            com.afrobama.bbstools.client.fracture.VelocityDropWatcher.clearAll();
            com.afrobama.bbstools.client.fracture.FracturedBoneRegistry.clearAll();
            com.afrobama.bbstools.client.fracture.FractureRenderContext.reset();
            com.afrobama.bbstools.client.fracture.FracturePhysicsBridge.despawnAll();
            com.afrobama.bbstools.client.fracture.CameraShakeManager.clear();
            com.afrobama.bbstools.client.fracture.GravityGunHandler.clear();
            com.afrobama.bbstools.client.fracture.BlockShatterPropagator.clearAll();
            /* Drop snapshots without trying to restore — the world is
             *  unloading so setBlockState would fail anyway. The next
             *  client load will see an unmodified world. */
            com.afrobama.bbstools.client.fracture.BlockSnapshotRegistry.dropAll();
            com.afrobama.bbstools.client.fracture.BlockDestructionWireframe.clearAll();
        };

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
            .register(client -> wipeFractureState.run());
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
            .register((handler, client) -> wipeFractureState.run());
    }

    /** True if BBS Renewed (our fork of BBS) is loaded. We detect the
     *  presence of BBS Renewed's source-level editor-cam handler so we
     *  can stand down our own duplicate. The class only exists in the
     *  Renewed fork — stock BBS-CML won't have it. */
    private static boolean isBBSRenewedPresent()
    {
        try
        {
            Class.forName("mchorse.bbs_mod.client.editorcam.EditorCamHandler", false,
                BBSToolsModClient.class.getClassLoader());
            return true;
        }
        catch (Throwable ignored) { return false; }
    }

}
