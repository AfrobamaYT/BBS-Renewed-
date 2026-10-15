package mchorse.bbs_mod.ui.dashboard.list;

import com.mcstudio.nano.NanoDesign;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Kachelraster statt Zeilenliste — Muster B des Design-Systems.
 *
 * <p>⛔ NICHTS von der Listenlogik ist nachgebaut. Auswahl, Mehrfachauswahl, Suche,
 * Kontextmenue, Umbenennen, Loeschen und die Tastenkuerzel liegen weiter in
 * {@link UIDataPathList} und {@code UIList} und gehen alle ueber EINEN Index. Ersetzt sind
 * genau zwei Dinge: die Index-Rechnung ({@link RasterScroll}, zwei Achsen statt einer) und
 * das Zeichnen einer Zelle.</p>
 *
 * <p>⛔ KEINE VORSCHAUBILDER. Gemessen am 2026-08-22: BBS speichert je Datensatz nur den
 * Pfad — kein Thumbnail, kein Datum, keine Groesse ({@code DataPath} traegt
 * {@code List<String>} und ein Ordner-Flag, mehr nicht). Der Entwurf zeigt Kacheln mit
 * Vorschau; die braucht erst ein Thumbnail-System. Bis dahin traegt die Zelle die
 * Typ-Flaeche und den Namen — und keine erfundene Farbe, die Bedeutung vortaeuscht.</p>
 */
public class UIKachelListe extends UIDataPathList
{
    /**
     * Kachelhoehe waechst mit der Flaeche. Der Massstab der Formvorschau kommt aus der
     * Hoehe ({@code (y2-y1)/2.5}, siehe Miniaturschlange) — eine feste 46er Kachel liefert
     * auf jedem Fenster denselben winzigen Massstab 12, obwohl auf 960x511 Platz fuer das
     * Dreifache waere. Untergrenze 46, weil bei 427x240 Gestaltungsflaeche (854x480, laut
     * Briefing Pflicht) der Liste gemessen nur 48 px bleiben.
     */
    public static final int KACHEL_MIN_H = 46;
    public static final int KACHEL_MAX_H = 110;
    public static final int KACHEL_MIN_B = 96;

    /** Aktuelle Kachelhoehe; identisch mit {@code raster.scrollItemSize}. */
    private int kachelH = KACHEL_MIN_H;

    private final RasterScroll raster;

    public UIKachelListe(Consumer<List<DataPath>> callback)
    {
        super(callback);

        this.raster = new RasterScroll(this.area, KACHEL_MIN_H);
        this.scroll = this.raster;
    }

    /**
     * Wie aus einem Eintrag etwas Renderbares wird — oder {@code null}, wenn der Typ keine
     * Vorschau kennt (Partikel, Bloecke, Audio). BBS zeichnet Formen ueber
     * {@code FormUtilsClient.renderUI} schon im Replay-Menue und im Film-Controller; hier
     * ist derselbe Weg, keine neue Rendermaschine.
     */
    private Function<DataPath, Form> vorschau;

    /**
     * ⛔ GEDECKELT. Unity laesst die Cache-Groesse ausdruecklich einstellen
     * ({@code SetPreviewTextureCacheSize}), Blender bricht sich in 5.0 gerade an einer
     * Thumbnail-Cache-Grenze das Genick. Ein unbegrenzter Zwischenspeicher ueber einer
     * Bibliothek mit hunderten Modellen ist ein Leck mit Ansage.
     */
    private static final int FORMEN_DECKEL = 64;
    private final Map<String, Form> formen = new HashMap<>();

    /**
     * Asynchroner Nachlader fuer Typen, deren Vorschau erst geladen werden muss (Filme:
     * die Form des ersten Replays steckt in der Filmdatei). Unity macht es genauso —
     * {@code GetAssetPreview} liefert {@code null}, bis das Bild da ist.
     */
    private BiConsumer<DataPath, Consumer<Form>> nachlader;

    /**
     * ⛔ GEDECKELT auf drei gleichzeitige Anfragen. Ohne Deckel startet das erste Bild
     * eine Ladeanfrage je sichtbarer Kachel — bei 24 Kacheln 24 Dateizugriffe auf einmal.
     * Blender laedt Vorschauen ausdruecklich nicht fuer die ganze Bibliothek auf einmal.
     */
    private static final int GLEICHZEITIG = 3;
    private final Set<String> laeuft = new HashSet<>();

    public UIKachelListe nachlader(BiConsumer<DataPath, Consumer<Form>> nachlader)
    {
        this.nachlader = nachlader;

        return this;
    }

    /**
     * Wird vor jedem Zeichnen auf die zwischengespeicherte Form angewandt.
     *
     * <p>⛔ Noetig, weil beim ANLEGEN der Form das Modell noch nicht geladen ist: gemessen
     * kamen alle 30 Maszstaebe als Ausweichwert 1.0 heraus. Was vom geladenen Modell
     * abhaengt, gehoert nicht in den Erzeuger.</p>
     */
    private Consumer<Form> auffrischen;

    /**
     * Standbild je Eintrag — schlaegt die Form-Vorschau, wo es eines gibt. Bei einem Film
     * sagt ein Ausschnitt aus dem Film mehr als die Figur darin.
     *
     * <p>Der Lieferant gibt {@code null} zurueck, wenn kein Bild vorliegt; dann greift die
     * Form-Vorschau, sonst das Typ-Symbol. Ergebnis wird gemerkt — der Dateitest gehoert
     * nicht ins Bild.</p>
     */
    private Function<DataPath, Link> bild;
    private final Map<String, Link> bilder = new HashMap<>();

    public UIKachelListe bild(Function<DataPath, Link> bild)
    {
        this.bild = bild;

        return this;
    }

    /**
     * Scrub-Streifen je Eintrag: {@code felder} Bilder nebeneinander in einer Textur
     * (Storyboard wie bei YouTube/Jellyfin). Beim Darueberfahren waehlt der x-Anteil der Maus
     * das Feld; {@code null} vom Lieferanten = kein Streifen, das Standbild bleibt.
     */
    private Function<DataPath, Link> streifen;
    private final Map<String, Link> streifenLinks = new HashMap<>();
    private int streifenFelder = 1;

    public UIKachelListe streifen(int felder, Function<DataPath, Link> streifen)
    {
        this.streifenFelder = Math.max(1, felder);
        this.streifen = streifen;

        return this;
    }

    private Texture standbild(DataPath pfad)
    {
        return this.textur(this.bild, this.bilder, pfad);
    }

    private Texture textur(Function<DataPath, Link> lieferant, Map<String, Link> links, DataPath pfad)
    {
        if (lieferant == null || pfad.folder)
        {
            return null;
        }

        String schluessel = pfad.toString();

        if (!links.containsKey(schluessel))
        {
            links.put(schluessel, lieferant.apply(pfad));
        }

        Link link = links.get(schluessel);

        return link == null ? null : BBSModClient.getTextures().getTexture(link);
    }

    public UIKachelListe auffrischen(Consumer<Form> auffrischen)
    {
        this.auffrischen = auffrischen;

        return this;
    }

    public UIKachelListe vorschau(Function<DataPath, Form> vorschau)
    {
        this.vorschau = vorschau;

        return this;
    }

    private Form formHolen(DataPath pfad)
    {
        if (pfad.folder)
        {
            return null;
        }

        String schluessel = pfad.toString();

        if (this.vorschau == null)
        {
            return this.nachlader == null ? null : this.nachgeladen(pfad, schluessel);
        }

        Form form = this.formen.get(schluessel);

        if (form == null && !this.formen.containsKey(schluessel))
        {
            if (this.formen.size() >= FORMEN_DECKEL)
            {
                this.formen.clear();
            }

            form = this.vorschau.apply(pfad);
            this.formen.put(schluessel, form);
        }

        return form;
    }

    /** Pfad -> fertige Meta-Zeile. Einmal beim Setzen der Liste gefuellt, nie im Bild. */
    private final Map<String, String> meta = new HashMap<>();
    private Supplier<File> wurzel;

    /**
     * Datenordner als Lieferant, nicht als Wert: beim Bauen der Liste steht das Panel
     * noch nicht, und bei Server-Daten ist der Ordner {@code null}.
     */
    public UIKachelListe wurzel(Supplier<File> wurzel)
    {
        this.wurzel = wurzel;

        return this;
    }

    /**
     * ⛔ NICHT {@code setList}. Gemessen: der Auswahl-Bildschirm fuellt ueber
     * {@code fill(Collection&lt;String&gt;)}, {@code setList} lief nie — der Zwischenspeicher
     * blieb bei 0 Eintraegen, obwohl Ordner und Pfade stimmten. Am Bild sah das aus wie
     * „die Meta-Zeile zeichnet nicht".
     */
    @Override
    public void fill(java.util.Collection<String> hierarchie)
    {
        super.fill(hierarchie);

        this.metaAufbauen(this.getList());
    }

    @Override
    public void setList(List<DataPath> list)
    {
        super.setList(list);

        this.metaAufbauen(list);
    }

    /**
     * ⛔ EINMAL, nicht je Bild. Die Meta-Zeile braucht Dateisystem-Zugriffe; im Zeichenpfad
     * waeren das je Kachel und Bild ein {@code stat}. Hier laeuft es einmal beim Setzen der
     * Liste und landet im Zwischenspeicher.
     */
    private void metaAufbauen(List<DataPath> list)
    {
        this.meta.clear();
        this.formen.clear();
        /* ⛔ Auch die Bilder. Ein Standbild, das WAEHREND der Sitzung entsteht (Film-Screenshot),
         * bliebe sonst hinter einem zwischengespeicherten null haengen und erschiene erst nach
         * einem Neustart — ein stiller Fehlschlag, der wie „das Bild wird nicht gefunden" aussieht. */
        this.bilder.clear();
        this.streifenLinks.clear();

        File wurzel = this.wurzel == null ? null : this.wurzel.get();

        if (wurzel == null || list == null)
        {
            return;
        }

        for (DataPath pfad : list)
        {
            /* Modelle liegen als Ordner, Filme als <name>.dat — beides pruefen, sonst
             * bleibt die Meta-Zeile bei Filmen stumm (gemessen: nichts angezeigt). */
            File f = new File(wurzel, pfad.toString());

            if (!f.exists())
            {
                f = new File(wurzel, pfad + ".dat");

                if (!f.exists())
                {
                    continue;
                }
            }

            if (pfad.folder)
            {
                File[] kinder = f.listFiles();

                this.meta.put(pfad.toString(), kinder == null ? "" : kinder.length + (kinder.length == 1 ? " item" : " items"));
            }
            else
            {
                this.meta.put(pfad.toString(), alter(f.lastModified()));
            }
        }
    }

    /** Relatives Alter, wie im Entwurf — eine absolute Zeitangabe liest hier niemand. */
    private static String alter(long stand)
    {
        long min = Math.max(0L, (System.currentTimeMillis() - stand) / 60000L);

        if (min < 60)
        {
            return min <= 1 ? "just now" : min + " min ago";
        }

        long std = min / 60;

        if (std < 24)
        {
            return std + (std == 1 ? " hour ago" : " hours ago");
        }

        long tage = std / 24;

        return tage == 1 ? "yesterday" : tage + " days ago";
    }

    @Override
    public void resize()
    {
        super.resize();

        int hoehe = Math.max(KACHEL_MIN_H, Math.min(KACHEL_MAX_H, this.area.h / 2));
        int spalten = Math.max(1, this.area.w / Math.max(KACHEL_MIN_B, hoehe));

        if (spalten != this.raster.spalten || hoehe != this.kachelH)
        {
            this.kachelH = hoehe;
            this.raster.spalten = spalten;
            this.raster.scrollItemSize = hoehe;
            this.raster.setSize(this.getList().size());
            this.raster.clamp();
        }
    }

    /**
     * ⛔ ZWEITE Index-Rechnung. {@code UIList} fragt an manchen Stellen
     * {@code scroll.getIndex()}, an anderen diese Methode — und die rechnet einspaltig
     * {@code (y - area.y) / itemSize}. Gemessen: {@code getIndex(202,158)} lieferte richtig 1,
     * der Klick waehlte trotzdem 0. Wer nur eine der beiden ersetzt, bekommt ein Raster,
     * das richtig aussieht und falsch auswaehlt.
     */
    @Override
    public int getHoveredIndex(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return -1;
        }

        return this.raster.getIndex(context.mouseX, context.mouseY);
    }

    @Override
    public int renderElement(UIContext context, DataPath element, int i, int index, boolean postDraw)
    {
        int s = Math.max(1, this.raster.spalten);
        int breite = this.area.w / s;

        int x = this.area.x + (i % s) * breite;
        int y = this.area.y + (i / s) * this.kachelH - (int) this.raster.getScroll();

        if (y + this.kachelH < this.area.y)
        {
            return i + 1;
        }

        if (y >= this.area.ey())
        {
            return -1;
        }

        if (!postDraw)
        {
            boolean ueber = context.mouseX >= x && context.mouseY >= y
                && context.mouseX < x + breite && context.mouseY < y + this.kachelH;

            this.kachel(context, element, x, y, breite, ueber, this.current.contains(index));
        }

        return i + 1;
    }

    private void kachel(UIContext context, DataPath element, int x, int y, int breite, boolean ueber, boolean gewaehlt)
    {
        int rand = 3;
        int kx = x + rand;
        int ky = y + rand;
        int kb = breite - rand * 2;
        int kh = this.kachelH - rand * 2;
        int textZeile = 13;

        int flaeche = Colors.A100 | (ueber ? NanoDesign.PANELKOPF : NanoDesign.PANEL);

        context.batcher.rundeck(kx, ky, kb, kh, 6F, flaeche);

        /* Typ-Flaeche: hier saesse die Vorschau, sobald es eine gibt. */
        int vx = kx + 3;
        int vy = ky + 3;
        int vex = kx + kb - 3;
        int vey = ky + kh - textZeile;

        context.batcher.box(vx, vy, vex, vey, Colors.A100 | NanoDesign.EDITOR);

        Texture standbild = this.standbild(element);
        Texture streifen = ueber ? this.textur(this.streifen, this.streifenLinks, element) : null;
        Form form = standbild != null || streifen != null ? null : this.formHolen(element);

        if (streifen != null)
        {
            float anteil = Math.clamp((context.mouseX - vx) / (float) Math.max(1, vex - vx), 0F, 0.9999F);
            float feldB = streifen.width / (float) this.streifenFelder;

            this.ausschnitt(context, streifen, (int) (anteil * this.streifenFelder) * feldB, feldB, vx, vy, vex, vey);
            context.batcher.box(vx, vey - 2, vx + Math.round(anteil * (vex - vx)), vey, Colors.A100 | NanoDesign.BERNSTEIN);
        }
        else if (standbild != null)
        {
            this.ausschnitt(context, standbild, 0F, standbild.width, vx, vy, vex, vey);
        }
        else if (form == null)
        {
            context.batcher.icon(element.folder ? Icons.FOLDER : this.fileIcon,
                Colors.setA(element.folder ? NanoDesign.BERNSTEIN : NanoDesign.TXT_DIM, element.folder ? 0.85F : 0.34F),
                kx + kb / 2, ky + (kh - textZeile) / 2 + 2, 0.5F, 0.5F);
        }
        else
        {
            if (this.auffrischen != null)
            {
                this.auffrischen.accept(form);
            }

            FormUtilsClient.renderUI(form, context, vx, vy, vex, vey);
        }

        /* Meta rechtsbuendig auf der Namenszeile: bei 46 px Kachelhoehe passt keine zweite
         * Zeile, die Angabe soll aber nicht wegfallen. Der Name kuerzt sich davor. */
        String metaText = this.meta.getOrDefault(element.toString(), "");
        int metaB = metaText.isEmpty() ? 0 : context.batcher.getFont().getWidth(metaText);
        int textY = ky + kh - textZeile + 3;

        context.batcher.text(this.kuerzen(context, element.getLast() + (element.folder ? "/" : ""), kb - 12 - metaB),
            kx + 5, textY, Colors.A100 | (gewaehlt ? NanoDesign.TXT_MAX : NanoDesign.TXT));

        if (metaB > 0)
        {
            context.batcher.text(metaText, kx + kb - 5 - metaB, textY, Colors.A100 | NanoDesign.TXT_OFF);
        }

        if (gewaehlt)
        {
            context.batcher.outline(kx, ky, kx + kb, ky + kh, Colors.A100 | NanoDesign.BLAU);
        }
    }

    /**
     * Ein Bild (oder ein Feld eines Streifens ab {@code u0}, {@code quelleB} breit) mittig
     * beschnitten in die Flaeche: das Bild ist 16:9, die Kachel nicht. Verzerren waere
     * schlimmer als beschneiden — ein Gesicht soll ein Gesicht bleiben.
     */
    private void ausschnitt(UIContext context, Texture t, float u0, float quelleB, int vx, int vy, int vex, int vey)
    {
        float th = t.height;
        float kachelSeite = (float) (vex - vx) / Math.max(1F, vey - vy);
        float u1 = u0;
        float v1 = 0F;
        float u2 = u0 + quelleB;
        float v2 = th;

        if (quelleB / Math.max(1F, th) > kachelSeite)
        {
            float ausschnittB = th * kachelSeite;

            u1 = u0 + (quelleB - ausschnittB) * 0.5F;
            u2 = u1 + ausschnittB;
        }
        else
        {
            float ausschnittH = quelleB / kachelSeite;

            v1 = (th - ausschnittH) * 0.5F;
            v2 = v1 + ausschnittH;
        }

        context.batcher.texturedBox(t, Colors.WHITE, vx, vy, vex - vx, vey - vy, u1, v1, u2, v2, t.width, t.height);
    }

    /** Liefert die geladene Form oder {@code null}, solange sie unterwegs ist. */
    private Form nachgeladen(DataPath pfad, String schluessel)
    {
        if (this.formen.containsKey(schluessel))
        {
            return this.formen.get(schluessel);
        }

        if (this.laeuft.size() < GLEICHZEITIG && this.laeuft.add(schluessel))
        {
            this.nachlader.accept(pfad, (form) ->
            {
                if (this.formen.size() >= FORMEN_DECKEL)
                {
                    this.formen.clear();
                }

                this.formen.put(schluessel, form);
                this.laeuft.remove(schluessel);
            });
        }

        return null;
    }

    private String kuerzen(UIContext context, String text, int platz)
    {
        if (context.batcher.getFont().getWidth(text) <= platz)
        {
            return text;
        }

        while (text.length() > 1 && context.batcher.getFont().getWidth(text + "…") > platz)
        {
            text = text.substring(0, text.length() - 1);
        }

        return text + "…";
    }
}
