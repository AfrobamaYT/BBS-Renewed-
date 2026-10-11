package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoBausteine;
import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Render-Reiter des N-Panels, nach dem Entwurf: Engine als Segment, darunter die Ausgabewerte
 * als Feldstapel, unten die beiden Startknöpfe.
 *
 * <p>Alle Werte sind echte BBS-Einstellungen ({@code videoWidth/Height/FrameRate/MotionBlur},
 * {@code renderEngine}) und per Ziehen änderbar. „Samples" aus dem Entwurf fehlt bewusst — BBS
 * kennt den Begriff nicht; ein Feld ohne Wert dahinter wäre erfunden.</p>
 */
public class UIRenderReiterNano extends UIElement implements UISeitenleiste.Auffrischbar
{
	private final NanoFlaeche flaeche;
	/** Ob der Zieh-Handler an der Flaeche haengt — siehe zeichnen(). */
	private boolean ziehenVerdrahtet;
	private String stand = "";

	/** Status des BBS-eigenen Exports, solange kein externer Render-Weg eingehaengt ist. */
	private static volatile String nativStatus = "";

	public static UIElement neu()
	{
		return new UIRenderReiterNano();
	}

	public UIRenderReiterNano()
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.flaeche.beimZiehen(this::ziehen);
		this.add(this.flaeche);
		this.h(268);
	}

	@Override
	public void auffrischen()
	{
		String jetzt = BBSSettings.renderEngine.get() + "|" + BBSSettings.videoWidth.get()
			+ "x" + BBSSettings.videoHeight.get() + "|" + BBSSettings.videoFrameRate.get()
			+ "|" + BBSSettings.videoMotionBlur.get() + "|"
			+ status() + (laeuft() ? "!" : "");

		if (!jetzt.equals(this.stand))
		{
			this.stand = jetzt;
			this.flaeche.neuMalen();
		}
	}

	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
		this.auffrischen();

		super.render(context);
	}

	/** Zahlenfelder per Ziehen ändern — dieselbe Geste wie in den Properties. */
	private void ziehen(String zoneId, float d)
	{
		int schritt = Math.round(d);

		if (schritt == 0)
		{
			return;
		}

		switch (zoneId)
		{
			case "feld:resx" -> BBSSettings.videoWidth.set(spanne(BBSSettings.videoWidth.get() + schritt * 2, 2, 8096));
			case "feld:resy" -> BBSSettings.videoHeight.set(spanne(BBSSettings.videoHeight.get() + schritt * 2, 2, 8096));
			case "feld:fps" -> BBSSettings.videoFrameRate.set(spanne(BBSSettings.videoFrameRate.get() + schritt, 10, 1000));
			case "feld:blur" -> BBSSettings.videoMotionBlur.set(spanne(BBSSettings.videoMotionBlur.get() + schritt, 0, 6));
			default -> { }
		}

		this.stand = "";
		this.flaeche.neuMalen();
	}

	private static int spanne(int wert, int min, int max)
	{
		return Math.max(min, Math.min(max, wert));
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		/* ⛔ Zieh-Handler HIER sichern, nicht nur im Konstruktor: der laeuft beim Hotswap
		 * nicht neu, und die bestehende Flaeche stand danach ohne Handler da — die
		 * Zahlenfelder liessen sich nicht mehr ziehen, obwohl der Code stimmte. Gemessen
		 * 2026-08-20 an der Zeitleiste (zieheHandler == null bei laufender Instanz); es war
		 * dieselbe Ursache wie beim „unerklaerlichen" Mausweg-Fehler der Ziehzonen. */
		if (!this.ziehenVerdrahtet)
		{
			f.beimZiehen(this::ziehen);
			this.ziehenVerdrahtet = true;
		}

		final float k = Math.max(1F, f.skala());
		float rand = 6F * k;
		float y = 4F * k;

		NanoStifte.flaeche(vg, 0, 0, b, h, 0F, NanoDesign.REGION);

		/* ── Engine als Segment ── */
		y = this.gruppentitel(vg, rand, y, k, "Engine");
		String[] engines = engines();

		y = this.segment(vg, f, rand, y, b - rand * 2, k,
			engines, Math.min(BBSSettings.renderEngine.get(), engines.length - 1),
			(wahl) -> BBSSettings.renderEngine.set(wahl));

		/* ── Ausgabe ── */
		y = this.gruppentitel(vg, rand, y + 6F * k, k, "Output");

		float fb = b - rand * 2;

		/* Resolution X und Y bilden EINEN Block — der Entwurf stapelt sie (.stack). */
		y = this.wertfeld(vg, f, rand, y, fb, k, "Resolution X",
			String.valueOf(BBSSettings.videoWidth.get()), "feld:resx", -1F, 1);
		y = this.wertfeld(vg, f, rand, y + 1F * k, fb, k, "Y",
			String.valueOf(BBSSettings.videoHeight.get()), "feld:resy", -1F, 3);
		y = this.wertfeld(vg, f, rand, y + 4F * k, fb, k, "FPS",
			String.valueOf(BBSSettings.videoFrameRate.get()), "feld:fps", -1F, 0);

		/* Motion Blur ist in BBS eine Stufe 0–6, kein Ja/Nein — deshalb Feld mit Balken. */
		int blur = BBSSettings.videoMotionBlur.get();

		y = this.wertfeld(vg, f, rand, y + 4F * k, fb, k, "Motion blur",
			blur == 0 ? "aus" : String.valueOf(blur), "feld:blur", blur / 6F, 0);

		/* ── Startknöpfe unten ── */
		boolean laeuft = laeuft();
		float kh = 24F * k;
		float ky = h - kh - rand;

		this.knopf(vg, f, rand, ky, fb, kh, k, "render",
			laeuft ? "Rendering …" : "Render Animation", !laeuft, this::starten);

		/* Status über den Knöpfen, einzeilig gekürzt. */
		String status = status();

		if (!status.isEmpty())
		{
			NanoStifte.text(vg, NanoDesign.SANS, 10F * k, rand, ky - 7F * k, NanoDesign.TXT_DIM,
				NanoDesign.kuerzen(status, fb, 10F * k), null);
		}
	}

	/** Wertfeld mit Ziehzone; gibt das y darunter zurueck. */
	private float wertfeld(long vg, NanoFlaeche f, float x, float y, float b, float k,
		String etikett, String wert, String zone, float fuellung, int gruppe)
	{
		/* 0 = kein Achspunkt. Der Parameter ist eine FARBE, kein Index — -1 malte Weiss. */
		NanoBausteine.feld(vg, x, y, b, k, etikett, wert, 0, fuellung, gruppe);
		f.zone(zone, x, y, b, NanoDesign.H_WIDGET * k, () -> { });

		return y + NanoDesign.H_WIDGET * k;
	}

	private float gruppentitel(long vg, float x, float y, float k, String titel)
	{
		NanoStifte.text(vg, NanoDesign.SANS, 9.5F * k, x, y + 9F * k, NanoDesign.TXT_OFF,
			titel.toUpperCase(), null);

		return y + 14F * k;
	}

	/** Waagerechtes Segment wie im Entwurf: ein Rahmen, Trennfugen, aktiver Teil blau. */
	private float segment(long vg, NanoFlaeche f, float x, float y, float b, float k,
		String[] namen, int aktiv, java.util.function.IntConsumer wahl)
	{
		float hoehe = 19F * k;
		float teil = b / namen.length;

		NanoStifte.widget(vg, x, y, b, hoehe, 4F * k, NanoDesign.WIDGET, k);

		for (int i = 0; i < namen.length; i++)
		{
			float tx = x + i * teil;
			boolean an = i == aktiv;

			if (an)
			{
				NanoStifte.widget(vg, tx, y, teil, hoehe, 4F * k, NanoDesign.BLAU, k);
			}
			else if (f.istHover("engine" + i))
			{
				NanoStifte.widget(vg, tx, y, teil, hoehe, 4F * k, NanoDesign.WIDGET_HI, k);
			}

			if (i > 0)
			{
				NanoStifte.linie(vg, tx, y + 3F * k, tx, y + hoehe - 3F * k, 1F * k, 0x1A1A1A);
			}

			NanoStifte.textMitte(vg, an ? NanoDesign.BOLD : NanoDesign.SANS, 10.5F * k,
				tx + teil / 2F, y + hoehe / 2F + 3.5F * k, an ? 0xFFFFFF : NanoDesign.TXT,
				namen[i]);

			final int nummer = i;

			f.zone("engine" + i, tx, y, teil, hoehe, () -> wahl.accept(nummer));
		}

		return y + hoehe + 4F * k;
	}

	private void knopf(long vg, NanoFlaeche f, float x, float y, float b, float hoehe, float k,
		String id, String text, boolean primaer, Runnable tat)
	{
		boolean ueber = f.istHover(id);
		int grund = primaer ? (ueber ? 0x5580C4 : NanoDesign.BLAU)
			: (ueber ? NanoDesign.WIDGET_HI : NanoDesign.WIDGET);

		NanoStifte.flaeche(vg, x, y, b, hoehe, 4F * k, grund);
		NanoStifte.textMitte(vg, NanoDesign.BOLD, 11F * k, x + b / 2F, y + hoehe / 2F,
			primaer ? 0xFFFFFF : NanoDesign.TXT, text);
		f.zone(id, x, y, b, hoehe, tat);
	}

	/** Einstieg fuer das Render-Menue der Topbar — dieselbe Wahrheit wie der Knopf hier. */
	public static void rendernStarten()
	{
		new UIRenderReiterNano().starten();
	}

	private void starten()
	{
		UIFilmPanel panel = BBSModClient.getDashboard() == null ? null
			: BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;

		if (panel == null)
		{
			return;
		}

		mchorse.bbs_mod.client.Erweiterungen.Render extern = mchorse.bbs_mod.client.Erweiterungen.render;

		if ((extern != null && extern.laeuft()) || panel.recorder.isExporting())
		{
			return;
		}

		int engine = Math.min(BBSSettings.renderEngine.get(), engines().length - 1);

		if (engine == 0)
		{
			/* BBS-nativ laeuft ueber den bewiesenen Vorschau-Weg; ein externer Weg nur ab Engine 1. */
			nativStarten(panel);

			return;
		}

		extern.starten(panel, engine - 1);
	}

	private void nativStarten(UIFilmPanel panel)
	{
		if (panel.checkShowNoCamera() || panel.recorder.isExporting() || panel.getQueueExporter() != null)
		{
			return;
		}

		if (!mchorse.bbs_mod.utils.FFMpegUtils.checkFFMPEG())
		{
			status("ffmpeg not found — see BBS settings");

			return;
		}

		int dauer = panel.getData().camera.calculateDuration();

		panel.recorder.setFinishedListener(cancelled ->
			status(cancelled ? "Cancelled (BBS)" : "Done (BBS)"));
		UIFilmPanel.applyExportSizeToBBS();
		mchorse.bbs_mod.client.BBSRendering.scheduleAfterNextExportFrame(() ->
			panel.recorder.startRecording(dauer, mchorse.bbs_mod.client.BBSRendering.getTexture().id,
				mchorse.bbs_mod.client.BBSRendering.getVideoWidth(), mchorse.bbs_mod.client.BBSRendering.getVideoHeight()));
		status("Exporting (BBS) ...");
	}

	/** "BBS" und dahinter die Engines eines eingehaengten externen Render-Wegs. */
	private static String[] engines()
	{
		mchorse.bbs_mod.client.Erweiterungen.Render extern = mchorse.bbs_mod.client.Erweiterungen.render;

		if (extern == null)
		{
			return new String[] {"BBS"};
		}

		String[] weitere = extern.engines();
		String[] alle = new String[weitere.length + 1];

		alle[0] = "BBS";
		System.arraycopy(weitere, 0, alle, 1, weitere.length);

		return alle;
	}

	private static boolean laeuft()
	{
		mchorse.bbs_mod.client.Erweiterungen.Render extern = mchorse.bbs_mod.client.Erweiterungen.render;

		return (extern != null && extern.laeuft()) || mchorse.bbs_mod.film.VideoExportSession.laeuftExport();
	}

	/** Ein Status fuer beide Wege: mit externem Render-Weg dessen, sonst der eigene. */
	private static String status()
	{
		mchorse.bbs_mod.client.Erweiterungen.Render extern = mchorse.bbs_mod.client.Erweiterungen.render;

		return extern != null ? extern.status() : nativStatus;
	}

	private static void status(String status)
	{
		mchorse.bbs_mod.client.Erweiterungen.Render extern = mchorse.bbs_mod.client.Erweiterungen.render;

		if (extern != null)
		{
			extern.status(status);
		}
		else
		{
			nativStatus = status;
		}
	}
}
