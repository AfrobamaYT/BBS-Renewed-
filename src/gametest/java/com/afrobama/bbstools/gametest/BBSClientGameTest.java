package com.afrobama.bbstools.gametest;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonAlgorithm;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;
import net.minecraft.network.chat.Component;

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

	/*
	 * ⭐ WARUM HIER KEINE TOLERANZ STEHT, SONDERN exact() -- gemessen, nicht gewaehlt.
	 *
	 * Die Oberflaeche zeichnet BITGLEICH. Zwei vollstaendige Laeufe gegen dieselbe Vorlage:
	 *
	 *   MSE 0.00000000   abweichende Pixel 0.00 %   groesste Einzelabweichung 0
	 *   MSE 0.00000000   abweichende Pixel 0.00 %   groesste Einzelabweichung 0
	 *
	 * Damit ist jede Toleranz nur eine Luecke, durch die echte Fehler schluepfen. exact()
	 * meldet jedes einzelne veraenderte Pixel.
	 *
	 * ⛔ FUER DIE WELT GILT DAS NICHT, und der Unterschied ist gross: dort ergaben zwei
	 * Laeufe 0.000006 und 0.000073 -- Faktor 12 Streuung, ohne Codeaenderung. Deshalb wird
	 * die Welt hier nicht verglichen (Begruendung in runTest).
	 *
	 * ⚠️ Fabrics Vorgabe waere in beiden Faellen falsch gewesen: meanSquaredDifference(0.005)
	 * schlaegt erst ab rund 12 % veraenderter Bildflaeche an. Belegt an der Gegenprobe -- ein
	 * 60-Pixel-Streifen quer durchs Bild kam auf 0.005489 und ging damit gerade eben durch.
	 * Ein Test, der praktisch nichts prueft, und ohne die Gegenprobe waere er als Erfolg
	 * gemeldet worden.
	 */

	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("bbs.ci.production"))
		{
			if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()
				|| net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bbsdev"))
			{
				throw new AssertionError("Production smoke test requires the product JAR and no companion");
			}
			String source = mchorse.bbs_mod.BBSMod.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
			if (!source.endsWith(".jar")) throw new AssertionError("Production BBS CodeSource is not a JAR: " + source);
			System.out.println("BBS_PRODUCTION_SOURCE " + source);
			try
			{
				java.nio.file.Path jar = java.nio.file.Path.of(mchorse.bbs_mod.BBSMod.class.getProtectionDomain().getCodeSource().getLocation().toURI());
				String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(jar)));
				System.out.println("BBS_PRODUCTION_SHA256 " + digest);
				String expected = System.getProperty("bbs.ci.expectedProductSha256");
				String frozen = System.getenv("BBS_PRODUCTION_MOD_DIR");
				if (frozen != null && !frozen.isBlank() && expected == null)
					throw new AssertionError("The explicit frozen production input was ignored");
				if (expected != null && !expected.equals(digest))
					throw new AssertionError("Loaded product bytes differ from the frozen input: " + digest);
			}
			catch (Exception failure)
			{
				throw new AssertionError("Cannot identify the actual production product bytes", failure);
			}
		}
		java.nio.file.Path nativeWorld = null;
		try (TestSingleplayerContext welt = context.worldBuilder().create()) {
			if (System.getenv("BBS_EXPORT_NATIVE_WORLD") != null) nativeWorld = welt.getWorldSave().getSaveDirectory();
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

			/* ⛔ DIE WELT SELBST WIRD NICHT VERGLICHEN -- gemessen, nicht aufgegeben.
			 * Zwei Laeufe gegen dieselbe Vorlage ergaben MSE 0.000006 (max. 3 Stufen) und
			 * 0.000073 (max. 8) -- Faktor 12 Streuung, ohne dass sich irgendetwas am Code
			 * geaendert haette. Ursache ist die Welt selbst: Tageszeit, Wolken, Beleuchtung
			 * laufen weiter. Jede Schwelle, die diese Streuung durchlaesst, laesst auch
			 * echte Fehler durch; jede engere macht den Test zufaellig rot.
			 *
			 * Ein Test, der mal so und mal so ausgeht, ist schlimmer als keiner: er wird
			 * nach dem dritten Fehlalarm ignoriert, und dann faellt der echte Fehler auch
			 * nicht mehr auf.
			 *
			 * Die Welt wird trotzdem geladen -- der Editor braucht sie. Verglichen wird die
			 * OBERFLAECHE: die zeichnet deterministisch, und sie ist der Teil, um den es
			 * bei BBS ueberhaupt geht. */

			// ⭐ ERST HIERDURCH TAUGT DER BILDBEWEIS FUER BBS-ARBEIT. Ohne diesen Teil
			// prueft er nur, ob Vanilla eine Welt zeichnet -- das sagt ueber unseren
			// Editor nichts. UIScreen ist die Screen-Huelle, UIDashboard das Menue darin;
			// setScreen ist der von Fabric vorgesehene Weg und umgeht jede Tastensimulation.
			context.setScreen(() ->
			{
				/* The fixture compares the editor, not the installation's one-time announcement.
				 * Fabric runs this in its private clientGameTest directory. */
				com.afrobama.bbstools.client.dashboard.AnnouncementGate.markShown();

				if (com.afrobama.bbstools.client.dashboard.AnnouncementGate.shouldShow())
				{
					throw new AssertionError("Could not prepare the test-only announcement state");
				}

				return new UIScreen(Component.empty(), new StillDashboard());
			});

			// Das Dashboard baut seine Panels erst im ersten Zeichendurchlauf auf. Ohne
			// Wartezeit faengt der Screenshot ein halb aufgebautes Fenster ein und der
			// Vergleich wird flatterhaft -- derselbe Grund wie beim Warten auf die Chunks.
			context.waitTicks(20);
			DashboardFirstRun.dismiss(context);

			pruefe(context, "bbs-dashboard");
		}
		if (nativeWorld != null)
		{
			if (!context.computeOnClient(client -> client.getSingleplayerServer() == null))
				throw new AssertionError("Close the fixture server before exporting its files");
			NativeWorldExport.copyClosed(nativeWorld, java.nio.file.Path.of(System.getenv("BBS_EXPORT_NATIVE_WORLD")));
		}
	}

	/** Fix the native empty-panel arrow's animation phase only inside the private fixture. */
	private static final class StillDashboard extends UIDashboard
	{
		private final java.lang.reflect.Field clock;

		private StillDashboard()
		{
			try
			{
				this.clock = mchorse.bbs_mod.ui.framework.UIContext.class.getDeclaredField("tick");
				this.clock.setAccessible(true);
			}
			catch (ReflectiveOperationException failure)
			{
				throw new AssertionError("Cannot control the test UI clock", failure);
			}
		}

		@Override
		public void renderMenu(mchorse.bbs_mod.ui.framework.UIRenderingContext rendering, int mouseX, int mouseY)
		{
			long tick = this.context.getTick();
			float transition = this.context.getTransition();

			try
			{
				this.clock.setLong(this.context, 0L);
				this.context.setTransition(0F);
				super.renderMenu(rendering, mouseX, mouseY);
			}
			catch (IllegalAccessException failure)
			{
				throw new AssertionError("Cannot set the test UI clock", failure);
			}
			finally
			{
				try { this.clock.setLong(this.context, tick); }
				catch (IllegalAccessException failure) { throw new AssertionError(failure); }
				this.context.setTransition(transition);
			}
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
			.saveWithFileName(name + "-compared")
			.withGrayscale()
			.withAlgorithm(TestScreenshotComparisonAlgorithm.exact());

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
