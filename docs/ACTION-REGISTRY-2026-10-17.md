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

## Measured repair

Source30060eb1c passes all three jobs of production run39023007388: Linux,
Windows and the unchanged Linux product on Windows. All twenty offered action
IDs select their actual editors, retain their input type ID and native payload,
and copy to the correct client class. The canonical Linux product is
d7f3f2740ba9e86679dada9ccd2150c869396025053e2021c5dc9b7b91327161;
Windows' own build is e7d5821ec19eb9a4ae66e06fe0d0c464e8c6f0672ae2064905cff1ccd400a05d.
All twelve source-build editor images were inspected. Earlier NullPointerExceptions
are absent from this case. This accepts selection and type/data compatibility,
not the disabled effects themselves.

Run39023507738 adds an actual Block Animation offset drag and native film save/load.
Linux and Windows source jobs pass: X changes0 to2.25 through actual mouse input,
Y remains10, Z remains0, and the saved film retains those values. Both images
were inspected. The frozen-input Windows result is pending at this record.
Raw receipts: action-registry-after-39023007388.json and
block-animation-editor-39023507738.json under the public audit evidence directory.
