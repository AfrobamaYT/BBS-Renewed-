package mchorse.bbs_mod.ui.utils.shapes;

import mchorse.bbs_mod.cubic.model.config.ShapeController;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragEndEvent;
import mchorse.bbs_mod.ui.framework.elements.events.UITrackpadDragStartEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.utils.UI;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The animator's side of shape key controllers: pick a controller of the model, drag its height and
 * tilt; the shape keys it drives change by their weights, everything else stays. The values are read
 * back from the shape keys, so there is no second animation state to drift. From BBS FS 7a049804a
 * (Wemppy4/bbs-fs, MIT).
 */
public class UIShapeControllers extends UIElement
{
    public final UIList<ShapeController> list;
    public final UITrackpad height;
    public final UITrackpad tilt;
    public Runnable selectionChanged = () -> {};

    private ShapeKeys keys = new ShapeKeys();
    private final Consumer<Consumer<ShapeKeys>> edit;
    private Runnable boundary = () -> {};
    private Supplier<String> presetGroup;

    public static IKey key(String name)
    {
        return L10n.lang("bbs.ui.shape_controllers." + name);
    }

    public UIShapeControllers(Consumer<Consumer<ShapeKeys>> edit)
    {
        this.edit = edit;
        this.list = new UIList<>((picked) ->
        {
            this.refresh();
            this.selectionChanged.run();
        })
        {
            @Override
            protected String elementToString(UIContext context, int i, ShapeController element)
            {
                return element.name.get();
            }
        };
        this.list.background();
        this.list.h(64);
        this.height = new UITrackpad((v) -> this.setAxis(0, v.floatValue()));
        this.tilt = new UITrackpad((v) -> this.setAxis(1, v.floatValue()));
        this.height.increment(0.05F);
        this.tilt.increment(0.05F);
        this.height.tooltip(key("height"));
        this.tilt.tooltip(key("tilt"));

        /* A drag is one gesture: the host closes its history entry at both ends */
        for (UITrackpad field : List.of(this.height, this.tilt))
        {
            field.getEvents().register(UITrackpadDragStartEvent.class, (e) -> this.boundary.run());
            field.getEvents().register(UITrackpadDragEndEvent.class, (e) -> this.boundary.run());
        }

        this.column(4).vertical().stretch();
        this.add(UI.label(key("title")), this.list, UI.row(this.height, this.tilt));
        this.list.context(this::presetMenu);
    }

    public void boundary(Runnable boundary)
    {
        this.boundary = boundary;
    }

    /** The preset menu for the handles' right click; null without a model */
    public mchorse.bbs_mod.ui.utils.presets.UIDataContextMenu presetMenu()
    {
        return this.presetGroup == null ? null : UIShapeKeys.presetMenu(this.presetGroup.get(), () -> this.keys.toData(), (data) ->
        {
            this.boundary.run();
            this.edit.accept((keys) -> keys.fromData(data));
            this.boundary.run();
            this.refresh();
        });
    }

    public ShapeKeys shapeKeys()
    {
        return this.keys;
    }

    /** Closes the current history entry, at both ends of a drag of a handle */
    public void endGesture()
    {
        this.boundary.run();
    }

    public void presets(Supplier<String> group)
    {
        this.presetGroup = group;
    }

    public ShapeController selected()
    {
        return this.list.getCurrentFirst();
    }

    public void select(ShapeController controller)
    {
        this.list.setCurrent(controller == null ? List.of() : List.of(controller));
        this.refresh();
        this.selectionChanged.run();
    }

    public void fill(List<ShapeController> controllers, ShapeKeys keys)
    {
        String id = this.selected() == null ? "" : this.selected().getId();
        ShapeController current = controllers.stream().filter((c) -> c.getId().equals(id)).findFirst().orElse(controllers.isEmpty() ? null : controllers.get(0));

        this.keys = keys;
        this.list.setList(controllers);
        this.list.setCurrent(current == null ? List.of() : List.of(current));
        this.list.h(Math.max(1, Math.min(5, controllers.size())) * 16);
        this.refresh();
    }

    public void refresh(ShapeKeys keys)
    {
        this.keys = keys;
        this.refresh();
    }

    private void refresh()
    {
        ShapeController selected = this.selected();
        float[] values = selected == null ? new float[2] : selected.read(this.keys);

        this.height.setEnabled(selected != null && selected.bindings.getAllTyped().stream().anyMatch((b) -> b.height.get() != 0));
        this.tilt.setEnabled(selected != null && selected.bindings.getAllTyped().stream().anyMatch((b) -> b.tilt.get() != 0));

        if (!this.bearbeitet(this.height)) this.height.setValue(values[0]);
        if (!this.bearbeitet(this.tilt)) this.tilt.setValue(values[1]);
    }

    public void setAxis(int axis, float value)
    {
        ShapeController selected = this.selected();

        if (selected != null)
        {
            this.edit.accept((keys) -> selected.set(keys, axis, value));
            this.refresh();
        }
    }

    /** Being dragged or typed in: a refresh must not overwrite it */
    private boolean bearbeitet(UITrackpad field)
    {
        return field.isDragging() || field.textbox.isFocused();
    }

    /** Bindings and values change elsewhere (the author's section, the timeline): read them every frame */
    @Override
    public void render(UIContext context)
    {
        this.refresh();
        super.render(context);
    }

    public boolean isEditing()
    {
        return this.bearbeitet(this.height) || this.bearbeitet(this.tilt);
    }
}
