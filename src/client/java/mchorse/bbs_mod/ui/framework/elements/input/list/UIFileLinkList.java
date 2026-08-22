package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class UIFileLinkList extends UIList<UIFileLinkList.FileLink>
{
    /** Row height of the plain list, and the smallest step of the size walk. */
    public static final int VIEW_LIST = 16;
    public static final int VIEW_ICONS_SMALL = 32;
    public static final int VIEW_ICONS_MEDIUM = 64;
    public static final int VIEW_ICONS_LARGE = 96;
    public static final int VIEW_ICONS_VERY_LARGE = 160;
    private static final int MAX_ITEM_SIZE = 220;
    /** Step of the Ctrl+wheel zoom — fine enough to land between the named steps. */
    private static final int FREE_ZOOM_STEP = 8;
    private static final int GRID_PADDING = 4;
    private static final int GRID_GAP = 6;
    private static final int GRID_TITLE_HEIGHT = 12;
    /** Thumbnail edge inside a list row — leaves row height and text column untouched. */
    private static final int PREVIEW_SIZE = 12;

    /** Preview edge in use. {@link #VIEW_LIST} means rows; anything above means tiles. */
    private int itemSize = VIEW_LIST;

    public Consumer<Link> fileCallback;
    public Link path = new Link("", "");
    public Predicate<Link> filter;


    public UIFileLinkList(Consumer<Link> fileCallback)
    {
        super(null);

        this.callback = (list) ->
        {
            FileLink fileLink = list.get(0);

            if (!fileLink.folder)
            {
                if (this.fileCallback != null)
                {
                    this.fileCallback.accept(fileLink.link);
                }
            }
            else
            {
                this.setPath(fileLink.link, !fileLink.title.equals(".."));
            }
        };
        this.fileCallback = fileCallback;

        /* Zuletzt gewaehlte groesse wiederherstellen — sie ueber die sitzung hinaus zu
         * vergessen ist bei einer ansichts-einstellung immer falsch. */
        this.setItemSize(gemerkteGroesse());
    }

    /* Die einstellung wird ueber try/catch angefasst, nicht nur ueber einen null-check:
     * beim hotswap in eine laufende instanz existiert ein NEUES statisches feld dort noch
     * gar nicht, und der zugriff scheitert dann mit NoSuchFieldError statt mit null. Ohne
     * das waere die ansicht bis zum naechsten boot unbenutzbar. */

    private static int gemerkteGroesse()
    {
        try
        {
            return BBSSettings.texturePickerItemSize == null ? VIEW_LIST : BBSSettings.texturePickerItemSize.get();
        }
        catch (Throwable e)
        {
            return VIEW_LIST;
        }
    }

    private static void merkeGroesse(int groesse)
    {
        try
        {
            if (BBSSettings.texturePickerItemSize != null)
            {
                BBSSettings.texturePickerItemSize.set(groesse);
            }
        }
        catch (Throwable e)
        {
            /* Einstellung in dieser instanz (noch) nicht vorhanden — die ansicht laeuft trotzdem. */
        }
    }

    public UIFileLinkList filter(Predicate<Link> filter)
    {
        this.filter = filter;

        return this;
    }

    public void setPath(Link link)
    {
        this.setPath(link, true);
    }

    /**
     * Set current link
     */
    public void setPath(Link link, boolean fastForward)
    {
        if (link == null || link.source.isEmpty())
        {
            this.clear();

            for (String source : BBSMod.getProvider().getSourceKeys())
            {
                this.add(new FileLink(source, new Link(source, ""), true));
            }

            this.path = new Link("", "");

            this.sort();
        }
        else
        {
            Collection<Link> links = BBSMod.getProvider().getLinksFromPath(link, false);

            if (fastForward && links.size() == 1)
            {
                Link first = links.iterator().next();

                if (first.path.endsWith("/"))
                {
                    this.setPath(first);

                    return;
                }
            }

            this.path = link;

            FileLink parent = link.path.isEmpty()
                ? new FileLink("..", new Link("", ""), true)
                : new FileLink("..", new Link(link.source, StringUtils.parentPath(link.path)), true);

            this.clear();
            this.add(parent);

            for (Link l : links)
            {
                if (this.filter == null || this.filter.test(l))
                {
                    this.add(new FileLink(StringUtils.fileName(l.path).replaceAll("/", ""), l, l.path.endsWith("/")));
                }
            }

            this.sort();
        }
    }

    public void setCurrent(Link link)
    {
        this.setCurrent(link, false);
    }

    public void setCurrent(Link link, boolean scroll)
    {
        this.deselect();

        if (link == null)
        {
            return;
        }

        for (FileLink entry : this.list)
        {
            if (entry.link.equals(link))
            {
                if (scroll) this.setCurrentScroll(entry);
                else this.setCurrent(entry);

                return;
            }
        }
    }

    /* Groesse der vorschau: liste (16) → klein → mittel → gross → sehr gross, dazwischen
     * frei per Strg+mausrad. Ab der ersten stufe ueber der liste wird gekachelt statt
     * gezeilt — dasselbe system wie im alten fork. */

    public void toggleLargeView()
    {
        this.setItemSize(this.itemSize <= VIEW_LIST ? VIEW_ICONS_MEDIUM : VIEW_LIST);
    }

    public void setItemSize(int size)
    {
        this.itemSize = Math.max(VIEW_LIST, Math.min(MAX_ITEM_SIZE, size));
        this.scroll.scrollItemSize = this.isLargeViewEnabled() ? this.getGridRowStride() : this.itemSize;
        this.scroll.scrollSpeed = this.scroll.scrollItemSize;

        merkeGroesse(this.itemSize);

        this.update();
    }

    public int getItemSize()
    {
        return this.itemSize;
    }

    public boolean isLargeViewEnabled()
    {
        return this.itemSize > VIEW_LIST;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY) && Window.isCtrlPressed() && context.mouseWheel != 0)
        {
            this.setItemSize(this.itemSize + (context.mouseWheel > 0 ? FREE_ZOOM_STEP : -FREE_ZOOM_STEP));

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public void update()
    {
        if (!this.isLargeViewEnabled())
        {
            this.scroll.scrollItemSize = this.itemSize;
            this.scroll.scrollSpeed = this.scroll.scrollItemSize;

            super.update();

            return;
        }

        int rows = (int) Math.ceil(this.getVisibleElementsCount() / (double) this.getGridColumns());

        this.scroll.scrollItemSize = this.getGridRowStride();
        this.scroll.scrollSpeed = this.scroll.scrollItemSize;
        this.scroll.setSize(rows);
        this.scroll.clamp();
    }

    @Override
    public void resize()
    {
        super.resize();

        /* Die spaltenzahl haengt an der breite — nach jeder groessenaenderung neu rechnen. */
        if (this.isLargeViewEnabled())
        {
            this.update();
        }
    }

    @Override
    public int getHoveredIndex(UIContext context)
    {
        if (!this.isLargeViewEnabled())
        {
            return super.getHoveredIndex(context);
        }

        return this.getListIndexFromVisible(this.getGridVisibleIndex(context.mouseX, context.mouseY));
    }

    @Override
    public void setCurrentScroll(FileLink element)
    {
        this.setCurrent(element);

        if (!this.isLargeViewEnabled() || this.current.isEmpty())
        {
            return;
        }

        int sichtbar = this.getVisibleIndexFromList(this.current.get(0));

        if (sichtbar >= 0)
        {
            this.scroll.setScroll((sichtbar / this.getGridColumns()) * this.scroll.scrollItemSize);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.isLargeViewEnabled())
        {
            return super.subMouseClicked(context);
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int index = this.getListIndexFromVisible(this.getGridVisibleIndex(context.mouseX, context.mouseY));

            if (this.exists(index))
            {
                this.setIndex(index);

                if (this.callback != null)
                {
                    this.callback.accept(this.getCurrent());
                }
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void renderList(UIContext context)
    {
        if (!this.isLargeViewEnabled())
        {
            super.renderList(context);

            return;
        }

        int anzahl = this.getVisibleElementsCount();

        if (anzahl == 0)
        {
            return;
        }

        int spalten = this.getGridColumns();
        int zeilenSchritt = this.getGridRowStride();
        int kachelB = this.getGridTileWidth();
        int kachelH = this.getGridTileHeight();
        int scroll = (int) this.scroll.getScroll();

        /* Nur die zeilen zeichnen, die im sichtfenster liegen — sonst laedt ein ordner mit
         * hunderten dateien beim oeffnen jede einzelne textur hoch. */
        int ersteZeile = Math.max(0, scroll / zeilenSchritt);
        int letzteZeile = Math.max(ersteZeile, Math.min((anzahl - 1) / spalten, (scroll + this.area.h) / zeilenSchritt + 1));

        for (int zeile = ersteZeile; zeile <= letzteZeile; zeile++)
        {
            int y = this.area.y + zeile * zeilenSchritt - scroll;

            for (int spalte = 0; spalte < spalten; spalte++)
            {
                int sichtbar = zeile * spalten + spalte;

                if (sichtbar >= anzahl)
                {
                    break;
                }

                FileLink element = this.getElementAt(sichtbar);
                int index = this.getListIndexFromVisible(sichtbar);

                if (element == null || !this.exists(index))
                {
                    continue;
                }

                int x = this.area.x + GRID_PADDING + spalte * (kachelB + GRID_GAP);
                boolean hover = context.mouseX >= x && context.mouseX < x + kachelB
                    && context.mouseY >= y && context.mouseY < y + kachelH;

                this.renderListElement(context, element, index, x, y, hover, this.current.contains(index));
            }
        }
    }

    @Override
    public void renderListElement(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (!this.isLargeViewEnabled())
        {
            super.renderListElement(context, element, i, x, y, hover, selected);

            return;
        }

        /* Design-System: Kachel als Karte auf --panel, Radius 6, Auswahl als blaue Kontur —
         * dieselbe Anatomie wie im Auswahl-Bildschirm. Vorher lag hier ein flaches
         * halbdurchsichtiges Rechteck ohne Kante, das auf dem Grund kaum zu sehen war. */
        int rand = 2;
        int kx = x + rand;
        int ky = y + rand;
        int kb = this.getGridTileWidth() - rand * 2;
        int kh = this.getGridTileHeight() - rand * 2;

        context.batcher.rundeck(kx, ky, kb, kh, 6F,
            Colors.A100 | (hover ? com.mcstudio.nano.NanoDesign.PANELKOPF : com.mcstudio.nano.NanoDesign.PANEL));

        this.renderElementPart(context, element, i, x, y, hover, selected);

        if (selected)
        {
            context.batcher.outline(kx, ky, kx + kb, ky + kh, Colors.A100 | com.mcstudio.nano.NanoDesign.BLAU);
        }
    }

    private int getGridTileWidth()
    {
        return Math.max(56, this.itemSize + 20);
    }

    private int getGridTileHeight()
    {
        return this.itemSize + GRID_TITLE_HEIGHT + 8;
    }

    private int getGridRowStride()
    {
        return this.getGridTileHeight() + GRID_GAP;
    }

    private int getGridColumns()
    {
        int kachelB = this.getGridTileWidth();
        int platz = Math.max(kachelB, this.area.w - GRID_PADDING * 2 - this.scroll.getScrollbarWidth());

        return Math.max(1, (platz + GRID_GAP) / (kachelB + GRID_GAP));
    }

    /* Gefiltert stimmt der listen-index nicht mehr mit dem sichtbaren ueberein: das raster
     * rechnet in SICHTBAREN, auswahl und callback in LISTEN-indizes. */

    private int getVisibleElementsCount()
    {
        if (!this.isFiltering())
        {
            return this.list.size();
        }

        int anzahl = 0;

        while (this.getElementAt(anzahl) != null)
        {
            anzahl += 1;
        }

        return anzahl;
    }

    private int getListIndexFromVisible(int sichtbar)
    {
        if (sichtbar < 0)
        {
            return -1;
        }

        if (!this.isFiltering())
        {
            return sichtbar;
        }

        FileLink element = this.getElementAt(sichtbar);

        return element == null ? -1 : this.list.indexOf(element);
    }

    private int getVisibleIndexFromList(int listenIndex)
    {
        if (!this.exists(listenIndex))
        {
            return -1;
        }

        if (!this.isFiltering())
        {
            return listenIndex;
        }

        FileLink ziel = this.list.get(listenIndex);

        for (int i = 0; i < 4096; i++)
        {
            FileLink element = this.getElementAt(i);

            if (element == null)
            {
                return -1;
            }

            if (element == ziel)
            {
                return i;
            }
        }

        return -1;
    }

    private int getGridVisibleIndex(int mouseX, int mouseY)
    {
        if (!this.area.isInside(mouseX, mouseY))
        {
            return -1;
        }

        int kachelB = this.getGridTileWidth();
        int kachelH = this.getGridTileHeight();
        int zeilenSchritt = this.getGridRowStride();
        int x = mouseX - this.area.x - GRID_PADDING;
        int y = mouseY - this.area.y + (int) this.scroll.getScroll();

        if (x < 0 || y < 0)
        {
            return -1;
        }

        int spaltenSchritt = kachelB + GRID_GAP;
        int spalte = x / spaltenSchritt;

        /* In der luecke zwischen zwei kacheln liegt nichts — sonst waehlt ein klick daneben. */
        if (x % spaltenSchritt >= kachelB || y % zeilenSchritt >= kachelH || spalte >= this.getGridColumns())
        {
            return -1;
        }

        int index = (y / zeilenSchritt) * this.getGridColumns() + spalte;

        return index < this.getVisibleElementsCount() ? index : -1;
    }

    @Override
    protected boolean sortElements()
    {
        this.list.sort((a, b) ->
        {
            if (a.folder != b.folder)
            {
                return a.folder ? -1 : 1;
            }

            return NaturalOrderComparator.compare(true, a.title, b.title);
        });

        return true;
    }

    @Override
    protected void renderElementPart(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (!this.isLargeViewEnabled())
        {
            if (element.folder)
            {
                context.batcher.icon(Icons.FOLDER, Colors.setA(Colors.WHITE, hover ? 0.75F : 0.6F), x + 2, y);
            }
            else
            {
                this.renderPreview(context, element.link, x + 2, y + (VIEW_LIST - PREVIEW_SIZE) / 2, PREVIEW_SIZE, hover);
            }

            context.batcher.textShadow(element.title, x + 20, y + 4, hover ? Colors.HIGHLIGHT : Colors.WHITE);

            return;
        }

        /* Kachel: bild oben mittig, name darunter — auf die kachelbreite gekuerzt, sonst
         * laeuft ein langer dateiname in die nachbarkachel. */
        int kachelB = this.getGridTileWidth();
        int bildX = x + (kachelB - this.itemSize) / 2;
        int bildY = y + 3;

        if (element.folder)
        {
            /* Bernstein wie im Auswahl-Bildschirm: ein Ordner sieht ueberall gleich aus. */
            context.batcher.icon(Icons.FOLDER, Colors.setA(com.mcstudio.nano.NanoDesign.BERNSTEIN, hover ? 1F : 0.85F),
                bildX + this.itemSize / 2 - 8, bildY + this.itemSize / 2 - 8);
        }
        else
        {
            this.renderPreview(context, element.link, bildX, bildY, this.itemSize, hover);
        }

        FontRenderer font = context.batcher.getFont();
        String titel = font.limitToWidth(element.title, Math.max(1, kachelB - 8));

        context.batcher.textShadow(titel, x + (kachelB - font.getWidth(titel)) / 2, bildY + this.itemSize + 3,
            hover ? Colors.HIGHLIGHT : Colors.WHITE);
    }

    /**
     * The file's own pixels instead of one generic icon for every entry — a list of texture
     * names says nothing about which texture it is.
     *
     * <p>Only rows the scroll area actually draws get here, so a folder of hundreds of files
     * uploads only what is on screen. A file that fails to load (unreadable, not an image, a
     * shape the loader rejects) falls back to the generic icon rather than leaving a hole:
     * the entry stays selectable, which is what the browser is for.
     */
    private void renderPreview(UIContext context, Link link, int x, int y, int kante, boolean hover)
    {
        Texture texture = null;

        try
        {
            texture = BBSModClient.getTextures().getTexture(link, GL11.GL_NEAREST, true);
        }
        catch (Exception e)
        {
            /* handled below — the icon fallback covers every failure the same way */
        }

        if (texture == null || texture.width <= 0 || texture.height <= 0)
        {
            context.batcher.icon(Icons.IMAGE, Colors.setA(Colors.WHITE, hover ? 0.75F : 0.6F),
                x + kante / 2 - 8, y + kante / 2 - 8);

            return;
        }

        /* Fit, don't stretch: skins and sprite sheets are far from square, and a squashed
         * thumbnail is harder to recognise than a small one. */
        float scale = Math.min(kante / (float) texture.width, kante / (float) texture.height);
        int w = Math.max(1, Math.round(texture.width * scale));
        int h = Math.max(1, Math.round(texture.height * scale));

        context.batcher.fullTexturedBox(texture, x + (kante - w) / 2, y + (kante - h) / 2, w, h);
    }

    public static class FileLink
    {
        public String title;
        public Link link;
        public boolean folder;

        public FileLink(String title, Link link, boolean folder)
        {
            this.title = title;
            this.link = link;
            this.folder = folder;
        }
    }
}