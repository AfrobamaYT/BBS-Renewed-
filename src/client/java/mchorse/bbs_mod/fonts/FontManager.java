package mchorse.bbs_mod.fonts;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.watchdog.IWatchDogListener;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * TrueType fonts from the assets' {@code fonts} folder, for labels and subtitles. Built from vanilla's
 * own parts — a FreeType face, a {@link TrueTypeGlyphProvider}, a {@link FontSet} with its own atlas
 * and a {@link Font} whose glyph lookup ignores the style's font id — so the font is picked by which
 * renderer draws, and every existing width, wrap and draw call works as it is.
 *
 * <p>From BBS FS b0224f806 (Wemppy4/bbs-fs, MIT); 1.21 used stb_truetype, 26.3 rasterizes with
 * FreeType. The atlas is point-sampled, so glyphs are rasterized for the scale they will be seen at.</p>
 */
public class FontManager implements IWatchDogListener
{
    public static final String FOLDER = "fonts";
    public static final int MIN_SIZE = 4;
    public static final int MAX_SIZE = 96;

    /** A font nobody asked for during this long hands its glyph atlases back */
    private static final long DELETE_MS = 30_000;

    /** About as tall as a glyph can be before it no longer fits a 256x256 atlas page */
    private static final int MAX_RASTER = 192;

    /** For text whose on-screen size is not known up front, a label in the world: the finest raster the atlas holds */
    public static final float MAX_DETAIL = Float.MAX_VALUE;

    private static FontManager instance;

    private final Map<Key, Entry> fonts = new HashMap<>();
    private long lastSweep;

    public static FontManager get()
    {
        if (instance == null) instance = new FontManager();

        return instance;
    }

    public static boolean isFont(String path)
    {
        String lower = path.toLowerCase();

        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    /** Every font file in the assets, for the pickers */
    public static List<Link> getFontLinks()
    {
        List<Link> links = new ArrayList<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets(FOLDER)))
        {
            if (isFont(link.path)) links.add(link);
        }

        return links;
    }

    public static File getFolder()
    {
        return new File(BBSMod.getAssetsFolder(), FOLDER);
    }

    /**
     * The font at the link laid out at a size, or null when there is no such font (the caller falls
     * back to the default one). {@code scale} is how many screen pixels a unit of that layout covers
     * where it is about to be drawn.
     */
    public FontRenderer get(Link link, int size, float scale)
    {
        if (link == null) return null;

        this.sweep();

        Key key = new Key(link, Math.max(MIN_SIZE, Math.min(MAX_SIZE, size)), scale);
        Entry entry = this.fonts.get(key);

        /* A resource reload closed the set it had in vanilla's map: build it again */
        if (entry != null && entry.set != null && !FontLader.eingetragen(key.id(), entry.set))
        {
            entry.provider.close();
            entry = null;
        }

        if (entry == null)
        {
            entry = this.load(key);
            this.fonts.put(key, entry);
        }

        entry.lastUsed = System.currentTimeMillis();

        return entry.font;
    }

    /** Drops the atlases of fonts that went unused, see {@link #DELETE_MS} */
    private void sweep()
    {
        long now = System.currentTimeMillis();

        if (now - this.lastSweep < 1000) return;

        this.lastSweep = now;

        for (Iterator<Entry> it = this.fonts.values().iterator(); it.hasNext(); )
        {
            Entry entry = it.next();

            if (now - entry.lastUsed > DELETE_MS)
            {
                entry.delete();
                it.remove();
            }
        }
    }

    /** A font file that changed on disk is rebuilt on the next request */
    @Override
    public void accept(Path path, WatchDogEvent event)
    {
        if (!isFont(path.toString())) return;

        Link link = BBSMod.getProvider().getLink(path.toFile());

        for (Iterator<Map.Entry<Key, Entry>> it = this.fonts.entrySet().iterator(); it.hasNext(); )
        {
            Map.Entry<Key, Entry> entry = it.next();

            if (entry.getKey().link.equals(link))
            {
                entry.getValue().delete();
                it.remove();
            }
        }
    }

    private Entry load(Key key)
    {
        File file = BBSMod.getProvider().getFile(key.link);
        FontLader.Geladen font = file == null || !file.isFile() ? null : FontLader.laden(file, key.size, key.oversample, key.id());

        return font == null ? new Entry(null, null, null, null) : new Entry(font.renderer(), font.set(), font.provider(), key.id());
    }

    private static class Key
    {
        final Link link;
        final int size;

        /** Whole numbers on purpose: an animated scale would otherwise build a fresh atlas every frame it moved */
        final int oversample;

        Key(Link link, int size, float scale)
        {
            this.link = link;
            this.size = size;
            this.oversample = Math.max(1, Math.min(Math.max(1, MAX_RASTER / size), Math.round(Math.min(scale, MAX_RASTER))));
        }

        /** Names the atlas pages, unique per font, size and raster */
        Identifier id()
        {
            String path = this.link.path.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");

            return Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, "font/" + path + "_" + this.size + "x" + this.oversample);
        }

        @Override
        public boolean equals(Object o)
        {
            return o instanceof Key k && k.link.equals(this.link) && k.size == this.size && k.oversample == this.oversample;
        }

        @Override
        public int hashCode()
        {
            return (this.link.hashCode() * 31 + this.size) * 31 + this.oversample;
        }
    }

    private static class Entry
    {
        final FontRenderer font;
        final FontSet set;
        final TrueTypeGlyphProvider provider;
        long lastUsed;

        final Identifier id;

        Entry(FontRenderer font, FontSet set, TrueTypeGlyphProvider provider, Identifier id)
        {
            this.font = font;
            this.set = set;
            this.provider = provider;
            this.id = id;
        }

        void delete()
        {
            if (this.set != null && FontLader.eingetragen(this.id, this.set)) FontLader.eintragen(this.id, null);
            if (this.set != null) this.set.close();
            if (this.provider != null) this.provider.close();
        }
    }
}
