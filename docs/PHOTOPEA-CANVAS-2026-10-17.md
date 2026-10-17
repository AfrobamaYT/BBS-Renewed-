# Texture Studio canvas zoom follow-up

The user reported that the skin opens as a tiny image and cannot be enlarged.
The earlier keyboard fix controlled CEF page zoom, not the document canvas.

After importing the PNG, the host now calls Photopea's `app.UI.fitTheArea()` once.
View commands share the existing done-message sequencing with PNG import/export.
Ctrl+Plus/Minus (including German plus and keypad) use `app.UI.zoomIn()/zoomOut()`;
Ctrl+0 fits the image, Ctrl+Alt+0 selects actual pixels. Alt+wheel uses the same
document-view commands. Ctrl+wheel still controls the complete browser page.
Other embedded pages retain the earlier page-zoom behavior.

Official interfaces: [viewing state](https://www.photopea.com/learn/scripts),
[message completion](https://www.photopea.com/api/live),
[document navigation](https://www.photopea.com/learn/navigation).

Measured in the owned Linux/NVIDIA Vulkan production client PID 1693763,
Studio 7966, private X109, Java 25.0.3:

- Initial 64x64 texture is visibly fitted to the large workspace.
- Five further close/reopen cycles all load a large fitted image and retain Steve Bends.
  All five screenshots were viewed together in `canvas-reopens.png`.
- Eight keyboard actions shrink, enlarge and fit the canvas. Browser readback remains
  zoom level 0, viewport 1253x934, DPR 1; BBS scale remains 1.9881799.
  Actual zoom changes were inspected in the 25-second native recording/contact sheets.
- XTest Alt is observed by SDL on private X109. Real MouseHandler wheel dispatch
  changes the canvas through the Photopea view API. Before/after PNGs were viewed;
  the browser viewport and BBS scale remain unchanged. Alt is released afterward.
- Pillow RGBA comparison of Photopea's exported PNG with the original skin has no
  pixel differences and preserves the 64x64 image dimensions.
- The Java change was also applied with the guarded hotswap tool on the owned client.
  Both JARs build; companion-boundary, Blender release-provenance and diff checks pass.

Raw evidence: `build/test-evidence/photopea/canvas-final-results.json`,
`build/test-evidence/photopea/canvas-reopens.png`, `build/test-evidence/photopea/canvas-after.png`.
Recording: `build/bbs-observation/watch-875eccc7136fc18ebceb5c31a2b050fb121d3001306f5fe18e38d70baf82c5a7/`.
This verifies the named Linux/Vulkan cases; Windows and arbitrary document types are untested.
