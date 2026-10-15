// Coded by Afrobama/Claude — Weltprojektion folgt der Vorschauflaeche statt dem Fenster
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Before 26.1 (replaces the shared mixin on 26.2's Projection): the world projection is built
 * in GameRenderer.getProjectionMatrix from the window's aspect. The same bridge
 * (VorschauFormatLogik.groesse) swaps window width/height for the preview surface's.
 */
@Mixin(GameRenderer.class)
public abstract class ProjectionPreviewAspectMixin
{
	@ModifyArg(method = "getProjectionMatrix", require = 0, index = 1,
		at = @At(value = "INVOKE", target = mchorse.bbs_mod.compat.alt.MixinZiele.PERSPEKTIVE))
	private float bbstools$seitenverhaeltnis(float verhaeltnis)
	{
		float breite = Minecraft.getInstance().getWindow().getWidth();
		float hoehe = Minecraft.getInstance().getWindow().getHeight();
		Object ersatz = MixinBruecke.rufe("VorschauFormatLogik", "groesse", breite, hoehe);

		return ersatz instanceof float[] werte && werte.length == 2 && werte[1] > 0F ? werte[0] / werte[1] : verhaeltnis;
	}
}
