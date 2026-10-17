package com.afrobama.bbstools.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Copy only the actual closed Fabric-created world; no client settings, mods or old user assets. */
final class NativeWorldExport
{
    static void copyClosed(Path source, Path output)
    {
        source = source.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        try
        {
            if (!Files.isRegularFile(source.resolve("level.dat")) || Files.exists(output)
                || output.startsWith(source) || source.startsWith(output))
                throw new IOException("Require a real saved world and a new separate output: " + source + " -> " + output);
            List<Path> paths;
            try (Stream<Path> walk = Files.walk(source))
            {
                paths = walk.sorted(Comparator.comparing(Path::toString)).toList();
            }
            for (Path path : paths)
                if (Files.isSymbolicLink(path)) throw new IOException("Unexpected world symlink: " + path);
            int files = 0;
            for (Path path : paths)
            {
                if (path.getFileName().toString().equals("session.lock")) continue;
                Path target = output.resolve(source.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(target);
                else
                {
                    Files.copy(path, target);
                    files++;
                }
            }
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(output.resolve("level.dat"))));
            Map<String, Object> receipt = Map.of("source", source.toString(), "world", output.toString(),
                "levelDatSha256", digest, "copiedFiles", files, "closedBeforeCopy", true);
            Files.writeString(output.resolve("fixture-manifest.json"), new com.google.gson.Gson().toJson(receipt) + "\n");
            System.out.println("BBS_NATIVE_WORLD_EXPORTED " + new com.google.gson.Gson().toJson(receipt));
        }
        catch (IOException | NoSuchAlgorithmException failure)
        {
            throw new AssertionError("Cannot export the explicitly requested native world fixture", failure);
        }
    }
}
