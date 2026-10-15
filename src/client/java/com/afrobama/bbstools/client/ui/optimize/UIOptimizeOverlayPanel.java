package com.afrobama.bbstools.client.ui.optimize;

import com.afrobama.bbstools.sonden.Messregister;
import com.afrobama.bbstools.config.BBSToolsConfig;
import com.afrobama.bbstools.debug.IFeatureDebugger;

import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Optimize — die render-optimierungen an- und ausschalten, ohne neustart.
 *
 * <p><b>Warum es das gibt.</b> Bis 2026-07-28 schaltete
 * {@code LegacyRendererDebuggers.flipAllToEnabledExceptUnsafe()} beim client-start JEDEN
 * registrierten debugger an, der nicht auf einer kleinen verbotsliste stand. Darunter sind
 * eingriffe, die direkt ins bild greifen. CML hatte dafuer oben im dashboard ein
 * "optimize"-dropdown; beim CML-cleanup flog das UI raus, die flags und ihre mixins
 * blieben. Ergebnis: alles war scharf, und niemand konnte es mehr abschalten.</p>
 *
 * <p>Der start-zustand kommt jetzt aus {@code StartPositivliste} (nur genannte namen sind
 * aktiv). Dieses menue bleibt der weg, die render-schalter waehrend der arbeit umzulegen —
 * und ist der einzige, der die wahl ueber den start hinaus behaelt.</p>
 *
 * <p>Konkret gemessen (2026-07-27): {@code paused-bone-cache} liess den mesh-rebuild
 * ausfallen, solange sein pose-digest gleich blieb — die replays flackerten zwischen
 * altem und neuem stand, auch bei pausiertem playback.</p>
 *
 * <p><b>Warum nur ~20 von 81 schaltern hier stehen.</b> Der rest sind diagnose-werkzeuge
 * (heap-analyzer, net-trace, flamegraph), die beim filmen nichts bewirken. Sie bleiben
 * ueber {@code /bbsdebug <name> on|off} erreichbar. Diese liste zeigt, was das BILD
 * veraendern kann — nichts anderes gehoert in ein menue, das man im zweifel benutzt,
 * weil etwas kaputt aussieht.</p>
 *
 * <p>Die wahl landet in {@code config/bbstools/config.json} und wird beim naechsten start
 * NACH dem start-zustand angewendet ({@code StartPositivliste.anwenden}). Wer vorher setzt,
 * wird ueberschrieben — genau daran starb die CML-loesung.</p>
 */
public class UIOptimizeOverlayPanel extends UIOverlayPanel
{
    /**
     * Die schalter, die ins BILD greifen — name in der registry → was er tut.
     *
     * <p>Reihenfolge ist absicht: oben, was am haeufigsten schuld ist, wenn etwas
     * flackert oder fehlt. Wer einen schalter ergaenzt, schreibt dazu, was der nutzer
     * SIEHT, wenn er falsch steht — nicht, was der code tut.</p>
     */
    private static final Map<String, String> RENDER_SCHALTER = new LinkedHashMap<>();

    static
    {
        RENDER_SCHALTER.put("paused-bone-cache",
            "Ueberspringt den mesh-neubau bei pause. Flackernde replays -> AUS.");
        RENDER_SCHALTER.put("static-paused",
            "Zeichnet pausierte replays aus einem batch. Haengt an paused-bone-cache.");
        RENDER_SCHALTER.put("paused-cache",
            "Cached matrizen bei pause. Falsche posen im standbild -> AUS.");
        RENDER_SCHALTER.put("frustum-cull",
            "Blendet replays ausserhalb der kamera aus. Fehlende actors -> AUS.");
        RENDER_SCHALTER.put("replay-fast-path",
            "Kuerzerer render-pfad fuer replays.");
        RENDER_SCHALTER.put("parallel-animator",
            "Rechnet posen auf mehreren threads. Zuckende actors -> AUS.");
        RENDER_SCHALTER.put("async-mesh",
            "Baut meshes nebenlaeufig. Kurz fehlende modelle -> AUS.");
        RENDER_SCHALTER.put("shadow-skip",
            "Laesst schatten-durchgaenge aus. Fehlende schatten -> AUS.");
        RENDER_SCHALTER.put("crowd-draw",
            "Sammel-zeichnen fuer crowds.");
        RENDER_SCHALTER.put("crowdgpu",
            "Crowd-posen auf der GPU.");
        RENDER_SCHALTER.put("occluder",
            "Verdeckte objekte weglassen. Fehlende objekte -> AUS.");
        RENDER_SCHALTER.put("mipmap",
            "Kleinere texturen in der ferne. Flimmernde texturen -> AN.");
        RENDER_SCHALTER.put("bindless",
            "Bindless-texturen. Falsche/schwarze texturen -> AUS.");
        RENDER_SCHALTER.put("atlas",
            "Texturen in einem atlas buendeln.");
        RENDER_SCHALTER.put("upload-thread",
            "GPU-uploads auf eigenem thread.");
        RENDER_SCHALTER.put("worker-pool",
            "Hintergrund-threads fuer render-arbeit.");
        RENDER_SCHALTER.put("world-gpu",
            "Welt-geometrie auf der GPU halten.");
        RENDER_SCHALTER.put("accelrender",
            "Beschleunigter renderer (AcceleratedRendering).");
        RENDER_SCHALTER.put("molang",
            "Molang-ausdruecke cachen. Stehende animationen -> AUS.");
        RENDER_SCHALTER.put("keyframes",
            "Keyframe-auswertung optimieren. Falsche werte -> AUS.");
        RENDER_SCHALTER.put("park-reapply",
            "Geparkte replays neu anwenden.");
    }

    private final List<UIToggle> schalter = new ArrayList<>();
    private final Map<UIToggle, String> namen = new LinkedHashMap<>();

    public final UIButton allesSicher;
    public final UIButton allesAn;
    public final UIScrollView liste;

    public UIOptimizeOverlayPanel()
    {
        super(IKey.constant("Optimize"));

        this.allesSicher = new UIButton(IKey.constant("Alles sicher"), (b) -> this.alleSetzen(false));
        this.allesSicher.tooltip(IKey.constant(
            "Schaltet alle render-eingriffe ab. Kostet FPS, aber das bild stimmt.\n"
            + "Erster griff, wenn etwas flackert oder fehlt."));

        this.allesAn = new UIButton(IKey.constant("Alles an"), (b) -> this.alleSetzen(true));
        this.allesAn.tooltip(IKey.constant(
            "Schaltet alle optimierungen an — der zustand, den der start-sweep herstellt."));

        this.liste = UI.scrollView(3, 4);

        for (Map.Entry<String, String> eintrag : RENDER_SCHALTER.entrySet())
        {
            IFeatureDebugger feature = sucheFeature(eintrag.getKey());

            if (feature == null)
            {
                /* Feature nicht registriert (anderer build/mod-satz) — zeile weglassen,
                 * statt einen schalter zu zeigen, der ins leere greift. */
                continue;
            }

            UIToggle toggle = new UIToggle(IKey.constant(eintrag.getKey()), (t) ->
                this.schalte(eintrag.getKey(), t.getValue()));

            toggle.setValue(sicherAktiv(feature));
            toggle.h(UIConstants.CONTROL_HEIGHT);
            toggle.tooltip(IKey.constant(eintrag.getValue()));

            this.schalter.add(toggle);
            this.namen.put(toggle, eintrag.getKey());
            this.liste.add(toggle);
        }

        this.allesSicher.relative(this.content).x(6).y(6).w(0.5F, -9).h(UIConstants.CONTROL_HEIGHT);
        this.allesAn.relative(this.content).x(0.5F, 3).y(6).w(0.5F, -9).h(UIConstants.CONTROL_HEIGHT);
        this.liste.relative(this.content)
            .x(6).y(6 + UIConstants.CONTROL_HEIGHT + 6)
            .w(1F, -12).h(1F, -(6 + UIConstants.CONTROL_HEIGHT + 12));

        this.content.add(this.allesSicher, this.allesAn, this.liste);
    }

    /** Feature mit diesem namen aus der registry, oder {@code null}. */
    private static IFeatureDebugger sucheFeature(String name)
    {
        for (IFeatureDebugger f : Messregister.HALTER.aktiv.alle())
        {
            if (name.equals(f.getName()))
            {
                return f;
            }
        }

        return null;
    }

    /**
     * {@code isEnabled()} defensiv lesen.
     *
     * <p>Manche debugger fragen beim status GL-zustand oder fremde mods ab und werfen,
     * wenn die gerade nicht da sind. Ein einzelner solcher schalter darf nicht das ganze
     * menue verhindern — im zweifel als AUS zeigen.</p>
     */
    private static boolean sicherAktiv(IFeatureDebugger f)
    {
        try
        {
            return f.isEnabled();
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /** Einen schalter setzen, merken und sofort speichern. */
    private void schalte(String name, boolean an)
    {
        IFeatureDebugger feature = sucheFeature(name);

        if (feature != null)
        {
            try
            {
                feature.setEnabled(an);
            }
            catch (Throwable t)
            {
                /* Kaputter schalter darf das menue nicht mitreissen — der toggle steht
                 * dann sichtbar anders als die wirkung, was ehrlicher ist als ein crash. */
            }
        }

        BBSToolsConfig.merkeFeature(name, an);
        BBSToolsConfig.save();
    }

    /** Alle schalter der liste auf einen wert setzen (preset-knoepfe). */
    private void alleSetzen(boolean an)
    {
        for (UIToggle toggle : this.schalter)
        {
            String name = this.namen.get(toggle);

            toggle.setValue(an);

            IFeatureDebugger feature = sucheFeature(name);

            if (feature != null)
            {
                try
                {
                    feature.setEnabled(an);
                }
                catch (Throwable t)
                {
                    /* siehe schalte() */
                }
            }

            BBSToolsConfig.merkeFeature(name, an);
        }

        /* Einmal am ende schreiben statt 21-mal. */
        BBSToolsConfig.save();
    }
}
