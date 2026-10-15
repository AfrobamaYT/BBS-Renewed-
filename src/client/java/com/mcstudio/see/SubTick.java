package com.mcstudio.see;

/**
 * SubTick — ein erzwungener Partial-Tick fuer den pausierten Editor.
 *
 * <p>WOFUER (REDACTED-Live-Link, Abnahme 1 bei 24/30/60 fps): {@link WeltbildFahrt} scrubbt den
 * offenen Film je Ausgabeframe auf den Tick {@code ⌊t⌋}. Der Rest {@code t − ⌊t⌋} ist der
 * Partial-Tick, mit dem Kamera-Clips und Actor-Eigenschaften zwischen zwei Ticks interpoliert
 * werden — beim ABSPIELEN liefert ihn Minecrafts DeltaTracker, beim SCRUBBEN setzen beide
 * Pfade ihn hart auf {@code 0F} ({@code RunnerCameraController.setup}: {@code playing ?
 * transition : 0F}; {@code FilmEditorController.getTransition}: dieselbe Null). Dieser Wert
 * hier ersetzt genau diese Null, solange eine Fahrt laeuft; sonst ist er {@link Float#NaN}
 * und die Pfade verhalten sich wie bisher.</p>
 *
 * <p>THREADS: gesetzt vom Fahrt-Thread (HTTP), gelesen je Bild auf dem Render-Thread — darum
 * {@code volatile}. Die Fahrt setzt den Wert VOR dem Cursor-Scrub (der ueber
 * {@code RenderThread.submit} am Tick-Ende laeuft); jedes danach gezeichnete Bild sieht Tick
 * und Rest zusammen. Nach der Fahrt (finally) wird geloest.</p>
 *
 * <p>Bewusst keine Lambda, kein Zustand ausser dem einen Feld: die Klasse ist neu und wird
 * lazy geladen — kein Boot noetig (Skill /hotswap: neue Klassen sind kein Boot-Grund).</p>
 */
public final class SubTick
{
	private static volatile float erzwungen = Float.NaN;

	private SubTick()
	{
	}

	/** Partial-Tick {@code 0 <= rest < 1} erzwingen — gilt bis {@link #loesen()}. */
	public static void setzen(float rest)
	{
		erzwungen = rest;
	}

	/** Zurueck zum Normalverhalten (pausiert == 0F). */
	public static void loesen()
	{
		erzwungen = Float.NaN;
	}

	public static boolean aktiv()
	{
		return !Float.isNaN(erzwungen);
	}

	/**
	 * Der Wert, den ein pausierter Pfad statt seiner Null nehmen soll: der erzwungene Rest,
	 * wenn eine Fahrt laeuft, sonst {@code sonst}.
	 */
	public static float oder(float sonst)
	{
		float e = erzwungen;

		return Float.isNaN(e) ? sonst : e;
	}
}
