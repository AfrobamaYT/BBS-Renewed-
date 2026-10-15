package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.FramebufferUtils;
import mchorse.bbs_mod.graphics.GpuBackend;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Run before the native world pass: offscreen contents cannot be rendered inside that pass. */
public final class FramebufferFormPreparation
{
    private static long epoch;
    private static final ReferenceQueue<FramebufferFormRenderer> retired = new ReferenceQueue<>();
    private static final List<Owner> owners = new ArrayList<>();
    private static final class Owner extends WeakReference<FramebufferFormRenderer>
    {
        final List<Framebuffer> buffers;
        Owner(FramebufferFormRenderer renderer, List<Framebuffer> buffers)
        {
            super(renderer, retired); this.buffers = buffers;
        }
    }

    private FramebufferFormPreparation() {}

    public static void beginFrame()
    {
        FramebufferUtils.outsidePass("Framebuffer form frame preparation");
        epoch++;
        for (Owner owner : owners)
        {
            FramebufferFormRenderer renderer = owner.get();
            if (renderer != null) renderer.beginPreparationFrame(epoch);
        }
        for (Owner owner; (owner = (Owner) retired.poll()) != null; )
        {
            owners.remove(owner);
            for (Framebuffer framebuffer : owner.buffers)
            {
                BBSModClient.getFramebuffers().framebuffers.values().removeIf(value -> value == framebuffer);
                framebuffer.delete();
            }
            owner.buffers.clear();
        }
    }

    static long epoch()
    {
        return epoch;
    }

    static void register(FramebufferFormRenderer renderer, List<Framebuffer> buffers)
    {
        owners.add(new Owner(renderer, buffers));
    }

    public static void prepare(Form root, FormRenderingContext context)
    {
        if (root == null || GpuBackend.isOpenGl()) return;
        FramebufferUtils.outsidePass("Framebuffer form content preparation");
        epoch();
        prepare(root, context, new IdentityHashMap<>());
    }

    private static void prepare(Form form, FormRenderingContext context, IdentityHashMap<Form, Boolean> path)
    {
        if (path.put(form, Boolean.TRUE) != null) throw new IllegalStateException("Cyclic framebuffer form graph");
        form.applyStates(context.getTransition());
        int previousLight = context.light;
        try
        {
            if (!form.visible.get()) return;
            float blend = 1F - mchorse.bbs_mod.utils.MathUtils.clamp(form.lighting.get(), 0F, 1F);
            int u = context.light & 65535, v = context.light >> 16 & 65535;
            u = (int) mchorse.bbs_mod.utils.interps.Lerps.lerp(u, net.minecraft.util.LightCoordsUtil.pack(15, 0), blend);
            context.light = u | v << 16;
            FormRenderer<?> renderer = FormUtilsClient.getRenderer(form);
            if (renderer instanceof FramebufferFormRenderer framebuffer)
            {
                framebuffer.prepareNative(context);
                return; // its actual offscreen traversal prepares nested framebuffer forms
            }
            for (BodyPart part : form.parts.getAllTyped())
            {
                if (part.getForm() == null) continue;
                IEntity previous = context.entity;
                context.entity = part.getRenderEntity(previous);
                try { prepare(part.getForm(), context, path); }
                finally { context.entity = previous; }
            }
        }
        finally
        {
            context.light = previousLight;
            form.unapplyStates();
            path.remove(form);
        }
    }
}
