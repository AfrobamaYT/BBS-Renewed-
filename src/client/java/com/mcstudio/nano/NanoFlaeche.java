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

	private Texture textur;
	private int fbo;
	private int rbo;
	private int texBreite;
	private int texHoehe;
	private float skala = 1F;
	private boolean dirty = true;
	private String hover = "";
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
	public boolean istHover(String id)
	{
		return id.equals(this.hover);
	}

	/** Vom Inhalt waehrend zeichnen() gerufen: Klickflaeche melden. */
	public void zone(String id, float x, float y, float w, float h, Runnable aktion)
	{
		this.zonen.melden(id, x, y, w, h, aktion);
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

		String neuerHover = this.zoneBei(mx, my);

		if (!neuerHover.equals(this.hover))
		{
			this.hover = neuerHover;
			this.dirty = true;
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

	@Override
	protected boolean subMouseClicked(UIContext context)
	{
		if (this.area.isInside(context) && context.mouseButton == 0)
		{
			float mx = (context.mouseX() - this.area.x) * this.skala;
			float my = (context.mouseY() - this.area.y) * this.skala;

			NanoZonen.Zone zone = this.zonen.treffe(mx, my);

			if (zone != null)
			{
				UIUtils.playClick();
				zone.aktion().run();
				this.dirty = true;

				return true;
			}
		}

		return super.subMouseClicked(context);
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
