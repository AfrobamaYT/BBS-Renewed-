package com.afrobama.blendertransform;

import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.sections.ExtraFormSection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logik des "Camera in den form-picker injizieren"-mixins — herausgelöst aus
 * {@code FormCategoriesAddCameraMixin} (2026-07-26), damit sie hotswapbar ist.
 *
 * <p>Fügt einen "Camera"-eintrag in BBS' form-picker ein, damit der user direkt einen
 * kamera-artigen replay im picker wählen kann — statt des mehrschrittigen manuellen wegs
 * (replay hinzufügen → actor abwählen → form-picker ohne auswahl schliessen).</p>
 *
 * <p>Implementierung: am TAIL von {@code FormCategories#setup()} wird die "extra"-kategorie
 * aus {@link ExtraFormSection} geholt und ein {@link AnchorForm} mit name="Camera" angehängt.
 * AnchorForm hat kein sichtbares mesh, also rendert ein replay damit nichts — genau das, was
 * ein kamera-replay tun soll. Die begleitende änderung in
 * {@code EditorCamHandler.isActorReplay} behandelt jeden AnchorForm-replay als kamera-replay,
 * damit F8 ihn aufgreift.</p>
 *
 * <p>BBS klont das gewählte form via {@code Form.copy()} in das eigene form-feld des replays;
 * die hier hinzugefügte instanz bleibt als vorlage in der kategorie-liste. Lebt also einmal
 * pro JVM.</p>
 */
public final class FormCategoriesAddCameraLogik
{
    private FormCategoriesAddCameraLogik() {}

    private static final Logger BBSTOOLS_CAM_LOG = LoggerFactory.getLogger("bbstools/form-camera");
    private static boolean cameraInjected;

    public static void injectCameraIntoExtraCategory(FormCategories self)
    {
        try
        {
            ExtraFormSection extras = self.getExtraForms();
            if (extras == null) return;

            FormCategory extra = extras.getExtraCategory();
            if (extra == null) return;

            /* Schon-hinzugefügt-check — setup() läuft jedesmal, wenn der user den form-picker
             * öffnet, aber wir wollen nur einmal pro JVM injizieren. Ohne diese wache würden
             * wir bei jedem öffnen eine neue Camera anhängen und einen langen scroll voller
             * duplikate erzeugen. */
            for (Object existing : extra.getDirectForms())
            {
                if (existing instanceof AnchorForm a
                    && a.name != null
                    && "Camera".equals(a.name.get()))
                {
                    cameraInjected = true;
                    return;
                }
            }

            AnchorForm cameraTemplate = new AnchorForm();
            cameraTemplate.name.set("Camera");
            extra.addForm(cameraTemplate);
            cameraInjected = true;
            BBSTOOLS_CAM_LOG.info("Injected 'Camera' AnchorForm template into form picker's Extra category");
        }
        catch (Throwable t)
        {
            BBSTOOLS_CAM_LOG.warn("Failed to inject Camera form into picker: {}", t.toString());
        }
    }
}
