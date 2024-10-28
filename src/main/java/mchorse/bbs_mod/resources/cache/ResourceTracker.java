package mchorse.bbs_mod.resources.cache;

import mchorse.bbs_mod.network.ServerNetwork;
import mchorse.bbs_mod.utils.Timer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class ResourceTracker
{
    public final Timer timer = new Timer(300);

    private MinecraftServer server;

    public ResourceTracker(MinecraftServer server)
    {
        this.server = server;
    }

    public boolean canSend()
    {
        return this.timer.checkReset();
    }

    public void tick()
    {
        if (this.canSend())
        {
            for (ServerPlayerEntity serverPlayerEntity : this.server.getPlayerManager().getPlayerList())
            {
                ServerNetwork.sendHandshake(this.server, serverPlayerEntity);
            }
        }
    }
}