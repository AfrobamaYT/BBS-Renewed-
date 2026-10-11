// Coded by Afrobama/Claude — BBS-side addon entrypoint for BBSTools-Fabric.
// Hooks into BBS's BBSClientAddon mechanism (separate from our Fabric
// ClientModInitializer) so we can register dashboard panels, form
// editors, and other BBS-specific things via BBS's own event bus.
//
// 2026-05-16: added action-clip registration hook for WorldSnapshotClip
// (scaffold). The override is reflection-driven so the file keeps
// compiling even if a particular BBS build doesn't ship
// RegisterActionClipsEvent at the expected package — see
// WorldSnapshotClipFactory.tryReflectiveRegister() for details.
package com.afrobama.bbstools.client.addon;

import com.afrobama.bbstools.clips.WorldSnapshotClipFactory;

import mchorse.bbs_mod.events.register.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.events.register.RegisterL10nEvent;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* AUTONOMIE 2026-07-27: erbt NICHT mehr von BBSClientAddon. Der fs-kern hat kein
 * addon-plugin-system (kein mchorse.bbs_mod.addons-paket) — er registriert alles direkt
 * in BBSModClient und postet dafuer genau 5 events ueber BBSMod.events. Wir brauchen
 * davon zwei, und die haengen ohnehin an @Subscribe, nicht an der basisklasse. Also
 * plain subscriber: BBSMod.events.register(new BBSToolsAddon()) beim client-start. */
public class BBSToolsAddon
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/addon");

    /** Re-declared for the same reason as onRegisterL10n — see its comment (EventBus uses
     *  getDeclaredMethods, so inherited @Subscribe dispatchers are invisible to it). */
    @mchorse.bbs_mod.events.Subscribe
    public void onRegisterDashboardPanels(RegisterDashboardPanelsEvent event)
    {
        this.registerDashboardPanels(event);
    }

    private void registerDashboardPanels(RegisterDashboardPanelsEvent event)
    {
        /* Piggyback: registerDashboardPanels fires once on BBS boot and
         *  is the safest hook we already override. We use it to confirm
         *  WorldSnapshotClip's factory is reachable. Real factory
         *  registration is performed by BBS via the typed event below
         *  (added once we verify RegisterActionClipsEvent signature). */
        WorldSnapshotClipFactory.tryReflectiveRegister();
        mchorse.bbs_mod.ui.film.clips.UIClip.register(com.afrobama.bbstools.clips.WorldSnapshotClip.class,
            com.afrobama.bbstools.client.clip.UIWorldSnapshotClip::new);
        com.afrobama.bbstools.client.clip.LegacyActionClips.register();
    }

    /**
     * Restores BBS setting LABELS that vanished with the CML source patches.
     *
     * WHY: the fork's own editor settings (GPU batching toggles, FPS counter, render-entities,
     * replay HUD, FP bobbing, …) had their BBSSettings fields AND their lang labels added as a
     * direct source patch to CML's own en_us.json in the pre-alpha jar. The beta-2 upgrade kept
     * the FIELDS (measured: BBSSettings still registers instanced_block_replays / fps_counter /
     * render_entities / replay_hud etc.), but the clean upstream jar has none of the LABELS — so
     * those options showed with raw keys / effectively invisible (user: typing "gpu" in settings
     * used to surface 4 options, now none).
     *
     * BBS builds a setting's lang key dynamically as "bbs.config.<category>.<name>", so the fix is
     * purely to supply those keys again. We register a companion BBS-lang file
     * (assets/bbstools/strings/<lang>.json) carrying the 108 keys recovered from the pre-alpha jar.
     * BBS's L10n merges every registered lang file, so our keys fill the gaps in BBS's own en_us
     * without touching BBS source. Orphan keys (features not in beta-2) are harmless — never
     * looked up.
     */
    /**
     * MUST be re-declared here, not just inherited.
     *
     * BBS's EventBus.register(Object) iterates subscriber.getClass().getDeclaredMethods() — DECLARED,
     * not getMethods(). The @Subscribe-annotated dispatchers (onRegisterL10n, onRegisterDashboardPanels,
     * …) live on the BBSClientAddon BASE class, so a subclass that only overrides the protected
     * register* hooks contributes ZERO handlers: measured live, EventBus.subscribers was map(0) and
     * none of this addon's hooks ever fired. Re-declaring the @Subscribe dispatcher on this class
     * puts it in getDeclaredMethods() and the bus picks it up.
     */
    @mchorse.bbs_mod.events.Subscribe
    public void onRegisterL10n(RegisterL10nEvent event)
    {
        this.registerL10n(event);
    }

    private void registerL10n(RegisterL10nEvent event)
    {
        try
        {
            int added = com.afrobama.bbstools.client.l10n.MissingSettingLabels.inject(event.l10n);

            LOG.info("Injected {} BBS setting labels lost in the CML upgrade.", added);
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to inject BBSTools setting labels: {}", t.toString(), t);
        }
    }

    /* ------------------------------------------------------------------
     * Action-clip registration hook for WorldSnapshotClip.
     *
     * Uncomment this block AFTER verifying that
     *   mchorse.bbs_mod.events.register.RegisterActionClipsEvent
     * exists with `getFactory()` returning the
     * MapFactory<Link, ? extends ActionClip> referenced in
     * FRACTURE_PLAN.md line 96. The method name MUST match BBSClientAddon's
     * abstract / overridable hook — likely `registerActionClips` based
     * on the parallel with registerDashboardPanels.
     *
     * @Override
     * protected void registerActionClips(RegisterActionClipsEvent event)
     * {
     *     WorldSnapshotClipFactory.register(event);
     *     // Also register ModelFractureActionClip, BlockAnimationActionClip,
     *     // PhysicsBlockDestructionActionClip, DamageHitboxClip here when
     *     // their FRACTURE_PLAN.md TODO is closed.
     * }
     * ----------------------------------------------------------------- */

}
