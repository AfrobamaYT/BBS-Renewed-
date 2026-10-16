package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Zugriff auf den Weiss-Anteil der Ueberlagerung (Creeper-Ladeblitz, Trefferblitz).
 *
 * <p>26.2: die Methode nimmt jetzt den RENDER-ZUSTAND statt Entity und Teiltick -- dieselbe
 * Umstellung wie ueberall bei den Entity-Zeichnern: erst wird der Sichtzustand eingesammelt,
 * dann gezeichnet, und im Zeichen-Zweig gibt es die Entity gar nicht mehr.</p>
 *
 * <p>⚠️ Der Parameter ist bewusst {@code LivingEntityRenderState} und nicht ein konkreterer
 * Zustand: ein {@code @Invoker} bindet ueber den Deskriptor, und der traegt nach dem Loeschen
 * der Generik genau diesen Obertyp ({@code javap -p} zeigt {@code getWhiteOverlayProgress(S)}).
 * Ein konkreterer Typ liesse das Weben fehlschlagen.</p>
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker
{
    @Invoker("getWhiteOverlayProgress")
    public float bbs$getAnimationCounter(LivingEntityRenderState zustand);

    /** For the mob bones' matrices without drawing (BBS FS e341fa733) */
    @Invoker("setupRotations")
    public void bbs$setupRotations(LivingEntityRenderState zustand, com.mojang.blaze3d.vertex.PoseStack stack, float bodyRot, float scale);

    @Invoker("scale")
    public void bbs$scale(LivingEntityRenderState zustand, com.mojang.blaze3d.vertex.PoseStack stack);
}
