package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.renderer.GameRenderer;
import mchorse.bbs_mod.compat.GameRendererAlt;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class GameRendererAltMixin implements GameRendererAlt
{}
