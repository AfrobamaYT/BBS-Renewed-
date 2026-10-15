// Coded by Afrobama/Claude — UI Overhaul Phase 1: theme constants
package com.afrobama.bbstools.client.ui.theme;

/**
 * Central palette + shape constants for the BBSTools UI overhaul.
 *
 * <p>Style direction: AE / Premiere / DaVinci — pro-video-editor.
 * Distinguishing features vs Refreshed:</p>
 * <ul>
 *   <li>Sharper corners (0–2px radius vs Refreshed's M3 pills)</li>
 *   <li>Background-only transparency support via {@link BBSToolsTransparency}</li>
 *   <li>Multi-accent palette (blue primary, teal for camera/timeline,
 *       orange for free-fly/destructive, magenta for paint/texture) rather
 *       than a single cyan accent</li>
 *   <li>Compact paddings — pro tools fit more density per pixel</li>
 * </ul>
 *
 * <p>All colors are ARGB ints — pack with {@link #argb}. Alpha lives in
 * the top byte. {@link BBSToolsTransparency#PANEL_BG_ALPHA} is applied
 * multiplicatively to the panel-background calls via the
 * {@code Batcher2DBackgroundAlphaMixin}; content (text/icons/borders)
 * stays opaque so legibility is preserved at any transparency setting.</p>
 */
public final class BBSToolsTheme
{
    /* ── Backgrounds ────────────────────────────────────────────── */

    /** Deepest panel — outer dashboard chrome. */
    public static final int BG_DEEP = 0xFF0E0F11;

    /** Standard panel background. */
    public static final int BG_PANEL = 0xFF181A1D;

    /** Slight elevation — nested panels, popups, dropdowns. */
    public static final int BG_ELEVATED = 0xFF212428;

    /** Highest elevation — modal overlays, context menus. */
    public static final int BG_OVERLAY = 0xFF2B2F34;

    /** Field interior (trackpad, color picker bg, etc.). */
    public static final int BG_FIELD = 0xFF14161A;

    /** Hover state on fields/buttons. */
    public static final int BG_HOVER = 0xFF2E333A;

    /** Pressed state. */
    public static final int BG_ACTIVE = 0xFF3A4047;

    /* ── Borders ────────────────────────────────────────────────── */

    /** Standard 1px panel border. */
    public static final int BORDER = 0xFF2E3137;

    /** Stronger divider between major regions. */
    public static final int BORDER_STRONG = 0xFF3D424A;

    /** Border on hovered interactive element. */
    public static final int BORDER_HOVER = 0xFF7AA7E0;

    /** Border on currently-focused / selected element. */
    public static final int BORDER_FOCUS = 0xFF4D9CFF;

    /* ── Text ───────────────────────────────────────────────────── */

    /** Primary text — labels, values, headings. */
    public static final int TEXT = 0xFFE2E5EA;

    /** Secondary / dimmed text — captions, units, hints. */
    public static final int TEXT_DIM = 0xFF8A9099;

    /** Most-dimmed — placeholder, disabled. */
    public static final int TEXT_FAINT = 0xFF5F656D;

    /** Accent text (links, current-value highlight, etc.). */
    public static final int TEXT_ACCENT = 0xFF4D9CFF;

    /* ── Accents (multi-accent palette) ─────────────────────────── */

    /** Primary brand accent — buttons, focus rings, sliders. */
    public static final int ACCENT_PRIMARY = 0xFF4D9CFF;

    /** Camera / timeline accent — alt-cam preview, viewport markers. */
    public static final int ACCENT_CAMERA = 0xFF4EC1FF;

    /** Free-fly / "you are editing" accent — warnings of live mutation. */
    public static final int ACCENT_FREEFLY = 0xFFFF6644;

    /** Destructive — delete, reset, remove. */
    public static final int ACCENT_DESTRUCTIVE = 0xFFE05858;

    /** Paint / texture / model-editor accent — magenta to distinguish. */
    public static final int ACCENT_PAINT = 0xFFD06FE0;

    /** Success / "applied" — confirmations, "saved". */
    public static final int ACCENT_OK = 0xFF6FCB6F;

    /* ── Shapes ─────────────────────────────────────────────────── */

    /** Panel corner radius. 0 = sharp (pro-tool feel). */
    public static final int RADIUS_PANEL = 0;

    /** Button corner radius. 2px = subtle softening, still sharp. */
    public static final int RADIUS_BUTTON = 2;

    /** Toggle / chip corner radius. 1px for crisp pro look. */
    public static final int RADIUS_CHIP = 1;

    /* ── Spacing ────────────────────────────────────────────────── */

    public static final int PAD_TIGHT = 2;
    public static final int PAD_SMALL = 4;
    public static final int PAD_MEDIUM = 8;
    public static final int PAD_LARGE = 12;

    /** Standard section header height including padding. */
    public static final int SECTION_HEADER_H = 14;

    /** Standard control row height. */
    public static final int ROW_H = 16;

    /* ── Utilities ──────────────────────────────────────────────── */

    private BBSToolsTheme() {}

    /** Pack 0–255 channels into ARGB int. */
    public static int argb(int a, int r, int g, int b)
    {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Multiply alpha of an ARGB color by {@code mult} (0..1). RGB
     *  unchanged. Used by the background-alpha mixin. */
    public static int withAlpha(int argb, float mult)
    {
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * mult);
        if (newA < 0) newA = 0;
        if (newA > 255) newA = 255;
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    /** Detect whether a color "looks like" a panel-background color
     *  (heuristic: low brightness + relatively neutral). Used by the
     *  background-alpha mixin to differentiate panel BG draws from
     *  text/icon draws. */
    public static boolean looksLikeBackground(int argb)
    {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max > 80) return false;
        int min = Math.min(r, Math.min(g, b));
        return (max - min) <= 20;
    }

}
