package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.config.ModelConfig;
import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers;

import java.util.List;

import static mchorse.bbs_mod.ui.utils.shapes.UIShapeControllers.key;

/**
 * The model editor's "Shape key controllers" section, where the model's author makes them: a
 * controller has a name, a bone and a position for its handle, and bindings, each a shape key with
 * a gain on height and on tilt. The animator's controls sit above, driving the editor's preview.
 * Saved and undone with the rest of config.json. From BBS FS 7a049804a (Wemppy4/bbs-fs, MIT); FS has
 * it in its newer model editor, with a placing gizmo this one does not have.
 */
public class ShapeControllerAbschnitt
{
    public final UISection section;

    private final UIModelEditorPanel panel;
    private final UIElement body;
    private final UIShapeControllers controls;
    private final UIElement settings = new UIElement();
    private final UIList<ShapeController.Binding> bindings;
    private final UIElement bindingSettings = new UIElement();
    private ModelConfig config;
    private ModelInstance bound;

    public ShapeControllerAbschnitt(UIModelEditorPanel panel)
    {
        this.panel = panel;
        this.section = panel.section(key("title"), false);
        this.body = panel.body();
        this.section.fields.add(this.body);

        /* The preview: the editor's own form wears the shape keys the controls set */
        this.controls = new UIShapeControllers((edit) ->
        {
            ShapeKeys keys = panel.form.shapeKeys.get();

            edit.accept(keys);
            panel.form.shapeKeys.set(keys);
        });
        this.controls.selectionChanged = this::fillSettings;
        this.controls.presets(() -> this.bound == null ? "" : this.bound.getPoseGroup());

        this.bindings = new UIList<>((picked) -> this.fillBinding())
        {
            @Override
            protected String elementToString(UIContext context, int i, ShapeController.Binding element)
            {
                return element.shape.get();
            }
        };
        this.bindings.background();
        this.bindings.h(16 * 4);
        this.settings.column(4).vertical().stretch();
        this.bindingSettings.column(4).vertical().stretch();
    }

    public void fill(ModelConfig config, ModelInstance bound)
    {
        this.body.removeAll();
        this.config = config;
        this.bound = bound;

        if (config == null || bound == null || bound.model.getShapeKeys().isEmpty())
        {
            this.section.setVisible(config != null && bound != null && !bound.model.getShapeKeys().isEmpty());

            return;
        }

        this.section.setVisible(true);
        this.panel.form.shapeKeys.set(new ShapeKeys());
        this.controls.fill(config.shapeControllers.getAllTyped(), this.panel.form.shapeKeys.get());

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.addController());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.removeController());

        add.tooltip(UIKeys.GENERAL_ADD);
        remove.tooltip(UIKeys.GENERAL_REMOVE);
        this.body.add(UI.row(add, remove), this.controls, this.settings);
        this.fillSettings();
    }

    private void addController()
    {
        ShapeController controller = new ShapeController("");

        controller.name.set("shape_controller_" + (this.config.shapeControllers.getAllTyped().size() + 1));
        this.config.shapeControllers.add(controller);
        this.config.shapeControllers.sync();
        this.controls.fill(this.config.shapeControllers.getAllTyped(), this.panel.form.shapeKeys.get());
        this.controls.select(controller);
    }

    private void removeController()
    {
        ShapeController selected = this.controls.selected();

        if (selected != null)
        {
            this.config.shapeControllers.getAllTyped().remove(selected);
            this.config.shapeControllers.sync();
            this.controls.fill(this.config.shapeControllers.getAllTyped(), this.panel.form.shapeKeys.get());
            this.fillSettings();
        }
    }

    private void fillSettings()
    {
        ShapeController c = this.controls.selected();

        this.settings.removeAll();

        if (c != null)
        {
            UIIcon addBinding = new UIIcon(Icons.ADD, (b) -> this.addBinding());
            UIIcon removeBinding = new UIIcon(Icons.REMOVE, (b) -> this.removeBinding());
            ShapeController.Binding picked = this.bindings.getCurrentFirst();

            addBinding.tooltip(UIKeys.GENERAL_ADD);
            removeBinding.tooltip(UIKeys.GENERAL_REMOVE);
            this.bindings.setList(c.bindings.getAllTyped());
            this.bindings.setCurrent(picked != null && c.bindings.getAllTyped().contains(picked) ? List.of(picked)
                : c.bindings.getAllTyped().isEmpty() ? List.of() : List.of(c.bindings.getAllTyped().get(0)));

            this.settings.add(
                this.panel.labeledRow(key("name"), this.panel.stringField(c.name)),
                this.panel.labeledRow(key("bone"), this.panel.bonePicker(c.bone::get, c.bone::set, () -> {})),
                UI.label(key("position")), UI.row(this.panel.component(c.position, 0), this.panel.component(c.position, 1), this.panel.component(c.position, 2)),
                UI.label(key("bindings")), UI.row(addBinding, removeBinding), this.bindings, this.bindingSettings
            );
            this.fillBinding();
        }

        this.panel.resizeGeneral();
    }

    private void addBinding()
    {
        ShapeController c = this.controls.selected();

        if (c != null)
        {
            ShapeController.Binding binding = new ShapeController.Binding("");
            List<String> shapes = this.bound.model.getShapeKeys().stream().sorted().toList();

            if (!shapes.isEmpty()) binding.shape.set(shapes.get(0));

            c.bindings.add(binding);
            c.bindings.sync();
            this.bindings.setList(c.bindings.getAllTyped());
            this.bindings.setCurrent(binding);
            this.fillSettings();
        }
    }

    private void removeBinding()
    {
        ShapeController c = this.controls.selected();
        ShapeController.Binding binding = this.bindings.getCurrentFirst();

        if (c != null && binding != null)
        {
            c.bindings.getAllTyped().remove(binding);
            c.bindings.sync();
            this.fillSettings();
        }
    }

    private void fillBinding()
    {
        ShapeController.Binding binding = this.bindings.getCurrentFirst();

        this.bindingSettings.removeAll();

        if (binding != null)
        {
            UIButton shape = new UIButton(IKey.constant(binding.shape.get()), (b) -> b.getContext().replaceContextMenu((menu) ->
            {
                for (String name : this.bound.model.getShapeKeys().stream().sorted().toList())
                {
                    menu.action(Icons.SHAPES, IKey.constant(name), () ->
                    {
                        binding.shape.set(name);
                        this.fillSettings();
                    });
                }
            }));

            mchorse.bbs_mod.ui.framework.elements.input.UITrackpad height = this.panel.floatField(binding.height);
            mchorse.bbs_mod.ui.framework.elements.input.UITrackpad tilt = this.panel.floatField(binding.tilt);

            height.tooltip(key("height_gain"));
            tilt.tooltip(key("tilt_gain"));
            this.bindingSettings.add(shape, UI.row(height, tilt));
        }

        this.panel.resizeGeneral();
    }
}
