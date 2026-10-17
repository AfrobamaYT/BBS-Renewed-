# Failed encoder finalization

The actuale803 public product reproduces a false-success export on Linux in
run39033057307, test sourcea2cefc87b. The normal editor button starts the actual
installed FFmpeg. After two submitted frames, the test terminates only that
recorder's own process, waits for its real exit137, and finalizes through the
ordinary UIFilmRecorder.stop path. No recorder/engine is mocked.

Actual result: finishedCancelled=false, editorIdle=true, no error messages and
no remaining encoder process/children. The captured UI was inspected and shows
the green success checkmark for the interrupted file. The empty exception handler
in VideoExportSession.finish suppresses the recorder's failure and then invokes
the successful teardown/listener path.

The source repair marks failed finalization cancelled, preserves cleanup and
reports an error through the actual editor overlay or world-player chat. Overlay
insertion is deferred past rendering. A failed video is not passed to captured-
sound muxing and prepared audio remains available for recovery. Build/API/boundary
pass. Run39033702049 passes all three production jobs. Actual Linux, Windows
and unchanged-Linux-on-Windows results report cancelled=true, editorIdle=true,
no surviving encoder/children and the actual error. All three error screenshots
were inspected and have no false green completion. Canonical Linux/frozen product:
d2b54f4f501c1fb51e46ea3619c3016d2ddf4282928adbc22e85d4f6499fb3d3.
Raw encoder-finalization-after-39033702049.json SHA256
1474f6e0a27b1ab5a69233ebf2525dbd09a68a3a1c17f7ed0f166c8196da16c3.

Raw encoder-finalization-before-39033057307.json SHA256
12bcefcc7daf416bc7f6db131a08684b074ae7ac6b4766b318eb27398f1ff396.
Source logs and screenshot are in ci-39033057307 under the public audit evidence
directory. This case does not prove all asynchronous mid-frame errors, ordinary
user cancellation, audio muxing or other export options. Java process termination
uses the [actual JDK Process contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Process.html).
