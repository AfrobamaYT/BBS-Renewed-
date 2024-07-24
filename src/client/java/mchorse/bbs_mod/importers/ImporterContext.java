package mchorse.bbs_mod.importers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImporterContext
{
    public List<File> files = new ArrayList<>();
    public File destination;

    public ImporterContext(List<File> files, File destination)
    {
        this.files = files;
        this.destination = destination;
    }
}