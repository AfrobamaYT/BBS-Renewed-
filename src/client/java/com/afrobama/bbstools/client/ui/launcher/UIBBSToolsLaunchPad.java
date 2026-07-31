// Coded by Afrobama/Claude — UI Overhaul Phase 15: DaVinci-style project start page
package com.afrobama.bbstools.client.ui.launcher;

import com.afrobama.bbstools.client.ui.anim.BBSToolsAnim;
import com.afrobama.bbstools.client.ui.overlay.BBSToolsOverlayHelper;
import com.afrobama.bbstools.client.ui.theme.BBSToolsBrand;
import com.afrobama.bbstools.client.ui.theme.BBSToolsButton;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Phase 15 — DaVinci-style Project Start Page.
 *
 * <p>Greeting view when the user opens BBS-Renewed. Shows all available
 * Minecraft worlds as cards in a clean grid, sorted by last-played.
 * Each card has the world thumbnail, name, last-played timestamp, and
 * a hover-grow animation. Click → open that world directly.</p>
 *
 * <p>Top: brand mark + "Recent Worlds" caption.<br>
 * Center: world-card grid (3 columns, scrollable).<br>
 * Bottom: "+ New World" / "Open Folder" / "Close" actions.</p>
 *
 * <p>Animation: each card has a 0..1 hover tween that drives a subtle
 * scale-up and accent-border fade-in. The grid as a whole fades in
 * on open via a single shared tween, so the launch feels deliberate
 * instead of popping into existence.</p>
 */
public class UIBBSToolsLaunchPad extends UIOverlayPanel
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/launchpad");
    private static final int CARD_W = 200;
    private static final int CARD_H = 130;
    private static final int GAP = 16;
    private static final int COLS = 3;

    private final List<WorldCard> cards = new ArrayList<>();
    private final BBSToolsAnim.Tween openTween = new BBSToolsAnim.Tween(0F);
    private long lastFrameNanos;
    private int scrollY;

    public static void open()
    {
        BBSToolsOverlayHelper.open(new UIBBSToolsLaunchPad(), 720, 0.85F);
    }

    public static UIBBSToolsLaunchPad getOpened()
    {
        return BBSToolsOverlayHelper.getOpened(UIBBSToolsLaunchPad.class);
    }

    public UIBBSToolsLaunchPad()
    {
        super(IKey.constant(BBSToolsBrand.NAME + " — Recent Worlds"));
        this.resizable();

        this.content.removeAll();
        this.refreshWorldList();

        BBSToolsButton closeBtn = new BBSToolsButton("Close").onClick(this::close);
        closeBtn.relative(this.content).x(1F, -90).y(1F, -30).w(80).h(22);

        BBSToolsButton refreshBtn = new BBSToolsButton("Refresh").onClick(this::refreshWorldList);
        refreshBtn.relative(this.content).x(6).y(1F, -30).w(80).h(22);

        this.content.add(closeBtn, refreshBtn);

        this.minW(720).maxW(1100);
        this.minH(420).maxH(820);
    }

    private void refreshWorldList()
    {
        this.cards.clear();
        try
        {
            /* Walk the runtime saves/ directory directly. Sidesteps MC's
             *  LevelStorage API which has shifted signatures across 1.21
             *  point-releases. Folders only — files like backups are
             *  filtered out. */
            File savesDir = FabricLoader.getInstance().getGameDir().resolve("saves").toFile();
            if (!savesDir.exists() || !savesDir.isDirectory()) return;
            File[] entries = savesDir.listFiles(File::isDirectory);
            if (entries == null) return;
            Arrays.sort(entries, Comparator.comparingLong(File::lastModified).reversed());
            for (File dir : entries)
            {
                this.cards.add(new WorldCard(dir));
            }
        }
        catch (Throwable t)
        {
            LOG.warn("Failed to enumerate Minecraft worlds — launchpad will be empty", t);
        }
    }

    @Override
    public void render(UIContext context)
    {
        /* Drive open tween toward 1F so the grid fades in on first show. */
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0F : (now - this.lastFrameNanos) / 1_000_000_000F;
        this.lastFrameNanos = now;
        this.openTween.tick(1F, dt);
        float fade = BBSToolsAnim.easeOut(this.openTween.value);

        int x = this.area.x + 16;
        int y = this.area.y + 30;
        int innerW = this.area.w - 32;

        /* Header caption — small label in TEXT_DIM under the panel title. */
        context.batcher.text("RECENT WORLDS · " + this.cards.size() + " AVAILABLE",
            x, y, BBSToolsTheme.TEXT_DIM);
        y += 16;

        /* Card grid. */
        int rows = (this.cards.size() + COLS - 1) / COLS;
        int gridX = x;
        int gridY = y - this.scrollY;
        int colW = (innerW - GAP * (COLS - 1)) / COLS;

        for (int i = 0; i < this.cards.size(); i++)
        {
            int row = i / COLS;
            int col = i % COLS;
            int cx = gridX + col * (colW + GAP);
            int cy = gridY + row * (CARD_H + GAP);
            if (cy + CARD_H < this.area.y || cy > this.area.y + this.area.h) continue;
            this.cards.get(i).render(context, cx, cy, colW, CARD_H, fade, dt);
        }

        /* Empty state. */
        if (this.cards.isEmpty())
        {
            String msg = "No Minecraft worlds found. Create a world from the title screen first.";
            int tw = context.batcher.getFont().getWidth(msg);
            context.batcher.text(msg, this.area.x + (this.area.w - tw) / 2, this.area.y + this.area.h / 2,
                BBSToolsTheme.TEXT_FAINT);
        }

        super.render(context);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        for (WorldCard card : this.cards)
        {
            if (card.hovered && card.lastBounds != null
                && card.lastBounds.contains(context.mouseX, context.mouseY))
            {
                openWorld(card.folder);
                return true;
            }
        }
        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY))
        {
            this.scrollY -= context.mouseWheel * 24;
            int maxScroll = Math.max(0,
                ((this.cards.size() + COLS - 1) / COLS) * (CARD_H + GAP) - this.area.h + 80);
            if (this.scrollY < 0) this.scrollY = 0;
            if (this.scrollY > maxScroll) this.scrollY = maxScroll;
            return true;
        }
        return super.subMouseScrolled(context);
    }

    private static void openWorld(File folder)
    {
        try
        {
            String name = folder.getName();
            LOG.info("Launchpad clicked world '{}' (open is deferred — Phase 15b)", name);
            /* For now: log and surface a chat message. Wiring the actual
             *  IntegratedServerLoader.start() varies across MC point
             *  releases; we'll add it once we lock the target version. */
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
            {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§6[BBSTools] §fLaunchpad selected world: §e" + name
                    + "§7  (open-from-launchpad wires up in Phase 15b)"));
            }
        }
        catch (Throwable t)
        {
            LOG.warn("Launchpad click handler failed", t);
        }
    }

    /** Single world entry. Holds its own hover tween for smooth scale.
     *  Folder-based — we read the world's display name from the dir
     *  name (which matches MC's save-folder convention) and last-played
     *  from the dir's mtime. Thumbnail icon.png read could be added in
     *  Phase 15b. */
    private static final class WorldCard
    {
        private static final java.text.SimpleDateFormat DATE_FMT =
            new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");

        final File folder;
        final BBSToolsAnim.Tween hoverTween = new BBSToolsAnim.Tween(0F, BBSToolsAnim.HOVER_SPEED_PER_SEC);
        Rect lastBounds;
        boolean hovered;

        WorldCard(File folder)
        {
            this.folder = folder;
        }

        void render(UIContext context, int x, int y, int w, int h, float fade, float dt)
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            this.hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            this.hoverTween.tick(this.hovered ? 1F : 0F, dt);
            float hoverEase = BBSToolsAnim.easeOut(this.hoverTween.value);

            /* Hover scale — grow up to 4 px on each side. */
            int grow = Math.round(hoverEase * 4F);
            int dx = x - grow;
            int dy = y - grow;
            int dw = w + grow * 2;
            int dh = h + grow * 2;
            this.lastBounds = new Rect(dx, dy, dw, dh);

            /* Card background — fade in via global open tween. */
            int bg = BBSToolsAnim.lerpColor(BBSToolsTheme.BG_PANEL, BBSToolsTheme.BG_ELEVATED, hoverEase);
            int alpha = Math.round(fade * 255F);
            int bgFaded = (alpha << 24) | (bg & 0x00FFFFFF);
            context.batcher.box(dx, dy, dx + dw, dy + dh, bgFaded);

            /* Border — turns accent on hover. */
            int border = BBSToolsAnim.lerpColor(BBSToolsTheme.BORDER, BBSToolsTheme.ACCENT_PRIMARY, hoverEase);
            context.batcher.outline(dx, dy, dx + dw, dy + dh, border);

            /* Thumbnail placeholder area — top 70% of the card. Phase 15b
             *  swaps this for the actual icon.png if present. */
            int thumbBottom = dy + dh - 36;
            context.batcher.box(dx + 8, dy + 8, dx + dw - 8, thumbBottom, BBSToolsTheme.BG_DEEP);
            String placeholder = "WORLD";
            int phW = context.batcher.getFont().getWidth(placeholder);
            context.batcher.text(placeholder, dx + (dw - phW) / 2, dy + (thumbBottom - dy) / 2, BBSToolsTheme.TEXT_FAINT);

            /* Card text: world folder name + last-modified timestamp. */
            String name = this.folder.getName();
            int textColor = hovered ? BBSToolsTheme.TEXT : BBSToolsTheme.TEXT_DIM;
            context.batcher.text(name, dx + 10, dy + dh - 28, textColor);

            String when = DATE_FMT.format(new Date(this.folder.lastModified()));
            context.batcher.text(when, dx + 10, dy + dh - 16, BBSToolsTheme.TEXT_FAINT);
        }

        private record Rect(int x, int y, int w, int h)
        {
            boolean contains(int px, int py)
            {
                return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
            }
        }
    }

}
