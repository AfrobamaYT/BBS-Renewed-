package mchorse.bbs_mod.ui.film.markers;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.TimerCue;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.film.UISeitenleiste;
import mchorse.bbs_mod.ui.film.replays.FilmGizmoDrag;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/** Two surface clicks create one undoable pair; no film data changes before the second click. */
public final class UITimerPlacement extends UIElement
{
    private final UIFilmPanel panel;
    private final UIFilmPreview preview;
    private final UITimerCuePanel inspector;
    public final UIButton create;
    private final UIText hint;
    private Film placing;
    private Point start;
    private int startTick;
    private String lastReplay = "";
    private Film lastFilm;

    private UITimerPlacement(UIFilmPreview preview, UIFilmPanel panel)
    {
        this.panel = panel;
        this.preview = preview;
        this.inspector = new UITimerCuePanel(panel);
        this.create = new UIButton(IKey.raw("+ 3D Timer"), (button) ->
        {
            if (this.placing == null)
            {
                this.begin();
            }
            else
            {
                this.cancel();
            }
        });
        this.create.tooltip(IKey.raw("Click START and END on the scene. Escape cancels without changing the film."));
        this.create.relative(this).x(1F, -148).y(5).w(120).h(20);
        this.hint = new UIText();
        this.hint.relative(this).x(40).y(34).w(1F, -80);
        this.hint.setVisible(false);
        this.relative(preview).x(0).y(0).w(1F).h(1F);
        this.add(this.create, this.hint);
        UISeitenleiste.anmelden("Timers", () -> this.inspector);
    }

    public static void install(UIFilmPreview preview, UIFilmPanel panel)
    {
        preview.add(new UITimerPlacement(preview, panel));
    }

    public static UITimerPlacement find(UIFilmPanel panel)
    {
        for (UITimerPlacement tool : panel.preview.getChildren(UITimerPlacement.class))
        {
            return tool;
        }
        throw new IllegalStateException("The film viewport has no 3D timer tool");
    }

    public void begin()
    {
        if (this.panel.getData() == null || this.panel.getController().isRecording() || this.panel.recorder.isRecording())
        {
            throw new IllegalStateException("Stop recording before placing a timer");
        }
        if (FilmGizmoDrag.isModalTransformActive(this.panel))
        {
            throw new IllegalStateException("Finish or cancel the transform before placing a timer");
        }
        if (this.panel.isRunning())
        {
            this.panel.togglePlayback();
        }
        this.getContext().unfocus();
        this.placing = this.panel.getData();
        this.startTick = this.panel.getCursor();
        this.start = null;
        this.create.label = IKey.raw("Cancel timer");
        this.hint.text("Click START on a surface. Then click END. Escape cancels.");
        this.hint.setVisible(true);
    }

    public void cancel()
    {
        this.placing = null;
        this.start = null;
        this.hint.setVisible(false);
        this.create.label = IKey.raw("+ 3D Timer");
    }

    private void showInspector()
    {
        for (UISeitenleiste sidebar : this.preview.getChildren(UISeitenleiste.class))
        {
            sidebar.show("Timers");
            return;
        }
        throw new IllegalStateException("The film viewport has no marker inspector");
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area viewport = this.preview.getViewport();
        if (this.placing == null || !viewport.isInside(context))
        {
            return false;
        }
        if (this.placing != this.panel.getData() || Minecraft.getInstance().level == null
            || this.panel.getController().isRecording() || this.panel.recorder.isRecording()
            || FilmGizmoDrag.isModalTransformActive(this.panel))
        {
            this.cancel();
            return true;
        }
        for (UISeitenleiste sidebar : this.preview.getChildren(UISeitenleiste.class))
        {
            if (sidebar.area.isInside(context))
            {
                return false;
            }
        }
        if (this.preview.werkzeuge.area.isInside(context))
        {
            return false;
        }
        if (context.mouseButton == 1)
        {
            this.cancel();
            return true;
        }
        if (context.mouseButton != 0)
        {
            return false;
        }
        Camera camera = this.panel.getCamera();
        Vector3f offset = new Vector3f();
        Vector3f direction = camera.getMouseRay(context.mouseX, context.mouseY,
            viewport.x, viewport.y, viewport.w, viewport.h, offset);
        Vec3 origin = RayTracing.fromVector3d(camera.position).add(offset.x, offset.y, offset.z);
        BlockHitResult hit = RayTracing.rayTrace(Minecraft.getInstance().level, origin,
            RayTracing.fromVector3f(direction), 64D);
        if (hit.getType() == HitResult.Type.MISS)
        {
            this.hint.text("No surface at the cursor within 64 blocks. Click a visible surface, or Escape to cancel.");
            return true;
        }
        Vec3 point = hit.getLocation().add(hit.getDirection().getStepX() * 0.02D,
            hit.getDirection().getStepY() * 0.02D, hit.getDirection().getStepZ() * 0.02D);
        if (this.start == null)
        {
            this.start = new Point(point.x, point.y, point.z);
            this.hint.text("START placed. Click END. Times can be set from the playhead afterwards.");
        }
        else
        {
            this.inspector.createAt(this.startTick, this.start, new Point(point.x, point.y, point.z));
            this.cancel();
            this.showInspector();
        }
        return true;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.placing != null && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.cancel();
            return true;
        }
        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.placing != null && this.placing != this.panel.getData())
        {
            this.cancel();
        }
        this.create.setEnabled(this.panel.getData() != null && !this.panel.getController().isRecording()
            && !this.panel.recorder.isRecording()
            && !FilmGizmoDrag.isModalTransformActive(this.panel));
        if (this.lastFilm != this.panel.getData())
        {
            this.lastFilm = this.panel.getData();
            this.lastReplay = "";
        }
        Replay replay = this.panel.replayEditor.getReplay();
        String uuid = replay == null ? "" : replay.uuid.get();
        if (!uuid.equals(this.lastReplay))
        {
            this.lastReplay = uuid;
            if (replay != null && this.panel.getData() != null)
            {
                for (TimerCue cue : this.panel.getData().timerCues.getList())
                {
                    if (uuid.equals(cue.startReplay.get()) || uuid.equals(cue.endReplay.get()))
                    {
                        this.inspector.selectCue(cue.uuid.get());
                        this.showInspector();
                        break;
                    }
                }
            }
        }
        super.render(context);
    }
}
