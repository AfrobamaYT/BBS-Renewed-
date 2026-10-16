package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;

public interface ICursor
{
    public int getCursor();

    public void setCursor(int tick);

    /** The playhead with its place between two ticks, whole unless "snap to ticks" is off (BBS FS 096836ac2) */
    public default float getCursor(float transition)
    {
        return this.getCursor();
    }

    public default void setCursor(float tick)
    {
        this.setCursor(Math.round(tick));
    }

    /** Where a scrub to {@code tick} lands: a whole tick while snapping */
    public static float einrasten(float tick)
    {
        return BBSSettings.editorSnapToTicks.get() ? Math.round(tick) : tick;
    }
}
