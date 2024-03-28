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
- [ ] Film controller
  - [ ] Player controller
  - [ ] Render actors
- [ ] Video recording
  - [ ] Framebuffer resize
- [ ] Entity selectors in EntityClip
- [x] Framebuffer picking
- [ ] Particles
  - [ ] Collision
  - [x] Lighting
  - [x] Rendering
- [x] Setup watch dog
- [x] Model blocks
  - [x] Add F3 indicators
  - [x] Add outline in 3d
  - [x] Add click picking
  - [x] Move to cursor (with ray tracing)
  - [x] Model block shadows
  - [ ] Immersive editing
- [ ] More forms
  - [ ] Block form
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

Considerations:

- [ ] Direction XYZ Snowstorm
- [ ] Benchbuster

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