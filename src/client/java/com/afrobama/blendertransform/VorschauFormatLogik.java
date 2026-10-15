package com.afrobama.blendertransform;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;

/**
 * Bildformat der Weltprojektion, wenn BBS die Editor-Vorschau rendert.
 *
 * <p>Minecraft rechnet die Projektion aus der FENSTER-Groesse ({@code Projection.setSize}).
 * Das passt, solange die Vorschau dasselbe Format hat — tut sie es nicht, wird dasselbe Bild
 * in die andere Form gezogen. Gemessen am 2026-08-19: Projektion 1.879 (Fenster 1920x1022)
 * gegen Vorschauflaeche 2.407 — 28 % breitgezogen.</p>
 *
 * <p>Blender koppelt das Sichtfeld an die FLAECHE: eine breitere Flaeche zeigt mehr Welt,
 * statt dieselbe Welt breiter. Genau das macht diese Logik — aber nur fuer die freie Ansicht.
 * In der Kamera-Ansicht gilt weiter das Exportformat, sonst zeigte die Vorschau einen anderen
 * Ausschnitt als der spaetere Film.</p>
 */
public final class VorschauFormatLogik
{
	private VorschauFormatLogik()
	{
	}

	/**
	 * Ersatzgroesse fuer {@code Projection.setSize}, oder {@code null} wenn Vanillas Wert gilt.
	 *
	 * <p>Genau ein Fall greift: BBS rendert gerade die Welt in seinen eigenen Puffer
	 * ({@code renderingWorld}), ein Film-Editor ist offen, und die Ansicht ist NICHT die
	 * Kamera. Alles andere — normales Spiel, HUD, Export, Kamera-Ansicht — bleibt unberuehrt.</p>
	 *
	 * @return {Breite, Hoehe} oder null
	 */
	public static float[] groesse(float breite, float hoehe)
	{
		/* ⛔ NICHT an renderingWorld haengen: Minecraft ruft setSize, BEVOR BBS seinen
		 * Weltdurchlauf markiert — die Bedingung war beim Aufruf immer false (gemessen).
		 * Stattdessen: Editor offen und freie Ansicht. Damit HUD- und Menue-Projektionen
		 * unberuehrt bleiben, greift der Ersatz nur, wenn die uebergebene Groesse der
		 * FENSTERgroesse entspricht — das ist die Weltprojektion. */
		/* ⛔ getDashboardIfCreated, NIEMALS getDashboard: letzteres ERZEUGT das Dashboard.
		 * Dieser Handler laeuft an Minecrafts Projektion, also auch waehrend die Welt laedt —
		 * dort ist cameraEntity noch null und der Aufbau warf eine NPE. Gemessen im Boot-Log
		 * vom 2026-08-19: „MixinBruecke: aufruf warf … Der handler ist WIRKUNGSLOS".
		 * Ein Messfuehler, der das System veraendert, das er messen soll. */
		UIDashboard dash = BBSModClient.getDashboardIfCreated();
		UIFilmPanel panel = dash != null && dash.panels.panel instanceof UIFilmPanel p ? p : null;

		/* ⛔ Der Kamera-Modus ist KEIN Sonderfall mehr. Solange er das Exportformat erzwang,
		 * musste die Projektion draussen bleiben; jetzt fuellt auch er die Flaeche, also muss
		 * das Sichtfeld genauso mitziehen — sonst zeigt gerade die Kamera-Ansicht ein
		 * verzerrtes Bild. */
		if (panel == null || panel.getController() == null)
		{
			return null;
		}

		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		float fensterB = mc.getWindow().getWidth();
		float fensterH = mc.getWindow().getHeight();

		/* Nur die Weltprojektion ersetzen — sie kommt mit der Fenstergroesse herein. */
		if ((breite > 0F && Math.abs(breite - fensterB) > 1F)
			|| (hoehe > 0F && Math.abs(hoehe - fensterH) > 1F))
		{
			return null;
		}

		int w = BBSRendering.getVideoWidth();
		int h = BBSRendering.getVideoHeight();

		/* Keine Gleichheitspruefung gegen die uebergebenen Werte: die beiden Parameter kommen
		 * getrennt herein (je ein ModifyVariable), einer davon ist immer 0. */
		if (w < 2 || h < 2)
		{
			return null;
		}

		return new float[] {w, h};
	}
}
