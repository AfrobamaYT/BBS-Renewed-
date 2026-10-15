package mchorse.bbs_mod.film;

import net.minecraft.client.renderer.state.CameraRenderState;
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
import net.minecraft.client.renderer.SubmitNodeCollector;

public class FilmControllerContext
{
    public final static FilmControllerContext instance = new FilmControllerContext();

    public IntObjectMap<IEntity> entities;
    public IEntity entity;
    public Replay replay;
    /* 26.2: die kamera ist kein lebendes Camera-objekt mehr, sondern der zustand,
     * den der weltdurchgang zu beginn eingefroren hat. Wer sie waehrend des zeichnens
     * liest, bekommt damit garantiert den wert DIESES bildes -- vorher konnte sich das
     * objekt mitten im durchgang weiterdrehen. */
    public CameraRenderState camera;
    public PoseStack stack;
    public SubmitNodeCollector consumers;
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

    /**
     * Läuft gerade der SAMMEL-Durchgang (COLLECT_SUBMITS) statt des Zeichen-Durchgangs
     * (AFTER_SOLID_FEATURES)?
     *
     * <p>⛔ WOFÜR: Schatten und Namensschild werden in 26.2 nicht gezeichnet, sondern
     * EINGEREICHT ({@code SubmitNodeCollector.submitShadow} / {@code submitText}). Vanilla
     * reicht in der Sammelphase ein und zeichnet unmittelbar danach — BBS hing seinen
     * ganzen Film-Durchgang aber an AFTER_SOLID_FEATURES, also NACH dem Punkt, an dem die
     * Sammlung geleert wird. Das Eingereichte landete damit in einer frischen Sammlung und
     * wurde erst im NÄCHSTEN Bild gezeichnet — mit einer Matrix, die relativ zur Kamera des
     * VORIGEN Bildes gebaut war. Versatz = camPos(N) − camPos(N+1): im Stillstand exakt
     * null, beim Schwenk gleitend, beim Anhalten zurückschnappend.
     *
     * <p>Deshalb läuft der Durchgang jetzt zweimal: einmal in der Sammelphase, wo NUR das
     * Aufschiebbare (Schatten, Namensschild) eingereicht wird, und einmal wie bisher in
     * AFTER_SOLID_FEATURES für alles, was sofort gezeichnet wird. Das Modell selbst war nie
     * betroffen — es wird direkt gezeichnet ({@code ModelVAORenderer}) und stand deshalb
     * still, während sein Schatten hinterherzog.
     *
     * <p>⚠️ STATISCH UND NICHT IN {@link #reset()}: das Flag beschreibt die PHASE des
     * ganzen Durchgangs, nicht eine einzelne Figur. {@code reset()} läuft pro Figur und
     * würde es mitten im Durchgang löschen.
     */
    public static boolean sammelDurchgang;

    public FilmControllerContext()
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
        this.camera = context.gameRenderer().getLevelRenderState().cameraRenderState;
        this.stack = context.matrixStack();
        /* 26.2: WorldRenderContext#consumers() heisst jetzt submitNodeCollector() — Fabric ist
         * der Umstellung vom Sofort-Verbraucher auf den Auftrags-Sammler im Namen gefolgt. */
        this.consumers = new mchorse.bbs_mod.compat.gpu.SofortSammler(context.consumers());
        this.transition = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);

        /* ⚗️ HIER STAND EINE KAMERA-SONDE, UND SIE HAT DIE FRAGE BEANTWORTET (2026-08-03):
         * kamera.gleich = 22527, kamera.weicht-ab = 0. Die Kameraposition, mit der der
         * Auswahlpuffer rendert, ist EXAKT die des Weltdurchgangs — auch bei bewegter Kamera.
         * Die gemessene Divergenz der beiden Puffer kommt also nicht von der Position.
         * Wieder entfernt, weil diese Methode je Bild mehrfach laeuft und der String-Bau
         * dort echte Bilder kostet; der Befund steht in der Aufgabe und braucht sie nicht. */

        return this;
    }

    public FilmControllerContext setup(IntObjectMap<IEntity> entities, IEntity entity, Replay replay, CameraRenderState camera, PoseStack stack, SubmitNodeCollector consumers, float transition)
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
