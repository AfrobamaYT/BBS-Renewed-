- [x] Shaders for multi-link preview
- [ ] Shaders for subtitle renderer
- [x] Form renderers
  - [x] Model rendering
    - [x] Model aren't reloading
    - [x] Fix normals
  - [x] Body part rendering
  - [x] Extruded form rendering
    - [x] Fix lack of diffuse lighting
  - [x] Fix label form rendering
  - [x] Fix particle form rendering
- [x] Camera editor
  - [x] Apply roll
  - [x] Apply FOV
- [x] Film controller
  - [x] Player controller
  - [x] Render actors
  - [ ] Shadow
- [ ] Minema
  - [ ] Framebuffer resize
  - [x] Tick synchronization
  - [x] Video recording
- [ ] Entity selectors in EntityClip
- [x] Framebuffer picking
- [x] Particles
  - [x] Collision
  - [x] Lighting
  - [x] Rendering
- [x] Setup watch dog
- [x] Model blocks
  - [x] Add F3 indicators
  - [x] Add outline in 3d
  - [x] Add click picking
  - [x] Move to cursor (with ray tracing)
  - [x] Model block shadows
  - [x] Immersive editing
  - [x] Display in hand
- [ ] More forms
  - [x] Block form
  - [ ] Item form
  - [ ] Structure form
- [x] File drag and dropping
- [x] Metamorph
  - [x] Morph picker

Core features:

- [ ] Action system
- [ ] Emoticons + S&B

Useful user suggestions:

- [ ] Sample lighting value at (model block)
- [ ] Add gizmos to model block
- [ ] Kirah's suggestions model block screen
- [ ] Add gizmos to film editor
- [ ] Rethink keyframe addition and removal
- [ ] Async model and texture loading
- [ ] Update model forms when models get reloaded

Considerations:

- [ ] Direction XYZ Snowstorm
- [ ] Benchbuster

Epic features

- [ ] Framebuffer form

### A couple of notes about film controller

Key movement:

    InputUtil.Key key = InputUtil.fromKeyCode(context.getKeyCode(), context.getScanCode());
    
    if (context.getKeyAction() == KeyAction.RELEASED)
    {
        KeyBinding.setKeyPressed(key, false);
    }
    else
    {
        KeyBinding.setKeyPressed(key, true);
        KeyBinding.onKeyPressed(key);
    }

Mouse movement can be just pass the delta in: 

    player.changeLookDirection(dX, dY)

As for mouse button: 

    KeyBinding.setKeyPressed(Type.MOUSE.createFromCode(button), bl);

    if (!notReleased) 
    {
        KeyBinding.onKeyPressed(Type.MOUSE.createFromCode(button));
    }