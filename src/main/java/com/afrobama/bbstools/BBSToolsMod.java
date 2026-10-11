package com.afrobama.bbstools;

import com.afrobama.bbstools.clip.BlockAnimationActionClip;
import com.afrobama.bbstools.clips.WorldSnapshotClip;
import com.afrobama.bbstools.fracture.ModelFractureActionClip;
import com.afrobama.bbstools.fracture.ModelRagdollProxyActionClip;
import com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.utils.factory.MapFactory;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server/common entry point for BBSTools. Most behaviour is wired via
 * Mixins (see {@code bbstools.mixins.json}); this class just announces
 * presence and is the place to register commands once they're migrated.
 */
public final class BBSToolsMod implements ModInitializer
{
    public static final String MOD_ID = "bbstools";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize()
    {
        LOG.info("BBSTools (Fabric) initializing — companion mod for BBS-CML-EDITION");

        /* Load the user-tweakable perf config (or write defaults on
         *  first run). Done before everything else so subsequent
         *  registrations see the configured values. */
        com.afrobama.bbstools.config.BBSToolsConfig.loadOrCreate();

        registriereActionClips();

        /* Custom-payload registration + server handler for
         *  BuilderActionPacket. Must register codec before any world loads. */
        com.afrobama.bbstools.network.BBSToolsNetworkInit.init();

        /* AUTONOMIE 2026-07-27: damage-hitbox (keyframed schaden-AABB fuer kampf-animationen).
         * Gleiches muster wie BuildSchematic drueber — register() haengt sich selbst an
         * CommandRegistrationCallback + END_SERVER_TICK. Der aufruf fehlte schlicht, damit
         * war das ganze paket (664z + clip + UI-editor) unerreichbar. */
        com.afrobama.bbstools.hitbox.DamageHitbox.register();
        com.afrobama.bbstools.ragdoll.RagdollSwap.register();

        /* /bbstools needs an explicit dispatcher pass-through. */
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, env) ->
            {
                BBSToolsStatusCommand.register(dispatcher);
            });

        LOG.info("BBSTools commands registered: /ragdolly");
    }

    /**
     * Unsere action-clips in BBS' clip-fabrik eintragen.
     *
     * AUTONOMIE 2026-07-27: diese fuenf clips waren FERTIG GEBAUT (vier davon mit eigenem
     * client-clip/UI-editor) und trotzdem im spiel nicht vorhanden — sie standen in keiner
     * registrierung. Im CML-fork sollte das ueber ein RegisterActionClipsEvent laufen, das
     * es dort nie gab (nur als kommentar in BBSToolsAddon: "register ... here when the event
     * exists"). fs registriert seine action-clips direkt in BBSMod:461; getFactoryActionClips()
     * ist public und MapFactory.register() auch, also tragen wir uns nach dessen aufbau ein.
     *
     * Reihenfolge ist sicher: BBS' entrypoint steht in fabric.mod.json vor unserem, die
     * fabrik existiert also bereits. Falls sich das je aendert, faellt es hier sofort auf
     * (NPE statt stiller nicht-registrierung) — genau das ist gewollt.
     */
    private static void registriereActionClips()
    {
        MapFactory<Clip, ClipFactoryData> fabrik = BBSMod.getFactoryActionClips();

        if (fabrik == null)
        {
            LOG.error("BBS' action-clip-fabrik ist null — unsere clips bleiben unregistriert. "
                + "Entrypoint-reihenfolge in fabric.mod.json pruefen (bbs muss vor bbstools stehen).");

            return;
        }

        BBSMod.getFactoryCameraClips().register(Link.create("bbstools:damage_hitbox"),
            com.afrobama.bbstools.hitbox.DamageHitboxClip.class,
            new ClipFactoryData(Icons.SPHERE, 0xef5350));

        fabrik
            .register(Link.create("bbstools:model_fracture"), ModelFractureActionClip.class,
                new ClipFactoryData(Icons.LIMB, 0xff7043))
            .register(Link.create("bbstools:block_destruction"), PhysicsBlockDestructionActionClip.class,
                new ClipFactoryData(Icons.BLOCK, 0x8d6e63))
            .register(Link.create("bbstools:ragdoll_proxy"), ModelRagdollProxyActionClip.class,
                new ClipFactoryData(Icons.SPHERE, 0x26a69a))
            .register(Link.create("bbstools:world_snapshot"), WorldSnapshotClip.class,
                new ClipFactoryData(Icons.SCENE, 0x5c6bc0))
            .register(Link.create("bbstools:block_animation"), BlockAnimationActionClip.class,
                new ClipFactoryData(Icons.BULLET, 0xab47bc));

        LOG.info("BBSTools action-clips registriert: {} (fabrik hat jetzt {} typen)",
            "model_fracture, block_destruction, ragdoll_proxy, world_snapshot, block_animation",
            fabrik.getKeys().size());
    }

}
