package mchorse.bbs_mod.film;

import com.mojang.blaze3d.vertex.PoseStack;
import io.netty.util.collection.IntObjectMap;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Matrix4f;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;

public class FilmControllerContext
{
    public final static FilmControllerContext instance = new FilmControllerContext();

    public IntObjectMap<IEntity> entities;
    public IEntity entity;
    public Replay replay;
    public Camera camera;
    public PoseStack stack;
    public MultiBufferSource consumers;
    public StencilMap map;

    public float transition;
    public int color;
    public float shadowRadius;

    public String bone;
    public boolean local;

    /** The reference frame the bone gizmo is drawn in, and the film camera's
     *  world&rarr;camera rotation used to reorient it (null keeps LOCAL). */
    public TransformSpace space = TransformSpace.LOCAL;
    public Matrix4f gizmoView;

    public String bone2;
    public boolean local2;

    /** Draw the editing gizmo at the entity's resolved {@code form.anchor} matrix. */
    public boolean anchorGizmo;
    public boolean anchorLocal;

    public String nameTag = "";
    public boolean relative;

    /**
     * Place the gizmo/axes but draw no body: the form itself, its shadow and its
     * name tag are left out, everything else runs as usual. First person hides
     * the current actor's model (the camera is inside it) while its bone gizmo
     * must stay live &mdash; and the gizmo is placed by the very pass that draws
     * the form, so the pass has to run without it.
     *
     * <p>Reset in {@link #reset()}: this context is a reused singleton, and a
     * leaked flag would blank the next actor.
     */
    public boolean gizmoOnly;

    private FilmControllerContext()
    {}

    private void reset()
    {
        this.map = null;
        this.shadowRadius = 0F;
        this.color = Colors.WHITE;
        this.bone = null;
        this.local = false;
        this.space = TransformSpace.LOCAL;
        this.gizmoView = null;
        this.anchorGizmo = false;
        this.anchorLocal = false;
        this.nameTag = "";
        this.relative = false;
        this.gizmoOnly = false;
    }

    public FilmControllerContext setup(IntObjectMap<IEntity> entities, IEntity entity, Replay replay, WorldRenderContext context)
    {
        this.reset();

        this.entities = entities;
        this.entity = entity;
        this.replay = replay;
        this.camera = context.camera();
        this.stack = context.matrixStack();
        this.consumers = context.consumers();
        this.transition = context.tickCounter().getTickDelta(false);

        return this;
    }

    public FilmControllerContext setup(IntObjectMap<IEntity> entities, IEntity entity, Replay replay, Camera camera, PoseStack stack, MultiBufferSource consumers, float transition)
    {
        this.reset();

        this.entities = entities;
        this.entity = entity;
        this.replay = replay;
        this.camera = camera;
        this.stack = stack;
        this.consumers = consumers;
        this.transition = transition;

        return this;
    }

    public FilmControllerContext transition(float transition)
    {
        this.transition = transition;

        return this;
    }

    public FilmControllerContext stencil(StencilMap map)
    {
        this.map = map;

        return this;
    }

    public FilmControllerContext shadow(boolean shadow, float shadowRadius)
    {
        this.shadowRadius = shadow ? shadowRadius : 0F;

        return this;
    }

    public FilmControllerContext shadow(float shadowRadius)
    {
        this.shadowRadius = shadowRadius;

        return this;
    }

    public FilmControllerContext color(int overlayColor)
    {
        this.color = overlayColor;

        return this;
    }

    public FilmControllerContext bone(String bone, boolean local)
    {
        this.bone = bone;
        this.local = local;

        return this;
    }

    /** Set the space and camera view used to reorient the bone gizmo (Phase C). */
    public FilmControllerContext gizmoSpace(TransformSpace space, Matrix4f view)
    {
        this.space = space;
        this.gizmoView = view;

        return this;
    }

    public FilmControllerContext bone2(String bone, boolean local)
    {
        this.bone2 = bone;
        this.local2 = local;

        return this;
    }

    public FilmControllerContext anchorGizmo(boolean anchorGizmo, boolean anchorLocal)
    {
        this.anchorGizmo = anchorGizmo;
        this.anchorLocal = anchorLocal;

        return this;
    }

    public FilmControllerContext nameTag(String nameTag)
    {
        this.nameTag = nameTag;

        return this;
    }

    public FilmControllerContext relative(boolean relative)
    {
        this.relative = relative;

        return this;
    }

    /** See {@link #gizmoOnly}. */
    public FilmControllerContext gizmoOnly(boolean gizmoOnly)
    {
        this.gizmoOnly = gizmoOnly;

        return this;
    }
}