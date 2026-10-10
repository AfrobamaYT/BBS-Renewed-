package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.graphics.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

import java.nio.ByteBuffer;
import java.util.function.ToIntBiFunction;

/**
 * Reads the pick ids of a {@link StencilFormFramebuffer} back from the GPU: synchronously, or
 * through a pixel pack buffer behind a fence ({@link StencilFormFramebuffer#pickAsync}).
 * Fields have no initialisers (a hotswap would leave them unset); all is created lazily.
 */
final class StencilLeser
{
    /** {@link #aufloesen} had nothing to evaluate (no request, or not finished yet). */
    static final int KEIN_ERGEBNIS = Integer.MIN_VALUE;

    /* Own read FBO and reused synchronous buffer; pixel pack buffer, its fence (0 = nothing
     * pending) and the request {x0, y0, w, h, x, y, radius, handleMax}. 0 = not created. */
    private int leseFbo;
    private ByteBuffer sofort;
    private int lesePuffer;
    private int lesePufferGroesse;
    private long leseZaun;
    private int[] leseLage;

    /** Synchronous read of a region; the returned buffer is reused by the next call. */
    ByteBuffer lesenSofort(Framebuffer framebuffer, int x0, int y0, int w, int h)
    {
        int needed = w * h * 4;

        /* A large tolerance × GUI scale can make this region far bigger than the
         * LWJGL frame stack holds, so read into a cached heap buffer instead. */
        if (this.sofort == null || this.sofort.capacity() < needed)
        {
            this.sofort = BufferUtils.createByteBuffer(needed);
        }

        this.sofort.clear();
        this.lesen(framebuffer, x0, y0, w, h, this.sofort);

        return this.sofort;
    }

    /** Queue the region {@code lage} (see {@link StencilAuswertung#region}) and fence it. */
    void anfordern(Framebuffer framebuffer, int[] lage)
    {
        int bytes = lage[2] * lage[3] * 4;
        int vorher = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        if (this.lesePuffer == 0)
        {
            this.lesePuffer = GL15.glGenBuffers();
        }

        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, this.lesePuffer);

        try
        {
            if (this.lesePufferGroesse < bytes)
            {
                GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, bytes, GL15.GL_STREAM_READ);
                this.lesePufferGroesse = bytes;
            }

            this.lesen(framebuffer, lage[0], lage[1], lage[2], lage[3], null);
        }
        finally
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, vorher);
        }

        this.leseLage = lage;
        this.leseZaun = GL32.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    boolean ausstehend()
    {
        return this.leseZaun != 0L;
    }
    /**
     * Evaluate the pending request once the GPU has written it.
     *
     * @param warten {@code false}: only if already done (same-frame drawing); {@code true}:
     *               wait for it (exact accessors, next request)
     * @return the evaluated index, or {@link #KEIN_ERGEBNIS} if nothing was evaluated
     */
    int aufloesen(boolean warten, ToIntBiFunction<ByteBuffer, int[]> auswertung)
    {
        if (this.leseZaun == 0L)
        {
            return KEIN_ERGEBNIS;
        }

        int status = GL32.glClientWaitSync(this.leseZaun, 0, 0L);

        if (status == GL32.GL_TIMEOUT_EXPIRED)
        {
            if (!warten)
            {
                return KEIN_ERGEBNIS;
            }

            status = GL32.glClientWaitSync(this.leseZaun, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, 2_000_000_000L);
        }

        this.verwerfen();

        ByteBuffer daten = null;
        int vorher = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, this.lesePuffer);

        try
        {
            if (status == GL32.GL_ALREADY_SIGNALED || status == GL32.GL_CONDITION_SATISFIED)
            {
                daten = GL30.glMapBufferRange(GL21.GL_PIXEL_PACK_BUFFER, 0L,
                    (long) this.leseLage[2] * this.leseLage[3] * 4, GL30.GL_MAP_READ_BIT);
            }

            if (daten == null)
            {
                /* No result rather than a wrong one -- and loud, not silent. */
                mchorse.bbs_mod.graphics.Messpunkt.stoerung("stencil.async", "status=0x" + Integer.toHexString(status));

                return 0;
            }

            return auswertung.applyAsInt(daten, this.leseLage);
        }
        finally
        {
            if (daten != null)
            {
                GL15.glUnmapBuffer(GL21.GL_PIXEL_PACK_BUFFER);
            }

            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, vorher);
        }
    }

    void verwerfen()
    {
        if (this.leseZaun != 0L)
        {
            GL32.glDeleteSync(this.leseZaun);
            this.leseZaun = 0L;
        }
    }

    /**
     * Liest eine Region AUS DEM EIGENEN Puffer zurueck.
     *
     * <h2>⛔ OHNE DAS BINDEN LIEST DER PICKER DEN BILDSCHIRM (2026-08-02 gemessen)</h2>
     *
     * <p>{@code glReadPixels} nimmt keine Zieltextur entgegen -- es liest, was gerade als
     * {@code GL_READ_FRAMEBUFFER} gebunden ist. Bis 1.21.1 band {@link StencilFormFramebuffer#apply()} den
     * Auswahlpuffer global, und das Lesen darunter fand ihn vor. In 26.2 nennt jeder
     * Durchgang sein Ziel selbst; {@code apply()} meldet nur noch {@code PickZiel} an und
     * bindet nichts mehr. Gemessen an der Sonde {@code stencil.leseziel}:
     * {@code gebunden=0 eigener=29} -- gelesen wurde der Standard-Framebuffer, also das
     * fertige Bild, dessen RGB keine Kennungen traegt. Ergebnis: {@code index=0} an jeder
     * Mausposition, an Kopf wie an Bein.
     *
     * <p>⚠️ ROHES {@code GL30} statt {@code GlStateManager}, mit Absicht: dessen
     * 26.2-Fassung merkt sich {@code readFbo} und UEBERSPRINGT den Aufruf bei einem
     * Treffer. Hier wird der ECHTE Zustand gelesen und exakt wiederhergestellt -- ein
     * Zwischenspeicher, der etwas anderes glaubt, wuerde die Bindung verschlucken und
     * genau den Fehler zurueckbringen, den diese Methode behebt.
     */
    private void lesen(Framebuffer framebuffer, int x, int y, int w, int h, ByteBuffer ziel)
    {
        if (framebuffer == null)
        {
            glLesen(x, y, w, h, ziel);

            return;
        }

        int vorher = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        /* ⛔ EIGENER LESE-FBO STATT DES ZEICHEN-FBOs (2026-08-04, per Bisect gefunden).
         *
         * Aus dem Zeichen-FBO herauszulesen lieferte an JEDER Position Nullen -- bei
         * vollstaendigem FBO, passendem Anhang (724) und Status COMPLETE. Ein
         * ausdrueckliches glReadBuffer(COLOR_ATTACHMENT0) aenderte daran nichts.
         *
         * Ein zweiter Lesepfad mit EIGENEM FBO fand dieselben 21606 Punkte wie RenderDoc;
         * das wird hier nachgezogen.
         *
         * Der FBO wird einmal erzeugt und behalten; die Textur wird bei jedem Lesen neu
         * angehaengt, weil sie sich bei einer Groessenaenderung ersetzen kann. */
        if (this.leseFbo == 0)
        {
            this.leseFbo = GL30.glGenFramebuffers();
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.leseFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, framebuffer.getMainTexture().id, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        try
        {
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                /* Haengt am FBO noch DIE Textur, in die 26.2 zeichnet? Eine alte GL-Nummer nach
                 * Framebuffer#resize liest eine freigegebene Textur und findet nichts. */
                mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.leseziel",
                    "gebunden=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                        + " eigener=" + framebuffer.id
                        + " anhang=" + GL30.glGetFramebufferAttachmentParameteri(
                            GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)
                        + " textur=" + framebuffer.getMainTexture().id
                        + " status=0x" + Integer.toHexString(
                            GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)));
            }

            glLesen(x, y, w, h, ziel);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);
        }
    }

    /** {@code ziel == null}: into the bound pixel pack buffer at offset 0 (asynchronous). */
    private static void glLesen(int x, int y, int w, int h, ByteBuffer ziel)
    {
        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int packBuffer = ziel == null ? 0 : GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        try
        {
            /* Both destinations hold exactly w*h RGBA8 pixels, not the renderer's row stride. */
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);

            /* RGBA8 as stored: GL_FLOAT made the driver convert on the CPU and block even into a
             * pixel pack buffer (27.09.2026). Same ids: (int) ((c / 255F) * 255F) == c for all 256 c. */
            if (ziel == null)
            {
                GL11.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
            }
            else
            {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
                GL11.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ziel);
            }
        }
        finally
        {
            if (ziel != null)
            {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            }

            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
        }
    }
}
