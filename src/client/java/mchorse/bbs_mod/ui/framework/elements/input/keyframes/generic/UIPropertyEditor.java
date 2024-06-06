package mchorse.bbs_mod.ui.framework.elements.input.keyframes.generic;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.properties.IFormProperty;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UICameraDopeSheetEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UIInterpolationContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.IAxisConverter;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIPropertyEditor <T extends UIProperties> extends UIElement
{
    public UIElement frameButtons;
    public UITrackpad tick;
    public UITrackpad duration;
    public UIIcon interp;
    public UIKeyframeFactory editor;

    public T properties;

    private int clicks;
    private long clickTimer;

    private IAxisConverter converter;

    protected List<BaseValue> valueChannels = new ArrayList<>();

    public UIPropertyEditor(IUIClipsDelegate delegate)
    {
        super();

        InterpolationTooltip tooltip = new InterpolationTooltip(0F, 1F, () ->
        {
            Keyframe keyframe = this.properties.getCurrent();

            if (keyframe == null)
            {
                return null;
            }

            return keyframe.getInterpolation().wrap();
        });

        this.frameButtons = new UIElement();
        this.frameButtons.relative(this).x(1F).y(1F).w(120).anchor(1F).column().vertical().stretch().padding(5);
        this.frameButtons.setVisible(false);
        this.tick = new UITrackpad(this::setTick);
        this.tick.limit(Integer.MIN_VALUE, Integer.MAX_VALUE, true).tooltip(UIKeys.KEYFRAMES_TICK);
        this.duration = new UITrackpad((v) -> this.setDuration(v.intValue()));
        this.duration.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.KEYFRAMES_FORCED_DURATION);
        this.interp = new UIIcon(Icons.GRAPH, (b) ->
        {
            Interpolation interp = this.properties.getCurrent().getInterpolation();
            UIInterpolationContextMenu menu = new UIInterpolationContextMenu(interp);

            this.getContext().replaceContextMenu(menu.callback(() -> this.properties.setInterpolation(interp)));
        });
        this.interp.tooltip(tooltip);

        this.properties = this.createElement(delegate);
        this.properties.relative(this).full();

        /* Add all elements */
        this.add(this.properties, this.frameButtons);
        this.frameButtons.add(UI.row(5, this.interp, this.tick, this.duration));

        this.context((menu) ->
        {
            if (this.properties.isEditing())
            {
                menu.action(Icons.CLOSE, UIKeys.KEYFRAMES_CONTEXT_EXIT_TRACK, () -> this.properties.editSheet(null));
            }
            else
            {
                UIProperty sheet = this.properties.getProperty(this.getContext().mouseY);

                if (sheet != null)
                {
                    menu.action(Icons.EDIT, UIKeys.KEYFRAMES_CONTEXT_EDIT_TRACK.format(sheet.id), () -> this.properties.editSheet(sheet));
                }
            }

            menu.action(Icons.MAXIMIZE, UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, this::resetView);
            menu.action(Icons.FULLSCREEN, UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, this::selectAll);

            if (this.properties.selected)
            {
                menu.action(Icons.REMOVE, UIKeys.KEYFRAMES_CONTEXT_REMOVE, this::removeSelectedKeyframes);
                menu.action(Icons.COPY, UIKeys.KEYFRAMES_CONTEXT_COPY, this::copyKeyframes);
            }

            Map<String, PastedKeyframes> pasted = this.parseKeyframes();

            if (pasted != null)
            {
                UIContext context = this.getContext();
                final Map<String, PastedKeyframes> keyframes = pasted;
                double offset = this.properties.fromGraphX(context.mouseX);
                int mouseY = context.mouseY;

                menu.action(Icons.PASTE, UIKeys.KEYFRAMES_CONTEXT_PASTE, () -> this.pasteKeyframes(keyframes, (long) offset, mouseY));
            }
        });

        IKey category = UIKeys.KEYFRAMES_KEYS_CATEGORY;

        this.keys().register(Keys.KEYFRAMES_MAXIMIZE, this::resetView).inside().category(category);
        this.keys().register(Keys.KEYFRAMES_SELECT_ALL, this::selectAll).inside().category(category);
        this.keys().register(Keys.COPY, this::copyKeyframes).inside().category(category);
        this.keys().register(Keys.PASTE, () ->
        {
            Map<String, PastedKeyframes> pasted = this.parseKeyframes();

            if (pasted != null)
            {
                UIContext context = this.getContext();
                double offset = this.properties.fromGraphX(context.mouseX);
                int mouseY = context.mouseY;

                this.pasteKeyframes(pasted, (long) offset, mouseY);
            }
        }).inside().category(category);
        this.keys().register(Keys.DELETE, this::removeSelectedKeyframes).inside().category(category);

        this.interp.keys().register(Keys.KEYFRAMES_INTERP, this.interp::clickItself).category(category);

        this.updateConverter();
    }

    protected T createElement(IUIClipsDelegate delegate)
    {
        return (T) new UIProperties(delegate, this::fillData);
    }

    public void setChannels(List<KeyframeChannel> properties, List<IFormProperty> property, List<Integer> colors)
    {
        List<UIProperty> sheets = this.properties.properties;

        sheets.clear();
        this.properties.clearSelection();

        this.valueChannels.clear();

        for (int i = 0; i < properties.size(); i++)
        {
            KeyframeChannel channel = properties.get(i);

            this.valueChannels.add(channel);
            sheets.add(new UIProperty(channel.getId(), IKey.raw(channel.getId()), colors.get(i), channel, property.get(i)));
        }

        this.frameButtons.setVisible(false);
    }

    public void updateConverter()
    {
        this.setConverter(UICameraDopeSheetEditor.CONVERTER);
    }

    public void setConverter(IAxisConverter converter)
    {
        this.converter = converter;
        this.properties.setConverter(converter);

        if (converter != null)
        {
            converter.updateField(this.tick);
        }

        this.fillData(this.properties.getCurrent());
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.area.isInside(mouseX, mouseY))
        {
            /* On double-click add or remove a keyframe */
            if (context.mouseButton == 0)
            {
                long time = System.currentTimeMillis();

                if (time - this.clickTimer < 175)
                {
                    this.clicks++;

                    if (this.clicks >= 1)
                    {
                        this.clicks = 0;
                        this.doubleClick(mouseX, mouseY);
                    }
                }
                else
                {
                    this.clicks = 0;
                }

                this.clickTimer = time;
            }
        }

        return super.subMouseClicked(context);
    }

    /**
     * Parse keyframes from clipboard
     */
    private Map<String, PastedKeyframes> parseKeyframes()
    {
        MapType data = Window.getClipboardMap("_CopyProperties");

        if (data == null)
        {
            return null;
        }

        Map<String, PastedKeyframes> temp = new HashMap<>();

        for (String key : data.keys())
        {
            MapType map = data.getMap(key);
            ListType list = map.getList("keyframes");
            IKeyframeFactory serializer = KeyframeFactories.FACTORIES.get(map.getString("type"));

            for (int i = 0, c = list.size(); i < c; i++)
            {
                PastedKeyframes pastedKeyframes = temp.computeIfAbsent(key, k -> new PastedKeyframes(serializer));
                Keyframe keyframe = new Keyframe("", serializer);

                keyframe.fromData(list.getMap(i));
                pastedKeyframes.keyframes.add(keyframe);
            }
        }

        return temp.isEmpty() ? null : temp;
    }

    /**
     * Copy keyframes to clipboard
     */
    private void copyKeyframes()
    {
        MapType keyframes = new MapType();

        for (UIProperty property : this.properties.getProperties())
        {
            int c = property.getSelectedCount();

            if (c > 0)
            {
                MapType data = new MapType();
                ListType list = new ListType();

                data.putString("type", CollectionUtils.getKey(KeyframeFactories.FACTORIES, property.channel.getFactory()));
                data.put("keyframes", list);

                for (int i = 0; i < c; i++)
                {
                    list.add(property.getSelectedKeyframe(i).toData());
                }

                if (!list.isEmpty())
                {
                    keyframes.put(property.id, data);
                }
            }
        }

        Window.setClipboard(keyframes, "_CopyProperties");
    }

    /**
     * Paste copied keyframes to clipboard
     */
    protected void pasteKeyframes(Map<String, PastedKeyframes> keyframes, long offset, int mouseY)
    {
        List<UIProperty> properties = this.properties.getProperties();

        this.properties.clearSelection();

        if (keyframes.size() == 1)
        {
            UIProperty current = this.properties.getProperty(mouseY);

            if (current == null)
            {
                current =  properties.get(0);
            }

            this.pasteKeyframesTo(current, keyframes.get(keyframes.keySet().iterator().next()), offset);

            return;
        }

        for (Map.Entry<String, PastedKeyframes> entry : keyframes.entrySet())
        {
            for (UIProperty property : properties)
            {
                if (!property.id.equals(entry.getKey()))
                {
                    continue;
                }

                this.pasteKeyframesTo(property, entry.getValue(), offset);
            }
        }
    }

    private void pasteKeyframesTo(UIProperty property, PastedKeyframes pastedKeyframes, long offset)
    {
        if (property.channel.getFactory() != pastedKeyframes.factory)
        {
            return;
        }

        long firstX = pastedKeyframes.keyframes.get(0).getTick();
        List<Keyframe> toSelect = new ArrayList<>();

        for (Keyframe keyframe : pastedKeyframes.keyframes)
        {
            keyframe.setTick(keyframe.getTick() - firstX + offset);

            int index = property.channel.insert(keyframe.getTick(), keyframe.getValue());
            Keyframe inserted = property.channel.get(index);

            inserted.copy(keyframe);
            toSelect.add(inserted);
        }

        for (Keyframe select : toSelect)
        {
            property.addToSelection(property.channel.getKeyframes().indexOf(select));
        }

        this.properties.selected = true;
        this.properties.setKeyframe(this.properties.getCurrent());
    }

    protected void doubleClick(int mouseX, int mouseY)
    {
        this.properties.doubleClick(mouseX, mouseY);
        this.fillData(this.properties.getCurrent());
    }

    public void resetView()
    {
        this.properties.resetView();
    }

    public void selectAll()
    {
        this.properties.selectAll();
    }

    public void removeSelectedKeyframes()
    {
        this.properties.removeSelectedKeyframes();
    }

    public void setTick(double tick)
    {
        this.properties.setTick(this.converter == null ? tick : this.converter.from(tick));
    }

    public void setDuration(int value)
    {
        Keyframe current = this.properties.getCurrent();

        if (current != null)
        {
            current.setDuration(value);
        }
    }

    public void setValue(Object value)
    {
        this.properties.setValue(value);
    }

    public void fillData(Keyframe frame)
    {
        boolean show = frame != null;

        this.frameButtons.setVisible(show);

        if (!show)
        {
            return;
        }

        double tick = frame.getTick();
        float duration = frame.getDuration();

        if (this.editor != null)
        {
            this.editor.removeFromParent();
            this.editor = null;
        }

        this.editor = UIKeyframeFactory.createPanel(frame, this);

        if (this.editor != null)
        {
            this.frameButtons.add(this.editor);
        }

        this.tick.setValue(this.converter == null ? tick : this.converter.to(tick));
        this.duration.setValue(this.converter == null ? duration : this.converter.to(duration));
        this.frameButtons.resize();
    }

    public void pickKeyframe(Keyframe frame)
    {
        this.fillData(frame);

        this.properties.selected = true;

        main:
        for (UIProperty property : this.properties.getProperties())
        {
            int i = 0;

            for (Object object : property.channel.getKeyframes())
            {
                if (object == frame)
                {
                    property.addToSelection(i);

                    break main;
                }

                i += 1;
            }
        }
    }

    private static class PastedKeyframes
    {
        public IKeyframeFactory factory;
        public List<Keyframe> keyframes = new ArrayList<>();

        public PastedKeyframes(IKeyframeFactory factory)
        {
            this.factory = factory;
        }
    }
}