package mchorse.bbs_mod.ui.dashboard.utils;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.utils.Area;

public class UIOrbitCameraKeys implements IUIElement
{
    private UIOrbitCamera orbitCamera;

    public UIOrbitCameraKeys(UIOrbitCamera orbitCamera)
    {
        this.orbitCamera = orbitCamera;
    }

    @Override
    public void resize()
    {}

    @Override
    public boolean isEnabled()
    {
        return this.orbitCamera.isEnabled();
    }

    @Override
    public boolean isVisible()
    {
        return true;
    }

    @Override
    public IUIElement mouseClicked(UIContext context)
    {
        return null;
    }

    @Override
    public IUIElement mouseScrolled(UIContext context)
    {
        return null;
    }

    @Override
    public IUIElement mouseReleased(UIContext context)
    {
        return null;
    }

    @Override
    public IUIElement keyPressed(UIContext context)
    {
        if (context.isFocused())
        {
            return null;
        }

        return this.orbitCamera.getControl() && this.orbitCamera.orbit.keyPressed(context) ? this : null;
    }

    @Override
    public IUIElement textInput(UIContext context)
    {
        return null;
    }

    @Override
    public boolean canBeRendered(Area viewport)
    {
        return false;
    }

    @Override
    public void render(UIContext context)
    {}
}