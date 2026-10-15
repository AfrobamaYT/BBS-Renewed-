package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Framebuffer-Objekte der Schicht vor 1.21.5: eines je Farb-/Tiefen-Paar (und Mip-Stufe),
 * zwischengespeichert; dazu das Loeschen von Farbe und Tiefe ueber GlStateManager.
 */
public final class RahmenPuffer
{
    /**
     * Schluessel ist der GL-Name; der Eintrag merkt sich aber die Huellen-Objekte. Minecraft
     * loescht beim Vergroessern eines Renderziels seine Texturen und GL vergibt den Namen neu --
     * RenderTargetAlt baut dafuer eine neue GlTexture. Ein Cache nur ueber den Namen lieferte
     * dann das alte FBO mit der verwaisten Textur: die GUI landete unsichtbar darin, das Fenster
     * blieb schwarz (gemessen 2026-10-14 mit RenderDoc, Dashboard nach Fenstergroessenwechsel).
     */
    private record Eintrag(int fbo, GlTexture farbe, GlTexture tiefe)
    {}

    private final Map<Long, Eintrag> objekte = new HashMap<>();

    public int framebuffer(GlTexture farbe, int mip, GlTexture tiefe)
    {
        long schluessel = ((long) farbe.id << 32) | ((long) (tiefe == null ? 0 : tiefe.id) << 4) | mip;
        Eintrag eintrag = this.objekte.get(schluessel);

        if (eintrag != null && (eintrag.farbe() != farbe || eintrag.tiefe() != tiefe))
        {
            GL30.glDeleteFramebuffers(eintrag.fbo());
            this.objekte.remove(schluessel);
            eintrag = null;
        }

        Integer fbo = eintrag == null ? null : eintrag.fbo();

        if (fbo == null)
        {
            fbo = GL30.glGenFramebuffers();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, farbe.getFormat().hasDepthAspect() ? GL30.GL_DEPTH_ATTACHMENT : GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, farbe.id, mip);

            if (tiefe != null)
            {
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, tiefe.id, 0);
            }

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                throw new IllegalStateException("Framebuffer fuer " + farbe.getLabel() + " unvollstaendig: " + status);
            }

            this.objekte.put(schluessel, new Eintrag(fbo, farbe, tiefe));
        }

        return fbo;
    }

    /** Vergisst die Framebuffer einer geschlossenen Textur. */
    public void texturWeg(GlTexture textur)
    {
        Iterator<Map.Entry<Long, Eintrag>> it = this.objekte.entrySet().iterator();
        long id = textur.id;

        while (it.hasNext())
        {
            Map.Entry<Long, Eintrag> e = it.next();

            if ((e.getKey() >>> 32) == id || ((e.getKey() >>> 4) & 0xFFFFFFFL) == id)
            {
                GL30.glDeleteFramebuffers(e.getValue().fbo());
                it.remove();
            }
        }
    }

    /** Fuehrt einen Befehl auf dem Framebuffer der Textur aus und stellt die vorige Bindung wieder her. */
    public void auf(GlTexture farbe, GlTexture tiefe, Runnable befehl)
    {
        int alt = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.framebuffer(farbe, 0, tiefe));
        befehl.run();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, alt);
    }

    public static void loeschen(OptionalInt farbe, OptionalDouble tiefe, boolean hatTiefe)
    {
        int maske = 0;

        if (farbe.isPresent())
        {
            int argb = farbe.getAsInt();

            GlStateManager._colorMask(true, true, true, true);
            GlStateManager._clearColor(kanal(argb, 16), kanal(argb, 8), kanal(argb, 0), kanal(argb, 24));
            maske |= GL11.GL_COLOR_BUFFER_BIT;
        }

        if (tiefe.isPresent() && hatTiefe)
        {
            GlStateManager._depthMask(true);
            GlStateManager._clearDepth(tiefe.getAsDouble());
            maske |= GL11.GL_DEPTH_BUFFER_BIT;
        }

        if (maske != 0)
        {
            GlStateManager._disableScissorTest();
            org.lwjgl.opengl.GL11.glClear(maske);
        }
    }

    /** Loescht Farbe und Tiefe nur im Rechteck. */
    public static void loeschenIn(int argb, double tiefe, int x, int y, int breite, int hoehe)
    {
        GlStateManager._enableScissorTest();
        GlStateManager._scissorBox(x, y, breite, hoehe);
        GlStateManager._colorMask(true, true, true, true);
        GlStateManager._depthMask(true);
        GlStateManager._clearColor(kanal(argb, 16), kanal(argb, 8), kanal(argb, 0), kanal(argb, 24));
        GlStateManager._clearDepth(tiefe);
        org.lwjgl.opengl.GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager._disableScissorTest();
    }

    /** ARGB-Kanal als 0..1 (1.21.4s ARGB kennt nur Rot und Alpha als Float). */
    private static float kanal(int argb, int verschiebung)
    {
        return ((argb >>> verschiebung) & 0xFF) / 255F;
    }
}
