package mchorse.bbs_mod.ui.film.regie;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.regie.Regie;
import mchorse.bbs_mod.film.regie.RegieGriff;
import mchorse.bbs_mod.film.regie.RegieMarker;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Marker im Editor-Viewport ziehen: Linksklick auf eine Bodenplatte (Blick-Marker: auf den
 * Punkt, Startpunkt: auf den Ring) fasst sie, der Marker folgt dann dem Block unter der Maus.
 * Der Pfeil am Startring dreht stattdessen die Blickrichtung, mit der jede Aufnahme beginnt.
 * Ein Zug ist ein Undo-Schritt. Trifft der Klick keinen Marker, geht er unberuehrt weiter an
 * Orbit-Gizmo und Replay-Auswahl — das Element liegt unter Seitenleiste, Werkzeugen und HUD.
 */
public class UIRegieGriff extends UIElement
{
    private final UIFilmPanel panel;
    private final Vector3f versatz = new Vector3f();
    /** UUID des gezogenen Markers, {@link RegieGriff#START} oder null. */
    private String gezogen;

    public UIRegieGriff(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    /** Mausstrahl der Editorkamera; der Ursprung ist Kamera + {@link #versatz}. */
    private Vector3f strahl(UIContext context)
    {
        Area area = this.panel.preview.getViewport();

        return this.panel.getCamera().getMouseRay(context.mouseX, context.mouseY, area.x, area.y, area.w, area.h, this.versatz);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Film film = this.panel.getData();

        if (context.mouseButton != 0 || film == null || this.panel.isFlying() || !this.panel.preview.getViewport().isInside(context))
        {
            return false;
        }

        Camera kamera = this.panel.getCamera();
        Vector3f d = this.strahl(context);

        this.gezogen = RegieGriff.fassen(film.regie, kamera.position.x + this.versatz.x, kamera.position.y + this.versatz.y,
            kamera.position.z + this.versatz.z, d.x, d.y, d.z);

        if (this.gezogen == null)
        {
            return false;
        }

        RegieBedienung.zaun(this.panel);

        return true;
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (this.gezogen != null)
        {
            this.ziehen(context);
        }
    }

    /** Den gefassten Marker auf den Block unter der Maus setzen. */
    void ziehen(UIContext context)
    {
        Film film = this.panel.getData();
        Regie regie = film == null ? null : film.regie;
        RegieMarker m = regie == null ? null : regie.marker.mitUuid(this.gezogen);
        boolean start = RegieGriff.START.equals(this.gezogen);
        boolean richtung = RegieGriff.START_RICHTUNG.equals(this.gezogen);

        if (regie == null || (m == null && !start && !richtung))
        {
            this.gezogen = null;

            return;
        }

        Camera kamera = this.panel.getCamera();
        Vector3f d = this.strahl(context);

        if (richtung)
        {
            this.drehen(regie, kamera, d);

            return;
        }

        BlockHitResult treffer = RayTracing.rayTrace(Minecraft.getInstance().level, new Vec3(kamera.position.x + this.versatz.x,
            kamera.position.y + this.versatz.y, kamera.position.z + this.versatz.z), new Vec3(d.x, d.y, d.z), 256D);

        if (treffer.getType() == HitResult.Type.MISS)
        {
            return;
        }

        Vec3 p = treffer.getLocation();

        if (start)
        {
            if (p.x != regie.startX.get() || p.y != regie.startY.get() || p.z != regie.startZ.get())
            {
                BaseValue.edit(regie, (r) ->
                {
                    r.startX.set(p.x);
                    r.startY.set(p.y);
                    r.startZ.set(p.z);
                });
            }

            return;
        }

        double y = p.y + RegieBedienung.hoehe(m.istOrt(), treffer);

        if (p.x != m.x.get() || y != m.y.get() || p.z != m.z.get())
        {
            BaseValue.edit(m, (w) ->
            {
                w.x.set(p.x);
                w.y.set(y);
                w.z.set(p.z);
            });
        }
    }

    /** Blickrichtung auf den Punkt, in dem der Mausstrahl die Bodenebene des Startpunkts schneidet. */
    private void drehen(Regie regie, Camera kamera, Vector3f d)
    {
        double oy = kamera.position.y + this.versatz.y;
        double t = Math.abs(d.y) < 1E-6F ? -1D : (regie.startY.get() - oy) / d.y;

        if (t <= 0D)
        {
            return;
        }

        float gier = RegieGriff.gier(regie, kamera.position.x + this.versatz.x + d.x * t, kamera.position.z + this.versatz.z + d.z * t);

        if (gier != regie.startYaw.get())
        {
            BaseValue.edit(regie.startYaw, (w) -> w.set(gier));
        }
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.gezogen == null)
        {
            return false;
        }

        this.gezogen = null;
        RegieBedienung.zaun(this.panel);

        return true;
    }
}
