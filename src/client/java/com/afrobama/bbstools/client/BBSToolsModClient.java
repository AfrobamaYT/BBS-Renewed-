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


        /* AUTONOMIE 2026-07-27: BBSToolsAddon von hand am event-bus anmelden.
         * Frueher lief das ueber BBS' custom-entrypoint "bbs-addon-client" (CML-fork), das
         * es im fs-kern nicht gibt — fs postet seine 5 register-events einfach ueber
         * BBSMod.events. Ohne diese zeile wird kein einziger @Subscribe-hook je gerufen
         * (dashboard-panel + sprachdatei fehlen dann still). */
        mchorse.bbs_mod.BBSMod.events.register(new com.afrobama.bbstools.client.addon.BBSToolsAddon());



        /* Per-tick heartbeat pulse — tells external watchers MC's tick
         *  thread is still alive. Cheap (single AtomicLong store). */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
            client ->
            {
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
         * WorldRenderEvents.END_MAIN feuert pro welt-render, auch bei offenem screen. Damit
         * zaehlt die id im editor wieder mit, das gate ist wieder self-gating wie gedacht.
         *
         * ⚠ Iris/shaderpacks koennen die welt mehrfach pro frame zeichnen (schatten-pass).
         * Mehr id-spruenge sind harmlos: das gate wird dadurch nur DURCHLAESSIGER (mehr
         * ticks laufen durch), nie strenger — es kann also nie wieder transforms verschlucken.
         */
        // PHASE NICHT BELEGT GLEICHWERTIG: alt war LAST/END/AFTER_SETUP. 26.2 hat dafuer kein benanntes Gegenstueck -- ob diese Phase an derselben Stelle liegt, ist am laufenden Bild zu pruefen (Export gegen Fenster).
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_MAIN.register(
            kontext ->
            {
                com.afrobama.bbstools.perf.BBSPerfConfig.bbstoolsRenderFrameId++;
            });


        /* UI Overhaul Phase 1: editor transparency keybind (Ctrl+Alt+T). */
        com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency.register();
        BBSToolsMod.LOG.info("UI Overhaul ready — theme={}, mixins=[toggle, button, scrollbar, icon, bg-alpha]",
            com.afrobama.bbstools.client.ui.theme.BBSToolsBrand.NAME);
        BBSToolsMod.LOG.info("UI Overhaul shortcuts: Ctrl+Alt+T transparency · Ctrl+, settings · Ctrl+H undo · Ctrl+Shift+Z/Y undo/redo step · Shift+F9 keybind editor");
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
        /* ⛔ P3-26.2-AUS — /bbstools accelrender ist AUS (massen-crowd ueber den
         * GPU-compute-pfad der fremd-mod AcceleratedRendering).
         * Hier stand:  com.afrobama.bbstools.client.cinematic.AccelRenderCommand.register();
         *
         * Der befehl schaltet BBSCrowdAcceleratedRenderer.ENABLED, und dieser renderer ist
         * im ausschluss-block von build.gradle stillgelegt — er uebersetzt gegen die
         * AcceleratedRendering-API, die es fuer 26.2 nicht gibt.
         *
         * ZURUECK KOMMT ES MIT DER ABHAENGIGKEIT, NICHT DURCH PORTIEREN: AccelRender-mod
         * fuer 26.2 einbinden, ausschluss von BBSCrowdAcceleratedRenderer +
         * mixin/accelrender/** streichen, diese zeile wieder einsetzen. */
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
        /* Whole-world-render GPU timer (GL_TIMESTAMP) — settles GPU-bound vs CPU-bound
         *  directly, since the cpu-sampler's leaf attribution is unreliable on a
         *  busy-waiting render thread. .../feature/world-gpu/dump -> worldGpuUs. */
        com.afrobama.bbstools.client.perf.WorldGpuTimer.register();
        /* Tier 4b — visible HUD renderer wired to BBSPerfHud.ENABLED.
         *  Toggle via /bbsdebug perfhud on (flips the same flag the
         *  Debugger above owns). HudRenderCallback early-returns when
         *  disabled so the OFF path is a single volatile read. */
        com.afrobama.bbstools.client.hud.BBSPerfHudRenderer.register();
        com.afrobama.bbstools.client.render.texture.BBSToolsTextureEnhancer.register();
        /* Tier 2e DH-style upload thread — hidden 64×48 GLFW window
         *  sharing the MC main context, dedicated worker for
         *  glBufferSubData uploads. Default OFF; opt in via /bbsdebug
         *  upload-thread on. Init AFTER MC window exists (CLIENT_STARTED). */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED
            .register(client ->
            {
                if (mchorse.bbs_mod.graphics.GpuBackend.isOpenGl())
                {
                    long handle = client.getWindow().handle();
                    com.afrobama.bbstools.perf.BBSUploadThread.initOnClient(handle);
                }
            });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING
            .register(client -> com.afrobama.bbstools.perf.BBSUploadThread.shutdown());









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

        /* Iris Path-A (CrowdBOBJVanilla) DISABLED — produced only depth
         *  outlines under shaders (Iris's gbuffers_entities patcher
         *  generated shadow/outline silhouette but main color was lost).
         *  Path B (raw-GL inside CrowdBOBJGL with GbufferPrograms phase
         *  tagging) handles both shader-on and shader-off cases now.
         *  Re-enable when Path A's color path is debugged. */
        // com.afrobama.bbstools.client.crowd.mass.CrowdBOBJVanilla.register();

        /* CrowdIrisWrappedRenderer — fresh take on Path-A. Same
         *  InnerWrappedRenderType + EntityRenderStateShard pattern but
         *  now uses real per-vertex block+sky-light (D5 fix). Without
         *  that fix Iris's patched gbuffers_entities shader emitted
         *  only depth/shadow data → user saw outlines only ("ghost
         *  silhouettes"). Pull-driven: tryDraw() called from
         *  CrowdInstancedRenderer's path-ladder, no separate
         *  WorldRenderEvents hook. Activates only when Iris is loaded
         *  + a shaderpack is active. */
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */
        /* iter 84 (2026-05-17): GPU-instanced crowd renderer that routes
         *  through the companion Iris fork's BBSTOOLS_CROWD_ENTITIES
         *  ShaderKey. ONE glDrawElementsInstanced for all visible mobs
         *  through the shaderpack-patched gbuffers_entities. On stock
         *  Iris (no fork installed) tryDraw returns false and the ladder
         *  falls through to CrowdIrisWrappedRenderer as before. */
        /* P3-26.2-AUS: fremd-mod ohne 26.2-bau, siehe ausschluss-block in build.gradle. */


        /* Phase 2 Editor: Block Animation. Captures Block-Selection region,
         *  animates per-block transforms over time. Primary trigger via
         *  bbs:block_animation BBS clip (registered below). /bbsanim
         *  command also kept for quick out-of-editor tests. */
        com.afrobama.bbstools.client.blockanim.BlockAnimationCommands.register();
        com.afrobama.bbstools.client.blockanim.BlockAnimationRenderer.register();

        /* Block-Spawn Animation FX: per-block staggered drop-in for the
         *  1m pillager-manhunt cinematic. Pure render-side ghost overlay
         *  via WorldRenderEvents.END_MAIN — does not modify
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
                net.minecraft.world.entity.EntityType/*s*/.ZOMBIE,
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





        /* enable() hier entfernt (2026-07-28) — start-pass entscheidet. Der snapshot-thread
         * lief sonst ab mod-init und schrieb alle 10 s in einen 360-slot-ring. */


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
