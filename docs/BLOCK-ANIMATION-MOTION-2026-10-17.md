# Block Animation: actual effect audit

Production run39025023645, source06520254b, executes both registered IDs through
Replay.applyClientActions with a real MCEntity/player and real world blocks.
It captures two gold blocks, samples the live effect, waits for elapsed engine
time, checks the configured endpoint and invokes the explicit clear operation.
The real server blocks remain gold. The inclusive single-block case fails on
both Linux and Windows because the client action incorrectly rejects min=max.

The Linux private Xvfb framebuffer was recorded with FFmpeg,20fps requested,
1280x720,146.5seconds/2930 encoded frames. These counts do not prove unique
rendered frames or smooth motion. watch-screen inspected138–146seconds at3fps
and139–140seconds at8fps. Both registered IDs visibly move their two copies
upwards and explicit clear removes the copies. Initial coplanar overlap shows
intermittent horizontal striping; it is not a clean-render acceptance.

Raw video and contacts are under ci-39025023645 in the public audit evidence
directory. Receipt block-motion-before-39025023645.json has SHA256
9ab82baf4675f136b2f1add41b962c1818751db0a0f7aed1dc522ae7cc2ac453.

## Corrections awaiting full acceptance

Sourcee286ae1bb removes the erroneous single-block exclusion; the native region
capture already iterates inclusive bounds. The test now also records the actual
single-block effect before clearing it. Production run39025821014 passes all
three jobs for this cut; Linux's real effect state confirms the one-block capture.

Sourcecbfcdbf5d applies Minecraft's existing projection-aware VIEW_OFFSET_Z_LAYERING
modifier before the camera-relative copy translation. The actual26.3 bytecode
delegates to ProjectionType's layering operation. It avoids introducing another
block-rendering path or changing real block positions. Run39026151559 records
the comparison. Its Linux and Windows source-build jobs pass. The Linux video
was inspected at114–123seconds/3fps and114–115seconds/8fps: both two-block actions
and the single-block action visibly rise, and explicit clear removes the copies.
The earlier overlap stripes are absent in the inspected perspective/gold-block
frames. Other materials, camera modes and performance are not accepted by this
case. Canonical Linux product9387e83c6ee649b9219fef4b4c1c79d8d9f19b5a539747548db3718cb928b73b.
The frozen-input Windows job also passes with unchanged9387 bytes and real
one-block/two-block state; the final runtime receipt was read. All three jobs pass.
Raw receipt: block-motion-after-39026151559.json in the public audit evidence directory.

Remaining acceptance includes reversed/empty and large regions, invalid input,
render failures, timeline/replay lifecycle and the relevant materials/cameras.
Explicit clear is not proof of automatic film-stop cleanup. Source concerns about
unbounded capture loops and swallowed render failures remain open. No arbitrary
new capture-size restriction is substituted for completing the feature.
