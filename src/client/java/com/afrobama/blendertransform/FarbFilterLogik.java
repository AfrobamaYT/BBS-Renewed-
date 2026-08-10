package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.ui.theme.BBSToolsTheme;
import com.afrobama.bbstools.client.ui.theme.BBSToolsTransparency;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Logik hinter zwei Mixins, die FARBEN IM DURCHLAUF umbiegen —
 * {@code Batcher2DBackgroundAlphaMixin} (Oberflaechen-Hintergrund) und
 * {@code sodium.ColorAttributeMixin} (Entity-Vertices unter Sodium).
 *
 * <p>Beide sind {@code @ModifyVariable} auf einen Farbwert und beantworten dieselbe Frage:
 * bleibt die Farbe, oder wird sie ersetzt? Sie stehen deshalb zusammen.</p>
 *
 * <p>⚠️ BEIDE LAUFEN SEHR OFT — je gezeichneter Flaeche beziehungsweise je Vertex. Die
 * Abbruchbedingung ganz vorne ist kein Beiwerk, sondern der Grund, warum das im Normalfall
 * nichts kostet: ohne aktives Umfaerben ist es ein Vergleich und ein {@code return}.</p>
 */
public final class FarbFilterLogik
{
    private FarbFilterLogik() {}

    /**
     * {@code Batcher2D.box} — Panel-Hintergruende durchscheinend machen.
     *
     * <p>⚠️ NUR HINTERGRUENDE. {@code looksLikeBackground} trennt Flaechen von Text und
     * Symbolen; ohne die Pruefung wuerde die Durchsichtigkeit auch Schrift erfassen.</p>
     *
     * @return die (evtl. veraenderte) Farbe — nie {@code null}, der Aufrufer setzt sie direkt
     */
    public static int hintergrundAlpha(int farbe)
    {
        if (BBSToolsTransparency.PANEL_BG_ALPHA >= 0.999F)
        {
            return farbe;
        }

        if (!BBSToolsTheme.looksLikeBackground(farbe))
        {
            return farbe;
        }

        return BBSToolsTheme.withAlpha(farbe, BBSToolsTransparency.PANEL_BG_ALPHA);
    }

    /**
     * {@code ColorAttribute.set} (Sodium) — die Replay-Faerbung in den Vertex-Strom legen.
     *
     * <p>Gegenstueck im Iris-Pfad: {@code iris.EntityVertexMixin}, gleiche Rechnung.</p>
     */
    public static int vertexFarbe(int farbe)
    {
        if (RecolorVertexConsumer.newColor == null)
        {
            return farbe;
        }

        Colors.COLOR.set(farbe);
        Colors.COLOR.mul(RecolorVertexConsumer.newColor);

        return Colors.COLOR.getARGBColor();
    }
}
