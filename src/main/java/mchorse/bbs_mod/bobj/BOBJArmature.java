package mchorse.bbs_mod.bobj;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BOBJArmature
{
    /**
     * Name of this armature 
     */
    public String name;

    /**
     * Map of all bones in this armature 
     */
    public Map<String, BOBJBone> bones = new HashMap<String, BOBJBone>();

    /**
     * List of all bones stored in {@link #bones}, but ordered by index 
     */
    public List<BOBJBone> orderedBones = new ArrayList<BOBJBone>();

    /**
     * Array of matrices which are going to be used for transforming
     * vertices.
     */
    public Matrix4f[] matrices;

    /** IK-Ketten aus arm_ik-Zeilen (eigener Exporter-Fork); leer bei Fremd-Exporten. */
    public List<BOBJIKChain> ikChains = new ArrayList<>();

    /**
     * Whether this armature was initialized already 
     */
    private boolean initialized;

    public BOBJArmature(String name)
    {
        this.name = name;
    }

    public void addBone(BOBJBone bone)
    {
        this.bones.put(bone.name, bone);
        this.orderedBones.add(bone);
    }

    /**
     * Initiate the armature. This method is responsible for connecting 
     * parent bones to their children and initializing matrix array. 
     * This method should be invoked only once.
     */
    public void initArmature()
    {
        if (!this.initialized)
        {
            /* "Connect" parent bones to children bones */
            for (BOBJBone bone : this.bones.values())
            {
                if (!bone.parent.isEmpty())
                {
                    bone.parentBone = this.bones.get(bone.parent);
                    bone.relBoneMat.set(bone.parentBone.boneMat);
                    bone.relBoneMat.invert();
                    bone.relBoneMat.mul(bone.boneMat);
                }
                else
                {
                    bone.relBoneMat.set(bone.boneMat);
                }
            }

            /* Sort bones according to their index */
            this.orderedBones.sort(Comparator.comparingInt((o) -> o.index));

            this.matrices = new Matrix4f[this.orderedBones.size()];
            this.initialized = true;
        }
    }

    /**
     * Setup matrices  
     */
    /**
     * Der importierte Constraint-Graph des Blender-Rigs (Client setzt ihn beim Binden).
     * Er uebernimmt den GANZEN Durchlauf, weil er eine andere Reihenfolge braucht:
     * Blenders Abhaengigkeiten (Ziel vor Besitzer), nicht nur Eltern vor Kind.
     */
    public interface Zwang
    {
        boolean auswerten(BOBJArmature armature);
    }

    public Zwang zwang;

    /**
     * Blenders Rahmen fuers Skinning: {@code inv(G·W)}, BBS-Armature-Raum nach Blender-Pose-Raum.
     * Setzt die Rig-Mechanik; {@code null} = kein Rig (dann Einheit). Die B-Bone-Segmentwahl je
     * Vertex ({@code RigBBone.segmentIndex}) rechnet in Blenders Pose-Raum.
     */
    public float[] rigPoseWandler;

    public void setupMatrices()
    {
        /* ⛔ NUR RECHNEN, WENN SICH DIE EINGABEN GEAENDERT HABEN. Gemessen am 2026-08-21:
         * bei stehendem Film lieferte diese Methode sechsmal hintereinander denselben
         * Pose-Hash (-1646228619), rund alle 100 ms — und zog dabei die volle Rig-Mechanik
         * samt IK-Solver mit. Das war der groesste Einzelposten der Allokationslast
         * (async-profiler: ~700 MB/s, Old Gen 91,75 %, ~32 % CPU im G1-Concurrent-Mark).
         *
         * Der Fingerabdruck deckt alles ab, was `computeMatrix` liest: `transform` jedes
         * Bones (translate/rotate/scale/pivot/quat). Aendert eine Rig-Mechanik ihre Ausgabe
         * ohne diese Felder anzufassen, MUSS `posenMarkieren()` gerufen werden — sonst
         * friert die Pose ein. Deshalb ist das Verwerfen oeffentlich und nicht versteckt. */
        long signatur = this.eingabeSignatur();

        if (signatur == this.letzteEingabe && this.matrizenGueltig)
        {
            return;
        }

        this.letzteEingabe = signatur;
        this.matrizenGueltig = true;

        if (this.zwang != null && this.zwang.auswerten(this))
        {
            return;
        }

        for (BOBJBone bone : this.orderedBones)
        {
            this.matrices[bone.index] = bone.compute();
        }
    }

    /** Nach dem naechsten {@link #setupMatrices()} wird wieder voll gerechnet. */
    public void posenMarkieren()
    {
        this.matrizenGueltig = false;
    }

    /**
     * Fingerabdruck aller Eingaben von {@link BOBJBone#computeMatrix}.
     *
     * <p>Kein Initialisierer an den Feldern waere hier gefaehrlich: nach einem Hotswap sind
     * sie 0 bzw. false — und {@code matrizenGueltig == false} bedeutet „voll rechnen", also
     * ist der sichere Zustand auch der Standardwert.</p>
     */
    private long eingabeSignatur()
    {
        long h = 17L;

        for (BOBJBone bone : this.orderedBones)
        {
            mchorse.bbs_mod.utils.pose.Transform t = bone.transform;

            h = h * 31L + t.translate.hashCode();
            h = h * 31L + t.rotate.hashCode();
            h = h * 31L + t.scale.hashCode();
            h = h * 31L + t.pivot.hashCode();
            h = h * 31L + t.quat.hashCode();
        }

        return h;
    }

    private long letzteEingabe;
    private boolean matrizenGueltig;

    public void copyOrder(BOBJArmature armature)
    {
        for (BOBJBone bone : armature.orderedBones)
        {
            BOBJBone thisBone = this.bones.get(bone.name);

            if (thisBone != null)
            {
                thisBone.index = bone.index;
            }
        }

        Collections.sort(this.orderedBones, (o1, o2) -> o1.index - o2.index);
    }
}