# Roadmap

For BBS mod, there would be three stages: BBS Studio Port, Old Core Adaptation, 
and Missing Features. Here is the breakdown of these stages:

## BBS Studio Port

This is the stage where I port majority of useful features from BBS Studio into a 
Minecraft Fabric mod. Besides that, I'll also add any features that are essential to 
the port (like model blocks, although that's also part of BBS Studio).

Beside that, here is a list to finish:

- [x] Optimize keyframe editor rendering
- [x] Clean up the UI
- [x] Finish remaining features
  - [x] Shaders for subtitle renderer
  - [x] Shadow for film controller
  - [x] Fix bundled models work incorrectly
- [x] Implement proper data managers
- [x] Bring back language editor stuff
- [x] Implement language picker thing
- [x] ffmpeg warning
- [x] Fix can't exit the keyframe graph editor
- [x] Check out player models
- [x] Make last undo unmergeable if 5 seconds passed or smth
- [x] Fix Iris bugs

## Old Core Adaptation

In this stage, I'll implement any missing features from my mods that are core 
experience. Like following:

- [ ] Action system
  - [ ] In-game recording
- [ ] Damage control
- [ ] Emoticons and S&B
- [ ] BB gun?
- [ ] Vanilla integrations
  - [ ] Item holding
  - [ ] Swimming
  - [ ] Vanilla-like animator
- [ ] Audio editor
- [ ] Structure form
- [ ] Mob form

## Missing features

And finally, once the BBS Studio Port and Old Core Features are done, it's time to 
implement any features that are missing for the user base. Anything that is going to 
be used by a lot of people. No niche features.

The list is TBD.

# Release

Once all of these stages are passed, and people are happy with the tools for the most 
part. It's time preparing for the release. For the release, I need to record a tutorial 
series that teaches how to practically use BBS mod to record machinimas by examples.

- [ ] What is BBS mod
- [ ] Obtain the mod and setup
- [ ] Recording characters
- [ ] Setting up cameras

### Miscellaneous

Bugs:

- [x] Reset camera when switching to another dimension
- [ ] Save films somehwere when the server doesn't have BBS mod

Useful user suggestions:

- [ ] Bezier support for Blockbench models
- [ ] Sample lighting value at (model block)
- [ ] Add gizmos to model block
- [ ] Kirah's suggestions model block screen
- [ ] Add gizmos to film editor
- [ ] Rethink keyframe addition and removal
- [ ] Async model and texture loading
- [ ] Update model forms when models get reloaded
- [ ] In-game mode of recording (like in Blockbuster mod)
- [ ] Lighting option
- [ ] Model block hitbox
- [ ] Add additional vertical mode camera
- [ ] Thumbnail of the form
- [ ] Demorph key bind (in the world)
- [ ] Add /bbs morph generation from a form
- [ ] Flight mode speed
- [ ] Change moving around to left click and mouse wheel button to fov and roll
- [ ] Add button to toggle orbit mode, orbit perspective and make context menu for perspectives
- [ ] Add an icon when in orbit mode
- [ ] Add loop option to /bbs film play
- [ ] Add /bbs film stop
- [ ] Teleport to model block
- [ ] Morph entity selectors 
- [ ] Base form options for shadows (vanilla and iris)
- [ ] Add color to extruded form
- [ ] Add step interpolation / break down a segment into more

Considerations:

- [ ] Direction XYZ Snowstorm
- [ ] Benchbuster
- [ ] Wave files cues
- [ ] mp3/mp4 to wav via drag and dropping
- [ ] Blockbench like interface (moving panels around)
- [ ] .geo.json + .animation.json import

Epic features

- [ ] Framebuffer form 