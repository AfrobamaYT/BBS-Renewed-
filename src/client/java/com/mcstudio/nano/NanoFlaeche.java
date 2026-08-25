package com.mcstudio.nano;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.UIUtils;

import org.lwjgl.opengl.GL33C;

import java.util.ArrayList;
import java.util.List;

/**
 * NanoFlaeche — das interaktive NanoVG-UIElement (Browser-Look mit Klick-Zonen und Hover):
 * der Baustein, mit dem BBS-Panels in die neue Optik umziehen. Der Inhalt meldet beim
 * Zeichnen seine Zonen ({@link #zone}), Klicks laufen ueber den normalen BBS-Weg,
 * {@link #istHover} traegt den Hover-Zustand. Render-Thread only, keine Synchronisation.
 *
 * <p>⛔ TIMING, die eine harte Regel: {@link #render} laeuft in der GUI-Phase — roher GL dort
 * waere die Crash-Klasse. render() MELDET nur; gemalt wird im Client-Tick
 * ({@link #tickAbarbeiten}) — die bewiesene /nano_demo-Stelle. Hover-Latenz max. 1 Tick.</p>
 */
public class NanoFlaeche extends UIElement
{
	/** Der Inhalt: zeichnet mit NanoVG und meldet Zonen. Maus in Flaechen-Pixeln (-1 = draussen). */
	public interface Inhalt
	{
		void zeichnen(long vg, int breite, int hoehe, float mausX, float mausY, NanoFlaeche flaeche);
	}

	private static final List<NanoFlaeche> ANSTEHEND = new ArrayList<>();

	private final Inhalt inhalt;
	private final NanoZonen zonen = new NanoZonen();
	/** Rollversatz in Zeilen und die vom Zeichner gemeldete Obergrenze. */
	private float rollen;
	private float rollGrenze;

	private Texture textur;
	private int fbo;
	private int rbo;
	private int texBreite;
	private int texHoehe;
	private float skala = 1F;
	private float klickX = -1F;
	/** Zone, auf der gerade gezogen wird (null = kein Zug). */
	private NanoZonen.Zone zieheZone;
	/** MMB-Schwenk: Startpunkt und Rollstand beim Druck. 0 = kein Schwenk.
	 *
	 * ⛔ NULL als „aus", nicht -1: ein nachtraeglich hinzugefuegtes Feld steht auf bestehenden
	 * Instanzen nach einem Hotswap auf 0, nicht auf dem Initialwert. Mit `-1 = aus` galt der
	 * Schwenk danach als dauerhaft aktiv (gemessen 2026-08-20: schwenkY=0.0 ohne Druck). */
	private float schwenkY;
	private float schwenkRollen;
	private float schwenkX;
	/** Waagerechter Schwenk und Zoom — der Zeichner rechnet Pixel in seine Einheit um. */
	private java.util.function.BiConsumer<Float, Float> schwenkHandler;
	private java.util.function.Consumer<Float> zoomHandler;
	private java.util.function.BiConsumer<String, float[]> rechtsHandler;
	/** Bereich, in dem diese Flaeche Eingaben annimmt (GUI-Pixel, relativ). null = alles. */
	private float[] eingabeFeld;
	private float zieheStartX;
	private java.util.function.BiConsumer<String, Float> zieheHandler;
	/** Zone mit Tastatur-Fokus (Texteingabe). */
	private String fokus = "";
	/** Flaeche, die den Tastatur-Fokus haelt. Nach einem Hotswap null — harmlos, sie wird
	 *  beim naechsten {@code setzeFokus} neu gesetzt. */
	private static NanoFlaeche fokusBesitzer;
	private java.util.function.BiConsumer<String, Character> tastenHandler;
	private float klickY = -1F;
	private boolean dirty = true;
	private String hover = "";
	/** Seit wann die Maus auf derselben Zone steht — fuer die Tooltip-Verzoegerung. */
	private long hoverSeit;
	/** Ob der Tooltip beim letzten Zeichenlauf schon stand — verhindert Dauer-Neuzeichnen. */
	private boolean tooltipStand;
	private float mausX = -1;
	private float mausY = -1;

	public NanoFlaeche(Inhalt inhalt)
	{
		this.inhalt = inhalt;
	}

	/** Inhalt hat sich geaendert (Status, Setting) — beim naechsten Tick neu malen. */
	public void neuMalen()
	{
		this.dirty = true;
	}

	/** Fuer den Inhalt: ist diese Zone gerade unter der Maus? */
	/** GUI-Skala dieser Flaeche: Textur-Pixel je GUI-Pixel. Panels multiplizieren ihre Masse damit. */
	/** Position des letzten Klicks in Flaechen-Pixeln — fuer Zonen, die WO auswerten (Scrubbing). */
	public float letzterKlickX()
	{
		return this.klickX;
	}

	public float letzterKlickY()
	{
		return this.klickY;
	}

	/**
	 * Aktuelle Mausposition in Flaechen-Pixeln, oder -1 ausserhalb.
	 *
	 * <p>Gebraucht fuer Zonen, die WAEHREND des Zuges die absolute Lage brauchen statt der
	 * Verschiebung — der Abspielkopf der Zeitleiste springt an die Maus, er zaehlt keine
	 * Deltas.</p>
	 */
	public float mausPositionX()
	{
		return this.mausX;
	}

	/**
	 * Zieh-Handler: bekommt beim Ziehen (Zone-Id, Verschiebung in Flaechen-Pixeln seit dem
	 * letzten Aufruf). Damit werden Zahlenfelder schrubbbar wie in Blender.
	 */
	public void beimZiehen(java.util.function.BiConsumer<String, Float> handler)
	{
		this.zieheHandler = handler;
	}

	/**
	 * Waagerechter MMB-Schwenk: (Verschiebung in Flaechen-Pixeln, Flaechenbreite).
	 *
	 * <p>Die Flaeche kennt nur Pixel; was eine Zeiteinheit ist, weiss der Zeichner. Ohne
	 * diesen Weg schwenkte MMB nur senkrecht durch die Zeilen — waagerecht durch die ZEIT
	 * konnte die alte Zeitleiste, die neue nicht.</p>
	 */
	public void beimSchwenken(java.util.function.BiConsumer<Float, Float> handler)
	{
		this.schwenkHandler = handler;
	}

	/** Mausrad: Zoomfaktor (&lt;1 = hinein). Ohne Handler rollt das Rad wie bisher. */
	public void beimZoomen(java.util.function.Consumer<Float> handler)
	{
		this.zoomHandler = handler;
	}

	/**
	 * Rechtsklick auf eine Zone: (Zone-Id, Klickpunkt in Flaechen-Pixeln).
	 *
	 * <p>Bis 2026-08-20 nahm diese Flaeche NUR Taste 0 an — ein Rechtsklick fiel ins Leere,
	 * obwohl die alte Zeitleiste dort ihr Kontextmenue hatte.</p>
	 */
	public void beimRechtsklick(java.util.function.BiConsumer<String, float[]> handler)
	{
		this.rechtsHandler = handler;
	}

	/**
	 * Eingaben nur in diesem Rechteck annehmen (GUI-Pixel, relativ zur Flaeche); alles
	 * ausserhalb geht an das Element DARUNTER.
	 *
	 * <p>⛔ Ohne das schluckt eine Flaeche, die als Rahmen ueber einer anderen liegt, deren
	 * gesamte Bedienung. Gemessen am 2026-08-21: die Zeitleisten-Kopfzeile hat volle Hoehe,
	 * zeichnet aber nur oben und links — Rechtsklick, Mittelklick und jeder Clip-Griff von
	 * UIClips darunter kamen nicht mehr an, obwohl dort nichts zu sehen war.</p>
	 */
	public void eingabeFeld(float x, float y, float b, float h)
	{
		this.eingabeFeld = new float[] {x, y, b, h};
	}

	/** Nimmt diese Flaeche den Klick an dieser Stelle ueberhaupt an? */
	private boolean nimmtEingabe(UIContext context)
	{
		if (!this.area.isInside(context))
		{
			return false;
		}

		if (this.eingabeFeld == null)
		{
			return true;
		}

		float rx = context.mouseX() - this.area.x;
		float ry = context.mouseY() - this.area.y;

		return rx >= this.eingabeFeld[0] && rx < this.eingabeFeld[0] + this.eingabeFeld[2]
			&& ry >= this.eingabeFeld[1] && ry < this.eingabeFeld[1] + this.eingabeFeld[3];
	}

	/** Tasten-Handler: bekommt (Fokus-Id, Zeichen). {@code \b} = Ruecktaste, {@code \n} = Enter. */
	public void beimTippen(java.util.function.BiConsumer<String, Character> handler)
	{
		this.tastenHandler = handler;
	}

	/**
	 * Tooltip, der jetzt faellig ist: {x, y, Beschreibung, Taste} — oder null. Faellig heisst,
	 * die Maus steht seit mehr als einer halben Sekunde auf derselben Zone und diese hat einen
	 * Hinweis. Blender wartet „a few moments"; 500 ms trifft das, ohne beim Vorbeifahren zu
	 * flackern.
	 */
	public Object[] tooltipFaellig()
	{
		if (this.hover.isEmpty() || System.currentTimeMillis() - this.hoverSeit < 500L)
		{
			return null;
		}

		NanoZonen.Zone z = this.zonen.treffe(this.mausX, this.mausY);

		if (z == null || z.hinweis().isEmpty())
		{
			return null;
		}

		return new Object[] {z.x(), z.y() + z.h(), z.hinweis(), z.taste()};
	}

	/** Welche Zone hat gerade Tastatur-Fokus? Leer = keine. */
	public String fokus()
	{
		return this.fokus;
	}

	public void setzeFokus(String id)
	{
		this.fokus = id == null ? "" : id;
		this.dirty = true;

		if (this.fokus.isEmpty())
		{
			if (fokusBesitzer == this)
			{
				fokusBesitzer = null;
			}
		}
		else
		{
			fremdenFokusLoesen(this);

			fokusBesitzer = this;
		}
	}

	/**
	 * Nimmt jeder ANDEREN Flaeche den Tastatur-Fokus.
	 *
	 * <p>⛔ Der Fokus lebt pro Flaeche, die Tastatur nicht. Gemessen am 2026-08-21: Fokus im
	 * Suchfeld des Outliners, Klick in die Zeitleiste, „XY" getippt — die Zeichen landeten
	 * weiter im Suchfeld. Der Nutzer tippt dann in ein Feld, das er gar nicht ansieht.</p>
	 */
	private static void fremdenFokusLoesen(NanoFlaeche ausser)
	{
		if (fokusBesitzer != null && fokusBesitzer != ausser)
		{
			fokusBesitzer.fokus = "";
			fokusBesitzer.dirty = true;
			fokusBesitzer = null;
		}
	}

	public float skala()
	{
		return this.skala;
	}

	public boolean istHover(String id)
	{
		return id.equals(this.hover);
	}

	/** Vom Inhalt waehrend zeichnen() gerufen: Klickflaeche melden. */
	/**
	 * Klickflaechen dieser Flaeche als {id, x, y, b, h} in TEXTURPIXELN — so, wie sie gemeldet
	 * wurden. Nur zum Messen: {@code /ui/zonen} rechnet sie in GUI-Koordinaten um.
	 */
	public java.util.List<float[]> zonenMasse()
	{
		java.util.List<float[]> aus = new java.util.ArrayList<>();

		for (NanoZonen.Zone z : this.zonen.alle())
		{
			aus.add(new float[] {z.x(), z.y(), z.w(), z.h()});
		}

		return aus;
	}

	/** Kennungen der Klickflaechen, gleiche Reihenfolge wie {@link #zonenMasse()}. */
	public java.util.List<String> zonenNamen()
	{
		java.util.List<String> aus = new java.util.ArrayList<>();

		for (NanoZonen.Zone z : this.zonen.alle())
		{
			aus.add(z.id());
		}

		return aus;
	}

	/**
	 * Klickflaeche mit Tooltip. Blender zeigt nach kurzem Verweilen eine Box mit Beschreibung
	 * und dem hinterlegten Kuerzel — so lernt man die Taste beim Benutzen, statt sie zu suchen.
	 *
	 * @param hinweis kurze Beschreibung, leer = kein Tooltip
	 * @param taste   Kuerzel wie „Space" oder „Ctrl+Z"; leer = keins
	 */
	public void zone(String id, float x, float y, float w, float h, Runnable aktion,
		String hinweis, String taste)
	{
		if (this.draussen(x, y, w, h))
		{
			return;
		}

		this.zonen.melden(id, x, y, w, h, aktion, hinweis, taste);
	}

	public void zone(String id, float x, float y, float w, float h, Runnable aktion)
	{
		if (this.draussen(x, y, w, h))
		{
			return;
		}

		this.zonen.melden(id, x, y, w, h, aktion);
	}

	/**
	 * Liegt die Zone VOLLSTAENDIG ausserhalb der Flaeche?
	 *
	 * <p>⛔ Solche Zonen sind Geisterflaechen: gemeldet, aber nie erreichbar. Gemessen am
	 * 2026-08-21: {@code UIPropertiesNano} endet bei y=486 und meldete {@code panel3} bei
	 * y=492 — unter der Statusleiste, die jeden Klick dorthin abfing. Der Knopf sah tot aus
	 * und war heil; erreichbar wurde er erst nach dem Rollen. Teilweise sichtbare Zonen
	 * bleiben bewusst erhalten, die kann man anklicken.</p>
	 */
	private boolean draussen(float x, float y, float w, float h)
	{
		float grenzeB = this.area.w * this.skala();
		float grenzeH = this.area.h * this.skala();

		return x >= grenzeB || y >= grenzeH || x + w <= 0F || y + h <= 0F;
	}

	@Override
	public void render(UIContext context)
	{
		this.skala = (float) net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();

		int wunschB = Math.max(1, Math.round(this.area.w * this.skala));
		int wunschH = Math.max(1, Math.round(this.area.h * this.skala));

		if (wunschB != this.texBreite || wunschH != this.texHoehe)
		{
			this.dirty = true;
		}

		/* Maus in Flaechen-Pixel + Hover-Zone bestimmen; Hover-Wechsel = neu malen. */
		float mx = -1;
		float my = -1;

		if (this.area.isInside(context))
		{
			mx = (context.mouseX() - this.area.x) * this.skala;
			my = (context.mouseY() - this.area.y) * this.skala;
		}

		this.mausX = mx;
		this.mausY = my;

		/* Laufender MMB-Schwenk: der Rollstand folgt der Maus. Eine ZEILE ist die Einheit,
		 * deshalb durch die Zeilenhoehe geteilt — sonst schoesse ein kurzer Zug durch die
		 * ganze Liste. */
		if (this.schwenkY > 0F)
		{
			if (this.rollGrenze > 0F)
			{
				float dy = (this.schwenkY - context.mouseY()) / 18F;

				this.rollen = Math.max(0F, Math.min(this.rollGrenze, this.schwenkRollen + dy));
			}

			/* Waagerecht: die Verschiebung SEIT DEM LETZTEN BILD melden, nicht seit dem
			 * Druck — der Zeichner addiert selbst und braucht keinen Startwert. */
			float dx = (this.schwenkX - context.mouseX()) * this.skala;

			if (this.schwenkHandler != null && Math.abs(dx) >= 1F)
			{
				this.schwenkHandler.accept(dx, this.area.w * this.skala);
				this.schwenkX = context.mouseX();
			}

			this.dirty = true;
		}

		/* Laufender Zug: Verschiebung seit dem letzten Bild melden. */
		if (this.zieheZone != null && this.zieheHandler != null && mx >= 0)
		{
			float d = mx - this.zieheStartX;

			if (Math.abs(d) >= 1F)
			{
				this.zieheStartX = mx;
				this.zieheHandler.accept(this.zieheZone.id(), d);
				this.dirty = true;
			}
		}

		String neuerHover = this.zoneBei(mx, my);

		if (!neuerHover.equals(this.hover))
		{
			this.hover = neuerHover;
			this.hoverSeit = System.currentTimeMillis();
			this.tooltipStand = false;
			this.dirty = true;
		}
		else if (!this.hover.isEmpty())
		{
			/* ⛔ Ein Tooltip, der erst nach 500 ms faellig wird, braucht ein Neuzeichnen ZU
			 * DIESEM Zeitpunkt — sonst erscheint er nie, weil sich sonst nichts aendert.
			 * Genau daran hing der erste Anlauf: Hover sichtbar, Tooltip unsichtbar. */
			boolean faellig = this.tooltipFaellig() != null;

			if (faellig != this.tooltipStand)
			{
				this.tooltipStand = faellig;
				this.dirty = true;
			}
		}

		if (this.dirty && !ANSTEHEND.contains(this))
		{
			ANSTEHEND.add(this);
		}

		if (this.textur != null)
		{
			/* v-geflippt: NanoVG malt in GL-richtung (ursprung unten links). */
			context.batcher.texturedBox(this.textur, 0xFFFFFFFF,
				this.area.x, this.area.y, this.area.w, this.area.h,
				0, this.texHoehe, this.texBreite, 0, this.texBreite, this.texHoehe);
		}

		super.render(context);
	}

	/**
	 * Rollen: der Zeichner bekommt einen Versatz in ZEILEN und entscheidet selbst, wie weit er
	 * laufen darf. Ohne das sind bei 55 Kanaelen je Replay alle bis auf die obersten vier
	 * unerreichbar — die Flaeche zeigt dann weniger, als sie hat, und sagt es nicht.
	 */
	public float rollVersatz()
	{
		return this.rollen;
	}

	/** Obergrenze setzen — der Zeichner kennt seine Zeilenzahl, die Flaeche nicht. */
	public void rollGrenze(float grenze)
	{
		this.rollGrenze = Math.max(0F, grenze);

		if (this.rollen > this.rollGrenze)
		{
			this.rollen = this.rollGrenze;
		}
	}

	@Override
	protected boolean subMouseScrolled(UIContext context)
	{
		/* ⛔ Rad ZOOMT, wenn der Zeichner es kann — Blenders Zeitleiste macht das, und die
		 * alte hier ebenfalls (Scale.zoomAnchor). Nur zu rollen ist der Ruecksschritt. */
		if (this.zoomHandler != null && this.nimmtEingabe(context))
		{
			this.zoomHandler.accept(context.mouseWheel > 0 ? 0.8F : 1.25F);
			this.dirty = true;

			return true;
		}

		if (!this.nimmtEingabe(context) || this.rollGrenze <= 0F)
		{
			return false;
		}

		float vorher = this.rollen;

		this.rollen = Math.max(0F, Math.min(this.rollGrenze,
			this.rollen - (float) context.mouseWheel * 3F));

		if (this.rollen != vorher)
		{
			this.neuMalen();
		}

		return true;
	}

	@Override
	protected boolean subMouseClicked(UIContext context)
	{
		/* ⛔ MITTLERE MAUSTASTE = SCHWENKEN, wie in Blender. Bis 2026-08-20 fragte diese
		 * Methode nur `mouseButton == 0` ab — die ganze neue Oberflaeche kannte AUSSCHLIESSLICH
		 * den Linksklick. Ein Nutzer, der nach Blender-Art die Zeitleiste mit MMB greift, fand
		 * eine Flaeche, die auf nichts reagiert. Mein Abnahmelauf hat es nie bemerkt, weil
		 * /ui/klick immer Taste 0 sendet: der Test war so blind wie der Code. */
		if (this.nimmtEingabe(context) && context.mouseButton == 2)
		{
			/* Max(1) — 0 bedeutet „kein Schwenk", eine Maus am oberen Rand darf das nicht
			 * ausloesen. */
			this.schwenkY = Math.max(1F, context.mouseY());
			this.schwenkX = context.mouseX();
			this.schwenkRollen = this.rollen;

			return true;
		}

		if (this.nimmtEingabe(context) && context.mouseButton == 1 && this.rechtsHandler != null)
		{
			float rx = (context.mouseX() - this.area.x) * this.skala;
			float ry = (context.mouseY() - this.area.y) * this.skala;
			NanoZonen.Zone treffer = this.zonen.treffe(rx, ry);

			this.rechtsHandler.accept(treffer == null ? "" : treffer.id(), new float[] {rx, ry});

			return true;
		}

		if (this.nimmtEingabe(context) && context.mouseButton == 0)
		{
			float mx = (context.mouseX() - this.area.x) * this.skala;
			float my = (context.mouseY() - this.area.y) * this.skala;

			/* Vor dem Ausloesen merken: Zonen wie das Zeitleisten-Scrubbing brauchen das WO,
			   nicht nur das DASS. */
			this.klickX = mx;
			this.klickY = my;

			NanoZonen.Zone zone = this.zonen.treffe(mx, my);

			if (zone != null)
			{
				UIUtils.playClick();

				/* Zug vormerken — Zahlenfelder werten die Verschiebung aus. */
				this.zieheZone = zone;
				this.zieheStartX = mx;

				/* ⛔ Auch ein klick auf eine ANDERE zone nimmt den tastatur-fokus weg.
				 * Vorher geschah das NUR beim klick ins leere: das suchfeld behielt den
				 * fokus, waehrend der nutzer laengst woanders arbeitete, und jede
				 * tastatureingabe landete weiter dort. Setzt die zone den fokus selbst
				 * wieder (`setzeFokus` in ihrer aktion), gewinnt sie — deshalb hier vor
				 * dem lauf der aktion und ohne namensvergleich. */
				this.setzeFokus("");
				fremdenFokusLoesen(this);

				zone.aktion().run();
				this.dirty = true;

				return true;
			}

			/* Klick ins Leere nimmt den Tastatur-Fokus weg — auch den einer anderen Flaeche. */
			this.setzeFokus("");
			fremdenFokusLoesen(this);
		}

		return super.subMouseClicked(context);
	}

	@Override
	protected boolean subMouseReleased(UIContext context)
	{
		this.zieheZone = null;
		this.schwenkY = 0F;

		return super.subMouseReleased(context);
	}

	@Override
	protected boolean subKeyPressed(UIContext context)
	{
		/* ⛔ UIScreen reicht auch das LOSLASSEN als keyPressed in den Baum (UIScreen:197).
		 * Ohne diesen Guard loescht jedes physische Backspace ZWEI zeichen (PRESS+RELEASE)
		 * — gemessen 2026-08-25. REPEAT bleibt erlaubt: halten soll weiterloeschen. */
		if (!this.fokus.isEmpty() && this.tastenHandler != null
			&& context.getKeyAction() != mchorse.bbs_mod.ui.utils.keys.KeyAction.RELEASED)
		{
			int taste = context.getKeyCode();

			if (taste == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE)
			{
				this.tastenHandler.accept(this.fokus, '\b');
				this.dirty = true;

				return true;
			}

			if (taste == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || taste == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)
			{
				this.fokus = "";
				this.dirty = true;

				return true;
			}
		}

		return super.subKeyPressed(context);
	}

	@Override
	protected boolean subTextInput(UIContext context)
	{
		if (!this.fokus.isEmpty() && this.tastenHandler != null)
		{
			char c = context.getInputCharacter();

			if (c >= ' ')
			{
				this.tastenHandler.accept(this.fokus, c);
				this.dirty = true;

				return true;
			}
		}

		return super.subTextInput(context);
	}

	private String zoneBei(float mx, float my)
	{
		NanoZonen.Zone zone = this.zonen.treffe(mx, my);

		return zone == null ? "" : zone.id();
	}

	/** Client-Tick (Render-Thread, zwischen den Frames): alle angemeldeten Flaechen malen. */
	public static void tickAbarbeiten()
	{
		if (ANSTEHEND.isEmpty())
		{
			return;
		}

		for (NanoFlaeche flaeche : new ArrayList<>(ANSTEHEND))
		{
			flaeche.offscreenMalen();
		}

		ANSTEHEND.clear();
	}

	private void offscreenMalen()
	{
		long vg = NanoKontext.holen();

		if (vg == 0)
		{
			return;
		}

		int wunschB = Math.max(1, Math.round(this.area.w * this.skala));
		int wunschH = Math.max(1, Math.round(this.area.h * this.skala));
		NanoGlKlammer klammer = NanoGlKlammer.sichern();

		try
		{
			if (this.textur == null)
			{
				this.textur = new Texture();
				this.fbo = GL33C.glGenFramebuffers();
				this.rbo = GL33C.glGenRenderbuffers();
			}

			if (wunschB != this.texBreite || wunschH != this.texHoehe)
			{
				this.textur.setSize(wunschB, wunschH);
				this.texBreite = wunschB;
				this.texHoehe = wunschH;
				GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, this.rbo);
				GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_DEPTH24_STENCIL8, wunschB, wunschH);
			}

			GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.fbo);
			/* id je durchlauf frisch — setSize vergibt neue GL-nummern (Texture-doku). */
			GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, this.textur.id, 0);
			GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_STENCIL_ATTACHMENT, GL33C.GL_RENDERBUFFER, this.rbo);

			if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE)
			{
				System.out.println("[nano] flaechen-FBO unvollstaendig (" + wunschB + "x" + wunschH + ")");

				return;
			}

			GL33C.glViewport(0, 0, wunschB, wunschH);
			GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
			GL33C.glDisable(GL33C.GL_DEPTH_TEST);
			GL33C.glDisable(GL33C.GL_CULL_FACE);
			GL33C.glClearColor(0F, 0F, 0F, 0F);
			GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_STENCIL_BUFFER_BIT);

			this.zonen.bauBeginnen();
			org.lwjgl.nanovg.NanoVG.nvgBeginFrame(vg, wunschB, wunschH, 1F);
			this.inhalt.zeichnen(vg, wunschB, wunschH, this.mausX, this.mausY, this);
			org.lwjgl.nanovg.NanoVG.nvgEndFrame(vg);

			this.zonen.uebernehmen();
			this.dirty = false;
		}
		catch (Throwable t)
		{
			System.out.println("[nano] flaechen-malen fehlgeschlagen: " + t);
		}
		finally
		{
			klammer.zurueck();
		}
	}
}
