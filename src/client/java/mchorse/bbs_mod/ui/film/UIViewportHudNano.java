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
 * <p>The editor-type selector accepts clicks; the remaining labels pass through to the viewport.</p>
 */
public class UIViewportHudNano extends UIElement
{
	private final NanoFlaeche flaeche;
	private String stand = "";

	public UIViewportHudNano(UIFilmPreview preview)
	{
		this.flaeche = new NanoFlaeche(this::zeichnen);
		this.flaeche.relative(this).x(0).y(0).w(1F).h(1F);
        this.flaeche.setEnabled(true);
        this.setEnabled(true);
		this.add(this.flaeche);
	}

	private static UIFilmPanel filmPanel()
	{
		return BBSModClient.getDashboard() != null
			&& BBSModClient.getDashboard().panels.panel instanceof UIFilmPanel p ? p : null;
	}

	@Override
	public void render(mchorse.bbs_mod.ui.framework.UIContext context)
	{
        this.setEnabled(true);
        this.flaeche.setEnabled(true);
		String jetzt = this.text() + mchorse.bbs_mod.utils.keyframes.KeyframeStufen.an() + keyHier(); /* toggling must repaint too */

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

	/** Katalog G7, Blender: the active object's line turns yellow on a frame where it has a key (any channel) */
	private static boolean keyHier()
	{
		UIFilmPanel panel = filmPanel();
		Replay replay = panel == null || panel.replayEditor == null ? null : panel.replayEditor.getReplay();

		if (replay == null)
		{
			return false;
		}

		int tick = panel.getCursor();

		for (mchorse.bbs_mod.utils.keyframes.KeyframeChannel<?> channel : replay.keyframes.getChannels())
		{
			if (KeyZustand.amTick(channel, tick)) return true;
		}

		for (mchorse.bbs_mod.utils.keyframes.KeyframeChannel<?> channel : replay.properties.properties.values())
		{
			if (KeyZustand.amTick(channel, tick)) return true;
		}

		return false;
	}

	private void zeichnen(long vg, int b, int h, float mausX, float mausY, NanoFlaeche f)
	{
		final float k = Math.max(1F, f.skala());
		String zweite = this.text();

		if (zweite.isEmpty())
		{
			return;
		}

        EditorTypAuswahl.nano(vg, f, this, "preview", "persp", k);

		/* Schattenkante, damit die Schrift ueber hellem Himmel und dunklem Boden lesbar bleibt. */
        NanoStifte.text(vg, NanoDesign.BOLD, 11F * k, 44F * k, 12F * k + 1F * k, 0x0A0A0A,
			"User Perspective", null);
        NanoStifte.text(vg, NanoDesign.BOLD, 11F * k, 43F * k, 12F * k, 0xFFFFFF,
			"User Perspective", null);

        NanoStifte.text(vg, NanoDesign.SANS, 10F * k, 44F * k, 24F * k + 1F * k, 0x0A0A0A, zweite, null);
        NanoStifte.text(vg, NanoDesign.SANS, 10F * k, 43F * k, 24F * k, keyHier() ? 0xE8D84A : 0xC8D2DC, zweite, null);

		if (mchorse.bbs_mod.utils.keyframes.KeyframeStufen.an()) /* stays in sight while on, or a held pose reads as a bug */
		{
			NanoStifte.text(vg, NanoDesign.BOLD, 10F * k, 1F * k, 36F * k + 1F * k, 0x0A0A0A, "Stepped preview  \u00b7  Alt+K", null);
			NanoStifte.text(vg, NanoDesign.BOLD, 10F * k, 0F, 36F * k, 0xFFA040, "Stepped preview  \u00b7  Alt+K", null);
		}
	}
}
