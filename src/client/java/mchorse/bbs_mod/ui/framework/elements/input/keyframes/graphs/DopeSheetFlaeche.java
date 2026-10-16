package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeGroup;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph-Schmuck des Dope Sheets — Entwurf foundations/zeitleiste.html: „keine sichtbare
 * Zeile ohne Inhalt". Gruppen- und Kopfzeilen ziehen ihre Toenung ueber die volle Breite,
 * tragen ein Range-Band vom ersten bis zum letzten Schluessel und zeigen die Schluessel
 * ihrer Kinder als Aggregat-Rauten (Summary-Zeile: alle Kanaele des Graphen). Kanalzeilen
 * bekommen das Range-Band. Aus {@link UIKeyframeDopeSheet} herausgehalten (Monolith).
 */
public final class DopeSheetFlaeche
{
	private static final int BAND_BLAU = 0x4772B3;
	private static final int BAND_GRUEN = 0x7CBF6B;

	private final UIKeyframeDopeSheet dope;

	DopeSheetFlaeche(UIKeyframeDopeSheet dope)
	{
		this.dope = dope;
	}

	/** Toenung, Range-Band und Aggregat-Rauten einer Gruppen-/Kopf-/Summary-Zeile. */
	void gruppenZeile(UIContext context, Matrix4f matrix, Area area, UIKeyframeGroup group, int by, int bh)
	{
		if (group.kameraEbene >= 0)
		{
			return;
		}

		int ton = group.summary ? Colors.setA(Colors.WHITE, 0.035F)
			: group.labelFarbe != 0 ? Colors.setA(group.labelFarbe, 0.12F)
			: group.kopf ? Colors.setA(BAND_BLAU, 0.07F) : Colors.setA(BAND_GRUEN, 0.05F);

		context.batcher.box(area.x, by, area.ex(), by + bh, ton);

		List<Keyframe> alle = this.sammle(group);

		if (alle.isEmpty())
		{
			return;
		}

		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;

		for (Keyframe frame : alle)
		{
			min = Math.min(min, frame.getTick());
			max = Math.max(max, frame.getTick());
		}

		int x1 = Math.max(area.x, this.dope.keyframes.toGraphX(min));
		int x2 = Math.min(area.ex(), this.dope.keyframes.toGraphX(max));

		if (x2 > x1)
		{
			int band = group.kopf || group.summary
				? Colors.setA(Colors.WHITE, 0.055F) : Colors.setA(BAND_GRUEN, 0.10F);

			context.batcher.box(x1, by + 2, x2, by + bh - 2, band);
		}

		this.rauten(context, matrix, area, alle, by + bh / 2);
	}

	/** Range-Band einer Kanalzeile: erster bis letzter Schluessel, dezent hell. */
	void kanalBand(UIContext context, Area area, UIKeyframeSheet sheet, int by, int bh)
	{
		List<?> keyframes = sheet.channel.getKeyframes();

		if (keyframes.size() < 2)
		{
			return;
		}

		int x1 = Math.max(area.x, this.dope.keyframes.toGraphX(((Keyframe<?>) keyframes.get(0)).getTick()));
		int x2 = Math.min(area.ex(), this.dope.keyframes.toGraphX(((Keyframe<?>) keyframes.get(keyframes.size() - 1)).getTick()));

		if (x2 > x1)
		{
			context.batcher.box(x1, by + 2, x2, by + bh - 2, Colors.setA(Colors.WHITE, 0.055F));
		}
	}

	/** Held-Balken (Entwurf/Blender: „Pose steht") + Naeherungs-Unterstrich einer Kanalzeile. */
	void heldBalken(UIContext context, VertexConsumer ecken, Matrix4f matrix, List<?> keyframes, int my, int trackWidth)
	{
		for (int j = 1; j < keyframes.size(); j++)
		{
			Keyframe<?> frame = (Keyframe<?>) keyframes.get(j);
			int vorher = j - 1;

			while (vorher >= 0 && !((Keyframe<?>) keyframes.get(vorher)).isEnabled()) vorher--; /* disabled keys hold nothing */
			if (!frame.isEnabled() || vorher < 0) continue;

			Keyframe<?> previous = (Keyframe<?>) keyframes.get(vorher);
			int xx = this.dope.keyframes.toGraphX(previous.getTick());
			int xxx = this.dope.keyframes.toGraphX(frame.getTick());

			if (previous.getFactory().compare(previous.getValue(), frame.getValue()))
			{
				int w = Math.min(trackWidth + 2, 5);
				int c = 0x4E4E4E | Colors.A100;
				int licht = Colors.setA(Colors.WHITE, 0.12F);

				context.batcher.fillRect(ecken, matrix, xx, my - w / 2, xxx - xx, w, c, c, c, c);
				context.batcher.fillRect(ecken, matrix, xx, my - w / 2, xxx - xx, 1, licht, licht, licht, licht);
			}

			/* Blender: gruene Linie zwischen Keys mit Nicht-Standard-Interpolation
			 * (Manual: „Green line between keys — custom interpolation"). */
			mchorse.bbs_mod.utils.interps.IInterp interp = previous.getInterpolation().getInterp();

			if (interp != mchorse.bbs_mod.utils.interps.Interpolations.LINEAR
				&& interp != mchorse.bbs_mod.utils.interps.Interpolations.CONST && xxx > xx)
			{
				int g = Colors.setA(BAND_GRUEN, 0.85F);

				context.batcher.fillRect(ecken, matrix, xx, my + 5, xxx - xx, 1, g, g, g, g);
			}

			if (Math.abs(xxx - xx) < 5)
			{
				int c = Colors.setA(Colors.WHITE, 0.25F);

				context.batcher.fillRect(ecken, matrix, xx - 2, my + trackWidth / 2 + 4, xxx - xx + 4, 2, c, c, c, c);
			}
		}
	}

	/** Rand- und Fuell-Pass einer Kanalzeile — der Topmost-Pfad des Dope Sheets. */
	void markerReihen(UIContext context, VertexConsumer builder, Matrix4f matrix, Area area, UIKeyframeSheet sheet, int y)
	{
		if (!this.dope.isVisible(sheet) || y + this.dope.trackHeight < area.y || y > area.ey())
		{
			return;
		}

		List<?> keyframes = sheet.channel.getKeyframes();
		int my = y + (int) this.dope.trackHeight / 2;

		for (int j = 0; j < keyframes.size(); j++)
		{
			Keyframe<?> frame = (Keyframe<?>) keyframes.get(j);

			this.dope.randMarker(context, builder, matrix, frame, this.dope.keyframes.toGraphX(frame.getTick()), my);
		}

		this.fuellungen(context, builder, matrix, sheet, my);
	}

	/** Fuellungen einer Zeile, die gewaehlten ZULETZT (Maler-Reihenfolge wie Blender/AE).
	 *
	 *  <p>⛔ WOFUER (Nutzerbefund 2026-10-13: „der ausgewaehlte Keyframe leuchtet nicht"):
	 *  in Listenreihenfolge uebermalte der naechste, NICHT gewaehlte Nachbar die Auswahl —
	 *  bei Standard-Zoom liegt er oft nur 1 px daneben (gemessen: Pose-Tick 184 gewaehlt,
	 *  186 bei 0,62 px/Tick). Ein freistehender Keyframe leuchtete schon vorher.</p> */
	void fuellungen(UIContext context, VertexConsumer builder, Matrix4f matrix, UIKeyframeSheet sheet, int my)
	{
		int n = sheet.channel.getKeyframes().size();

		for (int j = 0; j < n; j++)
		{
			if (!sheet.selection.has(j))
			{
				this.dope.fuelleMarker(context, builder, matrix, sheet, j, my);
			}
		}

		for (int j = 0; j < n; j++)
		{
			if (sheet.selection.has(j))
			{
				this.dope.fuelleMarker(context, builder, matrix, sheet, j, my);
			}
		}
	}

	/**
	 * Treffer in einer Aggregat-Zeile (Summary, Replay-Kopf, Gruppe).
	 *
	 * <p>⛔ WOFUER (Nutzerbefund 2026-09-09): "warum hat das tray fuer die modell anzeige
	 * keyframes die nichtmal benutzbar sind ... sind ne attrape". Er hatte recht —
	 * {@link #findKeyframe} kannte nur {@link UIKeyframeSheet}, und auf einer Gruppenzeile
	 * gibt es keins. Die dort GEZEICHNETEN Aggregat-Rauten waren damit weder waehlbar noch
	 * ziehbar; ein Klick klappte die Zeile nur auf und zu.</p>
	 *
	 * <p>Zurueck kommt ein ECHTER Keyframe aus einem Kindkanal — damit greift die vorhandene
	 * Auswahl unveraendert weiter: {@code getSheet(Keyframe)} findet sein Sheet ueber den
	 * Parent-Channel, also ist es gleich, ob er ueber die Gruppe oder direkt gefunden wurde.</p>
	 *
	 * <p>⛔ DIESELBE MENGE UND DIESELBE X-KLEMME WIE DIE ZEICHNUNG. Sonst klickt man neben
	 * die Raute, die man sieht — die Sammelquelle ist {@link DopeSheetFlaeche#sammle}, die
	 * Klemme {@link DopeSheetFlaeche#randKlemme}.</p>
	 */
	/**
	 * Auswahl nach einem Klick auf einen Keyframe.
	 *
	 * <p>Auf einer normalen Kanalzeile ist es der eine Keyframe — wie zuvor. Traf der Klick
	 * dagegen eine AGGREGAT-Zeile (Summary, Replay-Kopf, Gruppe), wird die ganze SPALTE
	 * dieser Gruppe gewaehlt: alle Keys aller ihrer Kanaele auf diesem Tick. Genau das
	 * erwartet man von einer Uebersichtszeile — ein Zug bewegt sie gemeinsam, wie in
	 * Blenders Dope Sheet.</p>
	 *
	 * <p>⛔ NUR AUS DEM KLICK-PFAD. findKeyframe ruft auch der Hover-Tooltip; eine Auswahl
	 * als Nebenwirkung davon wuerde beim blossen Darueberfahren die Selektion umwerfen.
	 * Darum ist das hier eine eigene Methode und keine Zeile in findInGroup.</p>
	 *
	 * <p>⛔ NUR DIE GRUPPE, nicht alles. selectByX nimmt ungefiltert jedes Blatt — in der
	 * Summary-Zeile ist das richtig, in "Transform" waere es zu viel.</p>
	 */
	public static void spaltenAuswahl(IUIKeyframeGraph graph, UIKeyframeSheet sheet,
		Keyframe gewaehlt, int mouseY)
	{
		sheet.selection.add(gewaehlt);

		if (!(graph instanceof UIKeyframeDopeSheet dope))
		{
			return;
		}

		dope.flaecheSchmuck().spalte(dope, gewaehlt, mouseY);
	}

	/** Die Blaetter der getroffenen Gruppenzeile auf dem Tick des Keyframes waehlen. */
	private void spalte(UIKeyframeDopeSheet dope, Keyframe gewaehlt, int mouseY)
	{
		UIKeyframeGroup group = this.gruppeBei(dope.elements, dope.getDopeSheetY(), mouseY);

		if (group == null)
		{
			return;
		}

		List<UIKeyframeSheet> blaetter = new ArrayList<>();

		this.blaetter(group, blaetter);

		float tick = gewaehlt.getTick();

		dope.waehle(blaetter, (x, y) -> Math.abs(x - dope.keyframes.toGraphX(tick)) < 1);
	}

	/**
	 * Alle Blaetter unterhalb einer Gruppe — dieselbe Menge, die {@link #sammle} abklappert.
	 *
	 * <p>⛔ DER SUMMARY-FALL GEHT NICHT UEBER children. Die Summary-Zeile hat keine, sie
	 * aggregiert ueber ALLE Blaetter des Graphen — genau wie sammle() es tut. Ohne diesen
	 * Zweig waehlte ein Klick auf Summary gemessen genau EINEN Keyframe statt aller
	 * (2026-09-09, Transform lieferte korrekt 7, Summary nur 1).</p>
	 */
	private void blaetter(UIKeyframeGroup group, List<UIKeyframeSheet> aus)
	{
		if (group.summary)
		{
			aus.addAll(this.dope.getSheets());

			return;
		}

		for (UIKeyframeElement element : group.children)
		{
			if (element instanceof UIKeyframeSheet blatt)
			{
				aus.add(blatt);
			}
			else if (element instanceof UIKeyframeGroup tiefer)
			{
				this.blaetter(tiefer, aus);
			}
		}
	}

	/** Die Gruppenzeile unter mouseY, mit derselben Zeilenrechnung wie {@link #sucheZeile}. */
	private UIKeyframeGroup gruppeBei(List<UIKeyframeElement> zeilen, int y, int mouseY)
	{
		for (UIKeyframeElement element : zeilen)
		{
			if (element instanceof UIKeyframeGroup group && mouseY >= y
				&& mouseY < y + this.dope.trackHeight)
			{
				return group;
			}

			if (element instanceof UIKeyframeGroup group && !group.collapsed)
			{
				UIKeyframeGroup tiefer = this.gruppeBei(group.children,
					y + (int) this.dope.trackHeight, mouseY);

				if (tiefer != null)
				{
					return tiefer;
				}
			}

			y += this.dope.getElementHeight(element);
		}

		return null;
	}

	Pair<Keyframe, KeyframeType> findInGroup(int mouseX, int mouseY)
	{
		return this.sucheZeile(this.dope.elements, this.dope.getDopeSheetY(), mouseX, mouseY);
	}

	/**
	 * Laeuft die Zeilen ab wie das Zeichnen und prueft in jeder GRUPPENZEILE, ob der Zeiger
	 * eine ihrer Aggregat-Rauten trifft.
	 *
	 * <p>⛔ OHNE EIGENEN Y-CACHE: die Y-Position wird hier genauso aufaddiert wie beim
	 * Zeichnen. Ein zweiter Cache muesste in UIKeyframeDopeSheet leben (dort rechnet das
	 * Layout), und die Datei steht bei 1347 Zeilen an der Groessengrenze. Ein Klick laeuft
	 * die Liste einmal ab — das faellt neben dem Zeichnen nicht ins Gewicht.</p>
	 */
	private Pair<Keyframe, KeyframeType> sucheZeile(List<UIKeyframeElement> zeilen, int y,
		int mouseX, int mouseY)
	{
		for (UIKeyframeElement element : zeilen)
		{
			if (element instanceof UIKeyframeGroup group && group.kameraEbene < 0
				&& mouseY >= y && mouseY < y + this.dope.trackHeight)
			{
				int my = y + (int) this.dope.trackHeight / 2;

				for (Keyframe keyframe : this.sammle(group))
				{
					int x = randKlemme(this.dope.keyframes.toGraphX(keyframe.getTick()),
						this.dope.keyframes.graphArea);

					if (this.dope.isNear(x, my, mouseX, mouseY, false))
					{
						return new Pair<>(keyframe, KeyframeType.REGULAR);
					}
				}

				return null;
			}

			if (element instanceof UIKeyframeGroup group && !group.collapsed)
			{
				/* Gleiche Rekursion wie clickElements: Kinder beginnen eine Zeile tiefer. */
				Pair<Keyframe, KeyframeType> tiefer = this.sucheZeile(group.children,
					y + (int) this.dope.trackHeight, mouseX, mouseY);

				if (tiefer != null)
				{
					return tiefer;
				}
			}

			y += this.dope.getElementHeight(element);
		}

		return null;
	}

	/** Radius der Raute am Rand. Zeichnung und {@link #randKlemme} muessen denselben nehmen. */
	static final int RAUTE_RADIUS = 3;

	/**
	 * Haelt eine Raute am Rand VOLLSTAENDIG im Bild.
	 *
	 * <p>⛔ WOFUER (Nutzerbefund 2026-09-09): ein Keyframe auf Tick 0 landet auf
	 * {@code toGraphX(0) == graphArea.x}, also exakt auf der Clip-Kante — die linke Haelfte
	 * wird weggeschnitten und der Rest sieht aus wie ein kleines Dreieck. Der Nutzer las
	 * das nicht als Keyframe und hielt die Zeile fuer leer, obwohl dort in diesem Film
	 * SIEBEN lagen (gemessen: x, y, z, yaw, pitch, headYaw, bodyYaw, alle Tick 0).</p>
	 *
	 * <p>⛔ NUR WAS DRIN IST, WIRD GEKLEMMT. Der Filter oben laesst bewusst 4 px Ueberhang
	 * durch; eine Raute, die WIRKLICH links draussen liegt, darf nicht nach innen rutschen —
	 * sie wuerde einen Keyframe an einer Stelle vortaeuschen, an der keiner ist. Verschoben
	 * wird darum hoechstens um den Radius, und nur innerhalb der Flaeche.</p>
	 */
	static int randKlemme(int x, Area area)
	{
		int r = RAUTE_RADIUS;

		if (x >= area.x && x < area.x + r)
		{
			return area.x + r;
		}

		if (x <= area.ex() && x > area.ex() - r)
		{
			return area.ex() - r;
		}

		return x;
	}

	/** Aggregat-Rauten: klein, grau, dedupliziert auf 3 px — die Dichte traegt die Info. */
	private void rauten(UIContext context, Matrix4f matrix, Area area, List<Keyframe> alle, int my)
	{
		List<Integer> xs = new ArrayList<>(alle.size());

		for (Keyframe frame : alle)
		{
			int x = this.dope.keyframes.toGraphX(frame.getTick());

			if (x >= area.x - 4 && x <= area.ex() + 4)
			{
				xs.add(randKlemme(x, area));
			}
		}

		if (xs.isEmpty())
		{
			return;
		}

		xs.sort(null);

		Keyframe muster = alle.get(0);

		context.batcher.netz(FormPipelines.holen(PrimitiveTopology.QUADS, true, false, false), (ecken) ->
		{
			int rand = 0x0F0F0F | Colors.A100;
			int fuellung = 0xB9B9B9 | Colors.A100;
			/* ⛔ NICHT Integer.MIN_VALUE: x - letzt lief ueber und uebersprang JEDE Raute (gemessen). */
			int letzt = -100000;

			for (int x : xs)
			{
				if (x - letzt < 3)
				{
					continue;
				}

				letzt = x;
				UIKeyframeDopeSheet.renderShape(muster, context, ecken, matrix, x, my, RAUTE_RADIUS, rand);
			}

			letzt = -100000;

			for (int x : xs)
			{
				if (x - letzt < 3)
				{
					continue;
				}

				letzt = x;
				UIKeyframeDopeSheet.renderShape(muster, context, ecken, matrix, x, my, 2, fuellung);
			}
		}, UIKeyframeDopeSheet.flaeche(area));
	}

	/**
	 * Magnet beim Key-Ziehen (Sequencer/NLE-Norm, UIClips macht es fuer Clips laengst):
	 * rundet auf ganze Ticks und rastet innerhalb von 10 px an Cursor, Film-Markern und
	 * Clip-Kanten ein. Shift (frei ziehen) laeuft am Aufrufer vorbei an dieser Methode.
	 */
	/* Hysterese-Zustand (Resolve-Gefuehl): gerastet wird bei 15 px, GELOEST erst bei
	 * 30 px — dazwischen klebt der Key am Punkt, obwohl die Maus weiterzieht. Der
	 * Zustand verfaellt, wenn magnet() ~eine Viertelsekunde nicht lief (Zug-Ende).
	 * Alles Add-Field-sichere Defaults (false/0). */
	private boolean rastAktiv;
	private float rastPunkt;
	private long rastZuletzt;

	float magnet(float tick, UIContext context)
	{
		return this.magnet(tick, context, false);
	}

	/** ohneCursor = Playhead-Scrub: der Cursor ist das Gezogene (kein Fangpunkt), und es
	 *  fangen NUR Marker und Keys — Clip-Kanten nicht (Nutzerentscheid Akte 38). */
	float magnet(float tick, UIContext context, boolean ohneCursor)
	{
		float roh = tick;

		/* The playhead scrub (ohneCursor) may stand between ticks when snapping is off (BBS FS 096836ac2) */
		tick = ohneCursor ? mchorse.bbs_mod.ui.film.ICursor.einrasten(tick) : Math.round(tick);

		if (!mchorse.bbs_mod.ui.film.ZeitleisteZweig.istMagnet()
			|| !(this.dope.keyframes instanceof mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes fk)
			|| fk.editor == null || fk.editor.getFilm() == null)
		{
			this.rastAktiv = false;

			return tick;
		}

		/* ⛔ Grosse Basis: toGraphX rundet auf int — mit Basis 1 war proTick bei weitem
		 * Zoom 0 und der Magnet stieg frueh aus (gemessen: magnet(cursor-1.4) blieb ungerastet). */
		double proTick = Math.abs(this.dope.keyframes.toGraphX(1000) - this.dope.keyframes.toGraphX(0)) / 1000D;

		if (proTick <= 0)
		{
			return tick;
		}

		long jetzt = System.currentTimeMillis();

		if (jetzt - this.rastZuletzt > 250L)
		{
			this.rastAktiv = false;
		}

		this.rastZuletzt = jetzt;

		/* KLEBEN: einmal gerastet, haelt der Punkt bis zum 1,6-fachen Fangabstand.
		 * (30 px Loeseweg war „zu stark" — Nutzer nach dem ersten spuerbaren Stand.) */
		if (this.rastAktiv)
		{
			if (Math.abs(roh - this.rastPunkt) < 16D / proTick)
			{
				this.rastLinie(context, this.rastPunkt);

				return this.rastPunkt;
			}

			this.rastAktiv = false;
		}

		long versatz = fk.getClipOffset();
		mchorse.bbs_mod.film.Film film = fk.editor.getFilm();
		List<Float> punkte = new ArrayList<>();

		if (!ohneCursor)
		{
			punkte.add((float) (fk.editor.getCursor() - versatz));
		}

		for (mchorse.bbs_mod.film.markers.Marker m : film.markers.getList())
		{
			punkte.add((float) (m.tick.get() - versatz));
		}

		if (!ohneCursor)
		{
			for (mchorse.bbs_mod.utils.clips.Clip c : film.camera.get())
			{
				punkte.add((float) (c.tick.get() - versatz));
				punkte.add((float) (c.tick.get() + c.duration.get() - versatz));
			}
		}

		/* Resolve-Gefuehl: auch die Keys der ANDEREN Spuren fangen — Posen aneinander
		 * ausrichten. Die AUSGEWAEHLTEN Keys bewegen sich mit dem Zug und sind darum
		 * keine Fangpunkte (sonst rastete die Auswahl an sich selbst). */
		for (UIKeyframeSheet sheet : this.dope.getSheets())
		{
			List<?> ks = sheet.channel.getKeyframes();

			for (int j = 0; j < ks.size(); j++)
			{
				if (!sheet.selection.has(j))
				{
					punkte.add(((Keyframe<?>) ks.get(j)).getTick());
				}
			}
		}

		float best = tick;
		double bestAbstand = 10D / proTick;
		boolean gefunden = false;

		for (float p : punkte)
		{
			double abstand = Math.abs(p - tick);

			if (abstand < bestAbstand)
			{
				bestAbstand = abstand;
				best = p;
				gefunden = true;
			}
		}

		if (gefunden)
		{
			this.rastAktiv = true;
			this.rastPunkt = best;
			this.rastLinie(context, best);
		}

		return best;
	}

	/** Sichtbares Einrasten: kraeftige Bernstein-Linie mit Schein am Fangpunkt — magnet()
	 *  laeuft in der Render-Phase (handleMouse aus render), der Batcher ist gueltig. */
	private void rastLinie(UIContext context, float punkt)
	{
		if (context == null)
		{
			return;
		}

		int sx = this.dope.keyframes.toGraphX(punkt);
		int y1 = this.dope.keyframes.graphArea.y;
		int y2 = this.dope.keyframes.graphArea.ey();
		int a = com.mcstudio.nano.NanoDesign.BERNSTEIN;

		context.batcher.box(sx - 2, y1, sx + 3, y2, Colors.setA(a, 0.20F));
		context.batcher.box(sx - 1, y1, sx + 2, y2, Colors.setA(a, 0.85F));
	}

	/** Schluessel der Zeile: Summary nimmt ALLE Kanaele des Graphen, Gruppen ihre Kinder. */
	/** ⛔ PAKETWEIT: UIKeyframeDopeSheet.findKeyframe muss GENAU diese Menge treffen, sonst
	 *  klickt man neben die gezeichnete Raute. Eine Quelle fuer beides. */
	List<Keyframe> sammle(UIKeyframeGroup group)
	{
		List<Keyframe> alle = new ArrayList<>();

		if (group.summary)
		{
			for (UIKeyframeSheet sheet : this.dope.getSheets())
			{
				alle.addAll(sheet.channel.getKeyframes());
			}

			return alle;
		}

		this.sammle(group.children, alle);

		return alle;
	}

	private void sammle(List<UIKeyframeElement> elements, List<Keyframe> alle)
	{
		for (UIKeyframeElement element : elements)
		{
			if (element instanceof UIKeyframeSheet sheet)
			{
				alle.addAll(sheet.channel.getKeyframes());
			}
			else if (element instanceof UIKeyframeGroup group)
			{
				this.sammle(group.children, alle);
			}
		}
	}
}
