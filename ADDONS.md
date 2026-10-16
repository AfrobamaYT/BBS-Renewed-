# Writing an addon for BBS

BBS has an addon API: a package of contracts an addon can build against, and a set of events BBS
posts while it registers its own things, which is where an addon registers its own.

## What is and isn't a contract

Everything in `mchorse.bbs_mod.api` and its sub-packages is a contract. Nothing else is.

The rest of BBS moves without notice — a class gets renamed, a field goes private, a method grows
a parameter — and an addon that reached into it breaks **silently, in the game rather than on the
build**. That is not a threat, it is just what a mixin into another mod's internals costs.

The whole public surface of the API is dumped into [`api/bbs-api.txt`](api/bbs-api.txt), which
lives in this repository. Any change to it shows up in the diff of the commit that made it, and
`gradlew apiCheck` (which `build` depends on) fails when the two drift apart. `BBSApi.VERSION` is
bumped whenever a change in there is one an addon cannot survive.

## Getting in

An addon implements `mchorse.bbs_mod.api.BBSAddonMod` — an empty marker — and declares it in its
`fabric.mod.json`:

```json
"entrypoints": {
    "bbs-addon": ["com.example.MyAddon"]
}
```

`bbs-addon` is read at the very top of BBS's initialization, on both sides. This build has no
separate client entrypoint yet: an addon whose subscribers touch client classes
(`mchorse.bbs_mod.api.client.events`) runs on a client only.

Methods annotated with `@Subscribe` and taking exactly one argument are then called with the
events of that argument's type:

```java
public class MyAddon implements BBSAddonMod
{
    @Subscribe
    public void onSettings(RegisterSettingsEvent event)
    {
        BBSApi.requireVersion("myaddon", 1);
        /* ... */
    }
}
```

## The registration phase

BBS registers its own things, then posts an event for each registry, in this order. Main side:
`RegisterKeyframeFactoriesEvent`, `RegisterFormsEvent`, `RegisterCameraClipsEvent`,
`RegisterActionClipsEvent`, `RegisterSettingsEvent`, `BBSReadyEvent`. Client side:
`RegisterClientSettingsEvent`, `RegisterFormRenderersEvent`, `RegisterFormEditorsEvent`,
`RegisterClipPanelsEvent`, `RegisterKeyframeEditorsEvent`, `RegisterValueWidgetsEvent`,
`BBSClientReadyEvent`. A form's renderer and editor panel are found by its class and then by the
classes it extends, so a form extending one of BBS's own draws and edits like its parent until it
registers its own. (BBS FS 83a15831a)

Five registries read on first use post their event right before that first read:
`RegisterModelLoadersEvent` (loaders are suppliers, rebuilt on every asset reload),
`RegisterFormSectionsEvent` (palette tabs as factories, same reason), `RegisterKeybindsEvent`
(a class of `KeyCombo` fields, read like BBS's `Keys`), and with the client registries
`RegisterClipRenderersEvent` and `RegisterImportersEvent`. (BBS FS 669329b8b)

## A film's life

`FilmEvents` (`mchorse.bbs_mod.api.client.events`) are plain Fabric events, since they fire every
tick and frame: `CREATED` when a film's actors are built, `TICK_BEFORE` and `TICK_AFTER` around
each update of them, `RENDER_AFTER` after they are drawn, `SHUTDOWN` when a film stops. Register in
a static block or the entry point. `Films.getControllers()` lists the films playing. (BBS FS
4677a881c)

## Forms and the preview

`FormRenderEvents.BEFORE`/`AFTER` wrap the drawing of every form; `AFTER` runs even when the
form's renderer threw. `RegisterFormModifiersEvent` adds a step to the making of every form, so an
addon can put a value of its own on BBS's forms: it is then saved, read, keyframed and gets a
track like any other (use your namespace). `RegisterPreviewOverlaysEvent` puts an element of yours
over the film preview, one that takes the mouse too. `mchorse.bbs_mod.api.compat.VanillaAccess`
reads and writes the living entity's item use, its living flags and its spin-attack ticks, which
an addon cannot reach with its own access widener. (BBS FS f37a1b632)

## Versioning

Declare the BBS version your addon needs in `fabric.mod.json`, so the loader refuses a mismatch
before any code runs:

```json
"depends": { "bbs": ">=2.4" }
```

and call `BBSApi.requireVersion("myaddon", <api version>)` from your entry point for the case
where it got in anyway.

## The snapshot

`api/bbs-api.txt` holds the public signatures of everything in `mchorse.bbs_mod.api`, read with
`javap`. `gradlew build` runs `apiCheck`, which fails when the classes and the file disagree; after
an intended change, `gradlew apiDump` rewrites the file and it is committed with the change.

From BBS FS 7bf588770 (Wemppy4/bbs-fs, MIT).
