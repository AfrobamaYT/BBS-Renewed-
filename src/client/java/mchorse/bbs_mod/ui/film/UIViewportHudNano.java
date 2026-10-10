package mchorse.bbs_mod.ui.film;

import com.mcstudio.nano.NanoDesign;
import com.mcstudio.nano.NanoFlaeche;
import com.mcstudio.nano.NanoStifte;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Kontextzeile im Viewport (Blenders Text-Info oben links): Ansichtsart, darunter Tick,
 * Film und gewaehltes Replay. Ohne sie muss man raten, worauf ein Klick gerade wirkt.
 *
 * <p>Nur Text auf dem Bild — keine Flaeche, keine Klickzonen: was hier steht, wird woanders
 * bedient. Deshalb {@code setEnabled(false)}, damit die Zeile keine Mausklicks abfaengt.</p>
 */
public class UIViewportHudNano extends UIElement
{
	private final UIFilmPreview preview;
	private final NanoFlaeche flaeche;
	private String stand = "";

	public UIViewportHudNano(UIFilmPreview preview)
	{
		this.preview = preview;
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
		this.flaeche.setEnabled(false);
		this.setEnabled(false);
		this.add(this.flaeche);
	}

	@Override
	public void afterResizeApplied()
	{
		super.afterResizeApplied();
		this.area.x = this.preview.werkzeuge.area.ex() + 7;
		this.area.w = Math.max(0, this.preview.area.ex() - this.area.x - 40);
	}

	private static UIFilmPanel filmPanel()
	{
		return BBSModClient.getDashboard() != null
			&& BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;
	}

	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
		UIFilmPanel panel = filmPanel();
		String jetzt = (panel == null ? "" : panel.getController().getPovMode() + "|") + this.text();

		if (!jetzt.equals(this.stand))
		{
			this.stand = jetzt;
			this.flaeche.neuMalen();
		}

		super.render(context);
	}

	/** Zeile 2: Tick, Film, gewaehltes Replay — leer, solange nichts geladen ist. */
	private String text()
	{
		UIFilmPanel panel = filmPanel();
		Film film = panel == null ? null : panel.getData();

		if (film == null)
		{
			return "";
		}

		StringBuilder sb = new StringBuilder("(").append(panel.getCursor()).append(") ");

		sb.append(film.getId());

		Replay replay = panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		if (replay != null)
		{
			sb.append("  ·  ").append(replay.getName());
		}

		return sb.toString();
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		final float k = Math.max(1F, f.skala());
		String zweite = this.text();

		if (zweite.isEmpty())
		{
			return;
		}

		UIFilmPanel panel = filmPanel();
		String erste = switch (panel.getController().getPovMode())
		{
			case 0 -> "Camera Perspective";
			case 3 -> "First Person";
			case 4 -> "Third Person Back";
			case 5 -> "Third Person Front";
			default -> "User Perspective";
		};

		/* Schattenkante, damit die Schrift ueber hellem Himmel und dunklem Boden lesbar bleibt. */
		NanoStifte.text(vg, NanoDesign.BOLD, 11F * k, 1F * k, 12F * k + 1F * k, 0x0A0A0A,
			erste, null);
		NanoStifte.text(vg, NanoDesign.BOLD, 11F * k, 0F, 12F * k, 0xFFFFFF,
			erste, null);

		NanoStifte.text(vg, NanoDesign.SANS, 10F * k, 1F * k, 24F * k + 1F * k, 0x0A0A0A, zweite, null);
		NanoStifte.text(vg, NanoDesign.SANS, 10F * k, 0F, 24F * k, 0xC8D2DC, zweite, null);
	}
}
