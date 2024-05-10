package mchorse.bbs_mod.forms.categories;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.CubicModel;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModelFormCategory extends FormCategory
{
    public ModelFormCategory()
    {
        super(UIKeys.FORMS_CATEGORIES_MODELS);
    }

    @Override
    public void update()
    {
        super.update();

        this.forms.clear();

        List<Link> models = new ArrayList<>(BBSMod.getProvider().getLinksFromPath(Link.assets("models"), false));

        models.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));

        for (Link link : models)
        {
            DataPath dataPath = new DataPath(link.path);

            if (!dataPath.folder)
            {
                continue;
            }

            ModelForm form = new ModelForm();

            form.model.set(dataPath.getLast());
            this.forms.add(form);
        }
    }

    @Override
    public UIFormCategory createUI(UIFormList list)
    {
        UIFormCategory category = super.createUI(list);

        category.context((menu) ->
        {
            if (category.selected == null)
            {
                return;
            }

            menu.action(Icons.FOLDER, UIKeys.FORMS_CATEGORIES_CONTEXT_OPEN_MODEL_FOLDER, () ->
            {
                ModelForm form = (ModelForm) category.selected;

                UIUtils.openFolder(BBSMod.getAssetsPath("models/" + form.model.get() + "/"));
            });

            menu.action(Icons.UPLOAD, UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL, () ->
            {
                ModelForm modelForm = (ModelForm) category.selected;
                CubicModel model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    MapType map = CubicLoader.toData(model);

                    try
                    {
                        File path = BBSMod.getAssetsPath("models/" + modelForm.model.get() + "/exported._bbs.json");

                        IOUtils.writeText(path, DataToString.toString(map, true));

                        UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_TITLE,
                            UIKeys.FORMS_CATEGORIES_CONTEXT_EXPORT_MODEL_DESCRIPTION,
                            path.getParentFile()
                        );

                        UIOverlay.addOverlay(category.getContext(), overlayPanel);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            });
        });

        return category;
    }
}