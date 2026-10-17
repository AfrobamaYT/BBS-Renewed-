# World snapshot — repair and acceptance

Prior frozen productfa96 includes WorldSnapshotClip. BBSToolsMod registers it as
bbstools:world_snapshot; the native107 boot log confirms registration and the
English resource supplies the World snapshot label. The actual shipped class's
applyClientAction bytecode ends in an informational RESTORE LOGIC PENDING message.
It has no world restore implementation. This is an unfinished exposed function,
not a successful feature or an inactive future class. Source and deployed-bytecode
evidence agree. Selecting/configuring its actual editor panel still needs testing.

## Repair approach

Use Minecraft's existing StructureTemplate/StructureTemplateManager format and
placement APIs, rather than inventing a block snapshot codec. Vanilla structure
blocks already capture that format. Confirmed26.3 signatures are recorded in the
audit evidence; older Yarn method names/signatures must not be copied blindly.
The existing registered clip ID and serialized value names must remain compatible.

Trace the ordinary server ActionClip path, as used by CommandActionClip, before
adding another client-to-server protocol. Restore on the actual server world,
with the existing BBS playback/command permission policy. Preserve the inclusive
AABB and both restore_block_entities and diff_only semantics; neither option may
be silently ignored. A one-block region is valid, despite the current placeholder
rejecting it as empty. Resolve snapshot identifiers through the world-owned
structure manager and reject invalid names/paths before mutation.

The editor needs real controls for the snapshot, region and options. Verify its
actual UI factory registration; the generic action base only provides frequency.
Capture should reuse vanilla's implemented structure workflow. Any additional
capture UI must be justified by the existing user workflow, not added as a second
world-editing subsystem. A missing snapshot must fail visibly with no partial restore.

## Required acceptance before completion

- Actual registered clip execution restores changed blocks in a real test world.
- Named saved snapshot loads after a real disk round trip; missing/invalid names
  and unauthorized execution leave the world unchanged.
- Inclusive one-block and reversed-corner regions behave correctly.
- Block entities restore captured contents when enabled and preserve their
  existing contents when disabled; diff_only handles states and entity data.
- The normal server playback route and dedicated-server-compatible class boundary
  are tested; local client-only block writes are not an implementation.
- The actual editor can configure/save/reopen the clip and its options. Inspect
  pixels and capture the visible reset before any demonstration is approved.

Removing the registered clip or relabelling the placeholder is not completion
of this repair. Remaining acceptance must be completed before a feature-ready claim.

## Implemented and world-tested; remaining acceptance

Source cc05a931fa3916965629b746a465e5ff08166b8d replaces the logging-only action
with server-side StructureTemplate placement through Replay.applyActions. It
preserves the registered ID and value names, normalizes the inclusive corners,
checks loaded world bounds and game-master permission, and supports both policy
options. A client-only initializer registers the actual action editor with the
structure ID, six coordinates, block-position context actions and two toggles.
Snapshot capture reuses vanilla structure blocks; no second snapshot format or
client-to-server editing protocol was introduced.

The full build, API check, gametest compilation and public/companion boundary
pass. Locally built product SHA256:
6ea87f2896b5eb3092f8f6a40eb1f219aa114b62e940f0a08a64bbabe94710d0.
This local artifact is distinct from the earlier fa96 product and is not released.

[Production run39019305927](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39019305927)
passes all three jobs: Linux, Windows and unchanged Linux product on Windows.
It tests actual server blocks/chests, saved vanilla structure disk round trip,
both policy modes, a repeated unchanged restore, reversed/single-block bounds
and rejected missing/invalid/unauthorized requests. A separate actual editor
test uses Fabric mouse/text input and film save/reopen, with screenshots.
The actual canonical Linux product is
1cee43da1b4fcd40e26994bbc8640fcd0e32c619299dc87f41070bca7624589b;
Windows' own build is c88f30277e442b62c7e56433a4392fe003d2a9d177ef7b41519bc9ab59645bf2.
All nine editor screenshots were inspected. They show truncated narrow-dock
labels. The test's reopen helper also toggled Actions off on its second call;
the saved values/widgets still passed, but this is not active-mode acceptance.

Source ebdb9b245 shortens the labels, adds explanatory option tooltips and makes
the test setup idempotent, with an explicit active-Actions assertion after reopen.
[Follow-up run39019817908](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39019817908)
passes the Linux and Windows source-build jobs. All six editor images were
inspected: labels are readable and the actual Actions timeline remains active
after reopening. Its canonical Linux product is
0e25e5031a2bad726928177b3247560a4af1d4f0cf652d8eb223137913a98d7b;
Windows' own build is46a3337e371b4e1790065aa2253d7091ccd311af71e7d3bf143e6e019582126d.
Against the previous canonical Linux JAR, exactly UIWorldSnapshotClip.class
changes; all server restore bytecode is unchanged.

The unchanged-Linux-input Windows job fails natively before gametests with
NTSTATUS0xC0000005 (-1073741819). No loaded-product receipt or snapshot case
is reached there, and the enabled WER/JVM capture still produces no dump/stack.
This run is red overall; the earlier full three-job pass does not clear this
additional unresolved Windows failure.

The subsequent postmortem-control run39021135850 passes all three jobs using the
same canonical0e25 Linux product, including its unchanged bytes on Windows.
All three frozen-Windows Snapshot editor pictures were inspected: readable
labels, saved values and active Actions after reopen. The native control proves
capture for its own deliberate fault; no new game fatal error occurs in this
run, so the earlier Windows game-crash cause is still unresolved.
Raw case/hash receipt: build/test-evidence/release-audit-20261017/world-snapshot-39019305927-receipt.json,
SHA25651f3fc05874770d3eca5b8b09cef4eb6899847c96ed80c5a4057235ac4a00f15.
Follow-up receipt: world-snapshot-39019817908-receipt.json,
SHA256d57a987b9b2c0d89261a610975c4cbdef93e745650ea73495bc5b29fc4dee785.
Frozen-input follow-up: postmortem-frozen-39021135850-receipt.json,
SHA256f5deb25183798af07fa45a876fc618d560f0282e03de8aba80646a87cba58429.
Dedicated-server startup, visible world-reset capture and the full release audit
remain open. This feature has no completed demonstration or publication approval.
