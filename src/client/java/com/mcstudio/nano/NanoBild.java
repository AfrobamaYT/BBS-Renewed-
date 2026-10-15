package com.mcstudio.nano;

import org.lwjgl.opengl.GL33C;

import java.io.File;

import javax.imageio.ImageIO;

/** NanoBild — liest das GERADE GEBUNDENE FBO als PNG aus (Y gespiegelt, GL liest von unten). */
final class NanoBild
{
	private NanoBild()
	{
	}

	/** null = ok, sonst fehler-JSON. */
	static String alsPng(String pfad, int breite, int hoehe)
	{
		java.nio.ByteBuffer puffer = org.lwjgl.system.MemoryUtil.memAlloc(breite * hoehe * 4);

		try
		{
			GL33C.glPixelStorei(GL33C.GL_PACK_ALIGNMENT, 1);
			GL33C.glReadPixels(0, 0, breite, hoehe, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, puffer);

			java.awt.image.BufferedImage bild = new java.awt.image.BufferedImage(breite, hoehe, java.awt.image.BufferedImage.TYPE_INT_ARGB);

			for (int y = 0; y < hoehe; y++)
			{
				int zeile = (hoehe - 1 - y) * breite * 4;

				for (int x = 0; x < breite; x++)
				{
					int i = zeile + x * 4;
					int r = puffer.get(i) & 0xFF;
					int g = puffer.get(i + 1) & 0xFF;
					int b = puffer.get(i + 2) & 0xFF;
					int a = puffer.get(i + 3) & 0xFF;

					bild.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
				}
			}

			ImageIO.write(bild, "png", new File(pfad));

			return null;
		}
		catch (Throwable t)
		{
			return "{\"error\":\"png schreiben: " + String.valueOf(t).replace("\"", "'") + "\"}";
		}
		finally
		{
			org.lwjgl.system.MemoryUtil.memFree(puffer);
		}
	}
}
