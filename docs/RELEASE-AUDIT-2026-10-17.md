# Release audit, 17 October 2026

Status: ongoing. This record preserves the user's full release/deep-audit objective;
no overall release-readiness or older-version parity is established.

## Frozen inputs and present candidates

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
  class binaries. Anonymous-class instruction comparison and exact public
  runtime acceptance remain necessary; binary differences are not waved away.
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
| Regie | Start/preroll/timing, marker selection/drag/edit/delete, save/reload, Simple/Detailed display at relevant scales | UI-scale2 guide controls are reported clipped; source attempts were reverted. |
| Materials/rendering | Real material/model pixels and preview/world pass with supported render backends; state restored after preview/picking | Shader/mod combinations and broad rendering regression cases remain open. |
| Audio/video/export | Actual recording/playback, audio sync and WAV/PCM, exported media inspected with FFprobe and motion/audio readback | No whole-product export acceptance from a launch/solver test. |
| Blender/Resolve | Actual included bridge workflows, ownership, reconnect/conflict/persistence, exporter package/source identity | Resolve reverse playback and process-restart pending-edit cases remain open; new exporter licence package needs independent inspection. |
| Older-version parity | Same complete supported product behavior on26.2/1.21.11/1.21.4/1.21.1/1.20.4/1.20.1; build both artifacts and use owned runtime/motion evidence per target | Compilation/data contracts alone do not establish parity. |

### Specific observations and status

1. Resolve J/reverse playback was actually reported leaving BBS at cursor792,
   playing=false while Resolve moves backward. Its owning chat is fixing it;
   source changes alone are not acceptance.
2. A separate Resolve Python-process restart may replace a camera title edited
   natively during link loss with an unchanged old BBS snapshot. This is a reported
   pending-edit/reconnect case, not yet an independently reproduced native result.
3. The Regie guide panel is clipped at UI scale2. Existing attempted layout
   corrections were reverted; do not treat those commits as a working fix.
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
the corrected input/runtime result is still pending.

Separately, Windows in
[run39008269225](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39008269225)
exited during resource loading with NTSTATUS0xC0000374 (-1073740940).
No cause is established. The next run did not repeat this native failure;
its hosted-only WER/CDB collection executed, with no dumps or matching events.
That round does not clear the earlier native crash. Raw dump memory is excluded
from uploaded artifacts; event/stack diagnostics are retained when available.
