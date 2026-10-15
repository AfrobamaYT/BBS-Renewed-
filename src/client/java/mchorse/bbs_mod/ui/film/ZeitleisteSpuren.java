package mchorse.bbs_mod.ui.film;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Pose;

/**
 * Baut die Zeilen der Zeitleiste — nach GRUPPEN, nicht nach Einzelkanälen.
 *
 * <p>Ein Replay hat 53 kuratierte Bewegungskanäle plus Form-Eigenschaften; einzeln aufgelistet
 * sind das über 60 Zeilen, von denen fast alle leer sind. Blender löst das mit Gruppen: eine
 * Zeile <em>Pose · 34 Knochen · 11</em> statt 34 Zeilen. Aufklappen zeigt die Einzelkanäle.</p>
 *
 * <p>Die Gruppen sind nicht erfunden — sie sind die echten Schlüssel aus
 * {@code Replay.properties} (transform, pose, pose_overlay, shape_keys) und die Item-Kanäle
 * aus {@code Replay.keyframes}.</p>
 */
public final class ZeitleisteSpuren
{
	/** Eine Zeile: Ebene 0 = Replay, 1 = Kanalgruppe, 2 = Einzelkanal. */
	public static final class Zeile
	{
		public final int ebene;
		public final String name;
		public final String zaehler;
		public final KeyframeChannel<?> kanal;
		/** ⛔ ALLE Kanaele der Gruppe. {@link #kanal} ist nur bei genau EINEM gesetzt — die
		 * Zeitleiste zeichnete deshalb fuer „Transform" (10 Kanaele) und „Pose" keine
		 * Keyframes, obwohl der Zaehler daneben zweistellig war. Genau das meldete der
		 * Nutzer als „ich kann die Pose-Tracks nicht sehen". */
		public final List<KeyframeChannel<?>> kanaele;
		public final List<mchorse.bbs_mod.utils.clips.Clip> clips;
		public final Replay replay;
		/** Kennung zum Merken des Auf-/Zuklappzustands; leer = nicht klappbar. */
		public final String klappe;
		public boolean auf;

		Zeile(int ebene, String name, String zaehler, KeyframeChannel<?> kanal,
			List<mchorse.bbs_mod.utils.clips.Clip> clips, Replay replay, String klappe)
		{
			this.ebene = ebene;
			this.name = name;
			this.zaehler = zaehler;
			this.kanal = kanal;
			this.kanaele = kanal == null ? new ArrayList<>() : new ArrayList<>(List.of(kanal));
			this.clips = clips;
			this.replay = replay;
			this.klappe = klappe;
		}

		/** Wie oben, aber mit ALLEN Kanaelen der Gruppe. */
		Zeile(int ebene, String name, String zaehler, KeyframeChannel<?> kanal,
			List<KeyframeChannel<?>> alle, List<mchorse.bbs_mod.utils.clips.Clip> clips,
			Replay replay, String klappe)
		{
			this.ebene = ebene;
			this.name = name;
			this.zaehler = zaehler;
			this.kanal = kanal;
			this.kanaele = alle == null ? new ArrayList<>() : new ArrayList<>(alle);
			this.clips = clips;
			this.replay = replay;
			this.klappe = klappe;
		}
	}

	/** Kanalgruppe: Anzeigename und die Schlüssel, die hineinfallen. */
	private record Gruppe(String name, String eigenschaft, String[] kanaele) {}

	/** Die Gruppen des Entwurfs, auf die echten BBS-Schlüssel gelegt. */
	private static final Gruppe[] GRUPPEN = {
		new Gruppe("Transform", "transform",
			new String[] {"x", "y", "z", "yaw", "pitch", "headYaw", "bodyYaw", "vX", "vY", "vZ"}),
		new Gruppe("Pose", "pose", new String[0]),
		new Gruppe("Pose Overlay", "pose_overlay", new String[0]),
		new Gruppe("Items", null,
			new String[] {"item_main_hand", "item_off_hand", "item_head", "item_chest",
				"item_legs", "item_feet", "selected_slot"}),
		new Gruppe("Shape Keys", "shape_keys", new String[0]),
	};

	private ZeitleisteSpuren()
	{
	}

	/**
	 * Zeilen bauen. {@code offen} merkt sich, welche Gruppen aufgeklappt sind;
	 * {@code nurBewegte} lässt Gruppen ohne einen einzigen Keyframe weg.
	 */
	public static List<Zeile> bauen(Film film, Replay gewaehlt, Map<String, Boolean> offen,
		boolean nurBewegte)
	{
		return bauen(film, gewaehlt, offen, nurBewegte, false);
	}

	/**
	 * @param nurKamera nur die Kameraspur zeigen — der dritte Modus des Editor-Typ-Wählers.
	 *                  Er stand im Menü und tat nichts, weil {@code modus == 2} nirgends
	 *                  ausgewertet wurde (Nutzerbefund 2026-08-20).
	 */
	public static List<Zeile> bauen(Film film, Replay gewaehlt, Map<String, Boolean> offen,
		boolean nurBewegte, boolean nurKamera)
	{
		List<Zeile> aus = new ArrayList<>();

		/* ⛔ offen darf null sein: es ist ein Instanzfeld des Aufrufers, und ein nachtraeglich
		 * hinzugefuegtes Feld steht nach einem Hotswap auf null. Genau daran ist die Instanz
		 * am 2026-08-18 gestorben — die Regel stand da schon in der Doku. */
		if (offen == null)
		{
			offen = new java.util.HashMap<>();
		}

		if (film == null)
		{
			return aus;
		}

		/* ⛔ EINE ZEILE JE EBENE. Camera-Clips tragen ein {@code layer}-Feld und duerfen sich
		 * ueberlappen — die alte Zeitleiste zeichnete sie deshalb gestapelt (56 Fundstellen
		 * fuer „layer"), die neue warf alle in EINE Reihe. Ueberlappende Clips lagen dort
		 * uebereinander und waren weder sicht- noch anklickbar; das war kein Bedienfehler,
		 * sondern ein Datenmodell, das die Ansicht nicht abbildete. */
		if (!nurBewegte || !film.camera.get().isEmpty())
		{
			int ebenen = 0;

			for (mchorse.bbs_mod.utils.clips.Clip c : film.camera.get())
			{
				ebenen = Math.max(ebenen, c.layer.get());
			}

			for (int e = 0; e <= ebenen; e++)
			{
				final int ebene = e;
				List<mchorse.bbs_mod.utils.clips.Clip> drauf = new ArrayList<>();

				for (mchorse.bbs_mod.utils.clips.Clip c : film.camera.get())
				{
					if (c.layer.get() == ebene)
					{
						drauf.add(c);
					}
				}

				if (drauf.isEmpty() && ebene > 0)
				{
					continue;
				}

				aus.add(new Zeile(0, ebenen == 0 ? "Camera" : "Camera " + (ebene + 1),
					drauf.size() + " clips", null, drauf, null, ""));
			}
		}

		if (nurKamera)
		{
			return aus;
		}

		for (Replay replay : film.replays.getList())
		{
			String schluessel = "r:" + replay.getName();
			boolean auf = offen.getOrDefault(schluessel, replay == gewaehlt);
			Zeile kopf = new Zeile(0, replay.getName(), zaehlerReplay(replay),
				null, null, replay, schluessel);

			kopf.auf = auf;
			aus.add(kopf);

			if (!auf)
			{
				continue;
			}

			for (Gruppe gruppe : GRUPPEN)
			{
				List<KeyframeChannel<?>> kanaele = kanaeleVon(replay, gruppe);
				int summe = 0;

				for (KeyframeChannel<?> k : kanaele)
				{
					summe += k.getKeyframes().size();
				}

				if (kanaele.isEmpty() || (nurBewegte && summe == 0))
				{
					continue;
				}

				String gs = schluessel + "/" + gruppe.name();
				boolean gauf = offen.getOrDefault(gs, false);
				Zeile zeile = new Zeile(1, gruppe.name(), zaehlerGruppe(gruppe, kanaele, summe),
					kanaele.size() == 1 ? kanaele.get(0) : null, kanaele, null, replay,
					kanaele.size() > 1 ? gs : "");

				zeile.auf = gauf;
				aus.add(zeile);

				if (!gauf || kanaele.size() <= 1)
				{
					continue;
				}

				for (KeyframeChannel<?> k : kanaele)
				{
					if (nurBewegte && k.getKeyframes().isEmpty())
					{
						continue;
					}

					aus.add(new Zeile(2, k.getId(), String.valueOf(k.getKeyframes().size()),
						k, null, replay, ""));
				}
			}
		}

		return aus;
	}

	/** „34 Knochen · 11" für Pose, sonst die reine Keyframe-Zahl. */
	private static String zaehlerGruppe(Gruppe gruppe, List<KeyframeChannel<?>> kanaele, int summe)
	{
		if (("pose".equals(gruppe.eigenschaft()) || "pose_overlay".equals(gruppe.eigenschaft()))
			&& !kanaele.isEmpty())
		{
			int knochen = knochenZahl(kanaele.get(0));

			if (knochen > 0)
			{
				return knochen + " bones · " + summe;
			}
		}

		return String.valueOf(summe);
	}

	/** Knochen im ersten Keyframe — die Pose trägt eine Map aus Knochennamen. */
	private static int knochenZahl(KeyframeChannel<?> kanal)
	{
		if (kanal.getKeyframes().isEmpty())
		{
			return 0;
		}

		Object wert = kanal.getKeyframes().get(0).getValue();

		return wert instanceof Pose pose ? pose.transforms.size() : 0;
	}

	private static String zaehlerReplay(Replay replay)
	{
		int n = 0;

		for (Gruppe gruppe : GRUPPEN)
		{
			for (KeyframeChannel<?> k : kanaeleVon(replay, gruppe))
			{
				n += k.getKeyframes().size();
			}
		}

		return n + " keys";
	}

	/** Die Kanäle einer Gruppe: entweder eine Form-Eigenschaft oder benannte Replay-Kanäle. */
	private static List<KeyframeChannel<?>> kanaeleVon(Replay replay, Gruppe gruppe)
	{
		List<KeyframeChannel<?>> aus = new ArrayList<>();

		if (gruppe.eigenschaft() != null)
		{
			KeyframeChannel<?> k = replay.properties.properties.get(gruppe.eigenschaft());

			if (k != null)
			{
				aus.add(k);
			}
		}

		if (gruppe.kanaele().length > 0)
		{
			Map<String, KeyframeChannel<?>> alle = nachName(replay);

			for (String name : gruppe.kanaele())
			{
				KeyframeChannel<?> k = alle.get(name);

				if (k != null)
				{
					aus.add(k);
				}
			}
		}

		return aus;
	}

	/** Alle Replay-Kanäle nach ihrem Kennzeichen — einmal je Aufruf, Reihenfolge bleibt stabil. */
	private static Map<String, KeyframeChannel<?>> nachName(Replay replay)
	{
		Map<String, KeyframeChannel<?>> aus = new LinkedHashMap<>();

		for (mchorse.bbs_mod.settings.values.base.BaseValue wert : replay.keyframes.getAll())
		{
			if (wert instanceof KeyframeChannel<?> kanal)
			{
				aus.put(kanal.getId(), kanal);
			}
		}

		return aus;
	}
}
