# Action selection and saved-ID compatibility

Production run39022116322, source833b722ae, reproduces five unusable action
selections on the actual Linux0e25 product. All offered actions are constructed
through their registered IDs, round-tripped through native DataStorage, copied,
added to an actual replay and selected through UIClipsPanel.pickClip.
The actions are disabled; no Physics/Gore/animation effect is evaluated.

- bbstools:model_fracture, bbstools:block_destruction, bbstools:ragdoll_proxy and
  bbstools:block_animation instantiate common data classes. Their selected client
  handler is the empty ActionClip base and their editor panel is missing.
- bbs:block_animation instantiates the implemented client class but also has
  no registered editor panel.
- The actual selector emits a NullPointerException at UIClip.setUndoId for
  these paths. Other offered actions retain data and select their editors.

Raw receipt: build/test-evidence/release-audit-20261017/action-registry-before-39022116322.json.

## Repair and required proof

Use the existing implemented client classes for the four older IDs, with distinct
client subclass identities. MapFactory has one inverse ID per class; reusing a
single class for two IDs would change which type gets written on save. The small
subclasses inherit the real action and preserve their own type on copy. Their
UI factories use the existing editors. No effect body is duplicated.

The Block Animation editor needs all six region coordinates, all three offsets
and duration, including normal multi-edit/undo callbacks and position contexts.
Register it for both Block Animation IDs. Keep existing common server classes,
canonical IDs and serialized value names intact.

The full build, API and public/companion boundary pass locally. Runtime selection,
native serialization, copy class and exact input-ID preservation must pass for
every offered action after the fix. Inspect actual editor images.
These checks do not prove effect rendering, PhysicsMod lifecycle or motion parity.
In particular, the Block Animation single-block rejection, unbounded region loop
and swallowed render exceptions are separate source findings awaiting real
effect/capture tests; they must not disappear behind a green registry check.
