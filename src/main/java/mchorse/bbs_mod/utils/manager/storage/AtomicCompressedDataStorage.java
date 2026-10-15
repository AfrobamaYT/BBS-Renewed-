package mchorse.bbs_mod.utils.manager.storage;

import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Film state and its transaction receipts must become durable in the same replacement. */
public final class AtomicCompressedDataStorage extends CompressedDataStorage
{
    @Override
    public void save(File file, MapType data) throws IOException
    {
        Path target = file.toPath().toAbsolutePath();
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".film-", ".pending");

        try
        {
            super.save(temporary.toFile(), data);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE))
            {
                channel.force(true);
            }
            /* No non-atomic fallback: unsupported filesystems must report failure. */
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            if (!System.getProperty("os.name").toLowerCase().contains("windows"))
            {
                try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ))
                {
                    directory.force(true);
                }
            }
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
