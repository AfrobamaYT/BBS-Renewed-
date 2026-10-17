# Texture Studio preview and browser zoom

Follow-up: [canvas zoom and automatic fit](PHOTOPEA-CANVAS-2026-10-17.md).
The keyboard zoom scope described below is historical; current Ctrl+Plus/Minus/0
controls the Photopea document, while Ctrl+wheel controls the browser page.

User report: a Steve Bends replay displayed a different rig with the skin applied;
Photopea looked small and Ctrl zoom did not work.

The installed product `dad01946bda5827e146d7153b7bbcc55e74f30268f296ea6e188bf74cf612eba`
has the same three Texture Studio classes as the public 26.3 candidate at `700e4f457`.
The old preview read the remembered model without consulting the edited form/replay.
An event carrying Ctrl+Minus was consumed by the BBS UI-scale shortcut, changing
scale 1.9881799 to 1.7381799 despite the embedded browser reporting focus.

The preview now prefers the open ModelForm editor, then the selected replay, then
the remembered standalone model. The embedded browser participates in BBS focus;
clicking the preview releases it. Keyboard modifiers use the dispatched event.
Page zoom uses CEF IN/OUT/RESET commands without synchronously querying CEF on the
Minecraft render thread. Other keys/wheel events still reach the document.

Owned Vulkan production instance, Java 25.0.3, PID 1654739, Studio 7966:

- Cold product SHA256: `42e0c1e1c612fd4240766291bbce61bb540682c8523778d665a405d06952ff08`.
- Steve Bends selected while the remembered model is Verity: preview is
  `player/steve_bends`. Opening the form editor for `steve_bends_ik` overrides it;
  closing that editor restores the replay as the next opening's source.
- Browser texture is 1253x934 pixels for a 630x470 UI area at scale 1.9881799.
  CEF reports 1253x934 CSS pixels and DPR 1 at 100 percent. No fixed 720p limit.
- Ten key actions cover reset, repeated minus, plus, German plus and keypad plus/minus.
  CEF/DOM readback reports 100 -> 90 -> 80 -> 90 -> 100 -> 110 -> 100 -> 110 -> 100 percent.
  BBS scale stays 1.9881799. Key dispatch takes less than 1 ms in these cases.
- XTest Control on private X109 is observed by SDL; actual MouseHandler scroll
  changes CEF/DOM 100 -> 110 -> 100 percent. Control is released afterward.
- Clicking the preview clears browser focus; the replay texture Link is unchanged.
  Repeated dialog close/reopen survives. The initial cleanup null-context regression
  was caught in the owned instance, removed and retested with a fresh product launch.
- Both artifacts build; companion boundary, Blender provenance release check and
  diff whitespace check pass. No user process was modified by these tests.

Raw native receipts and Java bodies are in `build/test-evidence/photopea/`.
The native 28-second recording/contact sheets are in
`build/bbs-observation/watch-a400e9ee058c35178a62d350a565f7a227bab74b97ea871e0a2047be96b0f8d6/`.
Samples 0-11 seconds were viewed and show the intended zoom changes and Steve preview.
This verifies the named Linux/Vulkan cases, not Windows or arbitrary model/material layouts.
The correction is public product code; all readback/test code remains outside the public JAR.
