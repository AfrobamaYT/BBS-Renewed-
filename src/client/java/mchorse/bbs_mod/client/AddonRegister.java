package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.BBSClientReadyEvent;
import mchorse.bbs_mod.api.client.events.RegisterClipPanelsEvent;
import mchorse.bbs_mod.api.client.events.RegisterFormEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterFormRenderersEvent;
import mchorse.bbs_mod.api.client.events.RegisterKeyframeEditorsEvent;
import mchorse.bbs_mod.api.client.events.RegisterValueWidgetsEvent;

/**
 * The client registries' turn for addons, from BBS FS 83a15831a (Wemppy4/bbs-fs, MIT). The
 * registries still fill themselves in static blocks; an addon's first {@code register} call
 * initialises the class, so BBS's own entries are always in before the addon's.
 */
public final class AddonRegister
{
    /** Layers addons put over the film preview: factories, as every film editor builds its own preview (BBS FS f37a1b632) */
    public static final java.util.List<java.util.function.Function<mchorse.bbs_mod.ui.film.UIFilmPreview, mchorse.bbs_mod.ui.framework.elements.UIElement>> VORSCHAU_EBENEN = new java.util.ArrayList<>();

    /** The CEM models of the resource packs; null until the client has started */
    private static mchorse.bbs_mod.utils.resources.CemSourcePack cem;

    private AddonRegister()
    {}

    /** The first reload runs before the client has started, when both packs index themselves as they are made */
    private static void packsNeuLesen()
    {
        if (cem == null) return;

        for (mchorse.bbs_mod.resources.ISourcePack pack : BBSMod.getProvider().getPacks("assets"))
        {
            if (pack instanceof mchorse.bbs_mod.utils.resources.MinecraftSourcePack minecraft) minecraft.setupPaths();
        }

        cem.reindex();
        mchorse.bbs_mod.cubic.jem.VanillaRigs.clear();
        mchorse.bbs_mod.BBSModClient.getModels().forgetFolder("cem/");
        mchorse.bbs_mod.BBSResources.formulareNeu();
    }

    public static void client()
    {
        /* OptiFine CEM models of the enabled resource packs, served under assets:models/cem/ once the
         * packs are loaded; listed after BBS's own sources, so a folder of the user's wins (BBS FS 4d7aa6a43) */
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register((client) -> BBSMod.getProvider().register(cem = new mchorse.bbs_mod.utils.resources.CemSourcePack()));

        /* Every resource reload (a pack switched on or off, F3+T) reads the packs again: the watchdog
         * only sees BBS's own folder, and a link a pack serves has no file behind it (BBS FS e9d4a885f) */
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES).registerReloadListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "resource_pack_models"), (net.minecraft.server.packs.resources.ResourceManagerReloadListener) (manager) -> packsNeuLesen());

        mchorse.bbs_mod.forms.FormUtils.spurFilter = (form, value) -> !(form instanceof mchorse.bbs_mod.forms.forms.ModelForm model && value.getId().startsWith("cem_"))
            || mchorse.bbs_mod.forms.renderers.ModelFormRenderer.getModel(model) != null && mchorse.bbs_mod.forms.renderers.ModelFormRenderer.getModel(model).cemAnimation != null;

        /* The video clip plays on the client only, as the audio clip does (BBS FS 3efe13ebc) */
        BBSMod.getFactoryCameraClips().register(mchorse.bbs_mod.resources.Link.bbs("video"), mchorse.bbs_mod.camera.clips.misc.VideoClientClip.class, BBSMod.getFactoryCameraClips().getData(mchorse.bbs_mod.resources.Link.bbs("video")));
        BBSMod.events.post(new RegisterFormRenderersEvent());
        BBSMod.events.post(new RegisterFormEditorsEvent());
        BBSMod.events.post(new RegisterClipPanelsEvent());
        BBSMod.events.post(new RegisterKeyframeEditorsEvent());
        BBSMod.events.post(new RegisterValueWidgetsEvent());
        BBSMod.events.post(new mchorse.bbs_mod.api.client.events.RegisterClipRenderersEvent());
        BBSMod.events.post(new mchorse.bbs_mod.api.client.events.RegisterImportersEvent());
        BBSMod.events.post(new mchorse.bbs_mod.api.client.events.RegisterPreviewOverlaysEvent());
        BBSMod.events.post(new mchorse.bbs_mod.api.client.events.RegisterFrameOverlaysEvent());
        BBSMod.events.post(new BBSClientReadyEvent());
    }

    /** The entry of a form's class, else of the nearest class it extends: a subclass draws and edits like its parent */
    public static <V> V nachKlasse(java.util.Map<Class, V> map, Class<?> klasse)
    {
        for (Class<?> c = klasse; c != null; c = c.getSuperclass())
        {
            V wert = map.get(c);

            if (wert != null) return wert;
        }

        return null;
    }
}
