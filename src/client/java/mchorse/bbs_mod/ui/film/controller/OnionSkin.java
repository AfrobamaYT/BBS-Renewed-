package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.utils.colors.Colors;

public class OnionSkin
{
    public boolean enabled;

    public int preFrames = 1;
    public int preColor = Colors.NEGATIVE | Colors.A75;

    public int postFrames = 1;
    public int postColor = Colors.POSITIVE | Colors.A75;

    public boolean all;

    private String group;

    public OnionSkin()
    {
        this.resetGroup();
    }

    public void resetGroup()
    {
        this.group = "pose";
    }

    public String getGroup()
    {
        return this.group;
    }

    public void setGroup(String group)
    {
        this.group = group;
    }
}