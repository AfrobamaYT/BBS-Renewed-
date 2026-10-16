package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;

/**
 * The model editor's sections that came with the BBS FS port, held in one place so the editor
 * panel, which may not grow, needs one hook for all of them.
 */
public class ModelEditorZusaetze
{
    public final ProzeduralAbschnitt prozedural;
    public final ShapeControllerAbschnitt formSchluessel;

    public ModelEditorZusaetze(UIModelEditorPanel panel)
    {
        this.prozedural = new ProzeduralAbschnitt(panel);
        this.formSchluessel = new ShapeControllerAbschnitt(panel);
    }

    public void fill(ModelConfig config, ModelInstance bound)
    {
        this.prozedural.fill(config, bound);
        this.formSchluessel.fill(config, bound);
    }
}
