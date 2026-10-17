# Camera/media clip selection and data audit

Source723f30724 enumerates the actual camera factory in a production client,
initializes position clips through their normal fromCamera path, then uses
native DataStorage, copy and the real UIClipsPanel selector for every offered ID.
Clips remain disabled. The complete resulting camera timeline is saved and
loaded through the actual film repository.

Linux run39027375264 on canonical9387 passes all21 offered IDs, with no failed
selector, payload, type-ID or copy-class checks. The full timeline save/reload
also passes. This includes audio, subtitle, image, video, damage_hitbox and
bbstools_add_camera. The Windows source-build job also passes all21 and full film
save/reload. The unchanged-input Windows job exits natively with0xC0000005 before
these tests, so the run is red overall. Its postmortem collector has only the
deliberate control-process dumps, not the terminal JVM context. No third-job
camera acceptance or general Windows stability follows from this run.
Raw receipt: camera-registry-39027375264.json in the public audit evidence directory.

This does not prove playable media, camera evaluation or a separate camera
viewport. BBSToolsAddCameraClip deliberately delegates its pose to the preview
manager rather than altering the main camera; an empty main-camera apply method
alone is not a reproduced feature failure. Actual viewport behavior needs its
own evaluated-state and visual proof.

Raw source-build output is under ci-39027375264 in the public audit evidence
directory. A successful editor constructor is not full functionality acceptance.
