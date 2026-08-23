package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.mehrfach.MehrfachSchreiber;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.ui.film.markers.UIMarkerLane;
import mchorse.bbs_mod.utils.factory.IFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIClipsPanel extends UIElement implements IUIClipsDelegate, com.mcstudio.nano.ZonenQuelle
{
    public UIClips clips;
    public UIFilmPanel filmPanel;

    private UIClip panel;
    private boolean hasClips;
    private boolean timelineVisible = true;
    private boolean propertiesVisible = true;

    private UIElement target;

    public UIClipsPanel(UIFilmPanel panel, IFactory<Clip, ClipFactoryData> factory)
    {
        this.filmPanel = panel;
        this.clips = new UIClips(this, factory);

        /* ⛔ 26 px Luft oben fuer die NanoVG-Kopfzeile (UIZeitleisteNano), die darueber liegt.
         * Ohne das verdeckt sie das Lineal von UIClips — beide beanspruchten dieselbe
         * y-Position, und die spaeter gezeichnete gewann. */
        /* ⛔ 26 px Luft oben (Kopfzeile) und 105 px links (Kanalspalte) — beides zeichnet
         * UIZeitleisteNano darueber. Ohne die Einrueckung laegen Kanalnamen und Clips
         * uebereinander. 105 ist die halbe Entwurfsbreite (.chan{210px}) in GUI-Pixeln. */
        this.add(this.clips.relative(this).x(105).y(0).w(1F, -105).h(1F));

        /* Die Merkstellen-Spur ist ein KIND der Zeitleiste: so wird sie danach gezeichnet
         * (liegt also oben) und bekommt den Klick zuerst. Sie gibt jeden Klick weiter, der
         * keinen Marker trifft. Siehe UIMarkerLane. */
        this.clips.add(new UIMarkerLane(this.clips, this).full(this.clips));
    }

    public UIClipsPanel target(UIElement target)
    {
        this.target = target;

        return this;
    }

    public void setClips(Clips clips)
    {
        this.hasClips = clips != null;
        this.clips.setClips(clips);
        this.clips.setVisible(this.hasClips && this.timelineVisible);
    }

    public void setTimelineVisible(boolean visible)
    {
        this.timelineVisible = visible;
        this.clips.setVisible(this.hasClips && visible);
    }

    public void setPropertiesVisible(boolean visible)
    {
        this.propertiesVisible = visible;

        if (this.panel != null)
        {
            this.panel.setVisible(visible);
        }
    }

    /**
     * Pick the clip that is driving the camera right now, so flying edits something
     * even when nothing was selected. Returns whether an editor is open afterwards.
     *
     * <p>Without this the flight had to be aimed at a clip the user had selected
     * beforehand; flying with nothing selected moved the camera and threw the result
     * away on leaving the mode. The clip chosen is the same one
     * {@link #editClip(Position)} already treats as the top of the stack: the highest
     * layer among the clips evaluated at this tick.</p>
     *
     * <p>Only runs while {@link BBSSettings#editorFlightEditsActiveClip} is on and no
     * clip is open — once one is picked the caller's first condition holds and this
     * is not asked again.</p>
     */
    /**
     * Whether flying should write into a clip right now — a clip is open and its
     * editor showing, or {@link #pickActiveClip()} just found one.
     *
     * <p>Before this, flying with nothing selected moved the camera and threw the
     * result away on leaving the mode.</p>
     */
    public boolean acceptsFlightEdit()
    {
        return (this.getClip() != null && this.isVisible()) || this.pickActiveClip();
    }

    public boolean pickActiveClip()
    {
        if (!BBSSettings.editorFlightEditsActiveClip.get() || this.getClip() != null)
        {
            return false;
        }

        List<Clip> kandidaten = new ArrayList<>(this.filmPanel.getRunner().getContext().getSnapshots().keySet());

        kandidaten.sort((a, b) -> Integer.compare(b.layer.get(), a.layer.get()));

        for (Clip clip : kandidaten)
        {
            this.pickClip(clip);

            /* Nicht jeder clip auf der kamera-spur nimmt eine kameraposition an — ein
             * audio-clip liegt dort genauso und wuerde den flug lautlos schlucken. */
            if (this.panel != null && this.panel.authorsCamera())
            {
                return true;
            }
        }

        this.pickClip(null);

        return false;
    }

    public void editClip(Position position)
    {
        if (this.panel != null)
        {
            Map<Clip, Position> snapshots = this.filmPanel.getRunner().getContext().getSnapshots();
            Position newPosition = new Position();
            Position snapshot = snapshots.get(this.panel.clip);

            newPosition.copy(position);

            if (snapshot != null)
            {
                Clip top = this.panel.clip;

                for (Clip clip : snapshots.keySet())
                {
                    if (clip.layer.get() > top.layer.get())
                    {
                        top = clip;
                    }
                }

                Position topPosition = snapshots.get(top);

                if (topPosition != null)
                {
                    newPosition.point.x -= topPosition.point.x - snapshot.point.x;
                    newPosition.point.y -= topPosition.point.y - snapshot.point.y;
                    newPosition.point.z -= topPosition.point.z - snapshot.point.z;

                    newPosition.angle.yaw -= topPosition.angle.yaw - snapshot.angle.yaw;
                    newPosition.angle.pitch-= topPosition.angle.pitch - snapshot.angle.pitch;
                    newPosition.angle.roll -= topPosition.angle.roll - snapshot.angle.roll;
                    newPosition.angle.fov -= topPosition.angle.fov - snapshot.angle.fov;
                }
            }

            this.panel.editClip(newPosition);
        }
    }

    @Override
    public Film getFilm()
    {
        return this.filmPanel.getData();
    }

    @Override
    public Camera getCamera()
    {
        return this.filmPanel.getCamera();
    }

    @Override
    public Clip getClip()
    {
        return this.panel == null ? null : this.panel.clip;
    }

    @Override
    public String getClipDisplayName(Clip clip)
    {
        return clip != null ? this.clips.getClipDisplayName(clip) : "";
    }

    @Override
    public void pickClip(Clip clip)
    {
        UIClip.saveScroll(this.panel);

        if (this.panel != null)
        {
            if (this.panel.clip == clip)
            {
                this.panel.fillData();

                return;
            }
            else
            {
                this.panel.removeFromParent();
            }
        }

        if (clip == null)
        {
            this.panel = null;

            this.clips.w(1F, 0);
            this.clips.clearSelection();
            this.resize();

            return;
        }

        try
        {
            this.clips.embedView(null);

            this.panel = UIClip.createPanel(clip, this);
            this.panel.setUndoId("clip_panel");

            if (this.target == null)
            {
                this.panel.relative(this).x(1F, -160).w(160).h(1F);
            }
            else
            {
                int top = this.filmPanel.getEditPanelTopOffsetPx();
                this.panel.relative(this.target).x(0).y(0, top).w(1F).h(1F, -top);
            }

            this.add(this.panel);
            this.resize();
            this.panel.fillData();
            this.panel.setVisible(this.propertiesVisible);
            this.panel.restoreScroll();

            if (this.filmPanel.isFlying())
            {
                this.setCursor(clip.tick.get());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.clips.w(1F, this.target == null ? -160 : 0);
        this.resize();

        this.filmPanel.pickClip(clip, this);
    }

    /** Re-applies edit panel position (e.g. after layout lock toggle). */
    public void refreshEditPanelOffset()
    {
        if (this.panel == null || this.target == null)
        {
            return;
        }
        int top = this.filmPanel.getEditPanelTopOffsetPx();
        this.panel.relative(this.target).x(0).y(0, top).w(1F).h(1F, -top);
        this.resize();
    }

    @Override
    public void setFlight(boolean flight)
    {
        this.filmPanel.setFlight(flight);
    }

    @Override
    public boolean isFlying()
    {
        return this.filmPanel.isFlying();
    }

    @Override
    public int getCursor()
    {
        return this.filmPanel.getCursor();
    }

    @Override
    public void setCursor(int tick)
    {
        this.filmPanel.setCursor(tick);
    }

    @Override
    public boolean isRunning()
    {
        return this.filmPanel.isRunning();
    }

    @Override
    public void togglePlayback()
    {
        this.filmPanel.togglePlayback();
    }

    @Override
    public boolean canUseKeybinds()
    {
        return this.filmPanel.canUseKeybinds();
    }

    @Override
    public void fillData()
    {
        if (this.panel != null)
        {
            this.panel.fillData();
        }
    }

    @Override
    public void embedView(UIElement element)
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            context.closeContextMenu();
        }

        this.clips.embedView(element);
    }

    @Override
    public void markLastUndoNoMerging()
    {
        this.filmPanel.getUndoHandler().getUndoManager().markLastUndoNoMerging();
    }

    /**
     * Alle clips, auf die eine aenderung im eigenschaften-panel wirkt.
     *
     * <p>⚠ Die zeitleisten-auswahl KANN leer sein, waehrend ein panel offen ist:
     * {@link #pickActiveClip()} oeffnet im flug ein panel, ohne die auswahl anzufassen.
     * Ohne den anker-zusatz unten fiel in diesem zustand jede panel-aenderung lautlos auf
     * den boden — die alte schleife lief dann ueber eine leere liste.</p>
     */
    @Override
    public List<Clip> getMehrfachZiele()
    {
        List<Clip> ziele = this.clips.getClipsFromSelection();
        Clip anker = this.getClip();

        if (anker != null && !enthaelt(ziele, anker))
        {
            ziele.add(anker);
        }

        return ziele;
    }

    /**
     * ⚠ Referenz-vergleich mit absicht: {@code Clip} erbt {@code ValueGroup.equals}, das
     * den INHALT vergleicht. {@code List.contains} wuerde zwei inhaltsgleiche clips
     * verwechseln.
     */
    private static boolean enthaelt(List<Clip> clips, Clip gesucht)
    {
        for (Clip clip : clips)
        {
            if (clip == gesucht)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Traegt eine panel-aenderung auf alle gewaehlten clips. Ob das als delta oder
     * absolut geschieht, entscheidet {@link mchorse.bbs_mod.ui.film.mehrfach.MehrfachWert}
     * — an EINER stelle, nicht hier und nicht in den panels.
     *
     * <p>Die alte fassung rief {@code clip.getRecursively(path)} auf. Das WIRFT bei
     * unbekanntem pfad ({@code IllegalStateException}), statt null zu liefern — bei
     * gemischter auswahl (z.B. audio- und untertitel-clip) und geschachtelten pfaden war
     * das ein absturz. Der neue weg benutzt {@code findRecursively}.</p>
     */
    @Override
    public <T extends BaseValue> void editMultiple(T property, Consumer<T> consumer)
    {
        MehrfachSchreiber.schreibe(this.getClip(), this.getMehrfachZiele(), property, consumer);
    }

    @Override
    public void editMultiple(ValueInt property, int value)
    {
        int difference = value - property.get();
        List<Clip> clips = this.clips.getClipsFromSelection();

        for (Clip clip : clips)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());
            int newValue = clipValue.get() + difference;

            if (newValue < clipValue.getMin() || newValue > clipValue.getMax())
            {
                return;
            }
        }

        for (Clip clip : clips)
        {
            ValueInt clipValue = (ValueInt) clip.get(property.getId());

            clipValue.set(clipValue.get() + difference);
        }
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        List<Integer> selection = DataStorageUtils.intListFromData(data.getList("selection"));

        this.clips.scale.view(data.getDouble("x_min"), data.getDouble("x_max"));
        this.clips.vertical.setScroll(data.getDouble("scroll"));
        this.clips.vertical.updateTarget();

        this.clips.setSelection(selection);
        this.pickClip(selection.isEmpty() ? null : this.clips.getClips().get(selection.get(selection.size() - 1)));
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.put("selection", DataStorageUtils.intListToData(this.clips.getSelection()));
        data.putDouble("x_min", this.clips.scale.getMinValue());
        data.putDouble("x_max", this.clips.scale.getMaxValue());
        data.putDouble("scroll", this.clips.vertical.getScroll());
    }

    /** ⛔ Lazy: ein per Hotswap neu hinzugekommenes Feld ist auf bestehenden Instanzen null. */
    private com.mcstudio.nano.ZonenMelder zonen;

    private com.mcstudio.nano.ZonenMelder zonen()
    {
        if (this.zonen == null)
        {
            this.zonen = new com.mcstudio.nano.ZonenMelder();
        }

        return this.zonen;
    }

    @Override
    public java.util.List<com.mcstudio.nano.ZonenMelder.Zone> gemeldeteZonen()
    {
        return this.zonen().alle();
    }

    @Override
    public void render(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        super.render(context);

        /* NACH dem Zeichnen: erst dann steht die Lage, die gemeldet wird. */
        com.mcstudio.nano.ZonenMelder melder = this.zonen();

        melder.beginnen();

        if (this.clips != null && this.clips.isVisible())
        {
            ClipZonen.melden(melder, this.clips);
        }

        melder.uebernehmen();
    }
}
