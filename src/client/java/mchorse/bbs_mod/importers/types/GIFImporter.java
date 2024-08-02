package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.ImporterUtils;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.File;

public class GIFImporter implements IImporter
{
    @Override
    public IKey getName()
    {
        return UIKeys.IMPORTER_GIF;
    }

    @Override
    public boolean canImport(ImporterContext context)
    {
        return ImporterUtils.checkFileEtension(context.files, ".gif");
    }

    @Override
    public void importFiles(ImporterContext context)
    {
        for (File file : context.files)
        {
            String name = StringUtils.removeExtension(file.getName()) + "_%d.png";

            FFMpegUtils.execute(context.destination, "-y", "-i", file.getAbsolutePath(), ImporterUtils.getName(context.destination, name));
        }
    }
}