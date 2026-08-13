package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.opengl.GlStateManager;
import mchorse.bbs_mod.graphics.texture.Texture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
     * ({@code RenderSystem.DEFAULT_DEPTH_CLEAR_VALUE}), der Test heisst
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

    public Framebuffer()
    {
        this.id = GL30.glGenFramebuffers();
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
        this.textures.add(texture);
        this.anhaenge.put(texture, attachment);

        this.bind();
        texture.bind();

        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, attachment, texture.target, texture.id, 0);

        return this;
    }

    /**
     * Haengt alle gemerkten Texturen mit ihrer AKTUELLEN GL-Nummer neu an — fuer Aufrufer,
     * deren Textur ausserhalb von {@link #resize} die Nummer gewechselt hat (setSize direkt,
     * siehe {@link #anhaenge}). Ein glFramebufferTexture2D je Bild kostet nichts Messbares.
     */
    public void anhangNachziehen()
    {
        this.bind();

        for (Texture texture : this.textures)
        {
            Integer anhang = this.anhaenge.get(texture);

            if (anhang == null)
            {
                continue;
            }

            texture.bind();

            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, anhang, texture.target, texture.id, 0);
        }

        this.unbind();
    }

    /**
     * Attach a renderbuffer as one of the attachment buffers
     */
    public void attach(Renderbuffer renderbuffer)
    {
        this.renderbuffers.add(renderbuffer);
        renderbuffer.bind();

        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, renderbuffer.target, GL30.GL_RENDERBUFFER, renderbuffer.id);
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
        GL30.glDrawBuffers(attachments);
    }

    public void applyClear()
    {
        this.apply();
        this.clear();
    }

    public void apply()
    {
        Texture texture = this.getMainTexture();

        GL11.glViewport(0, 0, texture.width, texture.height);
        this.bind();
    }

    public void clear()
    {
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
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.id);
    }

    public void unbind()
    {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void resize(int w, int h)
    {
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
            this.bind();

            for (Texture texture : this.textures)
            {
                Integer anhang = this.anhaenge.get(texture);

                if (anhang == null)
                {
                    continue;
                }

                texture.bind();

                GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, anhang, texture.target, texture.id, 0);
            }

            this.unbind();
        }

        for (Renderbuffer renderbuffer : this.renderbuffers)
        {
            renderbuffer.bind();
            renderbuffer.resize(w, h);
            renderbuffer.unbind();
        }
    }

    public void delete()
    {
        GL30.glDeleteFramebuffers(this.id);

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
}