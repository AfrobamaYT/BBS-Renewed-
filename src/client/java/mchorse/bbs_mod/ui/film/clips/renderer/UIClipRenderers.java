package mchorse.bbs_mod.ui.film.clips.renderer;

import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.HashMap;
import java.util.Map;

/**
 * How each kind of clip draws its strip on a timeline. The registry is static: a new instance is
 * made for every timeline, so filling it in the constructor left addons nowhere to register (BBS
 * FS 669329b8b, Wemppy4/bbs-fs, MIT).
 */
public class UIClipRenderers
{
    private static final Map<Class, IUIClipRenderer> RENDERERS = new HashMap<>();

    static
    {
        register(AudioClientClip.class, new UIAudioClipRenderer());
    }

    private final UIClipRenderer defaultRenderer = new UIClipRenderer();

    public static void register(Class key, IUIClipRenderer renderer)
    {
        RENDERERS.put(key, renderer);
    }

    public <T extends Clip> IUIClipRenderer<T> get(T clip)
    {
        IUIClipRenderer renderer = RENDERERS.get(clip.getClass());

        return renderer == null ? this.defaultRenderer : renderer;
    }
}
