package mchorse.bbs_mod.ui.forms.editors.panels.widgets.states;

import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.properties.ActionsConfigProperty;
import mchorse.bbs_mod.forms.properties.BlockStateProperty;
import mchorse.bbs_mod.forms.properties.BooleanProperty;
import mchorse.bbs_mod.forms.properties.ColorProperty;
import mchorse.bbs_mod.forms.properties.FloatProperty;
import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.forms.properties.IntegerProperty;
import mchorse.bbs_mod.forms.properties.ItemStackProperty;
import mchorse.bbs_mod.forms.properties.LinkProperty;
import mchorse.bbs_mod.forms.properties.StringProperty;
import mchorse.bbs_mod.forms.properties.TransformProperty;
import mchorse.bbs_mod.forms.properties.Vector4fProperty;
import mchorse.bbs_mod.forms.triggers.StateTrigger;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIActionsConfigPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIBlockStatePropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIBooleanPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIColorPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIFloatPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIFormPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIIntegerPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIItemStackPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UILinkPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIStringPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UITransformPropertyEditor;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.states.properties.UIVector4fPropertyEditor;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UIFormStatesOverlayPanel extends UIOverlayPanel
{
    private static Map<Class, IFormEditorFactory> FACTORIES = new HashMap<>();

    public UISearchList<String> states;
    public UIScrollView editor;

    private ModelForm modelForm;
    private StateTrigger trigger;
    private List<String> properties;

    static
    {
        register(ActionsConfigProperty.class, UIActionsConfigPropertyEditor::new);
        register(BlockStateProperty.class, UIBlockStatePropertyEditor::new);
        register(BooleanProperty.class, UIBooleanPropertyEditor::new);
        register(ColorProperty.class, UIColorPropertyEditor::new);
        register(FloatProperty.class, UIFloatPropertyEditor::new);
        register(IntegerProperty.class, UIIntegerPropertyEditor::new);
        register(ItemStackProperty.class, UIItemStackPropertyEditor::new);
        register(LinkProperty.class, UILinkPropertyEditor::new);
        register(StringProperty.class, UIStringPropertyEditor::new);
        register(TransformProperty.class, UITransformPropertyEditor::new);
        register(Vector4fProperty.class, UIVector4fPropertyEditor::new);
    }

    private static <V, T extends IFormProperty<V>> void register(Class<T> clazz, IFormEditorFactory<V, T> factory)
    {
        FACTORIES.put(clazz, factory);
    }

    public UIFormStatesOverlayPanel(ModelForm form, StateTrigger trigger)
    {
        super(UIKeys.STATE_TRIGGERS_TITLE);

        this.modelForm = form;
        this.trigger = trigger;
        this.properties = FormUtils.collectPropertyPaths(form);

        Iterator<String> it = this.properties.iterator();

        while (it.hasNext())
        {
            String next = it.next();
            IFormProperty property = FormUtils.getProperty(form, next);

            if (!FACTORIES.containsKey(property.getClass()))
            {
                it.remove();
            }
        }

        this.states = new UISearchList<>(new UIStringList((l) -> this.openState(l.get(0))));
        this.states.label(UIKeys.GENERAL_SEARCH);
        this.states.list.background().add(this.properties);
        this.states.relative(this.content).xy(6, 6).w(120).hTo(this.content.area, 1F, -6);

        this.content.add(this.states, this.editor);
    }

    private void openState(String property)
    {
        IFormProperty formProperty = FormUtils.getProperty(this.modelForm, property);

        if (this.editor != null)
        {
            this.editor.removeFromParent();
            this.editor = null;
        }

        if (formProperty != null)
        {
            try
            {
                IFormEditorFactory factory = FACTORIES.get(formProperty.getClass());
                UIFormPropertyEditor editor = factory.create(this.modelForm, this.trigger, property, formProperty);

                this.editor = UI.scrollView(5, editor);
                this.editor.relative(this.states).x(1F, 5).wTo(this.content.area, 1F, -6).h(1F);

                this.content.add(this.editor);
                this.resize();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    private static interface IFormEditorFactory <V, T extends IFormProperty<V>>
    {
        public UIFormPropertyEditor<V, T> create(ModelForm modelForm, StateTrigger trigger, String id, T property);
    }
}