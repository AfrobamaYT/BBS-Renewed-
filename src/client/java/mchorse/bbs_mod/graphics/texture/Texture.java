package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.gui.render.TextureSetup;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Texture class
 *
 * This class is responsible for managing a state of a texture
 *
 * <h2>26.2-Port: warum diese Klasse jetzt zwei Gesichter hat</h2>
 *
 * <p>Bis 1.21.1 war eine Textur eine rohe GL-Nummer: {@code glGenTextures()},
 * {@code glTexImage2D()}, {@code glBindTexture()} — und die GUI nahm diese Nummer
 * entgegen. In 26.2 ist der zweite Teil weg: ein {@code GuiElementRenderState}
 * benennt seine Textur ueber {@code TextureSetup.singleTexture(GpuTextureView,
 * GpuSampler)}, eine rohe GL-Nummer akzeptiert die GUI nirgends mehr.
 *
 * <p>BBS kann aber nicht einfach auf Mojangs Texturen umsteigen: 54 Dateien zeichnen
 * mit rohem GL (Weltrenderer, VAO-Pfad, eigene Framebuffer) und binden dafuer
 * {@link #id}. Darum besitzt diese Klasse jetzt beides, und zwar aus EINER Quelle:
 *
 * <ul>
 *   <li>Das GPU-Objekt ist die Wahrheit. Es entsteht ueber
 *       {@code GpuDevice.createTexture(...)}.</li>
 *   <li>{@link #id} ist kein eigenes {@code glGenTextures()} mehr, sondern
 *       {@code ((GlTexture) gpu).glId()} — die GL-Nummer GENAU DIESER GPU-Textur.
 *       Rohes GL und Mojangs GUI reden also ueber dasselbe Objekt, es gibt keine
 *       zweite Textur und nichts muss gespiegelt werden.</li>
 * </ul>
 *
 * <p>⚠️ VORAUSSETZUNG, GEMESSEN, NICHT ANGENOMMEN: {@code GlDevice.createTexture}
 * legt den Speicher mit {@code texImage2D} an, NICHT mit {@code texStorage2D}
 * (javap -c auf GlDevice: 2× texImage2D, 0× texStorage2D). Der Speicher ist also
 * veraenderlich, und BBS' eigene {@code glTexImage2D}-Uploads auf dieselbe Nummer
 * bleiben erlaubt. Stellt Mojang das je auf unveraenderlichen Speicher um, bricht
 * jeder Upload dieser Klasse — dann muss {@link #uploadTexture} auf
 * {@code CommandEncoder.writeToTexture} umgestellt werden.
 *
 * <p>⚠️ FILTER UND WRAP sind in 26.2 Sache des SAMPLERS, nicht der Textur. Sie
 * werden hier weiterhin per {@code glTexParameteri} gesetzt (der rohe GL-Pfad
 * braucht das unveraendert) UND gemerkt, damit {@link #sampler()} den passenden
 * Sampler aus Mojangs Cache holen kann.
 */
public class Texture
{
    public int id;
    public int target;

    public int width;
    public int height;

    private boolean mipmap;
    private boolean clearable;
    private boolean translucent;

    private AnimatedTexture parent;
    private TextureFormat format = TextureFormat.RGBA_U8;
    private int filter;

    /**
     * 26.2: das GPU-Objekt hinter {@link #id}. Wird angelegt, sobald die Groesse
     * bekannt ist, und neu angelegt, wenn Groesse oder Format sich aendern — sonst
     * wuerde {@code getWidth()} luegen und der {@link #view} die falschen Masse melden.
     */
    private GpuTexture gpu;
    private GpuTextureView view;

    private int gpuWidth;
    private int gpuHeight;
    private TextureFormat gpuFormat;

    /** GL-Standard ist GL_REPEAT; der Sampler muss denselben Standard haben. */
    private int wrap = GL11.GL_REPEAT;

    public static Pixels pixelsFromTexture(Texture texture)
    {
        if (!texture.isValid())
        {
            return null;
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(texture.width * texture.height * 4);

        texture.bind();
        GL11.glGetTexImage(texture.target, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        texture.unbind();

        return new Pixels(buffer, texture.width, texture.height);
    }

    public static Texture textureFromPixels(Pixels pixels, int filter)
    {
        Texture texture = new Texture();

        texture.setFilter(filter);
        texture.uploadTexture(pixels);
        texture.unbind();

        return texture;
    }

    public Texture()
    {
        this.target = GL11.GL_TEXTURE_2D;

        /* Eine 1x1-Platzhaltertextur, damit {@link #id} ab dem Konstruktor gueltig ist:
         * die Aufrufer binden und setzen Parameter, bevor sie Pixel hochladen
         * (siehe UIPixelsEditor.updateSelectionMaskTexture). Der erste echte Upload
         * ersetzt sie durch eine Textur der richtigen Groesse. */
        this.allocate(1, 1, this.format);

        this.bind();
    }

    /**
     * Legt das GPU-Objekt neu an, wenn Groesse oder Format sich geaendert haben, und
     * zieht {@link #id} nach.
     *
     * <p>Der alte {@link GpuTextureView} und die alte {@link GpuTexture} werden dabei
     * geschlossen — das gibt auch die alte GL-Nummer frei, weshalb {@link #id}
     * anschliessend IMMER neu gelesen werden muss. Wer die Nummer zwischenspeichert,
     * zeichnet nach der naechsten Groessenaenderung eine fremde Textur.
     */
    private void allocate(int width, int height, TextureFormat format)
    {
        /* ⚠️ Eine Textur mit Kantenlaenge 0 ist in GL erlaubt und meldet keinen Fehler -- sie
         * ist nur zu nichts zu gebrauchen. Haengt sie an einem Framebuffer, ist der
         * unvollstaendig, und jeder Zeichenaufruf darauf ist undefiniert. Weil BBS' Filmpanel
         * beim Oeffnen tatsaechlich 0/0 durchreicht (Log: "setCustomSize ... w=0 h=0"), wird
         * hier geklemmt und EINMAL laut gemeldet -- geklemmt, damit der Aufrufer weiterlaeuft,
         * gemeldet, damit die Null nicht still verschwindet. */
        if (width <= 0 || height <= 0)
        {
            nullGroesseMelden(width, height);

            width = Math.max(width, 1);
            height = Math.max(height, 1);
        }

        if (this.gpu != null && this.gpuWidth == width && this.gpuHeight == height && this.gpuFormat == format)
        {
            return;
        }

        this.closeGpu();

        /* Alle Nutzungsarten, die BBS braucht: die GUI liest (TEXTURE_BINDING), Uploads
         * schreiben (COPY_DST), pixelsFromTexture liest zurueck (COPY_SRC) und BBS' eigene
         * Framebuffer haengen Texturen an (RENDER_ATTACHMENT). In GL kostet das nichts. */
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

        this.gpu = RenderSystem.getDevice().createTexture(
            "bbs-texture", usage, gpuFormatOf(format), width, height, 1, 1);
        this.view = RenderSystem.getDevice().createTextureView(this.gpu);

        this.gpuWidth = width;
        this.gpuHeight = height;
        this.gpuFormat = format;

        /* ⛔ HIER ENDET BBS UNTER VULKAN — und zwar mit Ansage, nicht mit einem
         * ClassCastException-Rätsel im Absturzbericht.
         *
         * 26.2 hat zwei Grafik-Hintergründe: GlDevice und VulkanDevice. Der Spieler
         * waehlt sie unter Optionen > Grafik ("Graphics API"). Der Standard probiert
         * OpenGL zuerst (PreferredGraphicsApi.getBackendsToTry: nur bei VULKAN steht
         * VulkanBackend vorn), BBS laeuft also normalerweise richtig.
         *
         * Waehlt jemand Vulkan, gibt es keine GL-Nummer — und ohne GL-Nummer kann
         * KEINE der 54 Dateien zeichnen, die roh mit der Grafikkarte reden. Das ist
         * keine Luecke dieser Klasse, sondern die Grenze des ganzen Mods. */
        if (!(this.gpu instanceof GlTexture glTextur))
        {
            throw new IllegalStateException(
                "BBS braucht die Grafik-API OpenGL, aktiv ist aber: " + RenderSystem.getBackendDescription()
                + ". Umstellen unter Optionen > Grafik > Graphics API auf OpenGL, dann neu starten. "
                + "(BBS zeichnet an vielen Stellen direkt ueber OpenGL; unter Vulkan gibt es dafuer keine Entsprechung.)");
        }

        this.id = glTextur.glId();
    }

    /**
     * Meldet eine Textur mit Kantenlaenge 0 -- einmal, mit Stapelspur, damit der VERURSACHER
     * im Log steht und nicht bloss die Tatsache.
     */
    private static boolean nullGroesseGemeldet;

    private static void nullGroesseMelden(int width, int height)
    {
        if (nullGroesseGemeldet)
        {
            return;
        }

        nullGroesseGemeldet = true;

        new IllegalStateException(
            "Textur mit Kantenlaenge 0 angefordert (" + width + "x" + height
            + ") -- auf 1x1 geklemmt. Stapelspur zeigt den Verursacher:").printStackTrace();
    }

    private void closeGpu()
    {
        if (this.view != null)
        {
            this.view.close();
            this.view = null;
        }

        if (this.gpu != null)
        {
            this.gpu.close();
            this.gpu = null;
        }

        this.id = -1;
    }

    /**
     * BBS' Formate auf Mojangs {@link GpuFormat}.
     *
     * <p>⚠️ DEPTH_F24 hat kein Gegenstueck: 26.2 kennt nur {@code D32_FLOAT}. Der
     * anschliessende {@code glTexImage2D} dieser Klasse legt weiterhin echtes
     * {@code DEPTH_COMPONENT24} an, das GPU-Objekt behauptet also 32 bit. Das ist
     * folgenlos, solange Tiefentexturen nur an BBS' eigene Framebuffer gehen (dort
     * bindet BBS selbst) — sobald eine davon durch Mojangs Render-Pfad laeuft, ist
     * es eine Luege, die auffliegt.
     */
    private static GpuFormat gpuFormatOf(TextureFormat format)
    {
        switch (format)
        {
            case RGBA_U8: return GpuFormat.RGBA8_UNORM;

            /* ⛔ NICHT RGB8_UNORM -- das ist ein sicherer Absturz, kein Format (Bugliste #11,
             * Absturzgruppe G6: "RGB8_UNORM format cannot be used", Texture.allocate:155).
             *
             * Am 2026-08-04 an der laufenden Instanz gemessen, GlConst gefragt statt geraten:
             *
             *   RGB8_UNORM:   intern=0      extern=6407   typ=5121
             *   RGBA8_UNORM:  intern=32856  extern=6408   typ=5121
             *
             * GlDevice.createTexture wirft eine IllegalArgumentException, sobald eine der drei
             * Abbildungen 0 ist. 26.2s GL-Backend hat fuer dreikanaliges RGB8 keine interne ID
             * -- der Wurf ist also nicht selten, sondern SICHER, bei jedem Aufruf.
             *
             * Das GPU-Objekt behauptet damit einen Alphakanal, den die anschliessende
             * glTexImage2D dieser Klasse nicht anlegt (sie laedt weiter GL_RGB8/GL_RGB). Das ist
             * dieselbe folgenlose Luege wie unten bei der Tiefe, und aus demselben Grund
             * ertraeglich: BBS bindet seine eigenen Texturen selbst. Wichtig ist, was daraus
             * NICHT folgt -- gelesen wird weiter dreikanalig, der Treiber liest also nicht ueber
             * das Pufferende hinaus (siehe TextureFormat#bytesProPixel). */
            case RGB_U8: return GpuFormat.RGBA8_UNORM;
            case RGBA_F16: return GpuFormat.RGBA16_FLOAT;
            case DEPTH_F24: return GpuFormat.D32_FLOAT;
        }

        return GpuFormat.RGBA8_UNORM;
    }

    /** Der {@link GpuTextureView} dieser Textur — das, was 26.2s GUI entgegennimmt. */
    /** Das GPU-Objekt selbst -- gebraucht, wo eine Ansicht nicht reicht (Loeschen). */
    public GpuTexture gpu()
    {
        return this.gpu;
    }

    public GpuTextureView view()
    {
        return this.view;
    }

    /**
     * Der Sampler, der zu {@link #setFilter} und {@link #setWrap} passt.
     *
     * <p>Bis 1.21.1 hingen Filter und Wrap an der Textur, in 26.2 haengen sie am
     * Sampler. Mojangs Cache gibt fuer dieselbe Kombination dasselbe Objekt zurueck,
     * hier entsteht also nichts Neues pro Bild.
     */
    public GpuSampler sampler()
    {
        AddressMode address = addressModeOf(this.wrap);
        FilterMode mode = filterModeOf(this.filter);

        return RenderSystem.getSamplerCache().getSampler(address, address, mode, mode, this.mipmap);
    }

    /** Diese Textur als GUI-Bindung, wie {@code GuiElementRenderState.textureSetup()} sie verlangt. */
    public TextureSetup guiSetup()
    {
        return TextureSetup.singleTexture(this.view, this.sampler());
    }

    /**
     * ⚠️ GL_MIRRORED_REPEAT hat in 26.2 kein Gegenstueck und faellt auf REPEAT zurueck.
     * BBS benutzt es heute nirgends; wer es einfuehrt, bekommt still das falsche Bild.
     */
    private static AddressMode addressModeOf(int wrap)
    {
        return wrap == GL12.GL_CLAMP_TO_EDGE ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT;
    }

    /** Die Mipmap-Varianten unterscheiden sich nur in der Mipmap-Stufe, nicht im Filter selbst. */
    private static FilterMode filterModeOf(int filter)
    {
        boolean linear = filter == GL11.GL_LINEAR
            || filter == GL30.GL_LINEAR_MIPMAP_NEAREST
            || filter == GL30.GL_LINEAR_MIPMAP_LINEAR;

        return linear ? FilterMode.LINEAR : FilterMode.NEAREST;
    }

    public void setParent(AnimatedTexture parent)
    {
        this.parent = parent;
    }

    public AnimatedTexture getParent()
    {
        return this.parent;
    }

    public void setClearable(boolean clearable)
    {
        this.clearable = clearable;
    }

    public boolean isClearable()
    {
        return this.clearable;
    }

    public TextureFormat getFormat()
    {
        return this.format;
    }

    public boolean isMipmap()
    {
        return this.mipmap;
    }

    /**
     * Whether this texture has semi-transparent pixels (alpha strictly between the model
     * shader's 0.1 cutout threshold and fully opaque). Such textures need the two-pass
     * translucency treatment; plain opaque/cutout textures render in a single pass.
     */
    public boolean hasTranslucency()
    {
        return this.translucent;
    }

    public boolean isReallyMipmap()
    {
        return this.mipmap && this.getParameter(GL30.GL_TEXTURE_MAX_LEVEL) > 0;
    }

    public boolean isValid()
    {
        return this.id >= 0;
    }

    public void bind()
    {
        if (this.target == GL11.GL_TEXTURE_2D) GlStateManager._bindTexture(this.id);
        else GL11.glBindTexture(this.target, this.id);
    }

    public void bind(int texture)
    {
        GlStateManager._activeTexture(texture);
        this.bind();
    }

    public void unbind()
    {
        if (this.target == GL11.GL_TEXTURE_2D) GlStateManager._bindTexture(0);
        else GL11.glBindTexture(this.target, 0);
    }

    public void unbind(int texture)
    {
        GlStateManager._activeTexture(texture);
        this.unbind();
    }

    public void setFormat(TextureFormat format)
    {
        this.format = format;
    }

    public int getFilter()
    {
        return this.filter;
    }

    public boolean isLinear()
    {
        return this.filter == GL30.GL_LINEAR || this.filter == GL30.GL_LINEAR_MIPMAP_NEAREST;
    }

    public int getParameter(int parameter)
    {
        return GL11.glGetTexParameteri(this.target, parameter);
    }

    public void setFilterMipmap(boolean linear, boolean mipmap)
    {
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;

        this.setFilter(filter);

        if (!this.isMipmap())
        {
            this.generateMipmap();
        }

        this.setParameter(GL30.GL_TEXTURE_MAX_LEVEL, mipmap ? 4 : 0);
    }

    public void setFilter(int filter)
    {
        this.filter = filter;

        this.setParameter(GL11.GL_TEXTURE_MAG_FILTER, filter);
        this.setParameter(GL11.GL_TEXTURE_MIN_FILTER, filter);
    }

    public void setWrap(int mode)
    {
        this.wrap = mode;

        this.setParameter(GL11.GL_TEXTURE_WRAP_S, mode);
        this.setParameter(GL11.GL_TEXTURE_WRAP_T, mode);
    }

    public void setParameter(int param, int value)
    {
        GL11.glTexParameteri(this.target, param, value);
    }

    /**
     * 26.2: die GL-Nummer gehoert dem GPU-Objekt, also gibt dessen {@code close()} sie
     * frei — ein eigenes {@code glDeleteTextures} wuerde eine Nummer loeschen, die
     * Mojang noch fuer vergeben haelt.
     */
    public void delete()
    {
        this.closeGpu();
    }

    public void setSize(int width, int height)
    {
        this.width = width;
        this.height = height;

        this.allocate(width, height, this.format);
        this.bind();

        GL11.glTexImage2D(this.target, 0, this.format.internal, width, height, 0, this.format.format, this.format.type, 0);
    }

    public void updateTexture(Pixels pixels)
    {
        this.updateTexture(this.target, pixels);
    }

    public void updateTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels.width, pixels.height, pixels.getBuffer());
    }

    public void uploadTexture(Pixels pixels)
    {
        this.uploadTexture(this.target, pixels);
    }

    public void uploadTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels);
    }

    public void uploadTexture(int target, int level, Pixels pixels)
    {
        /* Some textures might not be pixel aligned. For example FunkyFight's 398x444 avatar
         * wasn't aligned, and it caused some interesting visual issues when loading
         * the texture. This fixes it.
         *
         * https://www.khronos.org/opengl/wiki/Pixel_Transfer#Pixel_layout */
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

        if (level == 0)
        {
            this.translucent = scanTranslucency(pixels);
        }

        /* ⚠️ 26.2: IMMER RGBA. GlDevice.createTexture lehnt RGB8 rundheraus ab, deshalb gibt
         * es die frueher hier stehende Fallunterscheidung nach pixels.bits nicht mehr.
         *
         * ⛔ DIESER KOMMENTAR HAT FRUEHER DAS GEGENTEIL BEHAUPTET und damit einen Absturz
         * getarnt: "ein dreikanaliges Bild bekommt also ein ungenutztes Alpha statt einer
         * Ausnahme". Es bekommt kein Alpha. Der Treiber liest einfach ein Drittel MEHR, als im
         * Puffer steht, und nimmt die JVM mit (Aufgabe 88).
         *
         * Dass hier immer RGBA hochgeladen wird, ist nur richtig, WEIL Pixels.fromPNGStream
         * von STB vier Kanaele anfordert. Die beiden Stellen haengen zusammen -- wer eine
         * davon aendert, muss die andere mitaendern. pufferPruefen() faengt den Bruch ab. */
        this.setFormat(TextureFormat.RGBA_U8);
        this.uploadTexture(target, level, pixels.width, pixels.height, pixels.getBuffer());

        pixels.delete();
    }

    public void uploadTexture(int target, int level, int w, int h, ByteBuffer buffer)
    {
        /* Nur die volle Stufe 0 bestimmt Groesse und Format der Textur; Mipmap-Stufen
         * schreiben in den vorhandenen Speicher und duerfen ihn nicht neu anlegen. */
        if (level == 0)
        {
            this.allocate(w, h, this.format);
            this.bind();
        }

        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, w);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);

        pufferPruefen(w, h, buffer);

        GL11.glTexImage2D(target, level, this.format.internal, w, h, 0, this.format.format, this.format.type, buffer);

        if (level == 0)
        {
            this.width = w;
            this.height = h;
        }
    }

    /**
     * Prueft VOR {@code glTexImage2D}, ob im Puffer ueberhaupt so viele Bytes stehen, wie der
     * Treiber gleich lesen wird -- und scheitert LAUT, statt die JVM mitzunehmen.
     *
     * <h2>Warum das noetig ist</h2>
     *
     * <p>{@code glTexImage2D} liest ab {@code memAddress(buffer)}, und das ist die Adresse
     * PLUS POSITION des Puffers, nicht sein Anfang. Zwei Fallen fuehren hier zu einem Lesen
     * hinter dem Puffer, und beide enden mit einer Zugriffsverletzung ohne Stapelspur:
     *
     * <ul>
     *   <li><b>Position nicht zurueckgesetzt.</b> {@code Pixels.getColor}/{@code setColor}
     *       verschieben die Position; {@code Pixels.fromSize} laesst sie am Ende stehen. Wer
     *       so einen Puffer hochlaedt, laesst den Treiber HINTER dem Puffer anfangen.</li>
     *   <li><b>Kanalzahl passt nicht.</b> Ein dreikanaliges Bild aus STB hat nur w*h*3 Bytes,
     *       diese Klasse laedt aber immer als RGBA hoch -- der Treiber will w*h*4.</li>
     * </ul>
     *
     * <p>Ohne diese Pruefung sind beide Faelle formal gueltige GL-Aufrufe: {@code glGetError}
     * bleibt still, und der Absturz passiert irgendwo im Treiber.
     *
     * @throws IllegalStateException wenn zu wenig Bytes bereitstehen
     */
    private void pufferPruefen(int w, int h, ByteBuffer buffer)
    {
        if (buffer == null)
        {
            return;
        }

        long noetig = (long) w * h * this.format.bytesProPixel();

        if (buffer.remaining() < noetig)
        {
            throw new IllegalStateException(
                "Texturupload wuerde hinter dem Puffer lesen: " + w + "x" + h + " als "
                + this.format + " braucht " + noetig + " Bytes, verfuegbar sind aber nur "
                + buffer.remaining() + " (Position " + buffer.position() + " von "
                + buffer.capacity() + "). Entweder wurde die Pufferposition nicht "
                + "zurueckgesetzt (Pixels.rewindBuffer) oder die Kanalzahl passt nicht.");
        }
    }

    public void generateMipmap()
    {
        this.mipmap = true;

        GL30.glGenerateMipmap(this.target);
    }

    /**
     * The 26..254 alpha range mirrors the model shader: below 0.1 the fragment is discarded
     * outright (cutout), at 255 it's opaque — only the range between makes blending matter.
     */
    private static boolean scanTranslucency(Pixels pixels)
    {
        if (pixels.bits != 4)
        {
            return false;
        }

        ByteBuffer buffer = pixels.getBuffer();

        for (int i = 3, c = buffer.limit(); i < c; i += 4)
        {
            int alpha = buffer.get(i) & 0xff;

            if (alpha >= 26 && alpha <= 254)
            {
                return true;
            }
        }

        return false;
    }
}
