package com.mcstudio.nano;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.opengl.GL33C;

import java.util.HashMap;
import java.util.Map;

/**
 * NanoEbene — eine NanoVG-Ebene, die JEDES BILD neu gemalt wird, in eine eigene Textur.
 *
 * <p>{@link NanoFlaeche} malt im Client-Tick (20 Hz) — fuer Panels richtig, fuer Beschriftungen
 * an Weltpunkten nicht: die haengen an der Kamera und wuerden beim Drehen hinterherschwimmen.
 * Engines projizieren solche Marker je Bild mit der fertigen Kamera; genau dafuer ist diese Ebene.</p>
 *
 * <p>Aufruf ausserhalb jedes RenderPass (HUD-Aufbau, UI-render): unter Vulkan haengt
 * {@link NanoVkRenderer} einen eigenen Befehlspuffer in die Abgabe — der Encoder wirft laut,
 * wenn gerade ein Pass offen ist. Unter GL sichert {@link NanoGlKlammer} jeden Zustand.</p>
 *
 * <p>Der Inhalt ist vormultipliziert (NanoVG mischt ONE, ONE_MINUS_SRC_ALPHA); zum Aufblenden
 * gehoert {@code RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA}, sonst werden halbdurchsichtige
 * Stellen doppelt abgedunkelt.</p>
 */
public final class NanoEbene
{
	public interface Inhalt
	{
		void zeichnen(long vg, int breite, int hoehe);
	}

	/** Schrift-Puffer leben so lange wie der Prozess (nvgCreateFontMem kopiert nicht). */
	private static final Map<String, java.nio.ByteBuffer> SCHRIFTEN = new HashMap<>();

	private Texture textur;
	private int fbo;
	private int rbo;
	private int breite;
	private int hoehe;

	/**
	 * Malt den Inhalt jetzt. {@code null}, wenn kein NanoVG-Kontext da ist (Grund steht im Log).
	 * Die Textur ist bei {@link #kopfueber()} unten-links verankert (GL), sonst oben-links.
	 */
	public Texture malen(int breite, int hoehe, Inhalt inhalt)
	{
		long vg = NanoKontext.holen();

		if (vg == 0 || breite <= 0 || hoehe <= 0)
		{
			return null;
		}

		NanoVkRenderer vulkan = NanoKontext.vulkan();

		if (this.textur == null)
		{
			this.textur = NanoRessourcen.textur();
		}

		if (vulkan != null)
		{
			this.groesse(breite, hoehe);

			try
			{
				vulkan.begin(this.textur.attachmentView(), breite, hoehe);
				inhalt.zeichnen(vulkan.context(), breite, hoehe);
				vulkan.end();
			}
			catch (Throwable fehler)
			{
				vulkan.cancel();
				throw new IllegalStateException("NanoVG-Ebene unter Vulkan gescheitert (" + breite + "x" + hoehe + ")", fehler);
			}

			return this.textur;
		}

		NanoGlKlammer klammer = NanoGlKlammer.sichern();

		try
		{
			if (this.fbo == 0)
			{
				this.fbo = NanoRessourcen.fbo(this.textur);
				this.rbo = NanoRessourcen.rbo(this.textur);
			}

			if (this.groesse(breite, hoehe))
			{
				GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, this.rbo);
				GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_DEPTH24_STENCIL8, breite, hoehe);
			}

			GL33C.glBindFramebuffer(GL33C.GL_FRAMEBUFFER, this.fbo);
			GL33C.glFramebufferTexture2D(GL33C.GL_FRAMEBUFFER, GL33C.GL_COLOR_ATTACHMENT0, GL33C.GL_TEXTURE_2D, this.textur.id, 0);
			GL33C.glFramebufferRenderbuffer(GL33C.GL_FRAMEBUFFER, GL33C.GL_DEPTH_STENCIL_ATTACHMENT, GL33C.GL_RENDERBUFFER, this.rbo);

			if (GL33C.glCheckFramebufferStatus(GL33C.GL_FRAMEBUFFER) != GL33C.GL_FRAMEBUFFER_COMPLETE)
			{
				throw new IllegalStateException("NanoVG-Ebene: FBO unvollstaendig (" + breite + "x" + hoehe + ")");
			}

			GL33C.glViewport(0, 0, breite, hoehe);
			GL33C.glDisable(GL33C.GL_SCISSOR_TEST);
			GL33C.glDisable(GL33C.GL_DEPTH_TEST);
			GL33C.glDisable(GL33C.GL_CULL_FACE);
			GL33C.glClearColor(0F, 0F, 0F, 0F);
			GL33C.glClear(GL33C.GL_COLOR_BUFFER_BIT | GL33C.GL_STENCIL_BUFFER_BIT);
			NanoVG.nvgBeginFrame(vg, breite, hoehe, 1F);
			inhalt.zeichnen(vg, breite, hoehe);
			NanoVG.nvgEndFrame(vg);
		}
		finally
		{
			klammer.zurueck();
		}

		return this.textur;
	}

	/** GL-Texturen liegen unten-links; beim Aufblenden die V-Koordinaten tauschen. */
	public boolean kopfueber()
	{
		return NanoKontext.vulkan() == null;
	}

	private boolean groesse(int breite, int hoehe)
	{
		if (breite == this.breite && hoehe == this.hoehe)
		{
			return false;
		}

		this.textur.setSize(breite, hoehe);
		this.breite = breite;
		this.hoehe = hoehe;

		return true;
	}

	/**
	 * Schrift aus den Mod-Ressourcen im geteilten Kontext anmelden (einmal je Kontext).
	 * Fehlt die Datei, wirft es — eine still fehlende Schrift zeichnet sonst leere Karten.
	 */
	public static void schrift(long vg, String name, String pfad)
	{
		if (NanoVG.nvgFindFont(vg, name) != -1)
		{
			return;
		}

		java.nio.ByteBuffer puffer = SCHRIFTEN.get(pfad);

		if (puffer == null)
		{
			try (java.io.InputStream strom = NanoEbene.class.getResourceAsStream(pfad))
			{
				if (strom == null)
				{
					throw new IllegalStateException("Schrift fehlt in den Ressourcen: " + pfad);
				}

				byte[] bytes = strom.readAllBytes();

				puffer = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length);
				puffer.put(bytes).flip();
				SCHRIFTEN.put(pfad, puffer);
			}
			catch (java.io.IOException fehler)
			{
				throw new IllegalStateException("Schrift unlesbar: " + pfad, fehler);
			}
		}

		if (NanoVG.nvgCreateFontMem(vg, name, puffer, false) == -1)
		{
			throw new IllegalStateException("NanoVG lehnt die Schrift ab: " + pfad);
		}
	}
}
