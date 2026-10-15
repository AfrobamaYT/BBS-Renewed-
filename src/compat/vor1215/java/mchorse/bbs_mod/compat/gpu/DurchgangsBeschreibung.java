package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import net.minecraft.util.ARGB;
import org.joml.Vector4fc;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** 26.2s Durchgangsbeschreibung auf dem Encoder vor 1.21.5 (ein Farbanhang, Render-Area als Schere). */
public final class DurchgangsBeschreibung
{
    private DurchgangsBeschreibung()
    {}

    public static RenderPass erzeugen(CommandEncoder encoder, RenderPassDescriptor descriptor)
    {
        RenderPassDescriptor.Attachment<Optional<Vector4fc>> farbe = null;
        int benutzt = 0;

        for (RenderPassDescriptor.Attachment<Optional<Vector4fc>> anhang : descriptor.colorAttachments())
        {
            if (anhang != null)
            {
                if (farbe == null) farbe = anhang;

                benutzt++;
            }
        }

        if (farbe == null) throw new UnsupportedOperationException("Durchgang ohne Farbanhang ist vor 1.21.5 nicht gebaut: " + descriptor.label().get());
        if (benutzt > 1) throw new UnsupportedOperationException("Durchgang mit " + benutzt + " Farbanhaengen ist vor 1.21.5 nicht gebaut: " + descriptor.label().get());

        OptionalInt farbeLoeschen = farbe.clearValue().map(c -> OptionalInt.of(argb(c))).orElse(OptionalInt.empty());
        GpuTextureView tiefe = descriptor.depthAttachment() == null ? null : descriptor.depthAttachment().textureView();
        OptionalDouble tiefeLoeschen = descriptor.depthAttachment() == null ? OptionalDouble.empty() : spiegeln(descriptor.depthAttachment().clearValue());
        RenderPass pass = encoder.createRenderPass(descriptor.label(), farbe.textureView(), farbeLoeschen, tiefe, tiefeLoeschen);

        if (descriptor.renderArea() != null && !descriptor.renderArea().fillsTexture(farbe.textureView()))
        {
            pass.enableScissor(descriptor.renderArea().x(), descriptor.renderArea().y(), descriptor.renderArea().width(), descriptor.renderArea().height());
        }

        return pass;
    }

    public static int argb(Vector4fc farbe)
    {
        return ARGB.colorFromFloat(farbe.w(), farbe.x(), farbe.y(), farbe.z());
    }

    /** 26.2 loescht die Tiefe umgekehrt (0 = fern), GL vor 26.1 klassisch (1 = fern). */
    private static OptionalDouble spiegeln(OptionalDouble tiefe)
    {
        return tiefe.isPresent() ? OptionalDouble.of(1D - tiefe.getAsDouble()) : tiefe;
    }
}
