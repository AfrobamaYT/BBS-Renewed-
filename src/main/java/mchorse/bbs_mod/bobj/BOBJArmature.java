package mchorse.bbs_mod.bobj;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BOBJArmature
{
    /** Enable reuse of evaluated poses for solver graphs whose outputs are snapshot-safe. */
    public static volatile boolean ENABLE_POSE_CACHE = true;
    static volatile boolean NORMALIZE_DRIVEN_INPUTS = true;
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
        this.posenMarkieren();
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

        /** Inputs consumed by the solver in addition to bone transforms. */
        default long eingabeSignatur()
        {
            return 0L;
        }

        /** Only solvers without externally mutable evaluation output may use pose snapshots. */
        default boolean poseCacheSicher()
        {
            return false;
        }

        /** Axes that channel drivers always overwrite before evaluating their bone. */
        default int[] eingabeMasken(BOBJArmature armature)
        {
            return null;
        }

        /** Restore solver-owned references needed by later shape/rig consumers. */
        default void poseAusCache(BOBJArmature armature)
        {}

        /** Completed immutable output for incremental solvers; null invalidates their basis. */
        default void poseGespeichert(BOBJPoseSnapshot snapshot)
        {}
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
        boolean cachebar = ENABLE_POSE_CACHE
            && this.zwang != null && this.zwang.poseCacheSicher();
        int[] masken = cachebar && NORMALIZE_DRIVEN_INPUTS
            ? this.zwang.eingabeMasken(this) : null;

        if (masken != null && masken.length == 0)
        {
            masken = null;
        }

        long signatur = this.eingabeSignatur(masken);

        if (signatur == this.letzteEingabe && this.matrizenGueltig)
        {
            if (masken == null)
            {
                return;
            }

            BOBJPoseSnapshot aktuell = this.poseSnapshots == null ? null : this.poseSnapshots.get(signatur);

            if (aktuell != null)
            {
                aktuell.restoreDrivenChannels(this, masken);
                this.zwang.poseAusCache(this);
                return;
            }
        }

        if (cachebar && this.poseSnapshots != null)
        {
            BOBJPoseSnapshot snapshot = this.poseSnapshots.get(signatur);

            if (snapshot != null)
            {
                snapshot.restore(this);
                this.zwang.poseAusCache(this);
                this.letzteEingabe = signatur;
                this.matrizenGueltig = true;
                return;
            }
        }
        else if (!cachebar && this.poseSnapshots != null && !this.poseSnapshots.isEmpty())
        {
            this.poseSnapshots.clear();
        }

        this.letzteEingabe = signatur;
        this.matrizenGueltig = true;

        if (this.zwang != null && this.zwang.auswerten(this))
        {
            if (cachebar)
            {
                BOBJPoseSnapshot snapshot = BOBJPoseSnapshot.capture(this);
                this.zwang.poseGespeichert(snapshot);

                if (snapshot != null)
                {
                    if (this.poseSnapshots == null)
                    {
                        this.poseSnapshots = new LinkedHashMap<>(8, 0.75F, true);
                    }

                    this.poseSnapshots.put(signatur, snapshot);

                    if (this.poseSnapshots.size() > 4)
                    {
                        Iterator<Long> oldest = this.poseSnapshots.keySet().iterator();
                        oldest.next();
                        oldest.remove();
                    }
                }
            }

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
        if (this.zwang != null)
        {
            this.zwang.poseGespeichert(null);
        }

        if (this.poseSnapshots != null)
        {
            this.poseSnapshots.clear();
        }
    }

    /**
     * Fingerabdruck aller Eingaben von {@link BOBJBone#computeMatrix}.
     *
     * <p>Kein Initialisierer an den Feldern waere hier gefaehrlich: nach einem Hotswap sind
     * sie 0 bzw. false — und {@code matrizenGueltig == false} bedeutet „voll rechnen", also
     * ist der sichere Zustand auch der Standardwert.</p>
     */
    private long eingabeSignatur(int[] masken)
    {
        long h = 17L;

        for (BOBJBone bone : this.orderedBones)
        {
            mchorse.bbs_mod.utils.pose.Transform t = bone.transform;
            int maske = masken == null ? 0 : masken[bone.index];

            h = h * 31L + vektorSignatur(t.translate, maske & 7);
            h = h * 31L + vektorSignatur(t.rotate, maske >> 6 & 7);
            h = h * 31L + vektorSignatur(t.scale, maske >> 3 & 7);
            h = h * 31L + t.pivot.hashCode();
            h = h * 31L + t.quat.hashCode();
            h = h * 31L + ((maske & (1 << 9)) != 0
                ? mchorse.bbs_mod.utils.pose.Transform.RotationMode.EULER.ordinal() + 1
                : t.rotationMode == null ? 0 : t.rotationMode.ordinal() + 1);
            h = h * 31L + (bone.orient == null ? 0 : bone.orient.hashCode());
            h = h * 31L + (bone.offset == null ? 0 : bone.offset.hashCode());
        }

        if (this.zwang != null)
        {
            h = h * 31L + this.zwang.eingabeSignatur();
        }

        return h;
    }

    private static int vektorSignatur(Vector3f value, int maske)
    {
        if (maske == 0)
        {
            return value.hashCode();
        }

        int h = 1;
        h = 31 * h + ((maske & 1) != 0 ? 0 : Float.floatToIntBits(value.x));
        h = 31 * h + ((maske & 2) != 0 ? 0 : Float.floatToIntBits(value.y));
        h = 31 * h + ((maske & 4) != 0 ? 0 : Float.floatToIntBits(value.z));
        return h;
    }

    private long letzteEingabe;
    private boolean matrizenGueltig;
    private LinkedHashMap<Long, BOBJPoseSnapshot> poseSnapshots;

    /** Signature of the pose currently held by this shared armature; absent before evaluation. */
    public long ausgewerteteSignatur()
    {
        return this.matrizenGueltig ? this.letzteEingabe : Long.MIN_VALUE;
    }

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
        this.posenMarkieren();
    }
}
