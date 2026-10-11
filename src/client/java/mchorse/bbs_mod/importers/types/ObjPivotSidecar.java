package mchorse.bbs_mod.importers.types;

import com.google.common.io.Files;

import java.io.File;
import java.util.List;
import java.util.Locale;


public final class ObjPivotSidecar
{
    private ObjPivotSidecar()
    {
    }

    /** basename einer datei ohne die gegebene endung, getrimmt + lowercase (match-schlüssel). */
    private static String key(File file, String ext)
    {
        String name = file.getName();

        return name.substring(0, name.length() - ext.length()).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean endsWith(File file, String ext)
    {
        return file.getName().toLowerCase(Locale.ROOT).endsWith(ext);
    }

    /**
     * Ist diese datei eine pivot-sidecar zu einer *.obj im selben drop? Dann darf
     * {@link ModelDropImporter#importFiles} sie NICHT als eigenes modell installieren —
     * sie wird von {@link #installCompanions} in den ordner des OBJ-modells kopiert.
     */
    public static boolean isObjSidecar(File file, List<File> allFiles)
    {
        if (!endsWith(file, ".bbs.json"))
        {
            return false;
        }

        String base = key(file, ".bbs.json");

        for (File other : allFiles)
        {
            if (other != file && endsWith(other, ".obj") && key(other, ".obj").equals(base))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Begleit-dateien eines OBJ-drops in den modell-ordner legen:
     * <ul>
     *   <li>{@code <base>.bbs.json} → {@code model.bbs.json} (pivots/gruppen —
     *       CubicModelLoader merged sie per gruppen-name mit den OBJ-meshes)</li>
     *   <li>{@code <base>.mtl} → {@code model.mtl} (CubicModelLoader leitet den mtl-namen
     *       aus dem OBJ-dateinamen ab: model.obj → model.mtl)</li>
     * </ul>
     * Gesucht wird erst im drop selbst, dann neben der quelldatei auf der platte —
     * dieselbe reihenfolge wie {@code ModelDropImporter.findTexture}.
     *
     * @param objFile  die quell-OBJ (schon als model.obj kopiert)
     * @param allFiles alle dateien des drops
     * @param targetDir zielordner {@code config/bbs/assets/models/<id>/}
     */
    public static void installCompanions(File objFile, List<File> allFiles, File targetDir)
    {
        File sidecar = findCompanion(objFile, ".obj", ".bbs.json", allFiles);

        if (sidecar != null && !sidecarLooksSane(sidecar))
        {
            /* defekte sidecar NICHT kopieren: ein parent-name ohne eigene group NPEt in
             * Model.fromData (Z.374-378). Der NPE wird zwar von CubicLoader.load gefangen,
             * aber das ergebnis wäre ein modell das STILL ohne pivots lädt + stacktrace —
             * lieber explizit warnen und den alten (pivot-losen) import-weg nehmen. */
            sidecar = null;
        }

        if (sidecar != null)
        {
            try
            {
                Files.copy(sidecar, new File(targetDir, "model.bbs.json"));
                System.out.println("[ObjPivotSidecar] pivot-sidecar übernommen: " + sidecar.getName() + " → model.bbs.json");
            }
            catch (Exception e)
            {
                System.err.println("[ObjPivotSidecar] konnte sidecar nicht kopieren: " + sidecar);
                e.printStackTrace();
            }
        }

        File mtl = findCompanion(objFile, ".obj", ".mtl", allFiles);

        if (mtl != null)
        {
            try
            {
                Files.copy(mtl, new File(targetDir, "model.mtl"));
            }
            catch (Exception e)
            {
                System.err.println("[ObjPivotSidecar] konnte mtl nicht kopieren: " + mtl);
                e.printStackTrace();
            }

            installMtlTexture(mtl, objFile, targetDir);
        }
    }

    /**
     * MTL-referenzierte textur retten (gemessen am echten user-file: verity.mtl sagt
     * {@code map_Kd smile (1).png} — die liegt neben der OBJ, heißt aber NICHT
     * {@code <base>.png}, also findet {@code ModelDropImporter.findTexture} sie nie).
     * Wir kopieren sie als {@code model.png} in den modell-ordner — {@code CubicModelLoader}
     * pickt {@code model.png} automatisch als modell-textur (getLink model.png, Z.77).
     * NUR wenn (a) die map_Kd-datei neben der quelle existiert und (b) keine gleichnamige
     * {@code <base>.png} da ist (die hat vorrang — bestehende konvention, findTexture
     * überschreibt model.png danach ohnehin).
     */
    private static void installMtlTexture(File mtl, File objFile, File targetDir)
    {
        try
        {
            /* (b) gleichnamige png vorhanden? dann macht findTexture den job, wir nicht. */
            String base = key(objFile, ".obj");
            File dir = objFile.getParentFile();

            if (dir != null)
            {
                File[] siblings = dir.listFiles();

                if (siblings != null)
                {
                    for (File f : siblings)
                    {
                        if (f.isFile() && endsWith(f, ".png") && key(f, ".png").equals(base))
                        {
                            return;
                        }
                    }
                }
            }

            /* (a) map_Kd aus der MTL lesen — dateiname kann leerzeichen enthalten ("smile (1).png"),
             * also alles NACH dem token als name nehmen, nicht tokens[1]. */
            for (String line : java.nio.file.Files.readAllLines(mtl.toPath(), java.nio.charset.StandardCharsets.UTF_8))
            {
                String trimmed = line.trim();

                if (!trimmed.startsWith("map_Kd ") || trimmed.startsWith("map_Kd_"))
                {
                    continue;
                }

                String texName = trimmed.substring("map_Kd ".length()).trim();
                File tex = new File(mtl.getParentFile(), texName);

                if (tex.isFile() && endsWith(tex, ".png"))
                {
                    Files.copy(tex, new File(targetDir, "model.png"));
                    System.out.println("[ObjPivotSidecar] MTL-textur übernommen: " + texName + " → model.png");
                }
                else if (!tex.isFile())
                {
                    System.out.println("[ObjPivotSidecar] MTL referenziert textur \"" + texName + "\", datei liegt aber nicht neben der OBJ — übersprungen");
                }

                return;
            }
        }
        catch (Exception e)
        {
            System.err.println("[ObjPivotSidecar] MTL-textur-übernahme fehlgeschlagen: " + mtl);
            e.printStackTrace();
        }
    }

    /**
     * Sidecar-vorabprüfung mit BBS' EIGENEM parser (DataToString — derselbe den CubicLoader
     * nutzt, kein format-drift): (a) parsebar, (b) model.groups nicht leer, (c) jeder
     * "parent"-verweis zeigt auf eine EXISTIERENDE group. (c) ist die NPE-falle in
     * Model.fromData Z.374-378: flatGroups.get(parent)==null → group.children.add NPE.
     */
    private static boolean sidecarLooksSane(File sidecar)
    {
        try
        {
            String text = new String(java.nio.file.Files.readAllBytes(sidecar.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            mchorse.bbs_mod.data.types.BaseType parsed = mchorse.bbs_mod.data.DataToString.fromString(text);

            if (!(parsed instanceof mchorse.bbs_mod.data.types.MapType root) || !root.has("model"))
            {
                System.err.println("[ObjPivotSidecar] sidecar unlesbar oder ohne \"model\"-key, wird ignoriert: " + sidecar.getName());

                return false;
            }

            mchorse.bbs_mod.data.types.MapType groups = root.getMap("model").getMap("groups");

            if (groups.keys().isEmpty())
            {
                System.err.println("[ObjPivotSidecar] sidecar hat keine groups, wird ignoriert: " + sidecar.getName());

                return false;
            }

            for (String key : groups.keys())
            {
                String parent = groups.getMap(key).getString("parent");

                if (!parent.isEmpty() && !groups.has(parent))
                {
                    System.err.println("[ObjPivotSidecar] sidecar-group \"" + key + "\" verweist auf parent \""
                        + parent + "\" den es nicht gibt (NPE-falle in Model.fromData) — sidecar wird ignoriert: " + sidecar.getName());

                    return false;
                }
            }

            return true;
        }
        catch (Exception e)
        {
            System.err.println("[ObjPivotSidecar] sidecar-prüfung fehlgeschlagen, wird ignoriert: " + sidecar);
            e.printStackTrace();

            return false;
        }
    }

    /** gleichnamige begleit-datei (trimmed/case-insensitive) im drop oder neben der quelle. */
    private static File findCompanion(File modelFile, String modelExt, String wantExt, List<File> allFiles)
    {
        String base = key(modelFile, modelExt);

        /* (1) im selben drop */
        for (File f : allFiles)
        {
            if (f != modelFile && endsWith(f, wantExt) && key(f, wantExt).equals(base))
            {
                return f;
            }
        }

        /* (2) neben der quelldatei — exakter name zuerst, sonst trimmed scan */
        File dir = modelFile.getParentFile();

        if (dir != null)
        {
            File exact = new File(dir, modelFile.getName().substring(0, modelFile.getName().length() - modelExt.length()) + wantExt);

            if (exact.exists())
            {
                return exact;
            }

            File[] siblings = dir.listFiles();

            if (siblings != null)
            {
                for (File f : siblings)
                {
                    if (f.isFile() && endsWith(f, wantExt) && key(f, wantExt).equals(base))
                    {
                        return f;
                    }
                }
            }
        }

        return null;
    }
}
