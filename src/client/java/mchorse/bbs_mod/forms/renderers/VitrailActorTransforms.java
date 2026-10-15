package mchorse.bbs_mod.forms.renderers;

/** Package access to the same protected transforms used by normal ModelForm rendering. */
public final class VitrailActorTransforms
{
    private VitrailActorTransforms() {}
    public static void apply(ModelFormRenderer renderer, FormRenderingContext context)
    {
        renderer.applyTransforms(context.stack, false, context.getTransition());
        if (context.world != null) renderer.applyTransforms(context.world, false, context.getTransition());
    }
}
