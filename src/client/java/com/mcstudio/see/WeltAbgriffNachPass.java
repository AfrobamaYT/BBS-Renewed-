package com.mcstudio.see;

/**
 * Faengt einen von WorldShot scharfgestellten Welt-Frame, nachdem die Welt fertig gezeichnet und
 * ihr Pass geschlossen ist, aber vor der Oberflaeche. In 26.3 geht das bei END_MAIN nicht mehr
 * (offener Pass, GpuReadback-Waechter). Gerufen aus SpielRendererLogik.vorGuiZeichnen.
 * Eigene Klasse, damit die Aenderung in eine laufende Instanz hotswapbar ist.
 */
public final class WeltAbgriffNachPass
{
	private WeltAbgriffNachPass()
	{
	}

	public static void vorGui()
	{
		/* Vor 26.3 greift WorldShot bei END_MAIN selbst ab; hier nichts doppelt. */
		//? if >=26.3 {
		/*new WorldShot.EndMainLauscher().endMain(null);
		*///?}
	}
}
