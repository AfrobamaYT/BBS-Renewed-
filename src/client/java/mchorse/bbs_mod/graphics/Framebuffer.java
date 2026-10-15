package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.opengl.GlStateManager;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.RenderPassDescriptor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.joml.Vector4f;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.ArrayDeque;
import java.util.Deque;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class Framebuffer
{
    private static final float[] CLEAR_COLOR = {0F, 0F, 0F, 0F};
    /**
     * ⛔ 26.2 RECHNET DIE TIEFE UMGEKEHRT — darum 0, nicht 1.
     *
     * <p>Bis 1.21.1 war "am weitesten weg" die 1, der Test hiess LEQUAL, und dieser Puffer
     * wurde entsprechend auf 1 geloescht. In 26.2 ist es andersherum: der Loeschwert ist 0
     * ({@code 0.0D}), der Test heisst
     * GREATER_THAN_OR_EQUAL, und {@code LevelRenderer} schiebt beim Loeschen
     * {@code dconst_0}.
     *
     * <p>Bliebe hier die 1 stehen, waere jeder GREATER-Test in BBS' eigenen Puffern von
     * vornherein verloren: Picking und Stencil lieferten ein leeres Bild — <b>ohne
     * Fehlermeldung, ohne Absturz, einfach nichts anklickbar</b>. Genau diese Falle hat
     * eine der Zustands-Pruefungen vorhergesagt, bevor sie jemand gesehen hat.
     */
    private static final float[] CLEAR_DEPTH = {0F};

    public int id;
    public List<Texture> textures = new ArrayList<>();
    public final List<Renderbuffer> renderbuffers = new ArrayList<>();

    /**
     * Welche Textur an welchem Anhangspunkt haengt -- gebraucht von {@link #resize}.
     *
     * <p>⛔ 26.2: EINE TEXTUR WECHSELT BEI GROESSENAENDERUNG IHRE GL-NUMMER. Bis 1.21.1 war
     * {@code Texture.id} fuer die Lebensdauer der Textur fest, ein {@code glTexImage2D} mit
     * neuer Groesse schrieb einfach in dieselbe Nummer, und das FBO blieb gueltig. In 26.2
     * gehoert die Nummer dem GPU-Objekt: {@code Texture.allocate} SCHLIESST es bei
     * Groessenwechsel und legt ein neues an (dort steht der Satz "wer die Nummer
     * zwischenspeichert, zeichnet nach der naechsten Groessenaenderung eine fremde Textur").
     *
     * <p>Das FBO speichert die Nummer -- im GL-Anhang. Ohne das Nachziehen hier zeigt es
     * nach dem ersten {@code resize} auf eine freigegebene Textur: {@code glClear} loescht
     * nichts (alte Kennungen bleiben stehen) und {@code glReadPixels} liest nichts (der
     * Klick findet nichts). Das trifft JEDEN Auswahl-Puffer, denn sie beginnen alle 2x2 und
     * werden sofort auf ihre echte Groesse gezogen.
     */
    private final Map<Texture, Integer> anhaenge = new IdentityHashMap<>();

    private boolean deleteTextures;
    private boolean advancedClearing;
    private final boolean openGl;
    private boolean deleted;
    private int[] drawAttachments = {GL30.GL_COLOR_ATTACHMENT0};
    private static final Deque<TargetBinding> bindings = new ArrayDeque<>();
    private record TargetBinding(Framebuffer framebuffer, GpuTextureView color, GpuTextureView depth,
        mchorse.bbs_mod.compat.alt.RenderArea area, GlPassBinding gl) {}

    /** Real GL state around an engine pass; an RBO remains attached to its real BBS FBO. */
    static final class GlPassBinding
    {
        private final Framebuffer owner;
        private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int[] viewport = new int[4], scissor = new int[4];
        private final boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        private final boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        private final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        GlPassBinding(Framebuffer owner)
        {
            this.owner = owner;
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, this.viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, this.scissor);
        }
        void bind()
        {
            if (this.owner != null) GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.owner.id);
        }
        void restore()
        {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, this.draw);
            GlStateManager._viewport(this.viewport[0], this.viewport[1], this.viewport[2], this.viewport[3]);
            GlStateManager._scissorBox(this.scissor[0], this.scissor[1], this.scissor[2], this.scissor[3]);
            if (this.scissorEnabled) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
            if (this.depthEnabled) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
            GlStateManager._depthMask(this.depthWrite); GlStateManager._depthFunc(this.depthFunc);
        }
    }

    static GlPassBinding glPassBindingFor(GpuTextureView color, GpuTextureView depth)
    {
        Framebuffer owner = bindings.isEmpty() ? null : bindings.peek().framebuffer;
        if (owner != null && (!owner.openGl || !sameAttachment(owner.colorView(), color)
            || !sameAttachment(owner.depthView(), depth))) owner = null;
        return new GlPassBinding(owner);
    }

    public Framebuffer()
    {
        this.openGl = GpuBackend.isOpenGl();
        this.id = this.openGl ? GL30.glGenFramebuffers() : -1;
    }

    public Framebuffer enableAdvancedClearing()
    {
        this.advancedClearing = true;

        return this;
    }

    public Framebuffer deleteTextures()
    {
        this.deleteTextures = true;

        return this;
    }

    public Texture getMainTexture()
    {
        return this.textures.get(0);
    }

    /**
     * Attach a texture as one of the attachment buffers
     */
    public Framebuffer attach(Texture texture, int attachment)
    {
        if (this.anhaenge.containsValue(attachment))
            throw new IllegalArgumentException("Framebuffer attachment already occupied: " + attachment);
        this.textures.add(texture);
        this.anhaenge.put(texture, attachment);

        if (!this.openGl) return this;

        GlPassBinding gl = new GlPassBinding(this);
        try { gl.bind(); texture.bind(); GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, texture.target, texture.id, 0); }
        finally { gl.restore(); }

        return this;
    }

    /**
     * Haengt alle gemerkten Texturen mit ihrer AKTUELLEN GL-Nummer neu an — fuer Aufrufer,
     * deren Textur ausserhalb von {@link #resize} die Nummer gewechselt hat (setSize direkt,
     * siehe {@link #anhaenge}). Ein glFramebufferTexture2D je Bild kostet nichts Messbares.
     */
    public void anhangNachziehen()
    {
        if (!this.openGl)
        {
            this.descriptor("BBS framebuffer attachment validation", false);
            return;
        }
        GlPassBinding gl = new GlPassBinding(this);
        gl.bind();
        try
        {
            for (Texture texture : this.textures)
            {
                Integer anhang = this.anhaenge.get(texture);
                if (anhang == null) continue;
                texture.bind();
                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, anhang, texture.target, texture.id, 0);
            }
        }
        finally { gl.restore(); }
    }

    /**
     * Attach a renderbuffer as one of the attachment buffers
     */
    public void attach(Renderbuffer renderbuffer)
    {
        this.renderbuffers.add(renderbuffer);
        if (!this.openGl) return;
        GlPassBinding gl = new GlPassBinding(this);
        try { gl.bind(); renderbuffer.bind(); GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, renderbuffer.target, GL30.GL_RENDERBUFFER, renderbuffer.id); }
        finally { gl.restore(); }
    }

    public void attachments(int count)
    {
        int[] attachments = new int[count];

        for (int i = 0; i < count; i++)
        {
            attachments[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }

        this.attachments(attachments);
    }

    public void attachments(int... attachments)
    {
        this.drawAttachments = attachments.clone();
        if (!this.openGl) return;
        GlPassBinding gl = new GlPassBinding(this);
        try { gl.bind(); GL30.glDrawBuffers(attachments); }
        finally { gl.restore(); }
    }

    public void applyClear()
    {
        this.apply();
        this.clear();
    }

    public void apply()
    {
        Texture texture = this.getMainTexture();

        this.bind();
        if (this.openGl) GL11.glViewport(0, 0, texture.width, texture.height);
        Zeichenbereich.setzen(0, 0, texture.width, texture.height);
    }

    public void clear()
    {
        if (!this.openGl)
        {
            CommandEncoder encoder = FramebufferUtils.outsidePass("Framebuffer clear");
            // Load-op clears target only the attached mip level and selected draw buffers.
            try (var pass = encoder.createRenderPass(this.descriptor("BBS framebuffer clear", true))) {}
            return;
        }
        if (this.advancedClearing)
        {
            int i = 0;

            for (Texture texture : this.textures)
            {
                if (texture.getFormat().isColor())
                {
                    if (texture.isClearable())
                    {
                        GL30.glClearBufferfv(GL30.GL_COLOR, i, CLEAR_COLOR);
                    }

                    i += 1;
                }
                else if (texture.isClearable())
                {
                    GL30.glClearBufferfv(GL30.GL_DEPTH, 0, CLEAR_DEPTH);
                }
            }
        }
        else
        {
            /* Auch hier gilt der umgekehrte Tiefenbereich: glClear nimmt den Wert aus
             * glClearDepth, und dessen GL-Vorgabe ist 1. Ohne diese Zeile loescht der
             * Zweig auf 1, waehrend der Zweig darueber auf 0 loescht — zwei Puffer
             * desselben Mods mit gegensaetzlicher Tiefe, je nach Anhang-Aufbau. */
            GL11.glClearDepth(CLEAR_DEPTH[0]);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }
    }

    /* ⛔ UEBER GlStateManager BINDEN, NICHT ROH — 26.2 FUEHRT EINEN CACHE (2026-08-02).
     *
     * Hier stand `GL30.glBindFramebuffer(...)` direkt. In 1.21.1 war das gleichwertig: dortiges
     * `GlStateManager._glBindFramebuffer` rief bedingungslos durch. In 26.2 haelt der
     * GlStateManager die Felder `readFbo` und `writeFbo` (an der laufenden JVM nachgesehen) und
     * UEBERSPRINGT den GL-Aufruf, wenn der gewuenschte Puffer schon gebunden zu sein scheint.
     *
     * Wer roh daran vorbei bindet, laesst diesen Cache auseinanderlaufen: nach unserem
     * unbind() (bindet 0) glaubt Blaze3D weiter, sein eigenes FBO sei gebunden, ueberspringt
     * das naechste Binden — und der folgende Zug landet in Puffer 0. Kein GL-Fehler, keine
     * Logzeile. Gleicher BBS-Code, geaenderte Umgebung: das Portmuster.
     *
     * Signatur an der laufenden 26.2 geprueft: `public static _glBindFramebuffer(int, int)`.
     * Der Weg ueber den Manager ist strikt sicherer als der rohe — er kann den Cache nur
     * richtig halten, nie zusaetzlich verwirren. */
    public void bind()
    {
        if (!this.openGl) FramebufferUtils.outsidePass("Framebuffer target setup");
        this.descriptor("BBS framebuffer target validation", false);
        if (bindings.isEmpty() || bindings.peek().framebuffer != this)
            bindings.push(new TargetBinding(this, Zeichenziel.farbe(), Zeichenziel.tiefe(), Zeichenbereich.aktiv(),
                this.openGl ? new GlPassBinding(this) : null));
        Zeichenziel.setzen(this.colorView(), this.depthView());
        if (this.openGl) GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.id);
    }

    public void unbind()
    {
        /* Setup code (attach + unbind) never entered a draw target. While another target is
         * bound -- a stencil buffer created lazily in the middle of the film preview's frame
         * (crash 2026-10-13 20:53) -- there is nothing of ours to restore. */
        if (bindings.stream().noneMatch(binding -> binding.framebuffer == this)) return;
        if (bindings.peek().framebuffer != this) throw new IllegalStateException("Framebuffer targets must be restored in LIFO order");
        TargetBinding previous = bindings.pop();
        Zeichenziel.setzen(previous.color, previous.depth);
        if (previous.area == null) Zeichenbereich.freigeben();
        else Zeichenbereich.setzen(previous.area.x(), previous.area.y(), previous.area.width(), previous.area.height());
        if (previous.gl != null) previous.gl.restore();
    }

    public void resize(int w, int h)
    {
        w = Math.max(1, w);
        h = Math.max(1, h);
        if (!this.openGl)
        {
            FramebufferUtils.outsidePass("Framebuffer resize");
            for (Texture texture : this.textures) texture.setSize(w, h);
            for (Renderbuffer renderbuffer : this.renderbuffers) renderbuffer.resize(w, h);
            if (!bindings.isEmpty() && bindings.peek().framebuffer == this)
            {
                Zeichenziel.setzen(this.colorView(), this.depthView());
                Zeichenbereich.setzen(0, 0, w, h);
            }
            return;
        }
        boolean neueNummern = false;

        for (Texture texture : this.textures)
        {
            int alteNummer = texture.id;

            texture.bind();
            texture.setSize(w, h);

            neueNummern |= texture.id != alteNummer;
        }

        /* Siehe {@link #anhaenge}: hat eine Textur ihre GL-Nummer gewechselt, haengt am FBO
         * noch die alte, freigegebene. Nur dann wird neu angehaengt -- der Normalfall
         * "gleiche Groesse" kostet keinen einzigen GL-Aufruf.
         *
         * ⚠️ Gebunden und wieder geloest wie in den setup()-Bloecken der Aufrufer:
         * glFramebufferTexture2D wirkt ausschliesslich auf das gebundene FBO. */
        if (neueNummern)
        {
            GlPassBinding gl = new GlPassBinding(this);
            gl.bind();
            try
            {
                for (Texture texture : this.textures)
                {
                    Integer anhang = this.anhaenge.get(texture);
                    if (anhang == null) continue;
                    texture.bind();
                    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, anhang, texture.target, texture.id, 0);
                }
            }
            finally { gl.restore(); }
        }

        for (Renderbuffer renderbuffer : this.renderbuffers)
        {
            renderbuffer.bind();
            renderbuffer.resize(w, h);
            renderbuffer.unbind();
        }
        if (!bindings.isEmpty() && bindings.peek().framebuffer == this)
        {
            Zeichenziel.setzen(this.colorView(), this.depthView());
            Zeichenbereich.setzen(0, 0, w, h);
            GlStateManager._viewport(0, 0, w, h);
        }
    }

    public void delete()
    {
        if (this.deleted) return;
        if (bindings.stream().anyMatch(b -> b.framebuffer == this))
            throw new IllegalStateException("Cannot destroy a bound framebuffer");
        if (this.openGl && this.id >= 0) GL30.glDeleteFramebuffers(this.id);
        this.id = -1;
        this.deleted = true;

        if (this.deleteTextures)
        {
            for (Texture texture : this.textures)
            {
                texture.delete();
            }

            this.textures.clear();
        }

        this.anhaenge.clear();

        for (Renderbuffer renderbuffer : this.renderbuffers)
        {
            renderbuffer.delete();
        }

        this.renderbuffers.clear();
    }

    public GpuTextureView colorView()
    {
        if (this.deleted) throw new IllegalStateException("Framebuffer was destroyed");
        for (Texture texture : this.textures)
            if (Integer.valueOf(GL30.GL_COLOR_ATTACHMENT0).equals(this.anhaenge.get(texture)))
                return texture.attachmentView();
        throw new IllegalStateException("Framebuffer has no COLOR_ATTACHMENT0");
    }

    public GpuTextureView depthView()
    {
        GpuTextureView result = null;
        for (Texture texture : this.textures)
            if (texture.getFormat().isDepth())
            {
                if (result != null) throw new IllegalStateException("Duplicate framebuffer depth attachments");
                result = texture.attachmentView();
            }
        for (Renderbuffer renderbuffer : this.renderbuffers)
            if (renderbuffer.view() != null)
            {
                if (result != null) throw new IllegalStateException("Duplicate framebuffer depth attachments");
                result = renderbuffer.view();
            }
        return result;
    }

    /** Ordered draw-buffer locations, including MRT holes, expressed as real engine attachments. */
    public RenderPassDescriptor descriptor(String label, boolean clear)
    {
        if (this.deleted) throw new IllegalStateException("Framebuffer was destroyed");
        Texture main = this.getMainTexture();
        RenderPassDescriptor/*Builder*/ descriptor = RenderPassDescriptor.create(() -> label);
        int limit = mchorse.bbs_mod.compat.Geraet.get().getDeviceInfo().limits().maxColorAttachments();
        if (this.drawAttachments.length > limit) throw new IllegalArgumentException("MRT exceeds actual device limit " + limit);
        for (int attachment : this.drawAttachments)
        {
            if (attachment == GL11.GL_NONE) { descriptor.withUnusedColorAttachment(); continue; }
            Texture texture = this.textures.stream().filter(t -> Integer.valueOf(attachment).equals(this.anhaenge.get(t)))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing framebuffer draw attachment " + attachment));
            if (!texture.getFormat().isColor()) throw new IllegalArgumentException("Draw attachment is not a color texture");
            GpuTextureView view = texture.attachmentView();
            FramebufferUtils.validateAttachment(view, main.width, main.height);
            descriptor.withColorAttachment(view, clear && (!this.advancedClearing || texture.isClearable())
                ? Optional.of(new Vector4f(0F, 0F, 0F, 0F)) : Optional.empty());
        }
        GpuTextureView depth = this.depthView();
        if (depth != null)
        {
            FramebufferUtils.validateAttachment(depth, main.width, main.height);
            boolean clearDepth = !this.advancedClearing || this.textures.stream()
                .anyMatch(t -> t.getFormat().isDepth() && t.isClearable());
            descriptor.withDepthAttachment(depth, clear && clearDepth ? OptionalDouble.of(0D) : OptionalDouble.empty());
        }
        return descriptor.build();
    }

    public boolean isDeleted() { return this.deleted; }

    /** Complete MRT descriptor for the active exact target; callers must preserve their native world pass first. */
    public static RenderPassDescriptor descriptorFor(String label, GpuTextureView color, GpuTextureView depth,
        mchorse.bbs_mod.compat.alt.RenderArea area)
    {
        if (bindings.isEmpty()) return null;
        Framebuffer framebuffer = bindings.peek().framebuffer;
        if (!sameAttachment(framebuffer.colorView(), color) || !sameAttachment(framebuffer.depthView(), depth)) return null;
        RenderPassDescriptor descriptor = framebuffer.descriptor(label, false);
        //? if >=26.3 {
        /*return area == null ? descriptor : new RenderPassDescriptor(descriptor.label(), descriptor.colorAttachments(),
            descriptor.depthAttachment(), area);
        *///?} else {
        return area == null ? descriptor : descriptor.withRenderArea(area);
        //?}
    }

    private static boolean sameAttachment(GpuTextureView a, GpuTextureView b)
    {
        return a == null || b == null ? a == b : a.texture() == b.texture()
            && a.baseMipLevel() == b.baseMipLevel() && a.mipLevels() == b.mipLevels();
    }
}
