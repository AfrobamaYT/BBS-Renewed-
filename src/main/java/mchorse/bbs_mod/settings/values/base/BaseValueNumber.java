package mchorse.bbs_mod.settings.values.base;

import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

public abstract class BaseValueNumber <T extends Number> extends BaseKeyframeFactoryValue<T>
{
    protected T min;
    protected T max;

    protected boolean slider;
    protected boolean live;

    /* Drag-Metadaten fuer generierte Widgets (P8c): gehoeren zur DEFINITION der
     * Property wie min/max, nicht in jedes Panel. null = Widget-Default. */
    protected float[] dragWerte;
    protected Double dragIncrement;

    public BaseValueNumber(String id, IKeyframeFactory<T> factory, T defaultValue, T min, T max)
    {
        super(id, factory, defaultValue);

        this.min = min;
        this.max = max;
    }

    public T getMin()
    {
        return this.min;
    }

    public T getMax()
    {
        return this.max;
    }

    public boolean isSlider()
    {
        return this.slider;
    }

    /**
     * Offer this value as a track rather than a drag field. Worth it only when
     * both ends are declared and the whole span fits the track with a useful
     * step — a resolution or a tick count belongs in a field you type into,
     * however finite its bounds happen to be.
     */
    public BaseValueNumber<T> slider()
    {
        this.slider = true;

        return this;
    }

    public boolean isLive()
    {
        return this.live;
    }

    /**
     * Report every step of a drag, not only the release — for a value the user
     * steers by eye while dragging, like the UI scale (Blender's Resolution Scale).
     */
    public BaseValueNumber<T> live()
    {
        this.live = true;

        return this;
    }

    /** Drag-Skalen des Feldes: normal, fein (Shift), grob (Ctrl). */
    public BaseValueNumber<T> dragWerte(float normal, float fein, float grob)
    {
        this.dragWerte = new float[] {normal, fein, grob};

        return this;
    }

    public BaseValueNumber<T> dragWerte(float normal)
    {
        return this.dragWerte(normal, normal / 5F, normal * 5F);
    }

    public BaseValueNumber<T> dragIncrement(double increment)
    {
        this.dragIncrement = increment;

        return this;
    }

    public float[] getDragWerte()
    {
        return this.dragWerte;
    }

    public Double getDragIncrement()
    {
        return this.dragIncrement;
    }

    @Override
    public void set(T value, int flag)
    {
        if (this.min != null && this.max != null)
        {
            value = this.clamp(value);
        }

        super.set(value, flag);
    }

    protected abstract T clamp(T value);
}