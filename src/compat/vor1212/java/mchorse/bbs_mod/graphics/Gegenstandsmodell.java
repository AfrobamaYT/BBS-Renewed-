package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * Vor 1.21.2 (ersetzt die vor1215-Fassung): 1.21.1 kennt keine Gegenstands-Render-Zustaende,
 * ItemRenderer.renderStatic zeichnet direkt; Umfaerbung wie in der vor1215-Fassung um jeden Puffer.
 */
public final class Gegenstandsmodell
{
    private Gegenstandsmodell()
    {}

    public static void einreichen(ItemStack stapel, ItemDisplayContext kontext, PoseStack pose,
        SubmitNodeCollector sammler, Function<VertexConsumer, VertexConsumer> umfaerbung,
        Level welt, int licht, int overlay)
    {
        if (!RenderSystem.isOnRenderThread())
        {
            throw new IllegalStateException("Gegenstandsmodell.einreichen laeuft nur im Renderthread");
        }

        if (stapel == null || stapel.isEmpty())
        {
            return;
        }

        MultiBufferSource puffer = SofortSammler.puffer(sammler);

        Minecraft.getInstance().getItemRenderer().renderStatic(stapel, kontext, licht, overlay, pose,
            umfaerbung == null ? puffer : (typ) -> umfaerbung.apply(puffer.getBuffer(typ)), welt, 0);
    }
}
