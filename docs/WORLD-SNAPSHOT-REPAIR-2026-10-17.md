# World snapshot — registered function not built

Current productfa96 includes WorldSnapshotClip. BBSToolsMod registers it as
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
of this repair. The full release audit remains open while this function is unbuilt.
