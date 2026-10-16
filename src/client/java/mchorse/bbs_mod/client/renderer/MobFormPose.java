package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;

import java.util.Map;

/**
 * BBS' Pose einer MobForm auf die Vanilla-Modellteile legen — und wieder herunternehmen.
 *
 * <h2>⚠️ 26.2: WARUM DAS NICHT MEHR IN EINEM STUECK GEHT</h2>
 *
 * <p>Bis 1.21.1 lag alles in {@code LivingEntityRendererMixin}: nach {@code setupAnim} die
 * Winkel der {@link ModelPart}e um die Pose ergaenzen, zeichnen, am Ende von {@code render}
 * zuruecksetzen. Ein Aufruf, ein Fenster.
 *
 * <p>In 26.2 zerfaellt der Zeichenweg in ZWEI Phasen, und {@code setupAnim} des Rumpfmodells
 * ist in die ZWEITE gewandert (am Bytecode geprueft):
 *
 * <ul>
 *   <li>EINREICHEN — {@code LivingEntityRenderer.submit} legt einen Auftrag an
 *       ({@code SubmitNodeCollector.submitModel}). Ein {@code setupAnim} gibt es hier nur
 *       noch fuer die EBENEN (Ruestung, Kleidung), direkt vor der Ebenen-Schleife.</li>
 *   <li>ZEICHNEN — {@code ModelFeatureRenderer.prepareModel} ruft
 *       {@code Model.setupAnim(zustand)} und danach {@code renderToBuffer}. Erst HIER
 *       entsteht die Haltung des Rumpfmodells.</li>
 * </ul>
 *
 * <p>Wer die Modellteile also nur beim EINREICHEN veraendert, veraendert nichts am Bild:
 * das {@code setupAnim} der Zeichenphase rechnet sie aus dem Zustand neu. Deshalb wird die
 * Pose in BEIDEN Phasen aufgelegt — in der ersten fuer die Ebenen, in der zweiten fuer den
 * Rumpf — und jedes Mal im selben Aufruf wieder heruntergenommen.
 *
 * <h2>Wie die Pose in die Zeichenphase kommt</h2>
 *
 * <p>{@link MobFormRenderer} setzt {@code currentPose} nur um sein {@code submit} herum;
 * beim Zeichnen ist das Fenster laengst zu (und muesste es auch sein — sonst bekaeme die
 * naechste Form die Pose der vorigen). Die Pose reist deshalb AM ZUSTAND mit, ueber Fabrics
 * {@code RenderStateDataKey} — dieselbe Zusatzablage, ueber die {@link MorphRenderer} die
 * Entity mitschickt. Gestempelt wird einmal, am Kopf von {@code LivingEntityRenderer.submit}
 * (dort ist das Fenster offen), gelesen in beiden Phasen.
 *
 * <p>⚠️ IMMER stempeln, auch {@code null}: Render-Zustaende koennen wiederverwendet werden,
 * ein liegengebliebener Eintrag wuerde eine fremde Entity posieren.
 *
 * <p>NEBENEFFEKT, GEWOLLT: das Fenster war global — jede Entity, die waehrend eines
 * Form-Renders gezeichnet wurde, bekam die Pose. Jetzt haengt sie am Zustand, also genau an
 * der Entity, fuer die sie gemeint war.
 */
public final class MobFormPose
{
    private MobFormPose() {}

    /** Die verschmolzene Pose der Form, die diesen Zustand eingereicht hat, oder {@code null}. */
    public static final RenderStateDataKey<Pose> POSE_AM_ZUSTAND = RenderStateDataKey.create(() -> "bbs:mobform_pose");

    /**
     * Haengt die gerade aktive Form-Pose an den Zustand — aufzurufen beim EINREICHEN, solange
     * {@link MobFormRenderer}s Fenster offen ist.
     */
    public static void stempeln(EntityRenderState zustand)
    {
        if (zustand == null)
        {
            return;
        }

        ((FabricRenderState) zustand).setData(POSE_AM_ZUSTAND, verschmelzen());
    }

    /**
     * Legt die am Zustand haengende Pose auf die Modellteile und merkt sich die alten Werte
     * in {@link MobFormRenderer#getCache()}.
     *
     * @param zustandRoh der Zustand; {@code Object}, weil {@code ModelFeatureRenderer$Submit}
     *                   ihn nur so herausgibt (er traegt auch Block-Entity-Zustaende)
     */
    public static void auflegen(Object zustandRoh)
    {
        if (!(zustandRoh instanceof EntityRenderState zustand))
        {
            return;
        }

        Pose pose = ((FabricRenderState) zustand).getData(POSE_AM_ZUSTAND);

        if (pose == null)
        {
            return;
        }

        Entity entity = MorphRenderer.entityVon(zustand);

        if (entity == null)
        {
            /* Ohne Entity kein Klassen-Schluessel fuer die Teile-Tabelle. Kann nur passieren,
             * wenn der Zustand nicht ueber den Dispatcher lief — dann gilt Vanilla. */
            return;
        }

        /* Bones by vanilla's names, per model (BBS FS e341fa733) */
        mchorse.bbs_mod.forms.renderers.mob.MobKnochen rig = mchorse.bbs_mod.forms.renderers.mob.MobKnochen.von(entity);

        if (rig == null)
        {
            return;
        }

        for (Map.Entry<String, ModelPart> entry : rig.teile.entrySet())
        {
            ModelPart value = entry.getValue();
            PoseTransform poseTransform = rig.wert(pose.transforms, entry.getKey());

            if (poseTransform == null)
            {
                continue;
            }

            Gemerkt transform = new Gemerkt();

            transform.sichtbar = value.visible;
            value.visible &= poseTransform.visible; /* a pose can hide a mob's part (BBS FS fce301787) */
            transform.translate.x = value.x;
            transform.translate.y = value.y;
            transform.translate.z = value.z;
            transform.rotate.x = value.xRot;
            transform.rotate.y = value.yRot;
            transform.rotate.z = value.zRot;
            transform.scale.x = value.xScale;
            transform.scale.y = value.yScale;
            transform.scale.z = value.zScale;

            /* Vanilla ModelPart holds euler pitch/yaw/roll only, so a quaternion pose bone is
             * decomposed to its euler equivalent here instead of reading the stale rotate
             * triple. */
            Vector3f rotation = poseTransform.getEulerRotation(new Vector3f());

            value.x += poseTransform.translate.x;
            value.y += poseTransform.translate.y;
            value.z += poseTransform.translate.z;
            value.xRot += rotation.x;
            value.yRot += rotation.y;
            value.zRot += rotation.z;
            value.xScale += poseTransform.scale.x - 1F;
            value.yScale += poseTransform.scale.y - 1F;
            value.zScale += poseTransform.scale.z - 1F;

            MobFormRenderer.getCache().put(value, transform);
        }
    }

    /**
     * Setzt alle veraenderten Teile auf ihre gemerkten Werte zurueck.
     *
     * <p>⚠️ Muss im SELBEN Aufruf geschehen wie {@link #auflegen(Object)} — die Teile gehoeren
     * dem geteilten Modell des Renderers, jede naechste Entity zeichnet mit denselben.
     */
    public static void zuruecksetzen()
    {
        Map<ModelPart, Transform> cache = MobFormRenderer.getCache();

        if (cache.isEmpty())
        {
            /* Heisser Pfad: das laeuft pro eingereichtem Modell und pro Bild. Ohne diese
             * Abkuerzung zahlt jede Entity der Welt fuer ein Feature, das nur Formen nutzen. */
            return;
        }

        for (Map.Entry<ModelPart, Transform> entry : cache.entrySet())
        {
            Transform transform = entry.getValue();
            ModelPart value = entry.getKey();

            value.x = transform.translate.x;
            value.y = transform.translate.y;
            value.z = transform.translate.z;
            value.xRot = transform.rotate.x;
            value.yRot = transform.rotate.y;
            value.zRot = transform.rotate.z;
            value.xScale = transform.scale.x;
            value.yScale = transform.scale.y;
            value.zScale = transform.scale.z;
            if (transform instanceof Gemerkt gemerkt) value.visible = gemerkt.sichtbar;
        }

        cache.clear();
    }

    /**
     * Pose und Pose-Ueberlagerung der gerade gerenderten Form zu EINER Pose verrechnen.
     *
     * <p>Das passierte bis 1.21.1 im Mixin, bei jedem Zeichnen. Es passiert jetzt einmal beim
     * Stempeln — dasselbe Ergebnis, aber es ueberlebt den Weg in die Zeichenphase.
     */
    private static Pose verschmelzen()
    {
        Pose pose = MobFormRenderer.getCurrentPose();

        if (pose == null)
        {
            return null;
        }

        pose = pose.copy();

        Pose ueberlagerung = MobFormRenderer.getCurrentPoseOverlay();

        if (ueberlagerung == null)
        {
            /* Bis 1.21.1 stand hier keine Pruefung — die Ueberlagerung wurde immer zusammen
             * mit der Pose gesetzt. Eine Nullzeiger-Ausnahme mitten im Zeichenweg reisst in
             * 26.2 den ganzen Bildgraphen ab, deshalb hier lieber die nackte Pose. */
            return pose;
        }

        /* Then the extra pose tracks, in order, as a model form stacks them (BBS FS 8ce6f0238). */
        pose.ueberlagern(ueberlagerung);

        for (mchorse.bbs_mod.settings.values.core.ValuePose weitere : MobFormRenderer.getCurrentOverlays()) pose.ueberlagern(weitere.get());

        return pose;
    }

    /** A part's own values before the pose, its visibility included. */
    private static class Gemerkt extends Transform
    {
        boolean sichtbar;
    }
}
