package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.l10n.keys.IKey;

public interface IImporter
{
    public IKey getName();

    public boolean canImport(ImporterContext context);

    public void importFiles(ImporterContext context);
}