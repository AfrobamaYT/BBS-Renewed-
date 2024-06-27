package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.List;

public interface IUIKeyframeGraph
{
    public List<UIKeyframeSheet> getSheets();

    /* Selection */

    public void clearSelection();

    public void selectAll();

    public void selectByX(int mouseX);

    public void selectInArea(Area area);

    public Keyframe getSelected();

    /* Keyframe management */

    public UIKeyframeSheet getSheet(Keyframe keyframe);

    public UIKeyframeSheet getSheet(int mouseY);

    public boolean addKeyframe(int mouseX, int mouseY);

    public void addKeyframe(UIKeyframeSheet sheet, long tick);

    public void removeKeyframe(Keyframe keyframe);

    public void removeSelected();

    public Keyframe findKeyframe(int mouseX, int mouseY);

    public void pickKeyframe(Keyframe keyframe);

    public void selectKeyframe(Keyframe keyframe);

    public void setTick(long tick);

    public void setInterpolation(Interpolation interpolation);

    public void setValue(Object value);

    public void resize();

    /* Input handling */

    public boolean mouseClicked(UIContext context);

    public void mouseReleased(UIContext context);

    public void mouseScrolled(UIContext context);

    public void handleMouse(UIContext context, int lastX, int lastY);

    /* Rendering */

    public void render(UIContext context);

    public void postRender(UIContext context);

    /* State recovery */

    public void saveState(MapType extra);

    public void restoreState(MapType extra);
}
