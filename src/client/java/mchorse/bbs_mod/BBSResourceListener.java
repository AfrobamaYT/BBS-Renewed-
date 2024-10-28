package mchorse.bbs_mod;

import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Path;

public class BBSResourceListener implements IWatchDogListener
{
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null)
        {
            return;
        }

        if (event == WatchDogEvent.CREATED || event == WatchDogEvent.MODIFIED)
        {
            ClientNetwork.sendAsset(link, 0);
        }
        else
        {
            ClientNetwork.sendAsset(link, -1);
        }
    }
}