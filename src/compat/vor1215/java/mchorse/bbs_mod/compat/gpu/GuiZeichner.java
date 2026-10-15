package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.Geraet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Zeichnet ein GUI-Buendel (gleiche Pipeline, Texturen, Schere) vor 1.21.6 sofort ins Hauptziel,
 * mit 1.21.4s GUI-Projektion und -Modelview, so wie Mojangs GuiRenderer ab 1.21.6 es tut.
 */
public final class GuiZeichner
{
    private GuiZeichner()
    {}

    public static void zeichnen(RenderPipeline pipeline, TextureSetup texturen, ScreenRectangle schere, MeshData netz)
    {
        MeshData.DrawState zustand = netz.drawState();
        GpuBuffer ecken = Geraet.get().createBuffer(() -> "bbs-gui-ecken", PufferNutzung.USAGE_VERTEX, netz.vertexBuffer());
        GpuBuffer eigeneIndizes = null;
        GpuBuffer indizes;
        VertexFormat.IndexType typ;

        if (netz.indexBuffer() != null)
        {
            eigeneIndizes = Geraet.get().createBuffer(() -> "bbs-gui-indizes", PufferNutzung.USAGE_INDEX, netz.indexBuffer());
            indizes = eigeneIndizes;
            typ = zustand.indexType();
        }
        else
        {
            RenderSystem.AutoStorageIndexBuffer folge = RenderSystem.getSequentialBuffer(zustand.mode());

            indizes = folge.getBuffer(zustand.indexCount());
            typ = folge.type();
        }

        RenderTarget ziel = Minecraft.getInstance().getMainRenderTarget();

        try (RenderPass pass = Geraet.get().createCommandEncoder().createRenderPass(() -> "bbs gui", ziel.getColorTextureView(), OptionalInt.empty(),
            ziel.useDepth ? ziel.getDepthTextureView() : null, OptionalDouble.empty()))
        {
            pass.setPipeline(pipeline);
            Vorgaben.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", Vorgaben.dynamicUniforms().writeTransform(RenderSystem.getModelViewMatrix(),
                new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), RenderSystem.getTextureMatrix()));

            if (texturen.texure0() != null) pass.bindTexture("Sampler0", texturen.texure0(), texturen.sampler0());
            if (texturen.texure1() != null) pass.bindTexture("Sampler1", texturen.texure1(), texturen.sampler1());
            if (texturen.texure2() != null) pass.bindTexture("Sampler2", texturen.texure2(), texturen.sampler2());

            if (schere != null)
            {
                double faktor = Minecraft.getInstance().getWindow().getGuiScale();
                int hoehe = ziel.height;

                pass.enableScissor((int) (schere.left() * faktor), (int) (hoehe - schere.bottom() * faktor),
                    Math.max(0, (int) (schere.width() * faktor)), Math.max(0, (int) (schere.height() * faktor)));
            }

            pass.setVertexBuffer(0, ecken);
            pass.setIndexBuffer(indizes, typ);
            pass.drawIndexed(0, 0, zustand.indexCount(), 1);
        }
        finally
        {
            ecken.close();

            if (eigeneIndizes != null) eigeneIndizes.close();

            netz.close();
        }
    }
}
