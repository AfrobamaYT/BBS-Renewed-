package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;
    private Texture zwischenbild;
    private Texture hervorhebung;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    /** Reused readback buffer for the tolerance region pick (grows as needed). */
    private FloatBuffer pickBuffer;

    /**
     * Das Zwischenbild der Filterdurchgaenge.
     *
     * <p>26.2: die Hervorhebung wird nicht mehr beim Zeichnen in die Oberflaeche
     * gerechnet, sondern VORHER in diese Textur (siehe Bildfilter) -- die Oberflaeche
     * blittet danach ein fertiges Bild.
     *
     * <p>⚠️ EIGENE Textur je Auswahl-Puffer, keine geteilte. Die Oberflaeche zeichnet
     * erst, nachdem alles gesammelt ist; ein geteiltes Zwischenbild waere zum
     * Zeichenzeitpunkt von der naechsten Aufrufstelle ueberschrieben und alle zeigten
     * dasselbe.
     */
    public Texture zwischenbild()
    {
        Texture quelle = this.framebuffer.getMainTexture();

        if (this.zwischenbild == null)
        {
            this.zwischenbild = new Texture();
            this.zwischenbild.setFilter(GL11.GL_NEAREST);
            this.zwischenbild.setWrap(GL13.GL_CLAMP_TO_EDGE);
        }

        if (this.zwischenbild.width != quelle.width || this.zwischenbild.height != quelle.height)
        {
            this.zwischenbild.bind();
            this.zwischenbild.setSize(quelle.width, quelle.height);
        }

        return this.zwischenbild;
    }

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    public int getIndex()
    {
        return this.index;
    }

    public Map<Integer, Pair<Form, String>> getIndexMap()
    {
        return this.indexMap;
    }

    public Pair<Form, String> getPicked()
    {
        return this.indexMap.get(this.index);
    }

    public void setup(Link id)
    {
        if (this.framebuffer != null)
        {
            return;
        }

        this.framebuffer = BBSModClient.getFramebuffers().getFramebuffer(id, (framebuffer) ->
        {
            Texture texture = new Texture();

            texture.setSize(2, 2);
            texture.setFilter(GL11.GL_NEAREST);
            texture.setWrap(GL13.GL_CLAMP_TO_EDGE);

            /* ⛔ 26.2: DIE TIEFE MUSS EINE TEXTUR SEIN, KEIN RENDERBUFFER.
             *
             * Bis 1.21.1 hing hier ein Renderbuffer (DEPTH24_STENCIL8). Das reichte, weil
             * das Ziel eines Zeichenaufrufs damals das GEBUNDENE FBO war -- was daran hing,
             * war GL egal. In 26.2 nennt jeder Durchgang seine Anhaenge als
             * {@code GpuTextureView}, und ein Renderbuffer ist keine. Er waere fuer den
             * Kennungs-Durchgang schlicht nicht anmeldbar (siehe PickZiel), und ohne Tiefe
             * pickt man verdeckte Knochen.
             *
             * Der GL-Stencil-Anteil des alten Renderbuffers faellt damit weg -- er wurde
             * nirgends benutzt (kein GL_STENCIL_TEST auf diesem Puffer; "Stencil" heisst
             * hier BBS' Kennungspuffer, nicht GLs Schablone).
             *
             * ⚠️ setFormat VOR setSize: setSize legt den Speicher mit dem Format an, das in
             * diesem Augenblick gesetzt ist. Umgedreht entstuende eine Farbtextur, die
             * anschliessend als Tiefe angehaengt wird -- ein unvollstaendiges FBO. */
            Texture tiefe = new Texture();

            tiefe.setFormat(TextureFormat.DEPTH_F24);
            tiefe.setSize(2, 2);
            tiefe.setFilter(GL11.GL_NEAREST);
            tiefe.setWrap(GL13.GL_CLAMP_TO_EDGE);

            framebuffer.deleteTextures().attach(texture, GL30.GL_COLOR_ATTACHMENT0);
            framebuffer.attach(tiefe, GL30.GL_DEPTH_ATTACHMENT);
            framebuffer.unbind();
        });
    }

    /**
     * Die Tiefenansicht dieses Auswahl-Puffers, oder {@code null}.
     *
     * <p>Gesucht wird ueber das FORMAT, nicht ueber einen Listenplatz: {@link #setup} legt
     * die Tiefe zwar als zweite Textur an, aber ein Auswahl-Puffer, der vor dieser Aenderung
     * schon im {@code FramebufferManager} lag (Hotswap in eine laufende Sitzung), hat gar
     * keine. Dann ist {@code null} richtig und der Durchgang zeichnet ohne Verdeckung --
     * eine Warnung je Zeichenaufruf, kein Absturz.
     */
    private com.mojang.blaze3d.textures.GpuTextureView tiefenAnsicht()
    {
        for (Texture texture : this.framebuffer.textures)
        {
            if (texture.getFormat().isDepth())
            {
                return texture.view();
            }
        }

        return null;
    }

    public void resizeGUI(int w, int h)
    {
        float scale = BBSModClient.getGUIScale();

        this.resize(Math.round(w * scale), Math.round(h * scale));
    }

    public void resize(int w, int h)
    {
        if (this.framebuffer != null)
        {
            this.framebuffer.resize(w, h);
        }
    }

    public void apply()
    {
        this.framebuffer.applyClear();

        /* 26.2: das Binden allein reicht nicht mehr -- jeder Durchgang nennt sein Ziel
         * selbst. Hier wird angemeldet, wohin die Kennungsfarben gehen; siehe PickZiel.
         * Farbe UND Tiefe: ohne Tiefe gewinnt beim Picken der zuletzt gezeichnete Knochen
         * statt des vordersten. */
        mchorse.bbs_mod.graphics.PickZiel.anmelden(
            this.framebuffer.getMainTexture().view(), this.tiefenAnsicht());
    }

    public void pickGUI(UIContext context, Area area)
    {
        this.pickGUI(context.mouseX - area.x, area.h - context.mouseY + area.y);
    }

    /** {@link #pickGUI(UIContext, Area)} with a gizmo-handle hover tolerance
     *  ({@code radius} in GUI pixels; ids in {@code [1, handleMax]} grab from nearby). */
    public void pickGUI(UIContext context, Area area, int radius, int handleMax)
    {
        float scale = BBSModClient.getGUIScale();
        int x = Math.round((context.mouseX - area.x) * scale);
        int y = Math.round((area.h - context.mouseY + area.y) * scale);

        this.pick(x, y, Math.round(radius * scale), handleMax);
    }

    public void pickGUI(int x, int y)
    {
        float scale = BBSModClient.getGUIScale();

        this.pick(Math.round(x * scale), Math.round(y * scale));
    }

    public void pick(int x, int y)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer floats = stack.mallocFloat(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floats);

            /* TODO: make other channels work */
            int r = (int) (floats.get() * 255F);
            int g = (int) (floats.get() * 255F);
            int b = (int) (floats.get() * 255F);
            int a = (int) (floats.get() * 255F);

            this.index = a < 1F ? 0 : r | (g << 8) | (b << 16);
        }
    }

    /**
     * Pick, but let ids in {@code [1, handleMax]} (the gizmo's handles) grab from
     * nearby: search a {@code radius}-pixel disc around the cursor and take the
     * <em>nearest</em> such id, so a thin line captures when the cursor is beside
     * it — the way a typical 3D gizmo hovers. Anything outside that id range (form
     * parts / bones) still resolves at the exact pixel under the cursor, so only
     * the handles get the tolerance. {@code radius} is in framebuffer pixels;
     * {@code radius <= 0} falls back to the plain single-pixel {@link #pick}.
     */
    public void pick(int x, int y, int radius, int handleMax)
    {
        if (radius <= 0 || this.framebuffer == null)
        {
            this.pick(x, y);

            return;
        }

        Texture texture = this.framebuffer.getMainTexture();
        int x0 = Math.max(0, x - radius);
        int y0 = Math.max(0, y - radius);
        int x1 = Math.min(texture.width - 1, x + radius);
        int y1 = Math.min(texture.height - 1, y + radius);
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;

        if (w <= 0 || h <= 0)
        {
            this.index = 0;

            return;
        }

        int needed = w * h * 4;

        /* A large tolerance × GUI scale can make this region far bigger than the
         * LWJGL frame stack holds, so read into a cached heap buffer instead. */
        if (this.pickBuffer == null || this.pickBuffer.capacity() < needed)
        {
            this.pickBuffer = BufferUtils.createFloatBuffer(needed);
        }

        FloatBuffer floats = this.pickBuffer;

        floats.clear();
        GL11.glReadPixels(x0, y0, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, floats);

        {
            int centerId = 0;
            int nearestHandle = 0;
            long nearestDist = Long.MAX_VALUE;
            long radiusSq = (long) radius * radius;

            for (int py = 0; py < h; py++)
            {
                for (int px = 0; px < w; px++)
                {
                    int base = (py * w + px) * 4;

                    if ((int) (floats.get(base + 3) * 255F) < 1)
                    {
                        continue;
                    }

                    int id = (int) (floats.get(base) * 255F)
                        | ((int) (floats.get(base + 1) * 255F) << 8)
                        | ((int) (floats.get(base + 2) * 255F) << 16);
                    int fx = x0 + px;
                    int fy = y0 + py;

                    if (fx == x && fy == y)
                    {
                        centerId = id;
                    }

                    if (id >= 1 && id <= handleMax)
                    {
                        long dx = fx - x;
                        long dy = fy - y;
                        long dist = dx * dx + dy * dy;

                        if (dist <= radiusSq && dist < nearestDist)
                        {
                            nearestDist = dist;
                            nearestHandle = id;
                        }
                    }
                }
            }

            this.index = nearestHandle != 0 ? nearestHandle : centerId;
        }
    }

    public void unbind(StencilMap map)
    {
        this.unbind();

        this.indexMap.clear();
        this.indexMap.putAll(map.indexMap);
    }

    public void unbind()
    {
        /* ⚗️ HIER messen, nicht spaeter. Der Stencil wird gedrosselt gefuellt (rund 4 von 120
         * Bildern, siehe UIFilmControllerStencilThrottleMixin) -- eine Probe irgendwo im
         * UI-Durchlauf trifft den gefuellten Zustand also fast nie und meldet "leer", obwohl
         * sie nur den falschen Augenblick erwischt hat. Genau hinter der Fuellung ist der
         * einzige Ort, an dem die Antwort etwas wert ist. */
        mchorse.bbs_mod.graphics.Pufferprobe.vielleichtFarbeZaehlen(
            this.framebuffer.getMainTexture().id,
            this.framebuffer.getMainTexture().width, this.framebuffer.getMainTexture().height,
            255, 255, 255, 255, "stencil DIREKT nach der fuellung");

        mchorse.bbs_mod.graphics.PickZiel.abmelden();
        this.framebuffer.unbind();
    }

    public void clearPicking()
    {
        this.index = 0;
        this.indexMap.clear();
    }

    public boolean hasPicked()
    {
        return this.index > 0;
    }
}