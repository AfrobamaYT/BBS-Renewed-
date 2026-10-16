package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * Posted on the client once BBS has registered the keyframe editors of its own value types.
 *
 * <p>An editor is picked by the value's factory.</p>
 */
public class RegisterKeyframeEditorsEvent
{
    public <T> void register(IKeyframeFactory<T> factory, UIKeyframeFactory.IUIKeyframeFactoryFactory<T> editor)
    {
        UIKeyframeFactory.register(factory, editor);
    }
}
