package com.mcstudio.nano;

import org.lwjgl.opengl.GL33C;

/**
 * NanoGlKlammer — sichert VOR einem NanoVG-Durchlauf jeden GL-Zustand, den NanoVG verstellt,
 * und stellt ihn in {@link #zurueck()} exakt wieder her (Blaze3d cached Zustand; ein
 * verstellter Rest macht den NAECHSTEN MC-Frame kaputt, nicht unseren).
 *
 * <p>Zwei 26.2-Fallen stecken MIT in der Klammer, beide am Spike gemessen (Formen ok,
 * Text unsichtbar, advance korrekt, glGetError leer):</p>
 * <ul>
 * <li>⛔ PBOs: haengt ein GL_PIXEL_UNPACK_BUFFER (MCs VideoRecorder nutzt PBOs), liest der
 * Glyphen-Atlas-Upload von fontstash aus dem PUFFER statt aus dem Speicher — leerer Atlas.</li>
 * <li>⛔ Sampler-Objekte: 26.2 bindet Mojang-Sampler an die Einheiten (Texture-Doku "Sampler
 * aus Mojangs Cache"). Ein Sampler mit Mipmap-Minfilter macht NanoVGs mipmaplosen Atlas
 * UNVOLLSTAENDIG — sampelt Nullen, fehlerfrei unsichtbar. Einheit 0 wird sampler-frei
 * gestellt (NanoVG zeichnet ausschliesslich auf Einheit 0).</li>
 * </ul>
 */
final class NanoGlKlammer
{
	private final int drawFbo;
	private final int readFbo;
	private final int programm;
	private final int vao;
	private final int aktiv;
	private final int tex0;
	private final int srcRgb;
	private final int dstRgb;
	private final int srcA;
	private final int dstA;
	private final int unpack;
	private final int unpackPbo;
	private final int packPbo;
	private final int sampler0;
	private final int[] viewport = new int[4];
	private final boolean blend;
	private final boolean cull;
	private final boolean depth;
	private final boolean stencil;
	private final boolean scissor;

	static NanoGlKlammer sichern()
	{
		return new NanoGlKlammer();
	}

	private NanoGlKlammer()
	{
		this.drawFbo = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
		this.readFbo = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
		this.programm = GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
		this.vao = GL33C.glGetInteger(GL33C.GL_VERTEX_ARRAY_BINDING);
		this.aktiv = GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
		GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
		this.tex0 = GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
		GL33C.glGetIntegerv(GL33C.GL_VIEWPORT, this.viewport);
		this.blend = GL33C.glIsEnabled(GL33C.GL_BLEND);
		this.cull = GL33C.glIsEnabled(GL33C.GL_CULL_FACE);
		this.depth = GL33C.glIsEnabled(GL33C.GL_DEPTH_TEST);
		this.stencil = GL33C.glIsEnabled(GL33C.GL_STENCIL_TEST);
		this.scissor = GL33C.glIsEnabled(GL33C.GL_SCISSOR_TEST);
		this.srcRgb = GL33C.glGetInteger(GL33C.GL_BLEND_SRC_RGB);
		this.dstRgb = GL33C.glGetInteger(GL33C.GL_BLEND_DST_RGB);
		this.srcA = GL33C.glGetInteger(GL33C.GL_BLEND_SRC_ALPHA);
		this.dstA = GL33C.glGetInteger(GL33C.GL_BLEND_DST_ALPHA);
		this.unpack = GL33C.glGetInteger(GL33C.GL_UNPACK_ALIGNMENT);
		this.unpackPbo = GL33C.glGetInteger(GL33C.GL_PIXEL_UNPACK_BUFFER_BINDING);
		this.packPbo = GL33C.glGetInteger(GL33C.GL_PIXEL_PACK_BUFFER_BINDING);
		this.sampler0 = GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);

		GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, 0);
		GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, 0);
		GL33C.glBindSampler(0, 0);
	}

	void zurueck()
	{
		GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, this.drawFbo);
		GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, this.readFbo);
		GL33C.glUseProgram(this.programm);
		GL33C.glBindVertexArray(this.vao);
		GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
		GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, this.tex0);
		GL33C.glActiveTexture(this.aktiv);
		GL33C.glViewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
		schalte(GL33C.GL_BLEND, this.blend);
		schalte(GL33C.GL_CULL_FACE, this.cull);
		schalte(GL33C.GL_DEPTH_TEST, this.depth);
		schalte(GL33C.GL_STENCIL_TEST, this.stencil);
		schalte(GL33C.GL_SCISSOR_TEST, this.scissor);
		GL33C.glBlendFuncSeparate(this.srcRgb, this.dstRgb, this.srcA, this.dstA);
		GL33C.glPixelStorei(GL33C.GL_UNPACK_ALIGNMENT, this.unpack);
		GL33C.glBindBuffer(GL33C.GL_PIXEL_UNPACK_BUFFER, this.unpackPbo);
		GL33C.glBindBuffer(GL33C.GL_PIXEL_PACK_BUFFER, this.packPbo);
		GL33C.glBindSampler(0, this.sampler0);
	}

	private static void schalte(int fach, boolean an)
	{
		if (an)
		{
			GL33C.glEnable(fach);
		}
		else
		{
			GL33C.glDisable(fach);
		}
	}
}
