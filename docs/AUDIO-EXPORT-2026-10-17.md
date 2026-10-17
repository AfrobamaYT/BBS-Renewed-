# Audio export probe and Windows failure

The actual saved AudioClip points at an owned1s/48kHz/PCM16 mono WAV with a
1kHz sine at peak8191/32768. The ordinary record button renders/muxes it into MP4.
Linux39034142776 passes: AAC-LC,48kHz,one channel,duration1.000000, RMS-15.069052dB
and zero-crossings rate0.042096. The actually decoded WAV's NumPy FFT peak is
1000.3324468Hz. This proves the named mono cue, not complete audio functionality.

The Windows job fails during the audio export. Its generated96,044-byte WAV is
byte-identical to the source fixture; Python's standard wave reader confirms
48,000 PCM16 mono frames at48kHz. The actual Windows FFmpeg9.0.2 log nevertheless
selects mpegts and exits with EOF. The recorder's subsequent broken-pipe write
propagates through BBSModClient.captureFinishedFrame and crashes Minecraft with
a Java Render Frame exception. This is distinct from the earlier native AVs.

The source candidate declares -f wav only for a verified RIFF/WAVE audio input
whose input group has no user-supplied -f. Other formats and explicit format
arguments remain intact; the actual audio regression checks that override rule.
The [FFmpeg input-format contract](https://ffmpeg.org/ffmpeg.html) supports declaring
the known container. Runtime acceptance remains pending. Generic asynchronous
frame-write failure handling is still open and must not be cleared by this fix.

Raw audio-windows-before-39034142776.json and the source/encoder/crash logs remain
in the public audit evidence directory. Cancellation, stereo/spatial audio,
multiple sources, offsets/envelopes and captured game sounds require separate proof.
