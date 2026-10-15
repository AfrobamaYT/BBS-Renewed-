package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.GpuBackend;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Pair;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class StencilFormFramebuffer
{
    private Framebuffer framebuffer;

    /**
     * Zwei Zwischenbilder im Wechsel — siehe {@link #zwischenbild()} fuer den Grund.
     *
     * <p>⛔ NICHT final, NICHT im Feldinitialisierer: nach einem Hotswap laufen auch
     * Instanz-Initialisierer nicht erneut, und ein neues Feld steht auf null. Das gilt fuer
     * das ARRAY genauso wie fuer die Texturen darin — am 2026-08-03 genau daran eine Instanz
     * verloren ({@code Cannot load from object array because "this.zwischenbilder" is null},
     * in JEDEM Bild beim Zeichnen des Umrisses). Der Kommentar, dass die Texturen traege
     * entstehen, stand da schon; das Array war trotzdem eifrig.</p>
     */
    private Texture[] zwischenbilder;
    private int zwischenbildIndex;
    private long letztesBild = Long.MIN_VALUE;

    /** Eigenes Puffer-Paar der Kennungs-Hervorhebung -- siehe hervorhebungsbild(). */
    private Texture[] hervorhebungsbilder;

    private int index;
    private Map<Integer, Pair<Form, String>> indexMap = new HashMap<>();

    /**
     * Reads the ids back ({@link StencilLeser}); created lazily, because a hotswap does not
     * run initialisers for live instances.
     */
    private StencilLeser leser;

    /** Id map of the frame whose asynchronous pick is still pending (see {@link #pickAsync}). */
    private Map<Integer, Pair<Form, String>> ausstehendeKarte;


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

        /* ⛔ ZWEI TEXTUREN IM WECHSEL, NICHT EINE (2026-08-03, der Umriss-Versatz).
         *
         * Der Kommentar oben loeste das Problem fuer mehrere AUFRUFSTELLEN — nicht fuer
         * mehrere BILDER. 26.2 sammelt die Oberflaeche erst und zeichnet danach: der
         * Filterdurchgang fuellt diese Textur SOFORT, `batcher.texturedBox` reicht dagegen
         * nur einen Auftrag ein. Zwischen Einreichen und Ausfuehren liegt der Rest des
         * Bildes — und im naechsten Bild fuellt derselbe Puffer neu. Der Blit zeigt dann,
         * was inzwischen drinsteht, nicht was er angefordert hat.
         *
         * Im Stillstand faellt das nicht auf (beide Bilder gleich); bei Kamerabewegung steht
         * der Umriss genau um die Bewegung eines Bildes daneben. Gemessen: Stillstand mit
         * mittiger Figur 1-2 px, Kamera ausgelenkt ~50 px.
         *
         * ⚠️ 1.21.1 hat diesen Puffer NICHT — dort laeuft der Umriss-Shader IM Blit
         * (StencilAnzeige: setzeZwei("Schritt"...) + texturedBox(texture.id, ...)), ein
         * Durchgang, nichts kann altern. Der Zwischenpuffer kam erst mit 26.2, weil ein
         * GUI-Element dort keine eigenen Uniforms mehr binden kann (Begruendung und drei
         * verworfene Auswege: Bildfilter). Er bleibt also — aber er darf nicht derselbe
         * sein wie der, aus dem gerade noch gezeichnet wird.
         *
         * Gewechselt wird je BILD, nicht je Aufruf: alle Aufrufstellen desselben Bildes
         * muessen dieselbe Textur sehen, sonst zeichnet die zweite in eine leere. */
        if (this.zwischenbilder == null)
        {
            this.zwischenbilder = new Texture[2];
        }

        long bild = net.minecraft.client.Minecraft.getInstance().getFrameTimeNs();

        if (bild != this.letztesBild)
        {
            this.letztesBild = bild;
            this.zwischenbildIndex ^= 1;
        }

        return ausPaar(this.zwischenbilder, quelle);
    }

    /**
     * Das Zwischenbild der KENNUNGS-HERVORHEBUNG -- ein anderes als das des Umrisses.
     *
     * <p>⛔ WARUM GETRENNT (2026-08-05, Bugliste #21, im Frame belegt). Beide Effekte
     * rechneten in {@link #zwischenbild()} und blitteten es danach. In 1.21.1 zeichnet der
     * erste Blit SOFORT und zeigt den Umriss; 26.2 SAMMELT beide Blits und zeichnet sie
     * spaeter -- dann sampeln beide den Endinhalt des geteilten Puffers, und das ist die
     * Hervorhebung. Der Umriss war gezeichnet und unsichtbar.</p>
     *
     * <p>Gemessen im Wiedergabe-Capture: das Zwischenbild bekam nach dem Umriss-Zug genau
     * EINEN weiteren Zug (eventId 2842) -- strukturell identisch, ohne Marker, aus
     * {@code Bildfilter.kennungHervorheben}. Im Stillstand keinen, dort war der Umriss
     * sichtbar. Sichtbare Umrisspunkte: Wiedergabe 0, Stillstand 845, die Vorlage 1013.</p>
     *
     * <p>Der Kommentar an {@link #zwischenbild()} sagte schon "EIGENE Textur je
     * Auswahl-Puffer, keine geteilte" -- das galt fuer mehrere Aufrufstellen DESSELBEN
     * Effekts. Zwei VERSCHIEDENE Effekte brauchen zwei Puffer.</p>
     */
    public Texture hervorhebungsbild()
    {
        Texture quelle = this.framebuffer.getMainTexture();

        if (this.hervorhebungsbilder == null)
        {
            this.hervorhebungsbilder = new Texture[2];
        }

        return ausPaar(this.hervorhebungsbilder, quelle);
    }

    /**
     * Die im aktuellen Bild gueltige Textur aus einem Paar holen und auf Quellgroesse bringen.
     *
     * <p>Der Wechsel je Bild ({@link #zwischenbildIndex}) ist bewusst GEMEINSAM: alle
     * Aufrufstellen desselben Bildes muessen dieselbe Haelfte sehen, sonst zeichnet die
     * zweite in eine leere.</p>
     */
    private Texture ausPaar(Texture[] paar, Texture quelle)
    {
        Texture ziel = paar[this.zwischenbildIndex];

        if (ziel == null)
        {
            ziel = new Texture();
            ziel.setFilter(GL11.GL_NEAREST);
            ziel.setWrap(GL13.GL_CLAMP_TO_EDGE);
            paar[this.zwischenbildIndex] = ziel;
        }

        if (ziel.width != quelle.width || ziel.height != quelle.height)
        {
            if (GpuBackend.isOpenGl()) ziel.bind();
            ziel.setSize(quelle.width, quelle.height);
        }

        return ziel;
    }

    public Framebuffer getFramebuffer()
    {
        return this.framebuffer;
    }

    /** Exact result of the last issued pick: waits for a pending asynchronous readback. */
    public int getIndex()
    {
        this.aufloesen(true);

        return this.index;
    }

    public Map<Integer, Pair<Form, String>> getIndexMap()
    {
        this.aufloesen(true);

        return this.indexMap;
    }

    public Pair<Form, String> getPicked()
    {
        this.aufloesen(true);

        return this.indexMap.get(this.index);
    }

    /**
     * For drawing in the same frame right after {@link #pickAsync}: the latest COMPLETED
     * result, never waiting for the GPU. At most one frame old; clicks and drags use the
     * exact accessors instead.
     */
    public boolean hasPickedOhneWarten()
    {
        this.aufloesen(false);

        return this.index > 0;
    }

    public int getIndexOhneWarten()
    {
        this.aufloesen(false);

        return this.index;
    }

    public Pair<Form, String> getPickedOhneWarten()
    {
        this.aufloesen(false);

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
        Texture tiefe = this.tiefenTextur();

        return tiefe == null ? null : tiefe.attachmentView();
    }

    /** Dieselbe Suche wie {@link #tiefenAnsicht()}, nur die Textur selbst — zum Leeren. */
    private Texture tiefenTextur()
    {
        for (Texture texture : this.framebuffer.textures)
        {
            if (texture.getFormat().isDepth())
            {
                return texture;
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
        /* ⭐ NAME FUER DEN FRAME-DEBUGGER. RenderDocs Event Browser ist ohne Gruppen eine
         * flache Liste — unser Frame hat ueber 2000 Zeichenaufrufe, und die RenderDoc-Doku
         * sagt selbst, den richtigen darin zu finden "kann sehr lange dauern". Genau daran
         * hing der Umriss-Versatz zwei Tage. Der Marker sitzt HIER und nicht beim Aufrufer,
         * weil apply()/unbind() die Klammer um den Durchgang sind: eine Aufrufstelle statt
         * vieler, und der Name kann nicht vergessen werden. Kosten: ein GL-Aufruf je
         * Durchgang. Warum die Marker vorher nie feuerten: ApiTraceBridge#pushGroup. */
        if (GpuBackend.isOpenGl()) mchorse.bbs_mod.sonden.Sonden.gpu.gruppeAuf(
            "BBS Auswahlpuffer (Stencil)");

        /* ⭐ DERSELBE ABSCHNITT FUER NSIGHT (2026-08-03). Zwei Zeilen statt einer, weil die
         * beiden Werkzeuge verschiedene Seiten messen: die GL-Debuggruppe darueber sieht
         * RenderDoc/apitrace im FRAME, dieser Bereich hier taucht in Nsights ZEITACHSE auf.
         * Erst zusammen beantworten sie "welcher Zeichenaufruf" UND "wie lange dauerte er".
         * Kostet nichts, wenn NVTX nicht angehaengt ist -- rangePush prueft ATTACHED und
         * kehrt sofort zurueck. */
        mchorse.bbs_mod.sonden.Sonden.gpu.bereichAuf(
            "BBS Auswahlpuffer (Stencil)");

        /* Setzt das Sichtfeld auf die Puffergroesse. Das Binden darin braucht der
         * Auswahl-Durchgang nicht mehr (siehe PickZiel unten), das Sichtfeld schon. */
        this.framebuffer.apply();

        this.leeren();

        int[] sichtfeld = new int[4];

        if (GpuBackend.isOpenGl()) org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, sichtfeld);
        else
        {
            sichtfeld[2] = this.framebuffer.getMainTexture().width;
            sichtfeld[3] = this.framebuffer.getMainTexture().height;
        }

        Texture ziel = this.framebuffer.getMainTexture();

        mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.durchgang",
            "viewport=" + sichtfeld[0] + "," + sichtfeld[1] + " " + sichtfeld[2] + "x" + sichtfeld[3]
            + " ziel=" + ziel.width + "x" + ziel.height
            + " guiSkala=" + BBSModClient.getGUIScale());

        /* 26.2: das Binden allein reicht nicht mehr -- jeder Durchgang nennt sein Ziel
         * selbst. Hier wird angemeldet, wohin die Kennungsfarben gehen; siehe PickZiel.
         * Farbe UND Tiefe: ohne Tiefe gewinnt beim Picken der zuletzt gezeichnete Knochen
         * statt des vordersten. */
        mchorse.bbs_mod.graphics.PickZiel.anmelden(
            this.framebuffer.getMainTexture().attachmentView(), this.tiefenAnsicht());
    }

    /**
     * Leert Farbe und Tiefe des Auswahlpuffers.
     *
     * <h2>⛔ WARUM NICHT Framebuffer#clear() — DER PUFFER SAMMELTE SICH AN (2026-08-02)</h2>
     *
     * <p>{@code Framebuffer#clear()} loescht ueber {@code glClearBufferfv}, und das trifft
     * den Puffer, der GERADE GEBUNDEN ist. Genau daran haengt es in 26.2: das Binden laeuft
     * ueber den FBO-Cache des GlStateManagers, waehrend die Durchgaenge von 26.2 ihr Ziel
     * selbst nennen und den Cache dabei auseinanderlaufen lassen. Das Loeschen ging also
     * ins Leere -- ohne GL-Fehler, ohne Logzeile.</p>
     *
     * <p>Was der Nutzer davon sah: bei jeder Kamerabewegung standen mehrere orange Umrisse
     * gleichzeitig im Bild. Es waren die Silhouetten aller vorherigen Kamerawinkel, die sich
     * im Auswahlpuffer aufeinanderstapelten. Ein Abzug des Puffers im Standbild zeigt EINE
     * saubere Figur, derselbe Abzug nach ein paar Sekunden Bewegung eine grosse Masse aus
     * Dutzenden ueberlagerten Silhouetten -- das ist der Beweis, und die beiden Abzuege
     * unterscheiden die Ursache eindeutig von einer falschen Projektion.</p>
     *
     * <p>⚠️ Diese Verwechslung hat mich Stunden gekostet: die aufgestapelte Masse SIEHT aus
     * wie eine viel zu gross gezeichnete Figur. Wer hier eine Skalierung nachmisst, misst
     * die Summe vieler Bilder. Immer zuerst im STANDBILD messen.</p>
     *
     * <p>Der Weg hier nennt beide Zieltexturen direkt, damit ueberhaupt kein Bindezustand
     * mehr dazwischensteht -- derselbe Weg, den {@code Bildfilter#loeschen} schon geht.</p>
     */
    private void leeren()
    {
        Texture farbe = this.framebuffer.getMainTexture();
        Texture tiefe = this.tiefenTextur();

        if (farbe == null)
        {
            return;
        }

        com.mojang.blaze3d.systems.CommandEncoder schreiber =
            mchorse.bbs_mod.compat.Geraet.get().createCommandEncoder();

        /* Tiefe 0, nicht 1: 26.2 rechnet den Tiefenbereich umgekehrt (naeher = groesserer
         * Wert). Derselbe Wert steht in Framebuffer.CLEAR_DEPTH. */
        if (tiefe == null)
        {
            schreiber.clearColorTexture(farbe.gpu(), new org.joml.Vector4f(0F, 0F, 0F, 0F));
        }
        else
        {
            schreiber.clearColorAndDepthTextures(farbe.gpu(), new org.joml.Vector4f(0F, 0F, 0F, 0F),
                tiefe.gpu(), 0D);
        }
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
        /* A synchronous pick replaces a pending asynchronous one. */
        this.verwerfen();

        ByteBuffer daten = this.leser().lesenSofort(this.framebuffer, x, y, 1, 1);

        /* TODO: make other channels work */
        this.index = (daten.get(3) & 0xFF) < 1 ? 0
            : (daten.get(0) & 0xFF) | ((daten.get(1) & 0xFF) << 8) | ((daten.get(2) & 0xFF) << 16);
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
        this.verwerfen();

        /* Messfuehler (hier, weil der Film-Controller gewoben ist): kommt beim Pick die
         * VIRTUELLE maus an oder die echte? Trennt UIContext-Weg von Roh-Koordinaten. */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.ui.framework.UIContext menuCtx = mchorse.bbs_mod.BBSModClient.getDashboard().context;

            mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.pick.eingang." + System.identityHashCode(this),
                "x=" + x + " y=" + y
                    + " ctxMaus=" + (menuCtx == null ? "?" : menuCtx.mouseX + "," + menuCtx.mouseY)
                    + " xpos=" + (int) net.minecraft.client.Minecraft.getInstance().mouseHandler.xpos()
                    + " von=" + mchorse.bbs_mod.graphics.Messpunkt.rufer(5));
        }

        if (radius <= 0 || this.framebuffer == null)
        {
            this.pick(x, y);

            return;
        }

        int[] lage = StencilAuswertung.region(this.framebuffer, x, y, radius, handleMax);

        this.index = lage == null ? 0 : StencilAuswertung.auswerten(this.framebuffer,
            this.leser().lesenSofort(this.framebuffer, lage[0], lage[1], lage[2], lage[3]), lage, this.indexMap);
    }

    /**
     * Hover pick of the film viewport without stalling the frame.
     *
     * <p>{@link #pick(int, int, int, int)} reads synchronously: the CPU waits in the middle of
     * the frame until the GPU has finished everything queued before, the world pass included.
     * Here the same region goes into a pixel pack buffer behind a fence and is evaluated
     * later -- the established asynchronous readback (PBO + {@code glFenceSync}, as behind
     * Unity's AsyncGPUReadback). Measured 27.09.2026: 0.1 ms per frame paused, 0.3 ms in
     * playback, see docs/PERFORMANCE-ASYNC-PICK-2026-09-27.md.
     *
     * <p>The exact accessors ({@link #getIndex}, {@link #hasPicked}, {@link #getPicked}) wait
     * for it, so clicks and drags still get the result of the last drawn frame. Only drawing in
     * the same frame takes the latest completed one ({@link #hasPickedOhneWarten}).
     * Reference path: {@code -Dbbs.stencil.syncPick=true} reads synchronously as before.
     */
    public void pickAsync(int x, int y, int radius, int handleMax)
    {
        if (radius <= 0 || this.framebuffer == null || Boolean.getBoolean("bbs.stencil.syncPick"))
        {
            this.pick(x, y, radius, handleMax);

            return;
        }

        /* The previous request must leave the buffer before it is reused (normally long done). */
        this.aufloesen(true);

        int[] lage = StencilAuswertung.region(this.framebuffer, x, y, radius, handleMax);

        if (lage == null)
        {
            this.index = 0;
        }
        else
        {
            this.leser().anfordern(this.framebuffer, lage);
        }
    }

    private void aufloesen(boolean warten)
    {
        if (this.leser == null || !this.leser.ausstehend())
        {
            return;
        }

        Map<Integer, Pair<Form, String>> karte = this.ausstehendeKarte == null ? new HashMap<>() : this.ausstehendeKarte;
        int ergebnis = this.leser.aufloesen(warten,
            (daten, lage) -> StencilAuswertung.auswerten(this.framebuffer, daten, lage, karte));

        if (ergebnis == StencilLeser.KEIN_ERGEBNIS)
        {
            return;
        }

        /* The index belongs to the frame that issued the read, and so does its map. */
        this.ausstehendeKarte = this.indexMap;
        this.indexMap = karte;
        this.index = ergebnis;
    }

    private void verwerfen()
    {
        if (this.leser != null)
        {
            this.leser.verwerfen();
        }
    }

    private StencilLeser leser()
    {
        if (this.leser == null)
        {
            this.leser = new StencilLeser();
        }

        return this.leser;
    }

    public void unbind(StencilMap map)
    {
        this.unbind();

        /* A pending asynchronous pick keeps the map of ITS frame until it is resolved. */
        Map<Integer, Pair<Form, String>> ziel = this.indexMap;

        if (this.leser != null && this.leser.ausstehend())
        {
            if (this.ausstehendeKarte == null)
            {
                this.ausstehendeKarte = new HashMap<>();
            }

            ziel = this.ausstehendeKarte;
        }

        ziel.clear();
        ziel.putAll(map.indexMap);
    }

    public void unbind()
    {
        /* ⚗️ HIER messen, nicht spaeter. Der Stencil wird gedrosselt gefuellt (rund 4 von 120
         * Bildern, siehe UIFilmControllerStencilThrottleMixin) -- eine Probe irgendwo im
         * UI-Durchlauf trifft den gefuellten Zustand also fast nie und meldet "leer", obwohl
         * sie nur den falschen Augenblick erwischt hat. Genau hinter der Fuellung ist der
         * einzige Ort, an dem die Antwort etwas wert ist. */
        if (GpuBackend.isOpenGl()) mchorse.bbs_mod.graphics.Pufferprobe.vielleichtFarbeZaehlen(
            this.framebuffer.getMainTexture().id,
            this.framebuffer.getMainTexture().width, this.framebuffer.getMainTexture().height,
            255, 255, 255, 255, "stencil DIREKT nach der fuellung");

        mchorse.bbs_mod.graphics.PickZiel.abmelden();
        this.framebuffer.unbind();

        /* Gegenstueck zum pushGroup in apply() — ohne das Paar verschachtelt sich der Event
         * Browser falsch, was schlimmer ist als gar kein Marker. */
        if (GpuBackend.isOpenGl()) mchorse.bbs_mod.sonden.Sonden.gpu.gruppeZu();

        /* Gegenstueck zum rangePush in apply(). Muss auf JEDEM Weg aus dem Durchgang
         * laufen, sonst wandert der Bereich in Nsight bis ans Ende der Aufzeichnung und
         * die Zeitachse ist unlesbar. */
        mchorse.bbs_mod.sonden.Sonden.gpu.bereichZu();
    }

    public void clearPicking()
    {
        this.verwerfen();
        this.index = 0;
        this.indexMap.clear();
    }

    public boolean hasPicked()
    {
        this.aufloesen(true);

        return this.index > 0;
    }
}
