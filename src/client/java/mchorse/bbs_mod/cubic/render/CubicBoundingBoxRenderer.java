package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Misst die Huellbox eines Cubic-Modells — die ECHTE Ausdehnung seiner Geometrie, nicht die
 * eingestellte Trefferbox.
 *
 * <h2>Warum es diese Klasse gibt</h2>
 *
 * <p>{@code Form.hitboxWidth}/{@code hitboxHeight} sind EINSTELLWERTE mit Vorgabe 0.5/1.8. Ein
 * Ender-Drache meldet damit dieselbe Hoehe wie ein Spieler. Alles, was aus der Groesse eines
 * Darstellers etwas ausrechnet (das Rahmen der Orbit-Kamera, Maus 4), rechnet mit dieser Vorgabe
 * falsch, ohne dass es auffaellt — es kommt ja immer irgendeine Zahl heraus.</p>
 *
 * <h2>⛔ WARUM DIE RECHNUNG NICHT VON HAND ZUSAMMENGESETZT IST</h2>
 *
 * <p>In die Ausdehnung gehen drei Faktoren ein, die man einzeln falsch setzen kann und deren
 * Fehler jeweils eine PLAUSIBLE Zahl liefern: die 1/16-Einheiten der Modelldaten, die Pose (jeder
 * Knochen hat eine eigene Kette aus Verschiebung/Drehung/Skalierung) und die Reihenfolge, in der
 * das alles wirkt. Deshalb wird hier NICHTS nachgebaut: diese Klasse ist ein
 * {@link ICubicRenderer} und laeuft ueber {@link CubicRenderer#processRenderModel} — sie erbt
 * {@link ICubicRenderer#applyGroupTransformations} unveraendert und sieht damit per Bauart genau
 * die Matrix, mit der auch gezeichnet wird. Aendert jemand die Renderer-Kette, aendert sich die
 * Messung mit.</p>
 *
 * <p>Ergebnis ist der Raum des MODELLS (Bloecke, 1/16 schon herausgerechnet), also noch OHNE die
 * Skalierung der ModelInstance und ohne die Transform der Form. Wer eine Weltgroesse braucht,
 * multipliziert die beiden dazu — getrennt gehalten, damit die Messung fuer sich pruefbar bleibt.</p>
 *
 * <h2>⛔ GEMESSEN WIRD DIE RUHELAGE, NICHT DIE LAUFENDE POSE</h2>
 *
 * <p>Der erste Entwurf las {@code group.current}, also die Pose des letzten Renderdurchgangs — das
 * schien richtig, weil daraus auch gezeichnet wird. Die Gegenprobe sagte etwas anderes:
 * {@code player/steve} kam mit {@code minY = -4.02} und 3,03 Bloecken Hoehe heraus. Grund: eine
 * ModelInstance, die noch nie gezeichnet wurde, hat ein {@code current} auf Rohvorgaben statt auf
 * der Ruhelage, und {@link ICubicRenderer#translateGroup} verschiebt dann jeden Knochen um seinen
 * eigenen Ursprung nach unten. Eine Zahl kam trotzdem heraus, und sie sah brauchbar aus.</p>
 *
 * <p>Deshalb {@code initial}. Das hat drei Vorteile, die zusammen den Ausschlag geben: es ist
 * unabhaengig davon, ob und was zuletzt gezeichnet wurde; es ist unabhaengig davon, welcher
 * Darsteller zuletzt dran war (eine ModelInstance ist pro Modell-ID GETEILT, siehe
 * {@code ModelManager#getModel}); und es ist WIEDERHOLBAR — zweimal dieselbe Taste auf einem
 * laufenden Darsteller ergibt sonst zwei verschiedene Groessen, was beim Rahmen genau der
 * unberechenbare Zoom waere, den es abzustellen galt.</p>
 *
 * <p>Preis: ein Modell, das seine Arme ausbreitet, meldet die Ruhe-Ausdehnung. Fuer eine Groesse
 * ist das die stabilere Wahl, fuer eine exakte Pro-Frame-Box waere es falsch.</p>
 */
public class CubicBoundingBoxRenderer implements ICubicRenderer
{
    private final Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    private final Vector3f max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);

    private boolean anyGeometry;

    /* Wiederverwendet, damit ein Modell mit tausenden Wuerfeln keine Vektorflut erzeugt. */
    private final Vector4f punkt = new Vector4f();

    /**
     * Die Huellbox der Modellgeometrie im Modellraum (Bloecke), oder {@code null}, wenn das Modell
     * keine Geometrie hat — dann gibt es nichts zu messen, und der Aufrufer muss das entscheiden,
     * nicht eine 0 geschenkt bekommen.
     */
    public static AABB messen(Model model)
    {
        if (model == null || model.topGroups.isEmpty())
        {
            return null;
        }

        CubicBoundingBoxRenderer messer = new CubicBoundingBoxRenderer();

        CubicRenderer.processRenderModel(messer, null, new PoseStack(), model);

        return messer.ergebnis();
    }

    private AABB ergebnis()
    {
        if (!this.anyGeometry)
        {
            return null;
        }

        return new AABB(
            this.min.x, this.min.y, this.min.z,
            this.max.x - this.min.x, this.max.y - this.min.y, this.max.z - this.min.z
        );
    }

    /**
     * Die Ruhelage-Fassung von {@link ICubicRenderer#applyGroupTransformations}. Sie unterscheidet
     * sich in genau einem Punkt: ueberall, wo die Vorlage {@code group.current} liest, steht hier
     * {@code group.initial} — warum, steht oben im Klassenkopf.
     *
     * <p>Die Verschiebung der Vorlage ({@code -(current.translate - initial.translate) / 16}) faellt
     * damit weg, weil sie in der Ruhelage null ist. Der Rest ist Schritt fuer Schritt dieselbe
     * Reihenfolge: an den Drehpunkt, drehen, skalieren, zurueck — die Wuerfelkoordinaten liegen im
     * Modellraum, nicht im Knochenraum, deshalb dreht ein Knochen UM seinen Punkt statt seine
     * Geometrie erst dorthin zu tragen. {@code offset} (die IK-Streckung) bleibt aussen vor: sie
     * ist ein Zustand des laufenden Bildes, kein Teil der Ruhelage.</p>
     */
    @Override
    public void applyGroupTransformations(PoseStack stack, ModelGroup group)
    {
        /* Euler und Skalierung 1 sind hier keine Annahme, sondern die Datenform: ModelGroup#fromData
         * schreibt auf initial ausschliesslich translate und rotate, und im ganzen Baum setzt nichts
         * initial.scale/quat/rotationMode. Ein Quaternion-Zweig waere Code fuer einen Fall, den es
         * nicht gibt — die Skalierung steht trotzdem drin, weil sie zu lesen nichts kostet. */
        Vector3f pivot = group.initial.translate;
        Vector3f rotate = group.initial.rotate;
        Vector3f scale = group.initial.scale;

        stack.translate(pivot.x / 16F, pivot.y / 16F, pivot.z / 16F);

        if (rotate.x != 0F || rotate.y != 0F || rotate.z != 0F)
        {
            stack.rotate(Matrices.toLocalRotationZYXDegrees(rotate));
        }

        MatrixStackUtils.scaleStack(stack, scale.x, scale.y, scale.z);

        stack.translate(-pivot.x / 16F, -pivot.y / 16F, -pivot.z / 16F);
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, PoseStack stack, ModelGroup group, Model model)
    {
        for (ModelCube cube : group.cubes)
        {
            this.messeWuerfel(stack, cube);
        }

        for (ModelMesh mesh : group.meshes)
        {
            this.messeMesh(stack, mesh);
        }

        /* false = weiterlaufen; wir wollen JEDE Gruppe sehen, nicht bei der ersten aufhoeren. */
        return false;
    }

    /**
     * Die acht Ecken eines Wuerfels, in derselben Reihenfolge aufgestellt wie
     * {@link ModelCube#generateQuads} sie schreibt (Ursprung minus/plus Aufblasung, geteilt durch
     * 16), unter derselben Wuerfel-Eigendrehung, die {@link CubicCubeRenderer#renderCube} setzt.
     * Ecken statt Quads, damit auch ein Wuerfel ohne UV-Belegung — der also gar keine Quads
     * erzeugt hat — mitgemessen wird.
     */
    private void messeWuerfel(PoseStack stack, ModelCube cube)
    {
        stack.pushPose();
        CubicCubeRenderer.moveToPivot(stack, cube.pivot);
        CubicCubeRenderer.rotate(stack, cube.rotate);
        CubicCubeRenderer.moveBackFromPivot(stack, cube.pivot);

        float minX = (cube.origin.x - cube.inflate) / 16F;
        float minY = (cube.origin.y - cube.inflate) / 16F;
        float minZ = (cube.origin.z - cube.inflate) / 16F;
        float maxX = (cube.origin.x + cube.size.x + cube.inflate) / 16F;
        float maxY = (cube.origin.y + cube.size.y + cube.inflate) / 16F;
        float maxZ = (cube.origin.z + cube.size.z + cube.inflate) / 16F;

        Matrix4f matrix = stack.last().pose();

        this.nimmAuf(matrix, minX, minY, minZ);
        this.nimmAuf(matrix, maxX, minY, minZ);
        this.nimmAuf(matrix, minX, maxY, minZ);
        this.nimmAuf(matrix, maxX, maxY, minZ);
        this.nimmAuf(matrix, minX, minY, maxZ);
        this.nimmAuf(matrix, maxX, minY, maxZ);
        this.nimmAuf(matrix, minX, maxY, maxZ);
        this.nimmAuf(matrix, maxX, maxY, maxZ);

        stack.popPose();
    }

    /**
     * Ein Mesh (BOBJ/OBJ-Geometrie) wird ueber seine Grunddaten gemessen. Die liegen MAL 16 vor
     * ({@code ModelData#fill} multipliziert beim Laden), und {@code ModelVertex#set} teilt sie
     * beim Zeichnen wieder — hier steht dieselbe Teilung, sonst kaeme ein Mesh-Modell 16-mal zu
     * gross heraus. Formschluessel bleiben aussen vor: sie verschieben einzelne Punkte, aendern
     * die Groessenordnung aber nicht, und sie einzurechnen hiesse den ganzen Mischcode doppelt zu
     * halten.
     */
    private void messeMesh(PoseStack stack, ModelMesh mesh)
    {
        stack.pushPose();
        CubicCubeRenderer.moveToPivot(stack, mesh.origin);
        CubicCubeRenderer.rotate(stack, mesh.rotate);
        CubicCubeRenderer.moveBackFromPivot(stack, mesh.origin);

        Matrix4f matrix = stack.last().pose();

        for (Vector3f vertex : mesh.baseData.vertices)
        {
            this.nimmAuf(matrix, vertex.x / 16F, vertex.y / 16F, vertex.z / 16F);
        }

        stack.popPose();
    }

    private void nimmAuf(Matrix4f matrix, float x, float y, float z)
    {
        this.punkt.set(x, y, z, 1F);
        matrix.transform(this.punkt);

        /* Von Hand statt Vector3f#min/max: die nehmen einen Vektor, den man hier je Punkt neu
         * anlegen muesste — bei einem Modell mit tausenden Wuerfeln zehntausende Wegwerf-Objekte
         * fuer eine Messung, die pro Tastendruck einmal laeuft. */
        if (this.punkt.x < this.min.x) this.min.x = this.punkt.x;
        if (this.punkt.y < this.min.y) this.min.y = this.punkt.y;
        if (this.punkt.z < this.min.z) this.min.z = this.punkt.z;

        if (this.punkt.x > this.max.x) this.max.x = this.punkt.x;
        if (this.punkt.y > this.max.y) this.max.y = this.punkt.y;
        if (this.punkt.z > this.max.z) this.max.z = this.punkt.z;

        this.anyGeometry = true;
    }
}
