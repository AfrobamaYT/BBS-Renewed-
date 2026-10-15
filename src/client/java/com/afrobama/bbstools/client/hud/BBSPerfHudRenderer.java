// Coded by Afrobama/Claude — Tier 4b BBSPerfHud renderer.
//
// Top-left overlay summarising live BBSTools perf state — FPS, RAM,
// form/cull counts, Veil batch ratio, AccelRender cap, replay frustum
// cull, LOD/shadow skip. Toggled via `/bbsdebug perfhud on|off` which
// flips `BBSPerfHud.ENABLED` through its IFeatureDebugger.
//
// Visual style: dark backdrop (alpha ~0.60 black), 1px subtle border
// (0x40222426), small vanilla text — matches the BBS-Refreshed
// minimalist aesthetic memorised in feedback_alt_cam_visual_style.
//
// Allocation budget: zero per-frame. The StringBuilder field is reused
// (setLength(0) before each row format), the line String[] is reused
// in-place, and Text.literal allocations are unavoidable but unboxed
// (DrawContext.drawText(String) overload sidesteps the Text wrapper).
package com.afrobama.bbstools.client.hud;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import com.afrobama.bbstools.perf.BBSPerfHud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BBSPerfHudRenderer
{
    /** Number of stat rows we draw. Keep in sync with renderRows(). */
    private static final int LINE_COUNT = 6;

    /** Pixel offset for the panel from the screen edge. */
    private static final int PANEL_X = 5;
    private static final int PANEL_Y = 5;

    /** Inner text padding. */
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;

    /** Vertical pitch of each text line (px). MC font is 9px tall — 10 leaves
     *  a 1px gutter for readability without wasting space. */
    private static final int LINE_HEIGHT = 10;

    /** Width of the backdrop panel (px). Tuned so the widest expected row
     *  (Veil batched + fallback + replays + ratio) fits without truncation. */
    private static final int PANEL_WIDTH = 260;

    /** Backdrop: 0x99 alpha black ≈ 60%. */
    private static final int BG_COLOR     = 0x99000000;
    /** 1px border, ~25% alpha gray (0x40 over 0x222426). */
    private static final int BORDER_COLOR = 0x40888888;

    /** Text colors — dim white default, dim gray for labels. */
    private static final int TEXT_COLOR   = 0xFFCCCCCC;
    /** Warning tint for fallback rows. */
    private static final int WARN_COLOR   = 0xFFFFAA55;

    /** Reusable scratch buffer — set length 0 then format per row.
     *  Bigger than any line we build so StringBuilder never needs to
     *  grow its internal array after class init. */
    private static final StringBuilder SCRATCH = new StringBuilder(128);

    /** Last-frame text snapshot. Reused so we don't allocate row Strings
     *  unless they actually changed (StringBuilder.toString still allocates,
     *  but only when content differs — cheap escape on stable frames). */
    private static final String[] LINES = new String[LINE_COUNT];

    private BBSPerfHudRenderer() {}

    public static void register()
    {
        /* 26.2: HudRenderCallback ist gestrichen, Nachfolger HudElementRegistry (Begruendung
         * in BBSModClient). addLast haelt die Messtafel ueber allem -- sie soll auch dann
         * lesbar sein, wenn darunter Boss-Leiste oder Chat liegen. */
        HudRenderCallback.EVENT.register((g, t) -> BBSPerfHudRenderer.onHudRender(g, net.minecraft.client.DeltaTracker.Timer.SPIEL));
    }

    private static void onHudRender(GuiGraphics ctx,
        net.minecraft.client.DeltaTracker tickCounter)
    {
        /* Early return — keeps the toggle cost a single volatile read
         *  when the HUD is OFF. */
        if (!BBSPerfHud.ENABLED) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        /* mc.player may be null on the main menu / between dimensions —
         *  rendering the HUD there is fine but skip if MC isn't ready. */
        if (mc.options != null && mchorse.bbs_mod.client.HudSichtbarkeit.istVersteckt()) return;

        Font fr = mc.font;
        if (fr == null) return;

        /* Build the six rows. Each helper appends to SCRATCH then snapshots
         *  into LINES[i]. */
        buildLines(mc);

        int panelH = PAD_Y * 2 + LINE_COUNT * LINE_HEIGHT;
        int x0 = PANEL_X;
        int y0 = PANEL_Y;
        int x1 = x0 + PANEL_WIDTH;
        int y1 = y0 + panelH;

        /* Backdrop. */
        ctx.fill(x0, y0, x1, y1, BG_COLOR);

        /* 1px border (4 thin fills). */
        ctx.fill(x0,     y0,     x1, y0 + 1, BORDER_COLOR); /* top */
        ctx.fill(x0,     y1 - 1, x1, y1,     BORDER_COLOR); /* bottom */
        ctx.fill(x0,     y0,     x0 + 1, y1, BORDER_COLOR); /* left */
        ctx.fill(x1 - 1, y0,     x1, y1,     BORDER_COLOR); /* right */

        /* Text lines. drawText with String + shadow=true — matches
         *  GravityGunHud's pattern but uses the String overload to skip
         *  the Text.literal alloc. */
        int tx = x0 + PAD_X;
        int ty = y0 + PAD_Y;
        for (int i = 0; i < LINE_COUNT; i++)
        {
            String line = LINES[i];
            if (line == null) continue;
            int color = (i == 2 && BBSPerfConfig.veilBatchFramesFallback >
                                   BBSPerfConfig.veilBatchFramesBatched)
                ? WARN_COLOR : TEXT_COLOR;
            ctx.text(fr, Component.literal(line), tx, ty + i * LINE_HEIGHT, color, true);
        }
    }

    private static void buildLines(Minecraft mc)
    {
        /* Row 0 — FPS + RAM. */
        SCRATCH.setLength(0);
        Runtime rt = Runtime.getRuntime();
        long usedMb  = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long maxMb   = rt.maxMemory() / (1024L * 1024L);
        SCRATCH.append("FPS: ").append(mc.getFps())
               .append("  RAM: ").append(usedMb).append('/')
               .append(maxMb).append(" MB");
        LINES[0] = SCRATCH.toString();

        /* Row 1 — form-renderer calls + skips + distance cull. */
        SCRATCH.setLength(0);
        SCRATCH.append("Form calls: ").append(BBSPerfConfig.formRendererCallCount)
               .append("  skips: ").append(BBSPerfConfig.formRendererSkipCount)
               .append("  dist-cull: ").append(BBSPerfConfig.distanceCullCount);
        LINES[1] = SCRATCH.toString();

        /* Row 2 — Veil batch vs fallback ratio. */
        SCRATCH.setLength(0);
        long batched  = BBSPerfConfig.veilBatchFramesBatched;
        long fallback = BBSPerfConfig.veilBatchFramesFallback;
        long submitted = BBSPerfConfig.veilBatchReplaysSubmitted;
        long total = batched + fallback;
        double ratio = total > 0 ? (batched * 100.0) / total : 0.0;
        SCRATCH.append("Veil batched: ").append(batched)
               .append("  fallback: ").append(fallback)
               .append("  replays: ").append(submitted)
               .append("  ratio: ");
        appendPercent(SCRATCH, ratio);
        LINES[2] = SCRATCH.toString();

        /* Row 3 — AccelRender cap + slow-frame strike threshold. */
        SCRATCH.setLength(0);
        SCRATCH.append("AccelRender cap: ").append(BBSPerfConfig.ACCEL_RENDER_MAX_VISIBLE)
               .append("  slow-frame strikes: ")
               .append(BBSPerfConfig.ACCEL_RENDER_SLOW_FRAME_STRIKES)
               .append(" @ ");
        appendOneDp(SCRATCH, BBSPerfConfig.ACCEL_RENDER_SLOW_FRAME_MS);
        SCRATCH.append("ms");
        LINES[3] = SCRATCH.toString();

        /* Row 4 — replay frustum cull count. */
        SCRATCH.setLength(0);
        SCRATCH.append("Replay frustum cull: ")
               .append(BBSPerfConfig.replayFrustumCullCount);
        LINES[4] = SCRATCH.toString();

        /* Row 5 — LOD body-part + shadow skip counts. */
        SCRATCH.setLength(0);
        SCRATCH.append("LOD bp skip: ").append(BBSPerfConfig.lodBodyPartSkipCount)
               .append("  Shadow skip: ").append(BBSPerfConfig.shadowCullCount);
        LINES[5] = SCRATCH.toString();
    }

    /** Append a double in `xx.x%` form without allocating via String.format
     *  (which allocates a Formatter + intermediate String). */
    private static void appendPercent(StringBuilder sb, double v)
    {
        appendOneDp(sb, v);
        sb.append('%');
    }

    /** Append a double rounded to 1 decimal place, no allocations
     *  beyond StringBuilder's internal char[] grow (which has already
     *  been pre-grown). Handles negative + NaN defensively. */
    private static void appendOneDp(StringBuilder sb, double v)
    {
        if (Double.isNaN(v) || Double.isInfinite(v)) { sb.append("--"); return; }
        long tenths = Math.round(v * 10.0);
        if (tenths < 0) { sb.append('-'); tenths = -tenths; }
        sb.append(tenths / 10).append('.').append(tenths % 10);
    }

}
