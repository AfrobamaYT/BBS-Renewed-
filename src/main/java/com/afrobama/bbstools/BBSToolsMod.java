package com.afrobama.bbstools;

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


        com.afrobama.bbstools.network.BBSToolsNetworkInit.init();

        /* /buildschematic + /ragdolly each register their own
         *  CommandRegistrationCallback internally — just call register(). */
        com.afrobama.bbstools.schematic.BuildSchematic.register();
        com.afrobama.bbstools.ragdoll.RagdollSwap.register();


        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
            (dispatcher, registry, env) ->
            {
                BBSToolsStatusCommand.register(dispatcher);
            });

    }

}
