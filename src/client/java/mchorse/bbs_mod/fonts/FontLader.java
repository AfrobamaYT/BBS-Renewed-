package mchorse.bbs_mod.fonts;

import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FreeType;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/**
 * One TrueType file into a vanilla {@link Font} of its own: a FreeType face, a
 * {@link TrueTypeGlyphProvider} rasterizing at {@code size * oversample}, a {@link FontSet} with its
 * own atlas, and a glyph lookup that ignores the style's font id (BBS FS b0224f806, Wemppy4/bbs-fs, MIT).
 */
final class FontLader
{
    record Geladen(FontRenderer renderer, FontSet set, TrueTypeGlyphProvider provider)
    {}

    private FontLader()
    {}

    /**
     * Into vanilla's font sets under its id, so world text, which 26.3 submits by style and draws
     * with the game's own Font, finds it; vanilla closes every set there on a resource reload
     */
    static void eintragen(Identifier id, FontSet set)
    {
        net.minecraft.client.gui.font.FontManager manager = Minecraft.getInstance().fontManager;

        if (set == null) manager.fontSets.remove(id);
        else manager.fontSets.put(id, set);

        manager.anyGlyphs.invalidate();
        manager.nonFishyGlyphs.invalidate();
    }

    /** Whether vanilla still holds this set: after a resource reload it has closed it */
    static boolean eingetragen(Identifier id, FontSet set)
    {
        return Minecraft.getInstance().fontManager.fontSets.get(id) == set;
    }

    /** The font, or null when the file is no font FreeType reads */
    static Geladen laden(File file, int size, int oversample, Identifier id)
    {
        ByteBuffer buffer = null;
        FT_Face face = null;

        try
        {
            byte[] bytes = Files.readAllBytes(file.toPath());

            /* FreeType reads out of this buffer for as long as the face lives, so it is off-heap; the provider frees it on close */
            buffer = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();

            int height, lineHeight;

            synchronized (FreeTypeUtil.LIBRARY_LOCK)
            {
                try (MemoryStack stack = MemoryStack.stackPush())
                {
                    PointerBuffer pointer = stack.mallocPointer(1);

                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), buffer, 0L, pointer), "Initializing font face");
                    face = FT_Face.create(pointer.get());
                }

                /* Vanilla's Font has a constant lineHeight of 9, so the layout metrics come from the face */
                FreeTypeUtil.assertError(FreeType.FT_Set_Pixel_Sizes(face, 0, size), "Setting font size");

                FT_Size_Metrics metrics = face.size().metrics();

                height = Math.max(1, Math.round(metrics.ascender() / 64F));
                lineHeight = Math.max(height + 1, Math.round(metrics.height() / 64F));
            }

            TrueTypeGlyphProvider provider = new TrueTypeGlyphProvider(buffer, face, size, oversample, 0F, 0F, "");
            FontSet set = new FontSet(new GlyphStitcher(Minecraft.getInstance().getTextureManager(), id));

            set.reload(List.of(new GlyphProvider.Conditional(provider, FontOption.Filter.ALWAYS_PASS)), Set.of());
            eintragen(id, set);

            Font font = new Font(new Font.Provider()
            {
                @Override
                public GlyphSource glyphs(FontDescription description)
                {
                    return set.source(false);
                }

                @Override
                public EffectGlyph effect()
                {
                    return set.whiteGlyph();
                }
            });

            FontRenderer renderer = new FontRenderer();

            renderer.setRenderer(font, height, lineHeight);
            renderer.description = new FontDescription.Resource(id);

            return new Geladen(renderer, set, provider);
        }
        catch (Exception e)
        {
            System.err.println("Font " + file + " could not be loaded: " + e);

            if (face != null) FreeType.FT_Done_Face(face);
            if (buffer != null) MemoryUtil.memFree(buffer);

            return null;
        }
    }
}
