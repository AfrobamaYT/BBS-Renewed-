package mchorse.bbs_mod.ui.film.controller;

import org.joml.Intersectiond;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.render.CubicBoundingBoxRenderer;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.FormFrameCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;

public class OrbitFilmCameraController implements ICameraController
{
    private static final float PITCH_LIMIT = MathUtils.PI * 0.5F - 0.01F;
    private static final float MIN_DISTANCE = 0.5F;
    private static final float MAX_DISTANCE = 256F;
    private static final int DRAG_THRESHOLD = 3;

    private final UIFilmController controller;

    public boolean enabled;

    private boolean orbiting;
    private int orbitButton = -1;
    private final Vector2i last = new Vector2i();
    private final Vector2i pressOrigin = new Vector2i();
    private boolean dragged;

    /* The state the input drives. */
    private final Vector2f targetRotation = new Vector2f();
    private final Vector3f targetPivot = new Vector3f();
    private float targetDistance;

    /* The state that is rendered, smoothly chasing the target above. */
    private final Vector2f rotation = new Vector2f();
    private final Vector3f pivot = new Vector3f();
    private float distance;

    /* Whether the pivot has been placed onto the subject after a reset. */
    private boolean positioned;


    /*
     * When attached, pivot and rotation are stored relative to the selected
     * replay's anchor (interpolated position + body yaw), and world values are
     * composed on the fly. Detached, the anchor is identity, so the same math
     * passes world values through untouched. Rebasing between anchors preserves
     * the world state, so attaching, detaching and switching replays never
     * moves the camera.
     */
    private boolean attached = true;
    private boolean ortho;

    /* Whether ortho was turned on by an axis snap rather than by the user, in
     * which case orbiting away from the axis turns it back off (see rotate). */
    private boolean autoOrtho;
    private Replay anchorReplay;
    private final Vector3d anchorPosition = new Vector3d();
    private float anchorYaw;

    private final PanState panState = new PanState();
    protected final Vector3i velocityPosition = new Vector3i();

    public OrbitFilmCameraController(UIFilmController controller)
    {
        this.controller = controller;
        this.reset();
    }

    public void start(UIContext context)
    {
        if (!this.canStart(context))
        {
            return;
        }

        this.orbitButton = context.mouseButton;
        this.orbiting = true;
        this.dragged = false;
        this.last.set(context.mouseX, context.mouseY);
        this.pressOrigin.set(context.mouseX, context.mouseY);

        if (this.isPanning())
        {
            this.cachePanState(context);
        }
    }

    public boolean wasDragged()
    {
        return this.dragged;
    }

    public void stop()
    {
        this.orbiting = false;
        this.orbitButton = -1;
    }

    public boolean keyPressed(UIContext context, Area area)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

        if (area.isInside(context) || (!this.velocityPosition.equals(0, 0, 0) && context.getKeyAction() == KeyAction.RELEASED))
        {
            if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
            {
                return false;
            }

            int x = this.getFactor(context, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, this.velocityPosition.x);
            int y = this.getFactor(context, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN, this.velocityPosition.y);
            int z = this.getFactor(context, Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, this.velocityPosition.z);
            boolean changed = x != this.velocityPosition.x || y != this.velocityPosition.y || z != this.velocityPosition.z;

            this.velocityPosition.set(x, y, z);

            return changed;
        }

        return false;
    }

    protected int getFactor(UIContext context, KeyCombo positive, KeyCombo negative, int x)
    {
        if (context.isPressed(positive.getMainKey()))
        {
            return 1;
        }
        else if (context.isPressed(negative.getMainKey()))
        {
            return -1;
        }
        else if (
            (context.isReleased(positive.getMainKey()) && x > 0) ||
            (context.isReleased(negative.getMainKey()) && x < 0)
        ) {
            return 0;
        }

        return x;
    }

    public void handleOrbiting(UIContext context)
    {
        if (!this.orbiting)
        {
            return;
        }

        int x = context.mouseX;
        int y = context.mouseY;
        int dx = x - this.last.x;
        int dy = y - this.last.y;

        if (!this.dragged && (Math.abs(x - this.pressOrigin.x) > DRAG_THRESHOLD || Math.abs(y - this.pressOrigin.y) > DRAG_THRESHOLD))
        {
            this.dragged = true;
        }

        if (this.orbitButton == 2)
        {
            this.pan(context);
        }
        else
        {
            this.rotate(dx, dy);
        }

        this.last.set(x, y);
    }

    /**
     * Maustaste 4: das Replay rahmen — eine Bewegung an eine gerechnete Stelle, wie Blender.
     *
     * <p>Der Pivot wandert dabei zuerst auf das Replay; sonst faehrt man auf den alten
     * Drehpunkt zu, und das ist beim Orbiten fast nie der Darsteller, um den es gerade geht.</p>
     *
     * <h2>⛔ WARUM DER ERSTE ENTWURF FALSCH WAR</h2>
     *
     * <p>Zuerst gab es hier nur das fortlaufende Zufahren: pro Tick ein fester ANTEIL des
     * Abstands. Der Nutzer am 2026-07-30: <em>"es zoomt nur ein wenig rein und das wars, bei
     * blender zoomt das deutlich naeher ran egal wie weit du weg bist oder wo du gerade
     * bist"</em>. Genau das ist der Unterschied zwischen anteilig und absolut — aus 60 Bloecken
     * Entfernung bringt ein Anteil einen proportional naeher, aber eben nicht ANS Replay.</p>
     *
     * <p>Blenders "Frame Selected" rahmt das Objekt unabhaengig davon, wo man steht: die
     * Zieldistanz kommt aus der GROESSE des Objekts, nicht aus der bisherigen Entfernung.
     * Genau das macht {@link #rahmenAbstand()} hier — und deshalb wirkt ein Druck aus jeder
     * Entfernung gleich.</p>
     *
     * <h2>⛔ UND WARUM ES KEIN WEITER-ZUFAHREN BEIM HALTEN MEHR GIBT</h2>
     *
     * <p>Der zweite Entwurf rahmte zwar, fuhr danach aber weiter zu, solange die Taste lag.
     * Der Nutzer: <em>"der zoom ist viel zu aggresiv und bei playern zoomt er auch so weit
     * rein"</em>. Das war genau dieses Weiterfahren: schon ein kurzer Tipp dauert mehrere
     * Ticks, und die fielen alle unter den Rahmen. Gemessen kam ein Tipp bei 1,1 statt bei
     * den gerahmten 2,2 heraus.</p>
     *
     * <p>Blender kennt so ein Halten nicht — dort ist es EINE Bewegung an eine gerechnete
     * Stelle. Damit ist es hier auch weg: Druck rahmt, fertig. Naeher kommt man mit dem Rad,
     * das dafuer schon da ist.</p>
     */
    public void starteZoomZumReplay()
    {
        if (!this.enabled)
        {
            return;
        }

        this.teleportPivotToReplay();
        this.zieleAufMitte();

        float rahmen = this.rahmenAbstand();

        /* ABSOLUT gesetzt, auch wenn das die Kamera zurueckzieht.
         *
         * ⛔ Hier stand zuvor "nur HINEIN rahmen, nie heraus" — mit der Begruendung, ein Sprung
         * nach hinten sei das Gegenteil dessen, was die Taste verspricht. Das war eine Annahme aus
         * der Zeit, als jede Groesse 1,8 war: dann steht man praktisch immer zu weit weg, und die
         * Klausel griff nie. Mit echten Ausdehnungen kehrt sich das um. Gemessen am Modell "Nuke"
         * (Rahmenabstand 15,25): aus 4,0 Bloecken heraus gedrueckt blieb der Abstand bei 4,0 —
         * die Taste tat sichtbar nichts, obwohl man mitten im Modell stand.
         *
         * Blender kennt die Klausel auch nicht: view3d_view.cc rechnet dist_new und uebergibt es
         * an ED_view3d_smooth_view, ohne den bisherigen Abstand anzusehen. Der Nutzer wollte genau
         * das — "egal wie weit du weg bist oder wo du gerade bist". */
        if (rahmen > 0F)
        {
            this.targetDistance = rahmen;
        }
    }

    /**
     * Hebt den Drehpunkt von der halben TREFFERBOX-Hoehe auf die halbe ECHTE Hoehe an.
     *
     * <h2>Warum das zum Rahmen dazugehoert</h2>
     *
     * <p>{@link #teleportPivotToReplay} setzt den Drehpunkt auf {@code Trefferbox.h / 2}, also bei
     * jedem Modell auf 0,9 Bloecke — dieselbe Vorgabe, die auch den Abstand verdorben hat. Beim
     * Abstand faellt es auf, beim Ziel nicht: es kommt ja ein Bild heraus, nur eben ein falsch
     * angeschnittenes. Nachgerechnet am Modell "Nuke" (reicht von -1,44 bis 13,81, Rahmenabstand
     * 15,25): die Oberkante steht 12,91 ueber dem Drehpunkt, das sind 40,2° — bei 35° halbem
     * Sichtfeld liegt die Spitze AUSSERHALB des Bildes. Rahmen heisst Abstand UND Ziel; Blenders
     * {@code view_selected} setzt beides in einem Zug ({@code ofs} und {@code dist}).</p>
     *
     * <p>Nur auf diesem Weg, nicht in {@link #teleportPivotToReplay} selbst: das setzt den
     * Drehpunkt auch fuer das normale Umkreisen, und dort will niemand, dass sich der Bezugspunkt
     * unter der Hand verschiebt.</p>
     *
     * <p>Y allein, kein X/Z: der Drehpunkt liegt in Anker-Koordinaten, und der Anker dreht nur um
     * die Hochachse — eine Hoehenverschiebung ueberlebt das unveraendert, eine seitliche nicht.
     * Seitlich steht die Geometrie ohnehin um ihren eigenen Ursprung.</p>
     */
    private void zieleAufMitte()
    {
        IEntity entity = this.controller.getCurrentEntity();

        if (entity == null)
        {
            return;
        }

        AABB huelle = this.weltHuelle(entity);

        if (huelle == null)
        {
            return;
        }

        float mitte = (float) (huelle.y + huelle.h / 2);
        float bisher = (float) (entity.getPickingHitbox().h / 2);

        this.targetPivot.y += mitte - bisher;
    }

    /**
     * Der Abstand, aus dem das Replay bildfuellend dasteht — aus seiner GROESSE, nicht aus
     * der bisherigen Entfernung.
     *
     * <p>Die Groesse kommt aus {@link #ausdehnung}, also aus der echten Modellgeometrie.</p>
     *
     * <p>0, wenn es nichts zu rahmen gibt (kein Darsteller, keine Ausdehnung) — dann bleibt
     * der Abstand, wie er ist.</p>
     */
    private float rahmenAbstand()
    {
        IEntity entity = this.controller.getCurrentEntity();
        Camera camera = this.controller.panel.getCamera();

        if (entity == null || camera == null || camera.fov <= 0F)
        {
            return 0F;
        }

        float groesse = this.ausdehnung(entity);

        if (groesse <= 0F)
        {
            return 0F;
        }

        /* Blenders Rechnung aus view_selected, eins zu eins (Quellen nachgelesen):
         *   view3d_view.cc     dist = ED_view3d_radius_to_dist(..., (size / 2) * VIEW3D_MARGIN)
         *   view3d_utils.cc    ED_view3d_radius_to_dist_persp(angle, radius)
         *                        -> radius * (1 / tan(angle / 2))
         * size ist die groesste Achse der Huellbox, VIEW3D_MARGIN = 1.4 der Rand, angle der
         * volle Oeffnungswinkel. camera.fov ist genau dieser Winkel in Radiant (er geht so in
         * JOMLs perspective()). Der Rand steht als Einstellung, damit man enger oder weiter
         * rahmen kann, ohne die Formel anzufassen. */
        float radius = (groesse / 2F) * BBSSettings.editorOrbitFrameMargin.get();
        float abstand = radius / (float) Math.tan(camera.fov / 2F);

        return MathUtils.clamp(abstand, MIN_DISTANCE, MAX_DISTANCE);
    }

    /**
     * Wie gross der Darsteller in der Welt dasteht — Blenders {@code size}, die laengste Achse
     * seiner Huellbox.
     *
     * <h2>Warum nicht die Trefferbox</h2>
     *
     * <p>Bis hierher stand hier {@code entity.getPickingHitbox()}. Deren Hoehe ist ein
     * EINSTELLWERT der Form mit Vorgabe 1.8 — Spieler, Ender-Drache und ein 15 Bloecke hohes
     * Modell meldeten alle dieselbe Zahl, und dieselbe rahmte man dann auch. Gemessen: mit der
     * Trefferbox kamen 60, 25, 8 und 3 Bloecke grosse Vorlagen samt und sonders bei 1,80 heraus.
     * Der Nutzer sah davon nur, dass es bei grossen Modellen "nicht richtig ranzoomt".</p>
     *
     * <h2>Die drei Faktoren, und warum keiner davon geraten ist</h2>
     *
     * <ol>
     * <li>die Modellgeometrie selbst — {@link CubicBoundingBoxRenderer#messen}, das den Baum mit
     * der Renderer-Kette abgeht (dort steht auch, warum die Ruhelage und nicht die laufende Pose);</li>
     * <li>{@link ModelInstance#getScale()}, die Skalierung aus der {@code config.json} des Modells;</li>
     * <li>die Transform der Form samt Overlay und zusaetzlichen — ueber
     * {@link FormRenderer#createTransform()}, damit die Zusammensetzung nicht ein zweites Mal
     * dasteht und auseinanderlaufen kann.</li>
     * </ol>
     *
     * <p>Faktor 2 und 3 sind genau die, die {@link ModelFormRenderer#applyTransforms} beim
     * Zeichnen auftraegt — dieselben zwei, in derselben Reihenfolge.</p>
     *
     * <h2>Rueckfall</h2>
     *
     * <p>Alles, was keine ModelForm ist (Block, Gegenstand, Vanilla-Mob), hat keine Cubic-Geometrie
     * zum Ausmessen und faellt auf die Trefferbox zurueck. Fuer diese Formen ist sie auch die
     * richtige Auskunft: ein Blockmodell IST einen Block gross.</p>
     */
    private float ausdehnung(IEntity entity)
    {
        AABB huelle = this.weltHuelle(entity);

        return huelle == null ? 0F : (float) Math.max(huelle.w, Math.max(huelle.h, huelle.d));
    }

    /**
     * Die Huellbox des Darstellers in Weltmassen, relativ zu seinem Fusspunkt — oder {@code null},
     * wenn sich nichts messen laesst.
     *
     * <p>Eine Messung fuer beides, was zum Rahmen gehoert: die GROESSE bestimmt den Abstand, die
     * MITTE bestimmt, worauf gezielt wird. Getrennt gemessen liefen die beiden auseinander.</p>
     */
    private AABB weltHuelle(IEntity entity)
    {
        Form form = entity.getForm();

        if (form instanceof ModelForm modelForm)
        {
            ModelInstance instance = ModelFormRenderer.getModel(modelForm);

            if (instance != null && instance.getModel() instanceof Model cubic)
            {
                AABB box = CubicBoundingBoxRenderer.messen(cubic);

                if (box != null)
                {
                    Vector3f modellSkal = instance.getScale();
                    Vector3f formSkal = FormUtilsClient.getRenderer(form).createTransform().scale;

                    /* Je Achse eigen skalieren: ein Modell, das nur in der Hoehe gestreckt wird,
                     * waere sonst zu klein gerahmt. */
                    float sx = modellSkal.x * formSkal.x;
                    float sy = modellSkal.y * formSkal.y;
                    float sz = modellSkal.z * formSkal.z;

                    return new AABB(
                        box.x * sx, box.y * sy, box.z * sz,
                        box.w * sx, box.h * sy, box.d * sz
                    );
                }
            }
        }

        /* Alles ohne Cubic-Geometrie (Block, Gegenstand, Vanilla-Mob) faellt auf die Trefferbox
         * zurueck — fuer diese Formen ist sie auch die richtige Auskunft: ein Blockmodell IST
         * einen Block gross. Auf den Fusspunkt bezogen, wie die gemessene Box: die Trefferbox
         * steht in Weltkoordinaten, ihre Hoehe beginnt aber ebenfalls am Fusspunkt. */
        AABB trefferbox = entity.getPickingHitbox();

        if (trefferbox == null || trefferbox.h <= 0)
        {
            return null;
        }

        return new AABB(-trefferbox.w / 2, 0, -trefferbox.d / 2, trefferbox.w, trefferbox.h, trefferbox.d);
    }

    public boolean zoom(double mouseWheel)
    {
        if (!this.enabled || this.controller.panel.isFlying() || mouseWheel == 0D)
        {
            return false;
        }

        float step = Window.isCtrlPressed() ? 0.22F : 0.1F;
        float factor = (float) Math.pow(1F - step, mouseWheel);

        this.targetDistance = MathUtils.clamp(this.targetDistance * factor, MIN_DISTANCE, MAX_DISTANCE);

        return true;
    }

    public boolean update(UIContext context)
    {
        if (!this.enabled)
        {
            return false;
        }


        this.applySmoothing();

        if (context.isFocused())
        {
            return false;
        }

        if (BBSSettings.editorOrbitMovementRequiresFlight.get() && !this.controller.panel.isFlying())
        {
            this.velocityPosition.set(0, 0, 0);

            return false;
        }

        if (this.velocityPosition.lengthSquared() > 0)
        {
            Vector3f delta = this.rotateVector(-this.velocityPosition.x, this.velocityPosition.y, -this.velocityPosition.z, this.targetRotation.y, this.targetRotation.x).mul(this.getSpeed());

            this.targetPivot.add(delta);

            return true;
        }

        return false;
    }

    private void applySmoothing()
    {
        float smoothness = BBSSettings.editorCameraSmoothness.get();

        if (smoothness <= 0F)
        {
            this.rotation.set(this.targetRotation);
            this.pivot.set(this.targetPivot);
            this.distance = this.targetDistance;

            return;
        }

        float dt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
        float factor = MathUtils.clamp(1F - (float) Math.pow(Math.min(smoothness, 0.99F), dt), 0F, 1F);

        this.rotation.lerp(this.targetRotation, factor);
        this.pivot.lerp(this.targetPivot, factor);
        this.distance = Lerps.lerp(this.distance, this.targetDistance, factor);
    }

    protected float getSpeed()
    {
        return this.controller.panel.dashboard.orbit.getSpeed();
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch)
    {
        return this.rotateVector(x, y, z, yaw, pitch, BBSSettings.editorHorizontalFlight.get());
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch, boolean horizontal)
    {
        Matrix3f rotation = new Matrix3f();
        Vector3f rotate = new Vector3f(x, y, z);

        rotation.rotateY(yaw);

        if (!horizontal)
        {
            rotation.rotateX(pitch);
        }

        rotation.transform(rotate);

        return rotate;
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Area viewport = this.controller.panel.preview.getViewport();
        Vector3d vector = new Vector3d();
        Vector3f originOffset = new Vector3f();
        Vector3f direction = this.panState.camera.getMouseRay(context.mouseX, context.mouseY, viewport.x, viewport.y, viewport.w, viewport.h, originOffset);
        Vector3d origin = new Vector3d(this.panState.camera.position)
            .add(originOffset.x, originOffset.y, originOffset.z)
            .sub(this.panState.pivot.x, this.panState.pivot.y, this.panState.pivot.z);
        Vector3d destination = new Vector3d(direction).mul(Math.max(this.distance, MIN_DISTANCE) * 2F).add(origin);

        Intersectiond.intersectLineSegmentPlane(
            origin.x,
            origin.y,
            origin.z,
            destination.x,
            destination.y,
            destination.z,
            this.panState.plane.x,
            this.panState.plane.y,
            this.panState.plane.z,
            0,
            vector
        );

        return vector;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        /* Re-armed every frame: BBSRendering resets it at the start of every
         * world render, so ortho turns itself off the moment the orbit stops
         * driving the camera. Shaderpacks get the ortho matrix too — Iris
         * captures gbufferProjection from the same WorldRenderer#render
         * argument the mixin replaces, so pack math built on the matrices
         * stays consistent (analytic perspective assumptions, e.g. the sky
         * direction or depth linearization, are up to the pack). */
        BBSRendering.setOrthoDistance(this.ortho ? this.distance : -1F);

        this.updateAnchor(transition);

        if (!this.positioned)
        {
            Vector3f replay = this.getReplayPivot(transition);

            if (replay != null)
            {
                this.toLocal(replay);
                this.pivot.set(replay);
                this.targetPivot.set(replay);
                this.positioned = true;
            }
            else if (this.hasNoReplays())
            {
                this.seedPivotFromCamera(camera);
                this.positioned = true;
            }
        }

        Vector3f offset = this.getOffset();

        camera.position.set(this.toWorld(new Vector3f(this.pivot)));
        camera.position.add(offset);
        camera.rotation.set(-this.rotation.x, -(this.rotation.y + this.anchorYaw), 0F);
    }

    @Override
    public int getPriority()
    {
        return 20;
    }

    public Vector3d getOrbitCenter(float transition)
    {
        return new Vector3d(this.toWorld(new Vector3f(this.pivot)));
    }

    public void teleportPivotToReplay()
    {
        Vector3f replay = this.getReplayPivot(this.getCurrentTransition());

        if (replay != null)
        {
            this.targetPivot.set(this.toLocal(replay));
            this.positioned = true;
        }
    }

    public boolean isOrtho()
    {
        return this.ortho;
    }

    public void toggleOrtho()
    {
        this.ortho = !this.ortho;

        /* Toggling by hand takes the projection away from the axis snap: it
         * stays whatever the user set until they toggle it again. */
        this.autoOrtho = false;
    }

    public boolean isAttached()
    {
        return this.attached;
    }

    public void toggleAttachment()
    {
        this.attached = !this.attached;
        this.updateAnchor(this.getCurrentTransition());
    }

    private void updateAnchor(float transition)
    {
        Replay target = null;
        IEntity entity = null;

        if (this.attached)
        {
            target = this.controller.panel.replayEditor.getReplay();
            entity = target == null ? null : this.resolveEntity(target);

            if (entity == null)
            {
                target = this.anchorReplay;
                entity = target == null ? null : this.resolveEntity(target);
            }

            if (entity == null)
            {
                target = null;
            }
        }

        if (target != this.anchorReplay)
        {
            this.rebase(target, entity, transition);
        }
        else if (entity != null)
        {
            this.writeAnchor(entity, transition);
        }
    }

    private void rebase(Replay replay, IEntity entity, float transition)
    {
        this.toWorld(this.pivot);
        this.toWorld(this.targetPivot);
        this.rotation.y += this.anchorYaw;
        this.targetRotation.y += this.anchorYaw;

        this.anchorReplay = replay;

        if (entity == null)
        {
            this.anchorPosition.set(0D, 0D, 0D);
            this.anchorYaw = 0F;
        }
        else
        {
            this.writeAnchor(entity, transition);
        }

        this.toLocal(this.pivot);
        this.toLocal(this.targetPivot);
        this.rotation.y -= this.anchorYaw;
        this.targetRotation.y -= this.anchorYaw;
    }

    private void writeAnchor(IEntity entity, float transition)
    {
        this.anchorPosition.set(
            Lerps.lerp(entity.getPrevX(), entity.getX(), transition),
            Lerps.lerp(entity.getPrevY(), entity.getY(), transition),
            Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition)
        );
        this.anchorYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition));
    }

    private IEntity resolveEntity(Replay replay)
    {
        Film film = this.controller.panel.getData();

        if (film == null)
        {
            return null;
        }

        int index = film.replays.getList().indexOf(replay);

        return index < 0 ? null : this.controller.getEntities().get(index);
    }

    private Vector3f toWorld(Vector3f pivot)
    {
        return pivot.rotateY(this.anchorYaw).add((float) this.anchorPosition.x, (float) this.anchorPosition.y, (float) this.anchorPosition.z);
    }

    private Vector3f toLocal(Vector3f pivot)
    {
        return pivot.sub((float) this.anchorPosition.x, (float) this.anchorPosition.y, (float) this.anchorPosition.z).rotateY(-this.anchorYaw);
    }

    public void reset()
    {
        this.pivot.set(0F, 0F, 0F);
        this.targetPivot.set(0F, 0F, 0F);
        this.rotation.set(0F, MathUtils.PI);
        this.targetRotation.set(0F, MathUtils.PI);
        this.distance = 4F;
        this.targetDistance = 4F;
        this.positioned = false;
        this.orbiting = false;
        this.orbitButton = -1;
        this.velocityPosition.set(0, 0, 0);
        this.anchorReplay = null;
        this.anchorPosition.set(0D, 0D, 0D);
        this.anchorYaw = 0F;
    }

    private Vector3f getReplayPivot(float transition)
    {
        OrbitTarget target = this.getOrbitTarget(transition);

        return target == null ? null : new Vector3f((float) target.position.x, (float) target.position.y, (float) target.position.z);
    }

    private boolean hasNoReplays()
    {
        return this.controller.panel.getData() == null || this.controller.panel.getData().replays.getList().isEmpty();
    }

    /**
     * When there is nothing to focus on, place the orbit center in front of the
     * current camera (keeping its position and rotation), instead of leaving it at
     * the world origin which could be nowhere near the view.
     */
    private void seedPivotFromCamera(Camera camera)
    {
        this.targetRotation.set(-camera.rotation.x, -camera.rotation.y);
        this.rotation.set(this.targetRotation);

        Vector3f forward = this.rotateVector(0F, 0F, -1F, this.rotation.y, this.rotation.x, false).mul(this.distance);

        this.pivot.set((float) camera.position.x, (float) camera.position.y, (float) camera.position.z).add(forward);
        this.targetPivot.set(this.pivot);
    }

    private OrbitTarget getOrbitTarget(float transition)
    {
        IEntity entity = this.controller.getCurrentEntity();

        if (entity == null)
        {
            return null;
        }

        float renderYaw = MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition) + 180F);
        Form form = entity.getForm();
        double h = entity.getPickingHitbox().h / 2;
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition) + h;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

        if (form != null)
        {
            /* Shared with the anchor resolution below (nothing between them touches the pose): a form
             * anchored within its own tree would otherwise evaluate the same pose twice. */
            FormFrameCache frame = new FormFrameCache();
            MatrixCache map = FormFrameCache.collect(frame, form, entity, transition);
            String group = "anchor";

            if (form instanceof ModelForm modelForm)
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    String anchor = model.getAnchor();

                    group = anchor.isEmpty() ? group : anchor;
                }
            }

            Matrix4f anchor = map.get(group).matrix();

            if (anchor != null)
            {
                Anchor v = form.anchor.get();
                Matrix4f defaultMatrix = BaseFilmController.getMatrixForRenderWithRotation(entity, x, y, z, transition);
                Pair<Matrix4f, Float> totalMatrix = BaseFilmController.getTotalMatrix(this.controller.getEntities(), v, defaultMatrix, x, y, z, transition, 0, false, frame);

                if (totalMatrix.a != null)
                {
                    defaultMatrix = totalMatrix.a;
                }

                defaultMatrix.mul(anchor);

                Vector3f translate = defaultMatrix.getTranslation(Vectors.TEMP_3F);

                x += translate.x;
                y += translate.y;
                z += translate.z;
            }
        }

        return new OrbitTarget(new Vector3d(x, y, z), renderYaw);
    }

    private boolean canStart(UIContext context)
    {
        if (this.controller.panel.isFlying())
        {
            return context.mouseButton == 0;
        }

        return context.mouseButton == 0 || context.mouseButton == 2;
    }

    private boolean isPanning()
    {
        return this.orbitButton == 2;
    }

    private void cachePanState(UIContext context)
    {
        this.panState.pivot.set(this.toWorld(new Vector3f(this.pivot)));
        this.panState.camera.copy(this.controller.panel.getCamera());
        this.panState.plane.set(this.panState.camera.getLookDirection()).normalize();
        this.panState.intersection.set(this.calculateOnPlane(context));
    }

    private void pan(UIContext context)
    {
        Vector3d point = this.calculateOnPlane(context);
        Vector3f pivot = new Vector3f(this.panState.pivot);

        pivot.sub((float) point.x, (float) point.y, (float) point.z);
        pivot.add((float) this.panState.intersection.x, (float) this.panState.intersection.y, (float) this.panState.intersection.z);

        this.targetPivot.set(this.toLocal(pivot));
    }

    public void rotate(int dx, int dy)
    {
        if (dx == 0 && dy == 0)
        {
            return;
        }

        float orbitSpeed = this.controller.panel.dashboard.orbit.getAngleSpeed() * 4F;

        this.targetRotation.x = MathUtils.clamp(this.targetRotation.x - dy * orbitSpeed, -PITCH_LIMIT, PITCH_LIMIT);
        this.targetRotation.y -= dx * orbitSpeed;

        /* Orbiting off the axis gives the perspective back: an ortho view is
         * what an axis snap is for, but away from an axis it only costs the
         * depth cues. A projection the user picked themselves is left alone
         * (see autoOrtho). */
        if (this.autoOrtho)
        {
            this.ortho = false;
            this.autoOrtho = false;
        }
    }

    /**
     * Snap the orbit rotation so the camera ends up on the given axis side of
     * the pivot, looking at it. The axis is given in the anchor's space: for a
     * detached orbit (or one without a valid anchor) that is world space, for
     * an attached orbit it is the replay's space, matching the axes the
     * navigation ball displays. Yaw is unwrapped to the closest turn, so the
     * camera never spins the long way around.
     *
     * The snap turns the orthographic projection on (editorOrbitAxisOrtho): an
     * axis view is asked for to read the pose as a blueprint (front, side,
     * top), and perspective skews exactly the alignment it is being read for.
     * Orbiting away turns it back off, unless the user had turned it on
     * themselves — and with the setting off nothing arms autoOrtho in the
     * first place, so the projection is left alone in both directions.
     */
    public void snapToAxis(int x, int y, int z)
    {
        float pitch;
        float yaw;

        if (y != 0)
        {
            pitch = y > 0 ? -PITCH_LIMIT : PITCH_LIMIT;
            yaw = this.targetRotation.y;
        }
        else
        {
            float twoPi = MathUtils.PI * 2F;

            pitch = 0F;
            yaw = (float) Math.atan2(x, z);
            yaw += Math.round((this.targetRotation.y - yaw) / twoPi) * twoPi;
        }

        this.targetRotation.set(pitch, yaw);

        if (!this.ortho && BBSSettings.editorOrbitAxisOrtho.get())
        {
            this.ortho = true;
            this.autoOrtho = true;
        }
    }

    /**
     * Yaw of the attachment anchor (zero when detached), i.e. the rotation
     * between the orbit's local space and world space.
     */
    public float getAnchorYaw()
    {
        return this.anchorYaw;
    }

    private Vector3f getOffset()
    {
        return this.rotateVector(0F, 0F, 1F, this.rotation.y + this.anchorYaw, this.rotation.x, false).mul(this.distance);
    }

    private float getCurrentTransition()
    {
        UIContext context = this.controller.getContext();

        return context == null ? 0F : context.getTransition();
    }

    private record OrbitTarget(Vector3d position, float renderYaw)
    {}

    private static class PanState
    {
        private final Vector3f pivot = new Vector3f();
        private final Camera camera = new Camera();
        private final Vector3d plane = new Vector3d();
        private final Vector3d intersection = new Vector3d();
    }
}
