package mchorse.bbs_mod.client;

import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.events.ModelBlockEntityUpdateCallback;

import java.util.HashSet;
import java.util.Set;

public class BBSRendering
{
    /**
     * Cached rendered model blocks
     */
    public static final Set<ModelBlockEntity> capturedModelBlocks = new HashSet<>();

    public static void startTick()
    {
        capturedModelBlocks.clear();
    }

    public static void setup()
    {
        ModelBlockEntityUpdateCallback.EVENT.register((entity) ->
        {
            if (entity.getWorld().isClient())
            {
                capturedModelBlocks.add(entity);
            }
        });
    }
}