package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Der Render-Reiter in NanoVG-Optik — das REFERENZ-Panel des UI-Umbaus: dieselbe Logik wie
 * der alte Widget-Reiter (Engine-Wahl BBS/Eevee/Cycles + Render + Status, persistiert in
 * {@code BBSSettings.renderEngine}), gezeichnet als {@link NanoFlaeche} mit Karten-Look,
 * echter Schrift, Hover und Klick-Zonen. Jede weitere BBS-Flaeche zieht nach diesem Muster um.
 */
public class UIRenderReiterNano extends UIElement implements UISeitenleiste.Auffrischbar
{
	private final NanoFlaeche flaeche;
	private String letzterStatus = "";
	private int letzteEngine = -1;

	public static UIElement neu()
	{
		return new UIRenderReiterNano();
	}

	public UIRenderReiterNano()
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.add(this.flaeche);
		this.h(232);
	}

	@Override
	public void auffrischen()
	{
		int engine = BBSSettings.renderEngine.get();

		if (!status.equals(this.letzterStatus) || engine != this.letzteEngine)
		{
			this.letzterStatus = status;
			this.letzteEngine = engine;
			this.flaeche.neuMalen();
		}
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		/* Alles skaliert mit der Flaechen-BREITE — GUI-scale-unabhaengig (referenz 200). */
		float k = b / 200F;
		float rand = 8 * k;
		int engine = BBSSettings.renderEngine.get();

		NanoStifte.flaeche(vg, 0, 0, b, h, 10 * k, 0x1C1C1F);
		NanoStifte.text(vg, "bold", 15 * k, rand + 2 * k, 16 * k, 0xF1F1F1, "Rendering", null);
		NanoStifte.text(vg, "bold", 10 * k, rand + 2 * k, 34 * k, 0x8A8A8E, "ENGINE", null);

		String[] namen = {"BBS", "Eevee", "Cycles"};

		for (int i = 0; i < 3; i++)
		{
			final int wahl = i;
			float y = (44 + i * 30) * k;
			float breite = b - 2 * rand;
			boolean aktiv = engine == i;
			boolean hover = f.istHover("engine" + i);
			int grund = aktiv ? 0x3EA6FF : hover ? 0x38383C : 0x2A2A2D;

			NanoStifte.flaeche(vg, rand, y, breite, 24 * k, 8 * k, grund);
			NanoStifte.textMitte(vg, aktiv ? "bold" : "sans", 12 * k, b / 2F, y + 12.5F * k,
				aktiv ? 0x0F0F0F : 0xF1F1F1, namen[i]);
			f.zone("engine" + i, rand, y, breite, 24 * k, () -> BBSSettings.renderEngine.set(wahl));
		}

		float renderY = 140 * k;
		boolean hoverRender = f.istHover("render");
		int rot = laeuft ? 0x7A1F1F : hoverRender ? 0xE53935 : 0xCC0000;

		NanoStifte.flaeche(vg, rand, renderY, b - 2 * rand, 28 * k, 14 * k, rot);
		NanoStifte.textMitte(vg, "bold", 13 * k, b / 2F, renderY + 14.5F * k, 0xFFFFFF,
			laeuft ? "Rendering ..." : "Render film");
		f.zone("render", rand, renderY, b - 2 * rand, 28 * k, this::starten);

		/* Status: eine zeile, hart gekappt — die flaeche ist schmal. */

		if (!status.isEmpty())
		{
			String zeile = status.length() > 34 ? status.substring(0, 34) + "…" : status;

			NanoStifte.text(vg, "sans", 10 * k, rand + 2 * k, (h / k - 16) * k, 0xAAAAAA, zeile, null);
		}
	}

	private void starten()
	{
		UIFilmPanel panel = BBSModClient.getDashboard() == null ? null
			: BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;

		if (panel == null)
		{
			return;
		}

		int engine = BBSSettings.renderEngine.get();

		if (engine == 0)
		{

			nativStarten(panel);

			return;
		}

	}

	private void nativStarten(UIFilmPanel panel)
	{
		if (panel.checkShowNoCamera() || panel.recorder.isExporting() || panel.getQueueExporter() != null)
		{
			return;
		}

		if (!mchorse.bbs_mod.utils.FFMpegUtils.checkFFMPEG())
		{

			return;
		}

		int dauer = panel.getData().camera.calculateDuration();

		UIFilmPanel.applyExportSizeToBBS();
		mchorse.bbs_mod.client.BBSRendering.scheduleAfterNextExportFrame(() ->
			panel.recorder.startRecording(dauer, mchorse.bbs_mod.client.BBSRendering.getTexture().id,
				mchorse.bbs_mod.client.BBSRendering.getVideoWidth(), mchorse.bbs_mod.client.BBSRendering.getVideoHeight()));
	}
}
