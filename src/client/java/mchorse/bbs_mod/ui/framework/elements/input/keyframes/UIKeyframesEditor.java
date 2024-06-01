package mchorse.bbs_mod.ui.framework.elements.input.keyframes;

import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.tooltips.InterpolationTooltip;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeInterpolation;
import mchorse.bbs_mod.utils.keyframes.KeyframeSimplifier;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class UIKeyframesEditor <T extends UIKeyframes> extends UIElement
{
    public UIElement frameButtons;
    public UITrackpad tick;
    public UITrackpad value;
    public UIIcon interp;

    public T keyframes;

    private int clicks;
    private long clickTimer;

    private IAxisConverter converter;

    public UIKeyframesEditor()
    {
        super();

        InterpolationTooltip tooltip = new InterpolationTooltip(0F, 0F, () ->
        {
            Keyframe keyframe = this.keyframes.getCurrent();

            if (keyframe == null)
            {
                return null;
            }

            return keyframe.getInterpolation().getInterp();
        });

        this.frameButtons = new UIElement();
        this.frameButtons.relative(this).x(1F).y(1F).w(100).anchor(1F).column().vertical().stretch().padding(5);
        this.frameButtons.setVisible(false);
        this.tick = new UITrackpad(this::setTick);
        this.tick.limit(Integer.MIN_VALUE, Integer.MAX_VALUE, true).tooltip(UIKeys.KEYFRAMES_TICK);
        this.value = new UITrackpad(this::setValue);
        this.value.tooltip(UIKeys.KEYFRAMES_VALUE);
        this.interp = new UIIcon(Icons.GRAPH, (b) ->
        {
            Interpolation interp = this.keyframes.getCurrent().getInterpolation();

            UICameraUtils.interps(this.getContext(), KeyframeInterpolation.INTERPOLATIONS, interp.getInterp(), (i) ->
            {
                interp.setInterp(i);
                this.keyframes.setInterpolation(interp);
            });
        });
        this.interp.tooltip(tooltip);

        this.keyframes = this.createElement();

        /* Position the elements */
        this.tick.w(70);
        this.value.w(70);
        this.keyframes.relative(this).set(0, 0, 0, 0).w(1, 0).h(1, 0);

        /* Add all elements */
        this.add(this.keyframes, this.frameButtons);
        this.frameButtons.add(UI.row(0, this.interp, this.tick), UI.row(this.value));

        this.context((menu) ->
        {
            if (this.keyframes.isEditing())
            {
                menu.action(Icons.CLOSE, UIKeys.KEYFRAMES_CONTEXT_EXIT_TRACK, () -> this.keyframes.editSheet(null));
            }
            else
            {
                UISheet sheet = this.keyframes.getSheet(this.getContext().mouseY);

                if (sheet != null)
                {
                    menu.action(Icons.EDIT, UIKeys.KEYFRAMES_CONTEXT_EDIT_TRACK.format(sheet.id), () -> this.keyframes.editSheet(sheet));
                }
            }

            menu.action(Icons.MAXIMIZE, UIKeys.KEYFRAMES_CONTEXT_MAXIMIZE, this::resetView);
            menu.action(Icons.FULLSCREEN, UIKeys.KEYFRAMES_CONTEXT_SELECT_ALL, this::selectAll);
            menu.action(Icons.MINIMIZE, UIKeys.KEYFRAMES_CONTEXT_SIMPLIFY, this::simplify);

            if (this.keyframes.which != Selection.NOT_SELECTED)
            {
                menu.action(Icons.REMOVE, UIKeys.KEYFRAMES_CONTEXT_REMOVE, this::removeSelectedKeyframes);
                menu.action(Icons.COPY, UIKeys.KEYFRAMES_CONTEXT_COPY, this::copyKeyframes);
            }

            Map<String, List<Keyframe>> pasted = this.parseKeyframes();

            if (pasted != null)
            {
                UIContext context = this.getContext();
                final Map<String, List<Keyframe>> keyframes = pasted;
                double offset = this.keyframes.scaleX.from(context.mouseX);
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
            Map<String, List<Keyframe>> pasted = this.parseKeyframes();

            if (pasted != null)
            {
                UIContext context = this.getContext();
                final Map<String, List<Keyframe>> keyframes = pasted;
                double offset = this.keyframes.scaleX.from(context.mouseX);
                int mouseY = context.mouseY;

                this.pasteKeyframes(keyframes, (long) offset, mouseY);
            }
        }).inside().category(category);
        this.keys().register(Keys.DELETE, this::removeSelectedKeyframes).inside().category(category);

        this.interp.keys().register(Keys.KEYFRAMES_INTERP, this::toggleInterpolation).category(category);
    }

    protected abstract T createElement();

    protected void toggleInterpolation()
    {
        this.interp.clickItself();
    }

    public void setConverter(IAxisConverter converter)
    {
        this.converter = converter;
        this.keyframes.setConverter(converter);

        if (converter != null)
        {
            converter.updateField(this.tick);
        }

        this.fillData(this.keyframes.getCurrent());
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
    private Map<String, List<Keyframe>> parseKeyframes()
    {
        MapType data = Window.getClipboardMap("_CopyKeyframes");

        if (data == null)
        {
            return null;
        }

        Map<String, List<Keyframe>> temp = new HashMap<>();

        for (String key : data.keys())
        {
            ListType list = data.getList(key);

            for (int i = 0, c = list.size(); i < c; i++)
            {
                List<Keyframe> keyframes = temp.computeIfAbsent(key, k -> new ArrayList<>());
                Keyframe keyframe = new Keyframe("");

                keyframe.fromData(list.getMap(i));
                keyframes.add(keyframe);
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

        for (UISheet sheet : this.keyframes.getSheets())
        {
            int c = sheet.getSelectedCount();

            if (c > 0)
            {
                ListType list = new ListType();

                for (int i = 0; i < c; i++)
                {
                    Keyframe keyframe = sheet.channel.get(sheet.selected.get(i));

                    list.add(keyframe.toData());
                }

                if (!list.isEmpty())
                {
                    keyframes.put(sheet.id, list);
                }
            }
        }

        Window.setClipboard(keyframes, "_CopyKeyframes");
    }

    /**
     * Paste copied keyframes to clipboard
     */
    protected void pasteKeyframes(Map<String, List<Keyframe>> keyframes, long offset, int mouseY)
    {
        List<UISheet> sheets = this.keyframes.getSheets();

        this.keyframes.clearSelection();

        if (keyframes.size() == 1)
        {
            UISheet current = this.keyframes.getSheet(mouseY);

            if (current == null)
            {
                current =  sheets.get(0);
            }

            this.pasteKeyframesTo(current, keyframes.get(keyframes.keySet().iterator().next()), offset);

            return;
        }

        for (Map.Entry<String, List<Keyframe>> entry : keyframes.entrySet())
        {
            for (UISheet sheet : sheets)
            {
                if (!sheet.id.equals(entry.getKey()))
                {
                    continue;
                }

                this.pasteKeyframesTo(sheet, entry.getValue(), offset);
            }
        }
    }

    private void pasteKeyframesTo(UISheet sheet, List<Keyframe> keyframes, long offset)
    {
        long firstX = keyframes.get(0).getTick();
        List<Keyframe> toSelect = new ArrayList<>();

        for (Keyframe keyframe : keyframes)
        {
            keyframe.setTick(keyframe.getTick() - firstX + offset);

            int index = sheet.channel.insert(keyframe.getTick(), keyframe.getValue());
            Keyframe inserted = sheet.channel.get(index);

            inserted.copy(keyframe);
            toSelect.add(inserted);
        }

        for (Keyframe select : toSelect)
        {
            sheet.selected.add(sheet.channel.getKeyframes().indexOf(select));
        }

        sheet.channel.sync();

        this.keyframes.which = Selection.KEYFRAME;
        this.keyframes.setKeyframe(this.keyframes.getCurrent());
    }

    protected void doubleClick(int mouseX, int mouseY)
    {
        this.keyframes.doubleClick(mouseX, mouseY);
        this.fillData(this.keyframes.getCurrent());
    }

    public void resetView()
    {
        this.keyframes.resetView();
    }

    public void selectAll()
    {
        this.keyframes.selectAll();
    }

    public void simplify()
    {
        for (UISheet sheet : this.keyframes.getSheets())
        {
            BaseValue.edit(sheet.channel, (channel) ->
            {
                channel.copyKeyframes(KeyframeSimplifier.simplify(channel));
            });
        }
    }

    public void removeSelectedKeyframes()
    {
        this.keyframes.removeSelectedKeyframes();
    }

    public void setTick(double tick)
    {
        this.keyframes.setTick(this.converter == null ? tick : this.converter.from(tick), false);
    }

    public void setValue(double value)
    {
        this.keyframes.setValue(value, false);
    }

    public void fillData(Keyframe frame)
    {
        boolean show = frame != null && this.keyframes.which != Selection.NOT_SELECTED;

        this.frameButtons.setVisible(show);

        if (!show)
        {
            return;
        }

        double tick = this.keyframes.which.getX(frame);
        boolean forceInteger = this.keyframes.which == Selection.KEYFRAME;

        this.tick.integer = this.converter == null ? forceInteger : this.converter.forceInteger(frame, this.keyframes.which, forceInteger);
        this.tick.setValue(this.converter == null ? tick : this.converter.to(tick));
        this.value.setValue(this.keyframes.which.getY(frame));
    }

    public void select(List<List<Integer>> selection, Vector2i selected)
    {
        int i = 0;
        boolean deselect = true;

        for (UISheet sheet : this.keyframes.getSheets())
        {
            List<Integer> sheetSelection = CollectionUtils.inRange(selection, i) ? selection.get(i) : null;

            if (sheetSelection != null)
            {
                sheet.selected.clear();
                sheet.selected.addAll(sheetSelection);
                this.keyframes.which = Selection.KEYFRAME;
            }

            if (i == selected.x)
            {
                Keyframe keyframe = sheet.channel.get(selected.y);

                if (keyframe != null)
                {
                    this.fillData(keyframe);

                    deselect = false;
                }
            }

            i += 1;
        }

        if (deselect)
        {
            this.fillData(null);
        }
    }
}