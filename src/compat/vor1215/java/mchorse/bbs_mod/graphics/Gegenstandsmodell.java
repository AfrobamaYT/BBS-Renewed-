package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.compat.gpu.SofortSammler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.function.Function;

/**
 * Vor 1.21.5 (ersetzt die vor261-Fassung): ein Gegenstand ueber 1.21.4s ItemStackRenderState,
 * sofort in den Puffer des SofortSammlers. Die Umfaerbung einer BBS-Form legt sich um jeden
 * Puffer, den der Zustand anfordert; Glanzpuffer tragen keine Farbe, dort bleibt sie wirkungslos.
 */
public final class Gegenstandsmodell
{
    private static final ItemStackRenderState ZUSTAND = new ItemStackRenderState();

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

        Minecraft.getInstance().getItemModelResolver().updateForTopItem(ZUSTAND, stapel, kontext, false, welt, null, 0);
        ZUSTAND.render(pose, umfaerbung == null ? puffer : (typ) -> umfaerbung.apply(puffer.getBuffer(typ)), licht, overlay);
    }
}
