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
Windows runtime and the normal film-export proof are pending.

Raw evidence is build/test-evidence/release-audit-20261017/encoder-root-path.
Its result.json preserves the local product hash and complete binary delta:
the local JAR also differs from canonicalbb6d in eleven anonymous classes and the
manifest. A new canonical CI comparison is required; no one-class binary-delta
claim or whole-release acceptance follows from this local build.
