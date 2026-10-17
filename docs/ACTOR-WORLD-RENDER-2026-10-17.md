# Standalone Actor world rendering

The public candidate includes the six-line correction from
`ba31f3278dd91db90679027f0fe895700d2243cd`: ActorEntityRenderer submits its
immediate model geometry to the existing MorphAufschub world-drawing phase.
The original collector submission continues to provide equipment/body-part
features. This is the same two-phase path already used for morphs.

## Independently inspected evidence

The native test used owned PID1182895, instance
`553b242d-f029-48ff-ab68-28e34473a194`, on the public baseline
`b4f75223fd482dc935bee3764b7912db2e483248f434a80029c186bc18056a44`
with companion `c85cbd5bb54a45ba06b6063dfaf983cc8c94bfb890cf9d2d31e0539ce48a1e76`.
The ordinary crawler and the separate addon mesh were absent before the swap,
visible after it, absent after restoring the original class and visible again
after restoring the corrected class. A Villager Replace stayed visible.

The release audit read the raw receipt file, verified its SHA256
`1fabd23921f75e791ca6274f04e81d6e4f35b29f13123d6451a7c1c5098a3ca9`,
read all three successful REDEFINED1 outputs, and inspected these native PNGs:

- Before8290a105: `18ded07f03d2dee7ae891232ddb8a008b17c0ff730b3bbaa614a5fedd2ff671f`.
- Afterea2ddbdc: `e63b324b3b65933c9bfe99ba321d8e40cd5d356438c79da477f8dc21c76a6d97`.
- Revertedf1c7430e: `8c4c193ce3e02957fa58bf8255d2367bb61a60e895acd0caef6d97db8e1e057d`.

Raw images and receipts remain in the owning `bbs-ragdoll-and-gore` worktree
under `build/bbs-observation/images/` and
`build/test-evidence/corrected-native-receipts-20261017.json`.

## Public build and limits

The release worktree's own build passes, including its API and native-source
gates. Its actual ActorEntityRenderer class has SHA256
`d32366141c5aa516e02f94f4147b200d458248e968daed9fc6e906706c7ae410`,
exactly matching the causally tested class. Local product SHA256:
`765766e928933292028ae96054c35a7fd0b26aee48f6cf81cc6604a65c70e96e`.
The actual product/companion boundary check passes against the separately
built retained companion. No private diagnostic classes are shipped.

The A/B/A proof is a class swap on the named baseline, not a cold acceptance
of the entire new public package. Cold production and broader Actor/render
workflows remain subject to the release audit. This correction does not
approve or publish the separate Gore addon or install a user instance.

The subsequent complete production suite passes in all three jobs of
[run39010351840](https://github.com/AfrobamaYT/BBS-Renewed-/actions/runs/39010351840).
Canonical Linux product fa96dc2487ef22d109dad4cf88ee879d0c8c2e873ac845ebb7bdd4576b8c3073
also runs unchanged on Windows. Against the preceding1823 product, only the
renderer and nested state entries differ; d323 remains the exact renderer.
This adds cold startup/world/dashboard and saved-anchor input evidence for
the new package. A cold standalone-Actor visual case remains separate.

Subsequent Linux case39016558132 proves the cold standalone geometry path:
normal server spawn/form synchronization, three resource reloads, same UUID and
position, then removal. Its five unobstructed images were independently inspected.
The first attempt's pictures were still under the loading overlay and are excluded.
Windows diagnostic case39017242286 also passes with all five images inspected,
under explicit ProcDump monitoring. Earlier uninstrumented Windows native crashes
remain unresolved. The subsequent normal run39018217629 passes all three jobs
without ProcDump attached. All ten Windows source-build/frozen-input Actor images
were inspected: present before and after three reloads, absent after removal.
Frozen Windows loads exactly fa96; the Windows build loads
92252a081a69eb892f38dd19030b7b893ee0347ad473d92782bdb27ca794046d.
This extends the scoped lifecycle evidence without establishing a native crash fix.
