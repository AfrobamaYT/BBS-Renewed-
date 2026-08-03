package com.afrobama.bbstools.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;

/**
 * Bildbeweis ueber Fabrics eigene Vergleichs-API statt ueber selbst gebaute Pixellogik.
 *
 * <p>⛔ WARUM DAS HIER STEHT (Nutzer-Order 2026-08-03): {@code beweis.py} vergleicht Bilder mit
 * einer selbst geschriebenen Schwellenlogik. Fabric bringt denselben Vergleich seit 1.21.5 mit,
 * und zwar geprueft und von vielen Mods benutzt. An einem einzigen Tag gaben fuenf eigene
 * Messwerkzeuge falsche Auskunft; fuenf Audits hatten das nicht verhindert. Fremder Code luegt
 * nicht wegen MEINER Fehler -- das ist der ganze Grund fuer diesen Umbau.
 *
 * <p>⭐ WAS DIE API MEHR KANN ALS MEIN EIGENBAU: {@code assertScreenshotContains} liefert einen
 * {@link org.joml.Vector2i} zurueck -- die FUNDSTELLE einer Farbe im Bild. Beim Umriss-Versatz
 * (2 Tage, 9 gescheiterte Fixe) habe ich genau diese Koordinaten stundenlang von Hand aus
 * Screenshots abgelesen. Zwei Aufrufe haetten Umriss- und Koerperposition direkt als Zahlen
 * gegeben, und die Differenz waere der Befund gewesen.
 *
 * <p>⚠️ GOLDEN-IMAGE-ABLAUF: Beim ERSTEN Lauf existiert kein Referenzbild, dann schlaegt der
 * Vergleich zwangslaeufig fehl. Deshalb {@code -Pbbs.gametest.aufnehmen} setzen -- der Lauf
 * schreibt die Bilder dann nur, statt zu vergleichen. Ohne die Option wird verglichen, und ein
 * Unterschied ist ein Fehlschlag. Referenzbilder gehoeren ins Repository, sonst prueft der
 * Test gegen nichts.
 */
public class BBSClientGameTest implements FabricClientGameTest {
	/**
	 * Beim Aufnahmelauf werden Referenzbilder geschrieben statt verglichen. Als System-Property,
	 * weil der Gametest in einer eigenen JVM laeuft und Gradle-Properties dort nicht ankommen.
	 */
	private static final boolean AUFNEHMEN = Boolean.getBoolean("bbs.gametest.aufnehmen");

	/**
	 * Mittlerer quadratischer Fehler (0..1), ab dem zwei Bilder als verschieden gelten.
	 *
	 * <p>⛔ NICHT GERATEN, SONDERN GEMESSEN -- und Fabrics Vorgabe von 0.005 waere hier
	 * fast nutzlos gewesen. Gegen dieselbe Testwelt gemessen:
	 *
	 * <pre>
	 *   Rauschen zwischen zwei echten Laeufen   0.000006   (max. 3 von 255 je Pixel)
	 *   SCHWELLE (hier)                         0.00005
	 *   1-Pixel-Streifen ueber die volle Breite  0.000054
	 *   Fabrics Vorgabe                         0.005      = erst ab ~12 % Bildflaeche rot
	 * </pre>
	 *
	 * <p>Die Vorgabe haette einen Fehler also erst bemerkt, wenn ein Achtel des Bildes
	 * falsch ist. Belegt: ein 60-Pixel-Streifen quer durchs Bild kam auf 0.005489 und ging
	 * damit gerade eben durch -- ein Test, der praktisch nichts prueft. Hier faellt schon
	 * eine einzelne falsche Pixelzeile auf, und zwischen ihr und dem Rauschen liegt immer
	 * noch Faktor 8.
	 *
	 * <p>⚠️ {@code exact()} ist KEINE Alternative: die Welt rendert nicht bitgleich (max. 3
	 * Stufen Unterschied durch Dithering), der Test waere bei jedem Lauf zufaellig rot.
	 * Wird die Testwelt geaendert, gehoert diese Zahl neu gemessen -- Ist-Bild aus
	 * {@code build/run/clientGameTest/screenshots/} gegen die Vorlage rechnen.
	 */
	private static final float SCHWELLE = 0.00005F;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext welt = context.worldBuilder().create()) {
			// Ohne dieses Warten faengt der Screenshot halb geladene Chunks ein -- der Vergleich
			// waere dann bei jedem Lauf anders, also flatterhaft. Genau solche Tests bringen
			// niemandem etwas: sie schlagen zufaellig fehl und werden dann ignoriert.
			// ⛔ FALLE, die hier zwei Fehlversuche gekostet hat: `find` im Gradle-Cache liefert
			// IRGENDEIN Artefakt, nicht das benutzte. Dort lag noch client-gametest 4.3.5 mit
			// intermediary-Namen (getClientWorld direkt am Singleplayer-Context); im Klassenpfad
			// steht aber 6.0.0 mit Mojmap, und dort fuehrt der Weg ueber getConnection().
			// Wer die API wissen will, fragt den COMPILE-KLASSENPFAD, nicht das Dateisystem:
			//   sourceSets.gametest.compileClasspath
			welt.getConnection().waitForChunksRender();

			pruefe(context, "welt-geladen");
		}
	}

	/**
	 * Vergleicht gegen das Referenzbild -- oder legt es an, wenn wir im Aufnahmelauf sind.
	 *
	 * <p>Graustufen sind Absicht: sie machen den Vergleich unempfindlich gegen die winzigen
	 * Farbabweichungen, die verschiedene Grafiktreiber erzeugen. Wo es auf Farbe ankommt (der
	 * orange Umriss), nimmt man stattdessen {@code assertScreenshotContains} mit
	 * {@code findColor}.
	 */
	private static void pruefe(ClientGameTestContext context, String name) {
		TestScreenshotComparisonOptions optionen = TestScreenshotComparisonOptions.of(name)
			.withGrayscale()
			.withAlgorithm(TestScreenshotComparisonAlgorithm.meanSquaredDifference(SCHWELLE));

		if (AUFNEHMEN) {
			context.takeScreenshot(name);
			return;
		}

		// Immer auch das IST-Bild ablegen. Ohne das steht bei einem roten Lauf nur "stimmt
		// nicht ueberein" da, und man kann nicht nachsehen, WIE weit es daneben lag --
		// genau die Sackgasse, in der ein Bildvergleich sonst endet.
		context.takeScreenshot(name + "-ist");

		context.assertScreenshotEquals(optionen);
	}
}
