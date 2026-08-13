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

    /** Reused readback buffer for the tolerance region pick (grows as needed). */
    private FloatBuffer pickBuffer;

    /**
     * Eigener FBO NUR zum Zurueklesen — 0 heisst "noch nicht erzeugt".
     *
     * <p>Ohne Initialisierer, damit ein Hotswap dieser Klasse das Feld nicht auf einem
     * Bytecode-Default haengen laesst, den kein {@code <clinit>} mehr fuellt.</p>
     */
    private int leseFbo;


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
            ziel.bind();
            ziel.setSize(quelle.width, quelle.height);
        }

        return ziel;
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
        Texture tiefe = this.tiefenTextur();

        return tiefe == null ? null : tiefe.view();
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
        com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.pushGroup(
            "BBS Auswahlpuffer (Stencil)");

        /* ⭐ DERSELBE ABSCHNITT FUER NSIGHT (2026-08-03). Zwei Zeilen statt einer, weil die
         * beiden Werkzeuge verschiedene Seiten messen: die GL-Debuggruppe darueber sieht
         * RenderDoc/apitrace im FRAME, dieser Bereich hier taucht in Nsights ZEITACHSE auf.
         * Erst zusammen beantworten sie "welcher Zeichenaufruf" UND "wie lange dauerte er".
         * Kostet nichts, wenn NVTX nicht angehaengt ist -- rangePush prueft ATTACHED und
         * kehrt sofort zurueck. */
        com.afrobama.bbstools.client.debug.master.external.NvtxBridge.rangePush(
            "BBS Auswahlpuffer (Stencil)");

        /* Setzt das Sichtfeld auf die Puffergroesse. Das Binden darin braucht der
         * Auswahl-Durchgang nicht mehr (siehe PickZiel unten), das Sichtfeld schon. */
        this.framebuffer.apply();

        this.leeren();

        int[] sichtfeld = new int[4];

        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, sichtfeld);

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
            this.framebuffer.getMainTexture().view(), this.tiefenAnsicht());
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
            com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();

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

    /**
     * Liest eine Region AUS DEM EIGENEN Puffer zurueck.
     *
     * <h2>⛔ OHNE DAS BINDEN LIEST DER PICKER DEN BILDSCHIRM (2026-08-02 gemessen)</h2>
     *
     * <p>{@code glReadPixels} nimmt keine Zieltextur entgegen -- es liest, was gerade als
     * {@code GL_READ_FRAMEBUFFER} gebunden ist. Bis 1.21.1 band {@link #apply()} den
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
     *
     * <h2>⛔ DER KLICK FINDET TROTZDEM NICHTS -- WAS BEREITS AUSGESCHLOSSEN IST</h2>
     *
     * <p>Damit hier niemand dieselben drei Sackgassen nochmal laeuft. Alles mit den Sonden
     * {@code stencil.leseziel} und {@code stencil.inhalt} gemessen, am laufenden Editor:
     *
     * <ul>
     * <li><b>FBO und Anhang stimmen:</b> {@code anhang=732 textur=732},
     *     {@code status=0x8cd5} (COMPLETE) -- und es ist DIESELBE Textur, aus der der
     *     Umriss liest ({@code umriss.gezeichnet: texturId=732 pufferId=29}).
     * <li><b>Nicht der Zeitpunkt:</b> weder ein {@code glFinish} unmittelbar vor dem Lesen
     *     noch das Lesen VOR {@code applyClear()} (also auf dem Puffer des vorigen Bildes)
     *     aendert etwas. Beides gemessen, beides {@code deckung=0/30625}.
     * <li><b>Nicht die Kennung:</b> die kommt seit {@code 869c910a7} am Zeichenaufruf an
     *     ({@code pick.einreichung: kennung=20}).
     * </ul>
     *
     * <p>Der offene Widerspruch, an dem es weitergeht: der orange Umriss ist im Bild
     * SICHTBAR und speist sich aus genau dieser Textur, waehrend ein Abzug derselben
     * Textur ({@code TextureDumper#dumpTexture}) sie voellig leer zeigt. Entweder liest
     * der Abzug falsch, oder der Umriss bezieht sein Ergebnis nicht aus dem Auswahlpuffer.
     * DAS zuerst klaeren -- vor dem naechsten Reparaturversuch.
     */
    private void leseEigenenPuffer(int x, int y, int w, int h, FloatBuffer ziel)
    {
        if (this.framebuffer == null)
        {
            GL11.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, ziel);

            return;
        }

        int vorher = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

        /* ⛔ EIGENER LESE-FBO STATT DES ZEICHEN-FBOs (2026-08-04, per Bisect gefunden).
         *
         * Aus dem Zeichen-FBO herauszulesen lieferte an JEDER Position Nullen -- bei
         * vollstaendigem FBO, passendem Anhang (724) und Status COMPLETE. Ein
         * ausdrueckliches glReadBuffer(COLOR_ATTACHMENT0) aenderte daran nichts.
         *
         * Der zweite Lesepfad (Pufferprobe) las DIESELBE Textur und fand 21606 Punkte
         * ungleich 0 -- exakt die Zahl, die auch RenderDoc im Capture zaehlt. Er
         * unterscheidet sich in genau einem Punkt: er legt einen EIGENEN FBO an und
         * haengt die Textur selbst dort an. Das wird hier nachgezogen.
         *
         * Der FBO wird einmal erzeugt und behalten; die Textur wird bei jedem Lesen neu
         * angehaengt, weil sie sich bei einer Groessenaenderung ersetzen kann. */
        if (this.leseFbo == 0)
        {
            this.leseFbo = GL30.glGenFramebuffers();
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, this.leseFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D, this.framebuffer.getMainTexture().id, 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

        try
        {
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                /* Haengt am FBO noch DIE Textur, in die 26.2 zeichnet? Framebuffer#resize
                 * zieht den Anhang nach, wenn eine Textur ihre GL-Nummer wechselt -- bleibt
                 * hier eine alte Nummer stehen, liest glReadPixels eine freigegebene Textur
                 * und findet nichts, egal wie richtig Zeitpunkt und Bindung sind. */
                mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.leseziel",
                    "gebunden=" + GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
                        + " eigener=" + this.framebuffer.id
                        + " anhang=" + GL30.glGetFramebufferAttachmentParameteri(
                            GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                            GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME)
                        + " textur=" + this.framebuffer.getMainTexture().id
                        + " status=0x" + Integer.toHexString(
                            GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)));
            }

            GL11.glReadPixels(x, y, w, h, GL11.GL_RGBA, GL11.GL_FLOAT, ziel);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, vorher);
        }
    }

    public void pick(int x, int y)
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer floats = stack.mallocFloat(4);

            this.leseEigenenPuffer(x, y, 1, 1, floats);

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
        this.leseEigenenPuffer(x0, y0, w, h, floats);

        /* ⚗️ ROHE WERTE, BEVOR DIE ALPHA-ANNAHME GREIFT (2026-08-04).
         *
         * Die Schleife unten ueberspringt jeden Texel mit alpha < 1. Steht in diesem Puffer
         * eine Kennung MIT alpha 0, sieht sie von aussen exakt aus wie ein leerer Puffer --
         * und der sichtbare Umriss, der dieselbe Textur im Shader liest, widerspricht dem.
         * Genau dieser Fehlertyp ("alpha != 0 als Annahme IM Messgeraet") hat hier schon
         * einmal Stunden gekostet. Also erst die rohen Maxima, dann urteilen. */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            float maxA = 0F;
            float maxRGB = 0F;
            int mitte = (((y - y0) * w) + (x - x0)) * 4;

            for (int i = 0; i + 3 < needed; i += 4)
            {
                maxA = Math.max(maxA, floats.get(i + 3));
                maxRGB = Math.max(maxRGB, Math.max(floats.get(i), Math.max(floats.get(i + 1), floats.get(i + 2))));
            }

            mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.roh",
                "maxAlpha=" + maxA + " maxRGB=" + maxRGB
                    + " mitte=" + (mitte >= 0 && mitte + 3 < needed
                        ? floats.get(mitte) + "/" + floats.get(mitte + 1) + "/"
                            + floats.get(mitte + 2) + "/" + floats.get(mitte + 3)
                        : "ausserhalb")
                    + " region=" + x0 + "," + y0 + " " + w + "x" + h);
        }

        {
            int centerId = 0;
            int nearestHandle = 0;
            int nearestAny = 0;
            long nearestDist = Long.MAX_VALUE;
            long nearestAnyDist = Long.MAX_VALUE;
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

                    long dx = fx - x;
                    long dy = fy - y;
                    long dist = dx * dx + dy * dy;

                    if (id >= 1 && id <= handleMax)
                    {
                        if (dist <= radiusSq && dist < nearestDist)
                        {
                            nearestDist = dist;
                            nearestHandle = id;
                        }
                    }
                    else if (id != 0 && dist <= radiusSq && dist < nearestAnyDist)
                    {
                        nearestAnyDist = dist;
                        nearestAny = id;
                    }
                }
            }

            /* Blenders Klick-Toleranz (view3d_select: 14->9->5px-Kaskade um den Cursor):
             * Gizmo-Handles gewinnen, dann der exakte Pixel, dann die NAECHSTE beliebige
             * Kennung im Radius — sonst sind duenne Wire-Widgets und schmale Marker nur
             * pixelgenau klickbar und fuehlen sich tot an. */
            this.index = nearestHandle != 0 ? nearestHandle : (centerId != 0 ? centerId : nearestAny);

            /* Messfuehler 2026-08-02, zweiter Anlauf: das Leseziel stimmt inzwischen
             * (gebunden=29=eigener), der Index bleibt 0. Diese Zeile trennt die zwei
             * verbliebenen Ursachen -- LEERER Puffer (Bildgraph zeichnet erst spaeter,
             * dann ist deckung=0) gegen FALSCHE STELLE (Puffer traegt Kennungen, nur
             * nicht unter der Maus, dann ist deckung>0 bei maxId>0). */
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                int deckung = 0;
                int maxId = 0;

                for (int py = 0; py < h; py++)
                {
                    for (int px = 0; px < w; px++)
                    {
                        int base = (py * w + px) * 4;

                        if ((int) (floats.get(base + 3) * 255F) < 1)
                        {
                            continue;
                        }

                        deckung++;
                        maxId = Math.max(maxId, (int) (floats.get(base) * 255F)
                            | ((int) (floats.get(base + 1) * 255F) << 8)
                            | ((int) (floats.get(base + 2) * 255F) << 16));
                    }
                }

                Texture t = this.framebuffer.getMainTexture();

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.inhalt",
                    "deckung=" + deckung + "/" + (w * h) + " maxId=" + maxId
                        + " region=" + x0 + "," + y0 + " " + w + "x" + h
                        + " textur=" + t.width + "x" + t.height);
            }

            /* Messfuehler 2026-08-02: prueft die ANNAHME aus ModelInstance#fillStencilMap --
             * dass orderedGroups/orderedBones der Reihenfolge entspricht, die
             * BOBJModelVAO.updateMesh als Knochenteil ins Licht-Attribut backt. Der
             * Formteil kommt seit 869c910a7 an; ob die SUMME den richtigen Knochen
             * trifft, sagt erst der zurueckgelesene Name.
             *
             * Steht HIER und nicht im Aufrufer: UIFilmController ist Mixin-Ziel eines
             * Redirectors, dessen Neuwebung beim Hotswap scheitert (ClassFormatError).
             * Diese Klasse ist ungewoben und damit live tauschbar. */
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                Pair<Form, String> getroffen = this.getPicked();

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("stencil.getroffen",
                    "index=" + this.index
                        + " knochen=" + (getroffen == null ? "NICHTS" : "'" + getroffen.b + "'")
                        + " gizmoTreffer=" + nearestHandle + " mitte=" + centerId);
            }
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

        /* Gegenstueck zum pushGroup in apply() — ohne das Paar verschachtelt sich der Event
         * Browser falsch, was schlimmer ist als gar kein Marker. */
        com.afrobama.bbstools.client.debug.master.external.ApiTraceBridge.popGroup();

        /* Gegenstueck zum rangePush in apply(). Muss auf JEDEM Weg aus dem Durchgang
         * laufen, sonst wandert der Bereich in Nsight bis ans Ende der Aufzeichnung und
         * die Zeitachse ist unlesbar. */
        com.afrobama.bbstools.client.debug.master.external.NvtxBridge.rangePop();
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