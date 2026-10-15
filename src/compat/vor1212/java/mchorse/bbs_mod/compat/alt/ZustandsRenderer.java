package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Die Zustands-API der Entity-Renderer von 1.21.2+ (createRenderState, extractRenderState,
 * render(Zustand, ...)) ueber 1.21.1s EntityRenderer, der direkt aus der Entitaet zeichnet.
 * Je Bild entsteht der Zustand aus der Entitaet; super.render(Zustand, ...) zeichnet wie
 * 1.21.1 das Namensschild.
 */
public abstract class ZustandsRenderer<T extends Entity, S extends EntityRenderState> extends EntityRenderer<T>
{
    protected ZustandsRenderer(EntityRendererProvider.Context kontext)
    {
        super(kontext);
    }

    public abstract S createRenderState();

    public S createRenderState(T entitaet, float teilTick)
    {
        S zustand = this.createRenderState();

        this.extractRenderState(entitaet, zustand, teilTick);

        return zustand;
    }

    public void extractRenderState(T entitaet, S zustand, float teilTick)
    {
        zustand.bbsEntitaet = entitaet;
        zustand.x = net.minecraft.util.Mth.lerp(teilTick, entitaet.xOld, entitaet.getX());
        zustand.y = net.minecraft.util.Mth.lerp(teilTick, entitaet.yOld, entitaet.getY());
        zustand.z = net.minecraft.util.Mth.lerp(teilTick, entitaet.zOld, entitaet.getZ());
        zustand.ageInTicks = entitaet.tickCount + teilTick;
        zustand.partialTick = teilTick;
        zustand.isInvisible = entitaet.isInvisible();
        zustand.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entitaet);
        zustand.shadowRadius = this.shadowRadius;
    }

    /** Wie 1.21.4s EntityRenderer.render(Zustand, ...): 1.21.1 zeichnet hier Namensschild und Leine. */
    @SuppressWarnings("unchecked")
    public void render(S zustand, PoseStack pose, MultiBufferSource puffer, int licht)
    {
        super.render((T) zustand.bbsEntitaet, 0F, zustand.partialTick, pose, puffer, licht);
    }

    @Override
    public final void render(T entitaet, float gierung, float teilTick, PoseStack pose, MultiBufferSource puffer, int licht)
    {
        S zustand = this.createRenderState(entitaet, teilTick);

        zustand.lightCoords = licht;
        this.render(zustand, pose, puffer, licht);
    }

    @Override
    public ResourceLocation getTextureLocation(T entitaet)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
