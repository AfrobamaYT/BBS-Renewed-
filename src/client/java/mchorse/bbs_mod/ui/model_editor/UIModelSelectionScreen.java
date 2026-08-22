package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.dashboard.list.UIKachelListe;
import mchorse.bbs_mod.ui.dashboard.panels.UISelectionScreen;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.HashMap;
import java.util.Map;

/**
 * The Model Editor's landing screen — a searchable list of models to open. Models are assets, so the
 * create/duplicate/rename/remove actions are off; this is a pure picker.
 */
public class UIModelSelectionScreen extends UISelectionScreen<ModelConfig>
{
    /** Name -> Massstab. Die Huellbox aendert sich nicht, also einmal rechnen. */
    private static final Map<String, Float> MASSSTAEBE = new HashMap<>();

    public UIModelSelectionScreen(UIModelEditorPanel panel)
    {
        super(panel);

        /* Modelle sind das einzige, was BBS als Form rendern kann — Partikel, Bloecke und
         * Audio behalten das Typ-Symbol. Derselbe Aufruf wie im Replay-Menue. */
        if (this.namesList instanceof UIKachelListe kachel)
        {
            kachel.vorschau((pfad) ->
            {
                ModelForm form = new ModelForm();

                form.model.set(pfad.toString());

                return form;
            });

            kachel.auffrischen((form) ->
            {
                if (form instanceof ModelForm modell)
                {
                    modell.uiScale.set(massstab(modell.model.get()));
                }
            });
        }
    }

    /**
     * Passender UI-Massstab, damit jedes Modell die Kachel gleich gut fuellt.
     *
     * <p>⛔ Alle Modelle tragen {@code getUiScale() == 1.0} (gemessen) — der Groessen-
     * unterschied in der Vorschau kommt allein aus der Geometrie: {@code iktest} misst
     * 0,5 Bloecke, {@code rigneu_cat} 2,0. {@code getUIMatrix} skaliert mit
     * {@code (y2-y1)/2.5}, ein 2,5 Bloecke grosses Modell fuellt die Flaeche also genau.
     * Daher {@code 2.5 / groesste Ausdehnung}.</p>
     *
     * <p>Der vorhandene {@code CubicBoundingBoxRenderer.messen} hilft hier nicht: er nimmt
     * {@code Model}, die Modelle sind aber {@code BOBJModel} (Blender-Export). Deren Masse
     * stehen in {@code posData} — einmal gerechnet, dann gemerkt.</p>
     */
    private static float massstab(String name)
    {
        Float gemerkt = MASSSTAEBE.get(name);

        if (gemerkt != null)
        {
            return gemerkt;
        }

        float wert = 1F;

        try
        {
            ModelInstance instanz = BBSModClient.getModels().getModel(name);

            if (instanz != null && instanz.getModel() instanceof BOBJModel instanz_bobj)
            {
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                float[] spanne = new float[] {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                    -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};

                for (BOBJLoader.CompiledData daten : instanz_bobj.getMeshes())
                {
                    float[] pos = daten.posData;

                    if (pos == null)
                    {
                        continue;
                    }

                    for (int i = 0; i + 2 < pos.length; i += 3)
                    {
                        for (int a = 0; a < 3; a++)
                        {
                            spanne[a] = Math.min(spanne[a], pos[i + a]);
                            spanne[a + 3] = Math.max(spanne[a + 3], pos[i + a]);
                        }
                    }
                }

                float breite = spanne[3] - spanne[0];
                float hoehe = spanne[4] - spanne[1];
                float tiefe = spanne[5] - spanne[2];

                /* Aus getUIMatrix abgelesen, nicht geraten: 1 Block = (y2-y1)/2.5 Bildpunkte,
                 * und der Ursprung sitzt bei 85 % der Hoehe (Fuesse unten). Senkrecht passen
                 * also 0,85 * 2,5 = 2,125 Bloecke. Waagerecht dreht das Modell um Y, die
                 * Silhouette wird bis zu sqrt(b² + t²) breit; bei ungefaehr quadratischer
                 * Kachel sind das rund 2,75 Bloecke. Die engere Grenze gewinnt. */
                float diagonale = (float) Math.sqrt(breite * breite + tiefe * tiefe);
                float senkrecht = 2.125F / Math.max(0.001F, hoehe);
                float waagerecht = 2.75F / Math.max(0.001F, diagonale);
                float passend = Math.min(senkrecht, waagerecht) * 0.92F;

                if (hoehe > 0.001F && passend > 0.001F && passend < 1000F)
                {
                    wert = passend;
                }
            }
        }
        catch (Exception fehler)
        {
            /* Ein nicht messbares Modell bekommt Massstab 1 — das ist der Zustand von vorher,
             * kein Rueckschritt. Still verschluckt wird nichts: der Wert wird gemerkt. */
        }

        /* ⛔ Fehlschlag NICHT merken. Beim ersten Zugriff ist das Modell oft noch nicht
         * geladen; wer den Ausweichwert einträgt, friert ihn für immer ein — gemessen: 30
         * von 30 Einträgen standen auf 1.0. */
        if (wert != 1F)
        {
            MASSSTAEBE.put(name, wert);
        }

        return wert;
    }

    @Override
    protected Icon getFileIcon()
    {
        return Icons.POSE;
    }

    @Override
    protected boolean showActionButtons()
    {
        return false;
    }
}
