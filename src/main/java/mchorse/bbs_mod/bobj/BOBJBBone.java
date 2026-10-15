package mchorse.bbs_mod.bobj;

/**
 * Bendy-Bone-Daten eines Knochens: die exportierten Blender-Felder ({@code Bone.bbone_*} und
 * die Pose-Kanaele {@code PoseBone.bbone_*}) plus der Laufzeit-Cache, den Blender in
 * {@code bPoseChannel_Runtime} haelt (Ruhe-, Pose-, Verform-Matrizen je Segment).
 *
 * <p>Anordnung der Matrizen wie ueberall in der Rig-Laufzeit: Blenders Layout, 16 Floats,
 * {@code m[i * 4 + j] = mat[i][j]}.
 */
public final class BOBJBBone
{
    public int segmente = 1;
    public float x;
    public float z;
    /** {@code bbone_mapping_mode}: STRAIGHT oder CURVED. */
    public String mapping = "STRAIGHT";
    /** Griff-Typen Anfang/Ende: AUTO, ABSOLUTE, RELATIVE, TANGENT. */
    public String[] griffTyp = {"AUTO", "AUTO"};
    /** Eigene Griff-Knochen (Namen), sonst null. */
    public String[] griff = {null, null};
    public BOBJBone[] griffKnochen = {null, null};
    /** {@code bbone_handle_use_scale_start/end} je x/y/z. */
    public boolean[][] griffSkala = {{false, false, false}, {false, false, false}};
    /** {@code bbone_handle_use_ease_start/end}. */
    public boolean[] griffEase = {false, false};
    public boolean endrollAlsInroll;
    public boolean skaliertEasing;

    /* Ruheform (Knochen-Ebene). */
    public float[] ruheEase = {0F, 0F};
    public float[] ruheRoll = {0F, 0F};
    public float[] ruheKurveIn = {0F, 0F};
    public float[] ruheKurveOut = {0F, 0F};
    public float[] ruheSkalaIn = {1F, 1F, 1F};
    public float[] ruheSkalaOut = {1F, 1F, 1F};

    /* Pose-Kanaele (Animator / Treiber). */
    public float[] poseEase = {0F, 0F};
    public float[] poseRoll = {0F, 0F};
    public float[] poseKurveIn = {0F, 0F};
    public float[] poseKurveOut = {0F, 0F};
    public float[] poseSkalaIn = {1F, 1F, 1F};
    public float[] poseSkalaOut = {1F, 1F, 1F};

    /* Laufzeit-Cache (Blenders bPoseChannel_Runtime). */
    /** Ruhe-Segmente, {@code segmente + 1} Matrizen. */
    public float[][] ruheMatrizen;
    /** Pose-Segmente, {@code segmente + 1} Matrizen. */
    public float[][] poseMatrizen;
    /** Verform-Matrizen, {@code segmente + 2}: [0] = inv(arm_mat), danach je Stuetzpunkt. */
    public float[][] verformMatrizen;
    /** Dieselben Verform-Matrizen im BBS-Armature-Raum (fuers Skinning), Index wie oben. */
    public float[][] verformBBS;
    /** {@link #verformBBS} als JOML-Matrizen — das, was der VAO je Vertex multipliziert. */
    public org.joml.Matrix4f[] verformJoml;
    /** Dual-Quaternionen je Stuetzpunkt fuer Preserve Volume. */
    public Object[] dualQuats;
    /** CURVED-Mapping: Ebenen der Segmentgrenzen (Punkt, Normale, Versatz, Tiefenskala). */
    public float[][] grenzPunkt;
    public float[][] grenzNormale;
    public float[] grenzVersatz;
    public float[] grenzTiefe;
    public float bogenLaengeKehrwert;

    public boolean aktiv()
    {
        return this.segmente > 1 && this.verformMatrizen != null;
    }
}
