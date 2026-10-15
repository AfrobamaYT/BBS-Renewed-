package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.graphics.InverseView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.vorschau.VorschauGitterZustand;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.Minecraft;
import org.joml.Intersectiond;
import org.joml.Matrix3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * Model renderer GUI element
 *
 * This base class can be used for full screen model viewer.
 */
public abstract class UIModelRenderer extends UIElement
{
    /**
     * ⛔ AUS, WEIL DER WEG DEN CLIENT REISST -- siehe {@link #renderGrid}.
     *
     * <p>Der Bild-im-Bild-Zeichner ist gebaut und seine Kette nachgewiesen, aber der
     * Blit-Bereich stimmt noch nicht. Auf {@code true} stellen, sobald die Bedeutung von
     * x0/y0/x1/y1 gemessen ist (Aufgabe 96).
     */
    /**
     * ⚠️ AUS, ABER NICHT MEHR AUS DEM ALTEN GRUND (2026-08-01).
     *
     * <p>Abgeschaltet wurde der Schalter, weil er den Client riss. Der Absturz kam vom
     * ausgelaufenen Zeichenbereich und ist mit dessen Freigabe (06fbc62e0) weg: einmal
     * angeschaltet und gemessen, kein Absturz mehr, und der Zeichner wird sauber erreicht
     * (41 Einreihungen, 41 Zustellungen, Grenzen exakt 200,18 bis 620,340).
     *
     * <p>Er bleibt trotzdem aus, weil er noch NICHTS ZEICHNET: die uebergebene Ansicht ist
     * eine Welt-Kameramatrix und passt nicht in den Raum des Bild-im-Bild-Zeichners. Ein
     * Schalter, der je Bild einen Durchgang kostet und nichts liefert, gehoert aus.
     * Anschalten, sobald die Ansicht stimmt -- Einzelheiten in VorschauGitterRenderer.
     */
    private static final boolean GITTER_UEBER_BILD_IM_BILD = true;

    private static Vector3d vec = new Vector3d();
    private static Matrix3d mat = new Matrix3d();

    protected IEntity entity = new StubEntity();

    protected int timer;
    protected int dragging;

    public Camera camera = new Camera();

    public Vector3f pos = new Vector3f();
    public Factor distance = new Factor(0, 0, 100, (x) -> Math.pow(x, 2) / 100D);
    public boolean grid = true;

    private Vector3d cachedPlaneIntersection = new Vector3d();
    private Vector3f cachedPos = new Vector3f();
    private Camera cachedCamera = new Camera();
    private Vector3d plane = new Vector3d();
    private float lastX;
    private float lastY;

    private long tick;
    private Matrix4f transform = new Matrix4f();

    public UIModelRenderer()
    {
        super();

        this.reset();
    }

    public void setTransform(Matrix4f transform)
    {
        this.transform = transform;
    }

    /**
     * The orthonormal axes of the frame the preview is actually drawn in &mdash;
     * what {@link mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace#GLOBAL}
     * means for anything rendered here. {@link #transform} is multiplied onto the
     * stack before the model AND before the grid ({@link #renderModel}), so it is
     * that frame, not the camera's, that reads as "the world" to the user: the
     * floor grid turns with it. Identity in a plain preview, so GLOBAL stays the
     * flat scene axes there; the model block's immersive editing sets it to the
     * BLOCK's own transform, and then GLOBAL follows the block the way the film's
     * follows the replay. Scale is divided out &mdash; these are directions, and
     * a scaled block must not stretch the gizmo's frame.
     */
    public Matrix3f getSceneAxes()
    {
        return MatrixStackUtils.stripScale(this.transform).get3x3(new Matrix3f());
    }

    /**
     * Lift a matrix out of the previewed form's own frame into the frame the
     * preview is drawn in &mdash; i.e. apply {@link #transform}, exactly as
     * {@link #renderModel} does before the model reaches the stack.
     *
     * <p>The gizmo needs this because its two halves are recovered from
     * different places: the drawn origin and axes come back out of the RENDER
     * matrix (so they already carry the transform), while the drag's Jacobian
     * and rotation axes are SAMPLED from the editor's own bone matrices (which
     * do not). With a plain preview the transform is the identity and the two
     * agree by accident; inside a rotated model block they would disagree by
     * the block's rotation, and every drag would run off the handles. Scale is
     * deliberately kept &mdash; a Jacobian must map local units to the scene's
     * real distances.
     */
    public Matrix4f toSceneMatrix(Matrix4f matrix)
    {
        return new Matrix4f(this.transform).mul(matrix);
    }

    public void setRotation(float yaw, float pitch)
    {
        this.camera.rotation.y = MathUtils.toRad(yaw);
        this.camera.rotation.x = MathUtils.toRad(pitch);
    }

    public void setPosition(float x, float y, float z)
    {
        this.pos.set(x, y, z);
    }

    public void setDistance(int distanceX)
    {
        this.distance.setX(distanceX);
    }

    public void setEntity(IEntity entity)
    {
        this.entity = entity;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void reset()
    {
        this.setDistance(15);
        this.setPosition(0, 1, 0);
        this.setRotation(0, 0);
    }

    public boolean isDragging()
    {
        return this.dragging != 0;
    }

    public boolean isDraggingPosition()
    {
        return this.dragging == 2;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.isDragging() && this.area.isInside(context) && (context.mouseButton == 0 || context.mouseButton == 2))
        {
            this.dragging = Window.isShiftPressed() || context.mouseButton == 2 ? 2 : 1;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            this.cachedPos.set(this.pos);
            this.cachedCamera.copy(this.camera);
            this.plane.set(0, 0, 1);
            this.rotateVector(this.plane);

            this.cachedPlaneIntersection = this.calculateOnPlane(context);
        }

        return false;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.isDragging())
        {
            int x = Integer.compare(-(int) context.mouseWheel, 0);

            if (Window.isCtrlPressed())
            {
                x *= 8;
            }

            this.distance.setX(this.distance.getX() + x);
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.dragging = 0;

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.updateLogic(context);

        context.batcher.clip(this.area, context);
        this.renderModel(context);
        context.batcher.unclip(context);

        super.render(context);
    }

    private void updateLogic(UIContext context)
    {
        /* Messwerkzeug, standardmaessig aus: haelt die Leerlauf-animation an, damit zwei
         * Aufnahmen desselben Zustands bildpunktgleich sind. Siehe Standbild -- der
         * Welt-Tick-Stopp erreicht diese Schleife nicht, sie zaehlt den UI-Tick. */
        if (mchorse.bbs_mod.graphics.Standbild.an())
        {
            this.tick = context.getTick();

            return;
        }

        long tick = context.getTick();
        long i = tick - this.tick;

        if (i > 10)
        {
            i = 10;
        }

        while (i > 0)
        {
            this.update();
            i --;
        }

        this.tick = tick;
    }

    /**
     * Update logic
     */
    protected void update()
    {
        this.timer += 1;
        this.entity.setAge(this.timer);
    }

    /**
     * Draw currently edited model
     */
    private void renderModel(UIContext context)
    {
        /* 26.2: der Tiefentest ist kein Schalter mehr, er steckt in der Pipeline jedes Zuges.
         * Fuer das Modell darunter traegt ihn ModelVAOPipeline (GREATER_THAN_OR_EQUAL, weil
         * 26.2 die Tiefe umgekehrt rechnet), fuer das Gitter FormPipelines.holen(...). Ein
         * Schalter hier erreicht sie nicht mehr. */

        this.setupPosition();
        this.setupViewport(context);

        PoseStack stack = context.render.batcher.pose();

        /* Cache the global stuff */
        MatrixStackUtils.cacheMatrices();

        /* Umkehren, weil die Pipeline oben GREATER_THAN_OR_EQUAL prueft, camera.projection aber
         * die alte OpenGL-Form traegt. Ohne das gewinnt die entferntere Flaeche — Beleg an
         * Projektion#setzenUndUmkehren.
         *
         * ⚠️ HIER IN PIXELN UNBELEGT (2026-08-11). Der Mechanismus ist gemessen, diese Stelle
         * hat aber heute keine tiefengepruefte 3D-Kundschaft: Modell und Bodengitter laufen
         * ueber Bild-im-Bild (dort steht der belegte Fix), und UIParticleSchemeRenderer
         * zeichnet mit tiefePruefen=false. A/B im Form-Editor: 233 Bildpunkte, also nichts.
         * Wer hier einen Sofortzug mit Tiefentest ergaenzt, hat den Fix schon. */
        mchorse.bbs_mod.graphics.Projektion.setzenUndUmkehren(this.camera.projection, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
        InverseView.set(new Matrix3f(this.camera.view).invert());

        /* Das Licht dreht mit der Umlaufkamera — wie in 1.21.1. Warum und mit welchen Zahlen
         * belegt, steht an UiLicht#ansicht. */
        mchorse.bbs_mod.graphics.UiLicht.ansicht(new Matrix3f(this.camera.view));

        /* Rendering begins... */
        stack.pushPose();
        MatrixStackUtils.multiply(stack, this.camera.view);
        stack.translate(-this.camera.position.x, -this.camera.position.y, -this.camera.position.z);
        MatrixStackUtils.multiply(stack, this.transform);

        /* ⚠️ VERHALTENSAENDERUNG 26.2: die beiden Lichtrichtungen sind weg.
         *
         * Hier standen zwei Richtungsvektoren (0, 0.85, ∓1), die mit der Kameraansicht
         * mitgedreht wurden -- das Licht folgte also der Umlaufkamera des Editors.
         * RenderSystem.setupLevelDiffuseLighting nahm sie entgegen und legte sie global ab.
         *
         * In 26.2 liegen die Lichtrichtungen in einem Uniform-Block auf der Grafikkarte, und
         * Vanilla gibt sie nur noch als FESTE Voreinstellungen heraus (Lighting.Entry). Einen
         * Weg, ein eigenes Richtungspaar zu setzen, gibt es oeffentlich nicht mehr; dafuer
         * braeuchte es einen eigenen Lighting-Block, und das waere ein Entwurf.
         *
         * ENTITY_IN_UI ist Vanillas Voreinstellung fuer genau diesen Fall (eine Figur in einer
         * Oberflaeche). Folge: das Licht steht jetzt FEST und dreht sich nicht mehr mit der
         * Kamera -- beim Umlaufen wandert der Glanz nicht mehr mit. */
        Minecraft mc = Minecraft.getInstance();

        mc.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);

        /* ⭐ DER SAMMLER MUSS HIER HERUM (2026-07-31, an einem funktionierenden Fall
         * abgelesen).
         *
         * Die Form-Palette des Morphing-Panels zeichnet ihre Vorschau korrekt -- ihr Weg
         * fuehrt ueber FormUtilsClient.renderUI, und der wickelt den Aufruf in
         * FormSammler.mitEigenemSammler. Das legt einen eigenen SubmitNodeStorage an, laesst
         * zeichnen und ruft danach renderAllFeatures, was aus eingereichten Auftraegen erst
         * ein Bild macht.
         *
         * UIModelRenderer rief renderUserModel direkt, ohne diese Klammer. In 26.2 heisst das:
         * die Auftraege werden eingereicht und NIEMAND fuehrt sie aus. Kein Fehler, kein Log,
         * nur ein schwarzes Feld -- betroffen waren Modell-Editor und Partikel-Editor
         * (Aufgaben 94, 96).
         *
         * ⚠️ Das Gitter gehoert MIT hinein: es zeichnet in dieselbe Szene und braucht
         * denselben Sammler. */
        mchorse.bbs_mod.forms.FormSammler.mitEigenemSammler(() ->
        {
            if (this.grid)
            {
                this.renderGrid(context);
            }

            this.renderUserModel(context);
        });

        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_FLAT);

        stack.popPose();

        /* Return back to orthographic projection */
        /* ⛔ HIER STAND, DAS ZURUECKSETZEN SEI UNNOETIG -- DAS WAR FALSCH (2026-08-01).
         *
         * Der alte Kommentar begruendete das Weglassen damit, "gesetzt hat ihn niemand mehr,
         * seit setupViewport() ohne viewport-Aufruf auskommt". setupViewport() SETZT ihn
         * aber: es ruft UIUtils.viewportArea, und die meldet den Ausschnitt bei
         * Zeichenbereich an (UIUtils:114).
         *
         * Folge des Weglassens: der Ausschnitt dieser Vorschau blieb nach dem Bild stehen.
         * Einziger Verbraucher ist GehaltenesNetz:133 -- also erbte JEDER spaetere
         * Netz-Zeichenauftrag das Rechteck dieses Panels, bis zufaellig ein Gizmo aufraeumte
         * (Gizmo:684 und :1605 sind die einzigen freigeben()-Aufrufe im Baum).
         *
         * ⚠️ Der Klassenkommentar von Zeichenbereich verspricht ein finally beim Aufrufer.
         * Das loest dieser Aufruf NICHT ein: fliegt oben eine Ausnahme, bleibt der Ausschnitt
         * stehen. Der normale Weg ist damit dicht, der Ausnahmeweg nicht -- bewusst so, weil
         * ein try/finally hier den ganzen Rumpf einruecken wuerde. */
        mchorse.bbs_mod.graphics.Zeichenbereich.freigeben();


        MatrixStackUtils.restoreMatrices();

        /* Das GL_ALWAYS entfaellt ersatzlos: es setzte den globalen Tiefenvergleich fuer die
         * Oberflaeche zurueck. Der GUI-Raum hat keine Tiefe mehr, die Ueberdeckung entscheidet
         * GuiGraphics.nextStratum(). */

        this.processInputs(context);
    }

    protected void processInputs(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.isDragging())
        {
            if (this.isDraggingPosition())
            {
                if (this.lastX != context.mouseX || this.lastY != context.mouseY)
                {
                    Vector3d newPoint = this.calculateOnPlane(context);

                    this.pos.set(this.cachedPos);
                    this.pos.sub((float) newPoint.x, (float) newPoint.y, (float) newPoint.z);
                    this.pos.add((float) this.cachedPlaneIntersection.x, (float) this.cachedPlaneIntersection.y, (float) this.cachedPlaneIntersection.z);

                    this.lastX = mouseX;
                    this.lastY = mouseY;
                }
            }
            else
            {
                this.camera.rotation.y -= MathUtils.toRad(this.lastX - mouseX);
                this.camera.rotation.x -= MathUtils.toRad(this.lastY - mouseY);

                this.lastX = mouseX;
                this.lastY = mouseY;
            }
        }
    }

    public void setupPosition()
    {
        this.camera.position.set(this.pos);

        vec.set(0, 0, -this.distance.getValue());
        this.rotateVector(vec);

        this.camera.position.x += vec.x;
        this.camera.position.y += vec.y;
        this.camera.position.z += vec.z;
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Vector3d vector = new Vector3d();
        Vector3d origin = new Vector3d(this.cachedCamera.position).sub(this.cachedPos);
        Vector3d destination = new Vector3d(this.cachedCamera.getMouseDirection(context.mouseX, context.mouseY, this.area.x, this.area.y, this.area.w, this.area.h)).mul(this.distance.getValue() * 2).add(origin);
        Intersectiond.intersectLineSegmentPlane(origin.x, origin.y, origin.z, destination.x, destination.y, destination.z, this.plane.x, this.plane.y, this.plane.z, 0, vector);

        return vector;
    }

    private void rotateVector(Vector3d vec)
    {
        mat.identity().rotateX(this.camera.rotation.x);
        mat.transform(vec);
        mat.identity().rotateY(MathUtils.PI - this.camera.rotation.y);
        mat.transform(vec);
    }

    protected void setupViewport(UIContext context)
    {
        /* Raw GL without a GL context under Vulkan: LWJGL aborted the JVM ~12 s after opening the
         * form, model or particle editor, no crash report (audit 2026-10-13). The model draws in
         * its own picture-in-picture pass, which brings its own depth. */
        if (mchorse.bbs_mod.graphics.GpuBackend.isOpenGl())
        {
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        }

        int[] viewport = UIUtils.viewportArea(this.area);

        this.camera.updatePerspectiveProjection(viewport[2], viewport[3]);
        this.camera.updateView();
    }

    /**
     * Draw your model here
     */
    protected abstract void renderUserModel(UIContext context);

    /**
     * Render block of grass under the model (which signify where
     * located the ground below the model)
     */
    protected void renderGrid(UIContext context)
    {
        Matrix4f matrix4f = new Matrix4f(context.batcher.pose().last().pose());

        /* 26.2: der Zug bringt seinen Zustand selbst mit. Tiefenpruefung an — das ist das
         * depthFunc(GL_LEQUAL) aus renderModel: die Vorschau loescht vorher die Tiefe und
         * zeichnet das Modell danach, das Gitter soll also hinter dem Modell verschwinden.
         * Kein Mischen (alle Linien undurchsichtig), kein Culling (bei Linien ohne Wirkung). */
        /* ⚠️ HIER STAND EIN SOFORTZUG, UND ER WAR SEIT DEM 26.2-PORT UNSICHTBAR.
         *
         * 26.2 sammelt die Oberflaeche erst und zeichnet sie danach; ein sofort geoeffneter
         * Durchgang malt VOR dem GUI und verschwindet unter dem Panel-Hintergrund. Das
         * blosse Einreihen ueber Batcher2D.netz half aber auch nicht -- die Geometrie kam an
         * und wurde mit der GUI-Projektion gezeichnet statt mit der Kameraprojektion dieser
         * Vorschau (beide Versuche gemessen, Aufgabe 96).
         *
         * Ein Bild-im-Bild-Zustand bekommt vom Bildgraphen eine eigene Textur MIT eigener
         * Projektion.
         *
         * ⛔ ABGESCHALTET 2026-07-31, WEIL ER DEN CLIENT REISST:
         *
         *     IllegalArgumentException: RenderPass render area
         *     RenderArea[x=422, y=281, width=1458, height=692]
         *     is out of bounds for texture of 854x480
         *
         * Der Blit-Bereich aus this.area passt nicht zur Textur, die der Bild-im-Bild-Renderer
         * anlegt. Beim blossen Ansehen faellt das nicht auf (das Feld blieb einfach schwarz) --
         * erst als der Nutzer das FENSTER VERGROESSERTE, wuchs der Bereich weit genug hinaus
         * und der Client starb. Ein Fehler, den kein Screenshot zeigt und keine Messung
         * gefunden haette, die nur ein stehendes Bild ansieht.
         *
         * Der Bereich ist damit als Ursache BELEGT, nicht mehr vermutet (Kandidat 2 des
         * Messplans in Aufgabe 96). Der Zeichner bleibt im Baum, der Aufruf ruht, bis die
         * Bedeutung von x0/y0/x1/y1 gemessen ist -- ein Absturz beim Nutzer wiegt schwerer
         * als ein fehlendes Gitter.
         *
         * DASS HIER NICHTS GEZEICHNET WIRD, IST KEIN RUECKSCHRITT: der alte Sofortzug war
         * ebenfalls unsichtbar. Es fehlt genau so viel wie vorher, nur ohne Absturz. */
        if (GITTER_UEBER_BILD_IM_BILD)
        {
            context.batcher.bildImBild(new VorschauGitterZustand(
                /* ⭐ PROJEKTION x ANSICHT, nicht nur die Ansicht (2026-08-01 gemessen).
                 *
                 * Hier stand nur die Pose des Stapels -- eine reine Ansichtsmatrix, an der
                 * unteren Zeile 0,0,0,1 abzulesen. Der Kommentar an scale() in
                 * VorschauGitterZustand behauptete, die Projektion sei darin schon
                 * eingerechnet; sie war es nie. Ohne sie gibt es keine Perspektive, und der
                 * Zeichner kann seinen Raum nicht auf die Flaeche abbilden.
                 *
                 * Projektion und Ansicht reisen GETRENNT: der Zeichner braucht beides
                 * einzeln, je nachdem ob er die Projektion dem Durchgang unterschiebt oder
                 * die Teilung durch w selbst rechnet. Die Pose traegt bereits Kameraansicht
                 * und die Verschiebung um die Kameraposition (siehe renderModel). */
                new Matrix4f(this.camera.projection),
                new Matrix4f(matrix4f),
                this.area.x, this.area.y, this.area.ex(), this.area.ey(),
                /* ⚠️ NICHT null: blitTexture reicht scissorArea an den BlitRenderState
                 * durch. Ob null dort erlaubt ist, ist ungeprueft -- also lieber der
                 * echte Bereich, der ohnehin richtig waere. */
                new net.minecraft.client.gui.navigation.ScreenRectangle(
                    this.area.x, this.area.y, this.area.w, this.area.h),
                new net.minecraft.client.gui.navigation.ScreenRectangle(
                    this.area.x, this.area.y, this.area.w, this.area.h)));
        }
    }
}
