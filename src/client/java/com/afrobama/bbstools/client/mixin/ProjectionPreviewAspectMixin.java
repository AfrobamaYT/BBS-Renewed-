// Coded by Afrobama/Claude — Weltprojektion folgt der Vorschauflaeche statt dem Fenster
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import net.minecraft.client.renderer.Projection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Setzt das Bildformat der Weltprojektion auf die Vorschauflaeche, solange BBS sie rendert.
 *
 * <h2>Warum hier</h2>
 *
 * <p>In 26.2 ist die Projektion ein GPU-Puffer; eine Matrix laesst sich nicht mehr abfangen
 * und zurueckschreiben. {@code Projection} ist die eine Stelle, an der Vanilla das Format noch
 * als zwei Zahlen fuehrt — {@code setSize(width, height)}. Der Eingriff ist damit zwei Werte
 * gross statt einer Matrixrechnung.</p>
 *
 * <p>Der Handler bleibt ein Einzeiler auf die Bruecke; die Bedingungen stehen in
 * {@code VorschauFormatLogik} und werden bei jedem Kernel-Swap mitgetauscht.</p>
 *
 * <p>⚠️ {@code require = 0}: faellt die Zielmethode in einer kuenftigen Version weg, soll der
 * Start nicht abbrechen — die Vorschau ist dann wieder im Fensterformat, was der Zustand vor
 * diesem Mixin war.</p>
 */
@Mixin(Projection.class)
public abstract class ProjectionPreviewAspectMixin
{
	/* ⛔ @ModifyVariable, NICHT @Inject mit Selbstaufruf: setSize von innen erneut zu rufen
	 * loest denselben Handler wieder aus — Endlosrekursion. Die Parameter zu aendern laesst
	 * Vanillas Dirty-Pruefung und Matrixrechnung unangetastet laufen.
	 *
	 * ⛔ BEIDE Wege abdecken. Gemessen am Bytecode:
	 *   setSize(width, height)                               -> Ordinal 0 und 1
	 *   setupPerspective(zNear, zFar, fov, width, height)    -> Ordinal 3 und 4
	 * Der erste Anlauf hing nur an setSize und griff nie — die Weltprojektion laeuft ueber
	 * setupPerspective. Ein Mixin, der am falschen Ende haengt, meldet keinen Fehler. */
	@ModifyVariable(method = "setSize", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
	private float bbstools$breiteSetSize(float breite)
	{
		return bbstools$ersetze(breite, 0F, 0);
	}

	@ModifyVariable(method = "setSize", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0)
	private float bbstools$hoeheSetSize(float hoehe)
	{
		return bbstools$ersetze(0F, hoehe, 1);
	}

	@ModifyVariable(method = "setupPerspective", at = @At("HEAD"), ordinal = 3, argsOnly = true, require = 0)
	private float bbstools$breitePerspektive(float breite)
	{
		return bbstools$ersetze(breite, 0F, 0);
	}

	@ModifyVariable(method = "setupPerspective", at = @At("HEAD"), ordinal = 4, argsOnly = true, require = 0)
	private float bbstools$hoehePerspektive(float hoehe)
	{
		return bbstools$ersetze(0F, hoehe, 1);
	}

	private static float bbstools$ersetze(float breite, float hoehe, int index)
	{
		Object ersatz = MixinBruecke.rufe("VorschauFormatLogik", "groesse", breite, hoehe);

		return ersatz instanceof float[] werte && werte.length == 2
			? werte[index] : (index == 0 ? breite : hoehe);
	}
}
