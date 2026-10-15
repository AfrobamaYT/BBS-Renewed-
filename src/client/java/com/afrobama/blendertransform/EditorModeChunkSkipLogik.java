package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.editormode.EditorModeClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik des editor-mode-chunk-skip-mixins — herausgelöst aus
 * {@code EditorModeChunkSkipMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>Wenn editor-mode aktiv ist und BBS gerade KEINEN video-export rendert, wird der aufruf
 * abgebrochen — dann zeichnet {@code WorldRenderer#renderLayer} keine terrain-chunks. Der
 * automatische self-disable während eines BBS-exports ({@link
 * EditorModeClient#shouldBypassWorld()}) sorgt dafür, dass das exportierte video weiterhin
 * alle chunks enthält — nur die live-vorschau verliert sie.</p>
 */
public final class EditorModeChunkSkipLogik
{
    private EditorModeChunkSkipLogik() {}

    private static final Logger LOG = LoggerFactory.getLogger("bbstools/editor-mode/chunk-skip");
    private static boolean loggedFirstSkip;
    private static boolean loggedFirstAllow;

    /**
     * @return {@code true}, wenn der handler den original-aufruf abbrechen soll
     */
    public static boolean shouldSkipChunks()
    {
        if (EditorModeClient.shouldBypassWorld())
        {
            if (!loggedFirstSkip)
            {
                loggedFirstSkip = true;
                LOG.info("Chunk-render skip ENGAGED (editor-mode active, BBS export inactive)");
            }
            return true;
        }
        else if (!loggedFirstAllow && com.afrobama.bbstools.editormode.EditorMode.ACTIVE)
        {
            loggedFirstAllow = true;
            LOG.info("Chunk-render ALLOWED (editor-mode active, BBS export running)");
        }

        return false;
    }
}
