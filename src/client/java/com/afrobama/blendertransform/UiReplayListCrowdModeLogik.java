// Coded by Afrobama/Claude — kernel-logik für UIReplayListCrowdModeMixin.
//
// Tier 1d UIReplayList crowdMode. Wenn BBSPerfConfig.UI_REPLAY_LIST_CROWD_MODE aktiv ist:
//   1. Bei HEAD von UIReplayList.render wird die pro-frame-aggregation geleert.
//   2. Bei HEAD von renderElementPart wird der actor-type-name der zeile (Form.getDisplayName)
//      in die geteilte map gebucht und das vanilla-render der zeile abgebrochen — spart sowohl
//      die einrück/label-arbeit ALS AUCH den teuren pro-zeile FormUtilsClient.renderUI-thumbnail-
//      bake am rechten rand.
//   3. Bei TAIL von UIReplayList.render wird ein kompakter streifen gezeichnet, der jeden
//      distinkten typ mit seiner zeilenzahl auflistet, damit der nutzer die population trotzdem
//      auf einen blick sieht.
//
// Geteilter zustand lebt weiter in UIReplayCrowdModeState (pro der mixin-no-public-static-regel).
package com.afrobama.blendertransform;

import java.util.Map;

import com.afrobama.bbstools.client.perf.UIReplayCrowdModeState;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.replays.UIReplayList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;

public final class UiReplayListCrowdModeLogik
{
    private UiReplayListCrowdModeLogik() {}

    /** HEAD von render: pro-frame-aggregation zurücksetzen. Muss vor allen
     *  renderElementPart-aufrufen dieses frames passieren. */
    public static void crowdModeBeginFrame()
    {
        if (UIReplayCrowdModeState.isEnabled())
        {
            UIReplayCrowdModeState.beginFrame();
        }
    }

    /** HEAD von renderElementPart: im crowd-mode das actor-type-label der zeile aggregieren
     *  und das vanilla-render ABBRECHEN.
     *
     * @return true wenn der handler ci.cancel() aufrufen soll */
    public static boolean crowdModeAggregateRow(Replay element)
    {
        if (!UIReplayCrowdModeState.isEnabled()) return false;
        if (element == null) return false;

        try
        {
            String typeName = null;
            try
            {
                Form form = (Form) element.form.get();
                if (form != null) typeName = form.getDisplayName();
            }
            catch (Throwable ignored)
            {
                /* Fall through to defaults below. */
            }
            if (typeName == null || typeName.isEmpty())
            {
                try { typeName = element.getName(); }
                catch (Throwable ignored) { typeName = "(unknown)"; }
            }
            UIReplayCrowdModeState.recordAndSkip(typeName);
            return true;
        }
        catch (Throwable t)
        {
            /* Bei jedem unerwarteten fehler NICHT abbrechen — der vanilla-pfad soll rendern,
             * damit die liste auch bei kaputtem crowd-mode nutzbar bleibt. */
            return false;
        }
    }

    /** TAIL von render: den kompakten crowd-mode-streifen zeichnen — jeder aggregierte typ
     *  + zähler, plus ein dezenter hinweis-footer. */
    public static void crowdModeDrawStrip(UIReplayList self, UIContext context)
    {
        if (!UIReplayCrowdModeState.isEnabled()) return;
        if (UIReplayCrowdModeState.distinctTypes() == 0) return;

        try
        {
            FontRenderer font = context.batcher.getFont();
            int fh = font.getHeight();
            int x = self.area.x + 4;
            int y = self.area.y + 4;
            int w = self.area.w - 8;

            Map<String, Integer> snapshot = UIReplayCrowdModeState.snapshot();
            int rows = snapshot.size();
            int boxH = (rows * (fh + 4)) + (fh + 6) + 8;
            if (boxH > self.area.h - 8) boxH = self.area.h - 8;

            /* Dark background, BBS-Refreshed minimalist styling. */
            context.batcher.box(x, y, x + w, y + boxH, 0xCC121212);
            /* 1px subtle top accent border. */
            context.batcher.box(x, y, x + w, y + 1, 0x66FFFFFF);

            /* Header. */
            int header = 0xFFCCDDFF;
            String headerStr = "CROWD MODE — " + UIReplayCrowdModeState.totalRowsAggregated()
                + " replays / " + rows + " types";
            context.batcher.text(headerStr, x + 6, y + 4, header, false);

            int ry = y + 6 + fh + 4;
            int rowColor = 0xFFE0E0E0;
            int countColor = 0xFF88CCFF;
            for (Map.Entry<String, Integer> e : snapshot.entrySet())
            {
                if (ry + fh > y + boxH - 2) break;
                String label = e.getKey();
                int count = e.getValue();
                /* Truncate the label so the count column always fits. */
                String countStr = "× " + count;
                int countW = font.getWidth(countStr);
                int avail = w - 12 - countW - 8;
                if (avail < 16) avail = 16;
                String shown = font.limitToWidth(label, avail);
                context.batcher.text(shown, x + 6, ry, rowColor, false);
                context.batcher.text(countStr, x + w - 6 - countW, ry, countColor, false);
                ry += fh + 4;
            }

            /* Footer hint. */
            String hint = "/bbstools ui crowdmode off";
            int hintW = font.getWidth(hint);
            int hintX = x + w - hintW - 6;
            int hintY = y + boxH - fh - 4;
            if (hintY > ry)
            {
                context.batcher.text(hint, hintX, hintY, 0x88888888, false);
            }

            UIReplayCrowdModeState.noteRendered();
        }
        catch (Throwable ignored)
        {
            /* Never throw out of a render injection. */
        }
    }
}
