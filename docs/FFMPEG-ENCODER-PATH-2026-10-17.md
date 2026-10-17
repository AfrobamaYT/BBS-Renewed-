# FFmpeg installation-root lookup

The actualbb6d product helper accepts an explicit encoder file and its bin
directory, but fails for the installation root containing bin/ffmpeg on Linux.
The filesystem probe loads FFMpegUtils from that exact product JAR and uses a
real installed FFmpeg binary through an owned symlink. It exits2 with only the
root case false; it starts no Minecraft instance.

The cause is File.pathSeparator, which separates entries in a search path rather
than directory names. Nested File(parent,child) construction resolves the bin
directory and executable through the platform filesystem convention, following
the [JDK File contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/File.html).

After the one-line product repair, the same actual-JAR probe exits0 for all three
cases. Build/API and public/companion boundary pass. The production test also
checks these three lookup forms on each platform with a file-lookup fixture;
actual video encoding separately runs the installed FFmpeg executable.
Both source-build platforms pass the real lookup check in39030503241. Its Windows
decoder inspection still fails on the old -vsync option. The corrected full
run39030928425 passes all three jobs, including unchanged Linux bytes on Windows,
the actual file/bin/root lookup and inspected normal export. See FILM-VIDEO-EXPORT-2026-10-17.md.

Raw evidence is build/test-evidence/release-audit-20261017/encoder-root-path.
Its result.json preserves the local product hash and complete binary delta:
the local JAR also differs from canonicalbb6d in eleven anonymous classes and the
manifest. The fresh canonical Linux CI producte803654d6aa59776d57e86225b03ce174f6c8daf90464b1f71a698bc510ea74a
resolves that comparison: exactly FFMpegUtils.class differs from canonicalbb6d;
every other decompressed entry is byte-identical. The local build alone did not
establish that one-class delta. Whole-release readiness remains open.
