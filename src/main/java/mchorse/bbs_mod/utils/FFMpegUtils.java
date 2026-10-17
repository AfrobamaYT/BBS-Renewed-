package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FFMpegUtils
{
    private static final Set<String> SKIP_DIRS = Set.of("Windows", "Program Files", "Program Files (x86)", "$Recycle.Bin", "System Volume Information", "AppData");

    /**
     * People usually are not bright enough, even though everything is stated
     * in the tutorial, they still manage to specify either wrong path to ffmpeg, or
     * they specify the path to the folder...
     *
     * This little method should simplify their lives!
     */
    private static File findFFMPEG(String path)
    {
        File file = new File(path);
        boolean isWin = OS.CURRENT == OS.WINDOWS;

        if (file.isDirectory())
        {
            String subpath = isWin ? "ffmpeg.exe" : "ffmpeg";
            File bin = new File(file, subpath);

            if (bin.isFile())
            {
                return bin;
            }

            bin = new File(new File(file, "bin"), subpath);

            if (bin.isFile())
            {
                return bin;
            }
        }
        else if (isWin && !file.exists())
        {
            File exe = new File(path + ".exe");

            if (exe.exists())
            {
                return exe;
            }
        }

        return file;
    }

    private static boolean missingEncoderReported;

    public static String getFFMPEG()
    {
        String encoder = BBSSettings.videoEncoderPath.get();
        File encoderPath = findFFMPEG(BBSSettings.videoEncoderPath.get());

        if (encoderPath.isFile())
        {
            return encoderPath.getAbsolutePath();
        }

        if (encoder.contains("/") || encoder.contains("\\"))
        {
            /* A path that is not there -- e.g. a Windows ffmpeg.exe in a config copied to Linux
             * (2026-10-12: the owner's 26.3 instance; the Resolve tab and video export failed with
             * "Exec failed"). The ffmpeg on the PATH is the default; say so once, loudly. */
            if (!missingEncoderReported)
            {
                missingEncoderReported = true;
                System.err.println("[BBS] video encoder_path '" + encoder + "' does not exist; using 'ffmpeg' from the PATH");
            }

            return "ffmpeg";
        }

        return encoder;
    }

    public static boolean checkFFMPEG()
    {
        return execute(BBSMod.getGameFolder(), "-version");
    }

    public static boolean execute(File folder, String... arguments)
    {
        List<String> args = new ArrayList<String>();

        args.add(getFFMPEG());

        for (String arg : arguments)
        {
            args.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(args);
        File log = BBSMod.getSettingsPath("converter.log");

        builder.directory(folder);
        builder.redirectErrorStream(true);
        builder.redirectOutput(log);

        try
        {
            Process start = builder.start();

            return start.waitFor() == 0;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return false;
    }

    public static Optional<Path> findFFMpeg(Path root)
    {
        Visitor visitor = new Visitor();

        try
        {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, visitor);
        }
        catch (FFMpegFoundException found)
        {
            return Optional.of(found.foundPath);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static class Visitor extends SimpleFileVisitor<Path>
    {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        {
            Path name = dir.getFileName();

            if (name != null && SKIP_DIRS.contains(name.toString()))
            {
                return FileVisitResult.SKIP_SUBTREE;
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
        {
            Path fileName = file.getFileName();

            if (fileName != null && fileName.toString().equalsIgnoreCase("ffmpeg.exe"))
            {
                Path parent = file.getParent();

                if (parent != null)
                {
                    Path parentName = parent.getFileName();

                    if (parentName != null && parentName.toString().equalsIgnoreCase("bin"))
                    {
                        throw new FFMpegFoundException(file.toAbsolutePath());
                    }
                }
            }

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc)
        {
            return FileVisitResult.CONTINUE;
        }
    }

    private static class FFMpegFoundException extends RuntimeException
    {
        final Path foundPath;

        public FFMpegFoundException(Path foundPath)
        {
            this.foundPath = foundPath;
        }
    }
}
