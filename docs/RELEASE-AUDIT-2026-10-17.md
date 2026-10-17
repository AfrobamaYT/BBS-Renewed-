# Release audit, 17 October 2026

Status: ongoing. This record preserves the user's full release/deep-audit objective;
no overall release-readiness or older-version parity is established.

Confirmed exposed unfinished function in the prior fa96 candidate:
WorldSnapshotClip is registered as bbstools:world_snapshot but only logs.
Repair source cc05a931f now builds the server restore and actual editor;
production world/input cases pass all three jobs of run39019305927.
Narrow-dock labels and the editor-test reopen setup are corrected in ebdb9b245;
follow-up run39019817908 passes Linux/Windows source builds and inspected UI,
but its unchanged-Linux-input Windows job crashes natively before tests.
Visible world-reset capture and dedicated-server startup remain open.
The complete current feature set is not ready.
See WORLD-SNAPSHOT-REPAIR-2026-10-17.md for evidence and full repair acceptance.

## Frozen inputs and present candidates

- Current snapshot world/UI-tested Linux candidate:
  `0e25e5031a2bad726928177b3247560a4af1d4f0cf652d8eb223137913a98d7b`,
  source ebdb9b245. Both source-build jobs pass, with readable labels and active
  Actions after reopen. The unchanged-input Windows job fails before tests;
  run39019817908 is red overall.
- Previous snapshot world-tested Linux candidate:
  `1cee43da1b4fcd40e26994bbc8640fcd0e32c619299dc87f41070bca7624589b`,
  source cc05a931f. The unchanged bytes also pass on Windows. The subsequent
  UI-only refinement ebdb9b245 is reported separately above.
- Previous production-tested Linux candidate:
  `fa96dc2487ef22d109dad4cf88ee879d0c8c2e873ac845ebb7bdd4576b8c3073`,
  built from `db3ae209f`; test-only source `ba80899a8` produces the same product.
  Cold startup, saved-anchor input and the scoped Guide case pass on Linux and
  Windows, including unchanged Linux bytes on Windows. This is not full release readiness.
- Published26.3 Preview2: source `80a1d8924e8aa798cffe7c64c2cfbf9020ed4644`, product
  `05ae129423604889fd75459da1fb3dd45b451c092a25ab930c99b4d862d3606e`.
- Original integrated parity reference: `cf99f2f828b0d74d1642fac7c5791ad81970d44f`.
- Newer cold-tested private26.3 program reference: `d4e2797cdc5448624151f632f3e2692d695d8d21`,
  product `d7b14f9340b13d9a72875ebe561912eb1e12b6a45e8dde619d0c908ed83a019f`.
  Its later licence-only correction is reported separately; it is not a new gameplay test.
- Public product source candidate `ffca5f77e50582a2edd6f50f13759642f3a4a61a`
  carries all28 changed product source files exactly from that program reference.
  Existing public packaging and first-run category guard remain intact.
- Locally built public candidate: `cded6b7bdb76d48e9cd68aa8bbc55ff16ac4eb1853744013da501da5977921dc`.
  Sources JAR: `90217ee5d2c7c03fb28af851202e166e7e1b54c291bfcae54c17b38484719afa`.
  Subsequent checker/client-test changes do not alter product source behavior.
- The subsequent standalone-Actor world correction is source `409ab2449`,
  local product `765766e928933292028ae96054c35a7fd0b26aee48f6cf81cc6604a65c70e96e`.
  Build/boundary and exact tested-class identity pass. Its subsequent canonical
  fa96 package passes cold production startup and the named client cases;
  cold standalone-Actor pixels remain separate. See ACTOR-WORLD-RENDER-2026-10-17.md.

The public candidate is not a new published release. Earlier Preview2 gameplay
results must not be used as acceptance of the changed candidate. New unrelated
changes from other worktrees require an explicit source/artifact delta and retest.

## Evidence obtained in this audit

- The actual new public build, API gate, native-library/source-fingerprint gate,
  Blender provenance and public/private artifact boundary pass. No companion is
  shipped in the product.
- All22 numerical IK cases pass on the actual new public JAR against the pinned
  official Blender C++ reference. This is solver-component evidence, not full rig
  or editor acceptance.
- Gitleaks scanned an isolated tracked-source snapshot with the corrected release
  checker:32.24MB, no findings. It does not establish arbitrary dependency safety.
- Full comparison to the private program reference finds no removed entries.
  Differences include public metadata/manifest, complete licence packaging,
  the existing VisibilityManager first-run correction and eleven anonymous
  class binaries in the local build. A complete raw-entry comparison now
  establishes that the canonical Linux1823 product matches the private reference
  byte-for-byte for those eleven and every other product class except the known
  VisibilityManager guard. Its only other entry differences are the explicit
  licence, manifest and mod metadata changes. Local anonymous-class instruction
  text also matches; one inspected verbose diff contains only a local-variable
  parameter name. Broad runtime acceptance remains separate from this comparison.
- The private26.2 fresh-runner CI39003103176 passes configuration, actual launch-
  dependency agent selection, build, real production client/world and screenshot.
  It asserts no bbsdev/development environment and logs the loaded product JAR.
  The inspected screenshot includes the welcome hint; it is startup evidence,
  not a clean dashboard or Film-editor interaction result.
- The updated public client suite compiles. It reads the real welcome button's
  rendered hit zone, sends Fabric mouse input, requires the known state and hint
  removal, then records the dashboard. Production mode now logs the loaded JAR
  SHA256 and CI retains the actual built JARs. The updated run is recorded below.
- Missing categories.json produced a real first-run stack trace in the26.2 CI.
  The existing public guard is now also in the shared source; all14 packages pass.
  A new fresh-client log is required to accept that correction at runtime.
- A real17337-byte truncated GPL copy was independently found in the private
  runtime/exporter packages. The full release CLI previously accepted it. The
  corrected checker rejects that copy and a missing copy, accepts the complete
  pinned18002-byte GNU text, and preserves artifact-entry checks as a separate
  requirement. The public candidate contains the complete text in both entries.

Local raw evidence for the public candidate is under
`build/test-evidence/release-audit-20261017/` in the public release worktree:
product-build.log, private-freeze-comparison.json, ik/results.json,
gpl-copy-gate-controls.json, source-gitleaks.json and first-run-gametest-compile.log.
The private matrix/runtime evidence is under
`build/test-evidence/parity-baseline-20261016/` in the parity worktree.

## Required acceptance, still open unless specifically proved

| Area | Required proof | Present limit |
| --- | --- | --- |
| Distribution/startup | Exact released bytes, matching full source/licences, correct Minecraft/Java metadata, fresh Windows/Linux production launch without companion, inspected images and relevant logs | Exact Linux product passes startup/welcome input on Linux and Windows; full release remains open. |
| Editor/input | Dashboard, actual Film editor, docks, search/text, BBS Default shortcuts, picking, scale and first interaction after cold start | Welcome mouse input passes on both systems; actual whole-editor acceptance open. |
| Film/data | Create/edit real replay/camera/pose keys, recording, first Undo/Redo, native save/reload without sibling or authored-data loss | Scoped26.2 contracts and handlers pass; first gizmo Undo/H1 and broad26.3 cases remain open. |
| Animation/timing | Playback, scrubbing, fractional time, loop/ghost/source edits, motion-shift drag/reset, grouped hidden tracks, stepped preview and visible state | The26.2 cold HUD, Default X11 Alt+K and multi-frame loop move pass; resize/Escape/motion-shift/ghost/hidden-group cases remain open. |
| Models/rigs/props | Cubic/BOBJ, hand/finger/facial controls, held-prop identity, independent hands, quaternion/Euler-pole/gimbal behavior | Named exports/poses have scoped private evidence; broad new-public-artifact acceptance remains open. |
| Regie | Start/preroll/timing, marker selection/drag/edit/delete, save/reload, Simple/Detailed display at relevant scales | The1280x720/scale2 Guide case passes real wheel and marker input on Linux/Windows; broader Regie editing and other sizes remain open. |
| Materials/rendering | Real material/model pixels and preview/world pass with supported render backends; state restored after preview/picking | Shader/mod combinations and broad rendering regression cases remain open. |
| Audio/video/export | Actual recording/playback, audio sync and WAV/PCM, exported media inspected with FFprobe and motion/audio readback | No whole-product export acceptance from a launch/solver test. |
| Blender/Resolve | Actual included bridge workflows, ownership, reconnect/conflict/persistence, exporter package/source identity | Resolve reverse playback and process-restart pending-edit cases remain open; the corrected exporter package identity/licence-only delta passes independent inspection. |
| Older-version parity | Same complete supported product behavior on26.2/1.21.11/1.21.4/1.21.1/1.20.4/1.20.1; build both artifacts and use owned runtime/motion evidence per target | Compilation/data contracts alone do not establish parity. |

### Specific observations and status

1. Resolve J/reverse playback was actually reported leaving BBS at cursor792,
   playing=false while Resolve moves backward. Its owning chat is fixing it;
   source changes alone are not acceptance.
2. A separate Resolve Python-process restart may replace a camera title edited
   natively during link loss with an unchanged old BBS snapshot. This is a reported
   pending-edit/reconnect case, not yet an independently reproduced native result.
3. The historical Guide clipping report does not reproduce as permanently
   unreachable fields in the current1280x720/scale2 case: actual scrolling reaches
   the bottom before/after marker selection. No layout correction was required;
   earlier attempted/reverted fixes are not the cause of this acceptance.
4. H1: first gizmo interaction after opening a Film was once not undone. Existing
   later Undo/data contracts do not replace a new exact first-interaction test.
5. The missing26.2 stepped badge is now fixed for the cold9b58075a7 pair. Actual
   orange pixels, BBS Default X11 Alt+K, continuous loop drag and attached
   Undo/Redo/save/reload pass. See OWNED-HUD-INPUT-26.2-2026-10-17.md in the
   parity worktree; other versions and remaining gestures are not inferred.
6. The older preview-world camera in `src/compat/vor261/.../ZweitKamera.java`
   remains an explicit false/null/zero/black stub. It is not built, and blocks full
   older-version parity. Primary version-specific rendering research already exists.
7. BOBJ/quaternion/Euler-pole gizmo breadth, long sessions, additional worlds and
   actual export/recording workflows still need corresponding evidence.

The separately requested model browser and free second3D editor are still unbuilt
product work. The original user notes explicitly schedule them later; they must
not be advertised as delivered or confused with the existing Camera View preview.
No Discord/video publication approval follows from this audit or a green build.

## Exact public product on both operating systems

[Run39006225543](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39006225543),
source `a7d739a7639ddc79b1489efd85f464083ff34af4`, passes both native source-build
jobs and an additional Windows job using the unchanged Linux product/test JARs.
The actual loaded product SHA256 matches on Linux and frozen-input Windows:
`1823b43a64ceb0f89b39fe64884fb72d7eb99acda848e4ad25f3f36a3c3e6dfc`.
All six actual before/after welcome/dashboard screenshots were inspected.
The welcome disappears through Fabric mouse events, known-state and removal
assertions pass, and no VisibilityManager category-read stack trace appears.
Both canonical licence entries in the new packages have the full pinned LF text.
This is production startup/world/dashboard/input evidence, not full Film, rig,
recording, shader or integration acceptance. The release remains unpublished.
Raw runtime hashes, images and logs are retained in
`build/test-evidence/release-audit-20261017/ci-39006225543-receipts.json` and the
corresponding downloaded artifact directory in the public release worktree.

## First-anchor Undo probe and separate Windows failure

The red Linux/Windows assertions in
[run39008963180](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39008963180)
are not a current product reproduction of historical H1. Inspection of the
actually resolved Fabric client-gametest6.0.7 input driver shows it constructs
every keyboard KeyEvent with modifiers=0, including after holdControl(). BBS
intentionally matches the captured event modifiers, including queued quick taps.
The test therefore sent an unmodified Z. The previous current-product claim
was explicitly withdrawn; the historical native-input H1 observation remains open.

The updated test records events at the real UIScreen, delegates them unchanged,
and compares the original Fabric path with the same Minecraft KeyboardHandler
receiving a KeyEvent carrying SDL's captured left-Control mask. It requires an
initially empty history, the first real ring drag, restored authored/widget
values and consumption of the existing history entry. It never calls the Undo
manager as a shortcut or changes product key matching. Compilation passes;
the corrected runtime test now passes in all three jobs of
[run39009807975](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39009807975),
source `3703d03479b36692523094b91d8523f4a0c24139`. Logs confirm modifiers0/ctrl=false
for Fabric, modifiers64/ctrl=true for the corrected KeyboardHandler event,
unchanged edited values after the first path, and exact authored/widget restore
plus history index0 to -1 after the second. The unchanged Linux1823 product
passes on Linux and Windows; Windows' own build also passes. All nine actual
ring-ready/drag/Undo images were inspected. Raw records and image/log hashes:
`build/test-evidence/release-audit-20261017/ci-39009807975-receipts.json`.
This is a test-adapter correction and one current saved-anchor case, not a product
Undo fix or closure of the historical native-X11 H1 observation.

Separately, Windows in
[run39008269225](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39008269225)
exited during resource loading with NTSTATUS0xC0000374 (-1073740940).
No cause is established. The next run did not repeat this native failure;
its hosted-only WER/CDB collection executed, with no dumps or matching events.
That round does not clear the earlier native crash. Raw dump memory is excluded
from uploaded artifacts; event/stack diagnostics are retained when available.

## Corrected exporter package and remaining version gap

The actual corrected exporter ZIP has SHA256
`d295ae800f95db862076172e8789e4b769c2c11a8bf20b6fc26b5da29060333d`.
Independent inspection confirms the complete18002-byte pinned GPL copy,
all SOURCE.json entry hashes and byte equality of all nine Python files plus
README against the previously inspected94767b package. SOURCE.json transparently
pins producer3cc118a9 and the unchanged c32bf4b9 payload provenance. This accepts
the licence-only package delta; no new Blender runtime or publication is implied.
Raw package and canonical/private program comparisons are retained beside the
CI receipts as `exporter-corrected-package-inspection.json` and
`canonical-private-program-comparison.json`.

The actual seven-version private matrix at eb5dc9983 still lacks the newer
public mchorse.bbs_mod.api classes. Full older-version parity requires their
functional contracts and real dispatch paths, not just a successful compile.
StructureForm/StructureRenderer are also absent and must not be fabricated as
empty compatibility classes. No older release is established by that matrix.

## Actor candidate, Guide input and complete source ZIP

[Run39010351840](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39010351840)
passes all three cold production jobs with the Actor correction. The canonical
Linux fa96 JAR differs from1823 only in ActorEntityRenderer and its nested state;
the renderer is exactly the causally tested d323 class. This accepts the package's
cold startup and saved-anchor case, not a cold standalone-Actor visual workflow.

[Run39010545176](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39010545176)
passes all three jobs with the same fa96 product and the Guide case added.
At1280x720/UI scale2, real wheel input reaches the final Start-facing field.
Clicking the actual marker attaches its edit fields; further wheel input reaches
the bottom again. The scroll content correctly expands from346 to471 logical
pixels. All nine Guide images were inspected. There is no product layout fix.
Other sizes and full Regie editing/recording acceptance are not inferred.
Raw records: `build/test-evidence/release-audit-20261017/ci-39010545176-receipts.json`.

The full source ZIP from ba80899a8 has SHA256
`19e2fdffb6e4cb58f5a3e152a9ba02820efaa579b9dc5a6be59abec1f20a3489`.
It contains no Git history, private companion source tree or symlinks. Extracted
outside every Git repository, its fresh source build passes all12 build tasks
with the existing dependency cache. The resulting product is
`40630648813e226516572766c91cd09e582e8b2078090f620ab8e449f513809d`.
Every decompressed entry matches canonical fa96 except MANIFEST.MF. Parsing its
attributes shows exactly the same2444 Client-Only-Entries with the same frequency,
in a different order; every other attribute is identical. This is a corresponding
source/build-input check, not a new gameplay run or a cold dependency download.
Raw result: `build/test-evidence/release-audit-20261017/full-source-outside-git-result.json`.

## Native X11 trial107 and replacement world fixture

The actual owned1920x1080/OpenGL run loaded fa96/c85, verified by native
Render-thread CodeSource hashes. Physical XTest Control+Z reached the unchanged
UIScreen with modifiers64; history remained empty at index-1. No valid visible
replay/gizmo X-ring hit was obtained and no first drag occurred. This is transport
evidence only: historical H1 has no new failure, fix or acceptance from this run.
The inherited owned-world player records contain the missing texture reference
logged by renderArm. That establishes an unsuitable dependency in the fixture,
not the cause of the invisible replay. The owned JVM, wrappers, Xvfb and four ports
were physically gone, and lease107 released, before the fixed07:31:55UTC end.

The replacement follows Fabric's TestWorldSave API: export the actual freshly
created world only after its server closes. All three jobs of
[run39014391362](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39014391362)
pass with product fa96. The actual exported level.dat has SHA256
`2d7eff267aed08ceb0e4d3483e161a9462ad3330b46116762c261f4e193a4b93`.
Minecraft's own NBT parser confirms DataVersion5023 and the exact overworld
generator minecraft:flat. The old missing reference is absent from its DAT files.
This validates the replacement dataset, not the pending native physical H1 case.
Inspection receipt: `build/test-evidence/release-audit-20261017/native-fixture-world-inspection.json`,
SHA25664f0a8de4cb47dcc27f79f2a65d09771880f9cfc69ed651ea082fe484bc1d760.

## Standalone Actor lifecycle and unresolved Windows native crashes

The Linux job of
[run39016558132](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39016558132)
passes cold standalone-Actor creation through normal server tracking/form sync,
three real Minecraft resource reloads, preserved UUID/position and server removal.
All five actual images were inspected: geometry is visible initially and after
each reload, then absent after removal. The test now waits for the loading overlay
to disappear; earlier pictures under that overlay are not visual acceptance.
Product remains fa96. Raw receipt: standalone-linux-39016558132-receipt.json,
SHA256af76fb30add7cfb29f5f39940bbe92d5963d4669baea3c073f301e79a691c43a.

That run's Windows JVM exited before tests with NTSTATUS0xC0000005
(-1073741819), in addition to the earlier0xC0000374 observation. No cause is
established. WER preparation ran but produced no dump, stack or matching event;
there was no uploaded hs_err file. This remains an unresolved native failure.

Opt-in signed Microsoft ProcDump monitoring was then added for the unique JVM
marked bbs.ci.production=true, including documented exception/termination triggers
and cancellation. Raw dumps stay in RUNNER_TEMP; only metadata/stacks are uploaded.
[Diagnostic run39017242286](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39017242286)
passes all three jobs. The Windows monitor observed PID5716, wrote a real minidump
on normal exit0 and CDB parsed it successfully. No fatal crash was reproduced.
Handled first-chance exception lines in the monitor log are not process crashes.
All five Windows Actor images were inspected and pass this scoped lifecycle case
under diagnostic monitoring; they do not clear the earlier uninstrumented failures.
The capture collector now rejects missing dump output and retains readable UTF8
copies of native logs. The JVM's own Windows crash-dump option is also explicit.
The same fa96 product source is frozen separately on
codex/native-fa96-diagnostics-20261017 for continued native failure isolation.

[Normal comparison run39018217629](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39018217629)
at frozen source91908c207 passes Linux, Windows and unchanged Linux product on
Windows without ProcDump attached. The JVM dump option is enabled. No fatal
failure was reproduced; the earlier0xC0000374/0xC0000005 causes remain unresolved.
This is another successful control run, not evidence of a crash fix.
Both Windows artifact sets were downloaded and all ten Actor lifecycle pictures
were inspected. Geometry is visible initially and after each reload, then absent
after removal. Frozen Windows logs load exactly fa96; Windows' own build is
92252a081a69eb892f38dd19030b7b893ee0347ad473d92782bdb27ca794046d.
Raw hashes, runtime records and the no-debugger diagnostic state are retained in
build/test-evidence/release-audit-20261017/normal-actor-lifecycle-39018217629.json.

## Additional action-picker path under investigation

Source and packaged class inspection identify four common bbstools IDs mapped
to data-only action bases: model_fracture, block_destruction, ragdoll_proxy and
block_animation. BBSToolsModClient registers their implemented client subclasses
and editor factories under distinct bbs IDs. MapFactory keeps both IDs; UIClips'
add menu enumerates every key, and UIClip resolves an exact class match.
The common base bytecode inherits the empty ActionClip.applyClientAction.
An actual production registry/picker check is still required before calling
this a reproduced UI/runtime failure. This finding does not imply that the
implemented bbs client path or the separately owned Physics/Gore work is unbuilt.

## Another native failure after the snapshot UI refinement

Run39019817908 passes source-build Linux and Windows world/input cases; all six
Snapshot editor images were inspected. The frozen-input Windows job exits with
0xC0000005 before tests. It produces no hs_err, dump, stack or application event,
despite enabled WER/JVM capture. No loaded-product hash marker is reached there.
The earlier successful controls do not clear this repeat. The cause is unknown.

Microsoft documents a separate ProcDump/AeDebug postmortem mode. The additional
optional CI capture must first produce a real dump from an owned native
access-violation control, then observe the marked game PID without attaching a
debugger until a fault. It preserves/restores the original runner registry and
keeps raw dumps in RUNNER_TEMP. This is a capture repair, not a product crash fix.
References: [ProcDump](https://learn.microsoft.com/en-us/sysinternals/downloads/procdump),
[Windows postmortem handling](https://learn.microsoft.com/en-us/windows-hardware/drivers/debugger/enabling-postmortem-debugging).
Implementation e57da0a1c passes PowerShell syntax and the actual non-Windows host
guard. [Run39020764340](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39020764340)
on the separate native-0e25-postmortem branch is recorded below. A configured flag and a
compiled control are not proof that its Windows postmortem capture has fired.

Run39020764340 subsequently failed in compiler discovery before the native control
or Windows game could run. The installed postmortem registry values were read back
and original values were restored, with no restoration errors. It produced no dump.
Source25f5d754e now selects Visual Studio's declared default toolset using
Microsoft.VCToolsVersion.default.txt and records discovery metadata, following
[Microsoft's vswhere procedure](https://github.com/microsoft/vswhere/wiki/Find-VC).
The separate [run39021135850](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39021135850)
tests this repair. Neither this tooling failure nor a future successful control
establishes the cause of the original game's native failure.

The Windows source-build job of run39021135850 now proves actual postmortem
capture for the owned native control. Visual Studio's declared toolset14.51.36231
compiles it; PID6592 exits with0xC0000005. Two real158771-byte minidumps retain
the exception context, and CDB exits0 for both. The inspected context shows
bbs_native_capture_control!entry writing1 through RAX=0. This is the deliberate
control fault, not a BBS/JVM crash. Both prior registry views were restored with
successful readback; downloaded artifacts contain no raw dumps.

The actual marked game PID10212 is observed-unattached, without a monitor PID.
Its source-build case loads46a3337e371b4e1790065aa2253d7091ccd311af71e7d3bf143e6e019582126d
and passes. The frozen Linux-on-Windows job remains pending at this record.
No new game fatal error was reproduced, so the earlier game's cause and whether
this path captures that particular fatal condition remain open.
Receipt: build/test-evidence/release-audit-20261017/postmortem-control-39021135850-receipt.json,
SHA2567d1f76e6e32382ebe21b478b249b3c342b868563681ed87e7ddbdcbcfbc54f45.
