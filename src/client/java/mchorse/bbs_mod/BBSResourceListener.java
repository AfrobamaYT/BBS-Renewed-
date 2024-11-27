package mchorse.bbs_mod;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;

import java.nio.file.Files;
import java.nio.file.Path;

public class BBSResourceListener implements IWatchDogListener
{
    private IAssetNotifier notifier;

    public BBSResourceListener(IAssetNotifier notifier)
    {
        this.notifier = notifier;
    }

    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        if (!BBSResources.canDetectChanges())
        {
            return;
        }

        Link link = BBSMod.getProvider().getLink(path.toFile());

        if (link == null || Files.isDirectory(path))
        {
            return;
        }

        if (this.notifier != null)
        {
            this.notifier.notifyAsset(link, event == WatchDogEvent.DELETED);
        }
    }

    public static interface IAssetNotifier
    {
        public void notifyAsset(Link link, boolean delete);
    }
}