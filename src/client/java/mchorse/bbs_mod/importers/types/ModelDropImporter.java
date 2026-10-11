package mchorse.bbs_mod.importers.types;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.types.IImporter;
import mchorse.bbs_mod.l10n.keys.IKey;

import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ModelDropImporter — zieh EIN oder MEHRERE modell-dateien ins BBS-fenster, sie werden nativ als
 * modelle installiert (kein install-dialog, kein externer dienst). Deckt ALLE modell-formate ab die
 * BBS' {@code ModelManager} laden kann.
 *
 * WARUM DIESER WEG (Regel 6 / host-nicht-neubauen): BBS hat das drag-drop-fundament schon —
 * MC/vanilla {@code Screen.filesDragged} → {@code UIScreen.acceptFilePaths} → {@code Importers}-registry.
 * Die BBS-autoren notierten in {@code Importers.java} sogar explizit als gewünschtes feature:
 * "Models (as folders, as multiple files, etc.)" — nie gebaut. Wir bauen genau diesen fehlenden
 * importer und hängen ihn über {@code RegisterImportersEvent} ein (kein BBS-source-edit, invariante 2).
 *
 * WAS ES TUT (pro modell-datei im drop):
 *   1. ordner {@code config/bbs/assets/models/<name>/} anlegen (name = dateiname ohne endung, entkollidiert)
 *   2. modell-datei als {@code model.<ext>} hineinkopieren (BOBJModelLoader sucht genau diese konvention)
 *   3. eine begleitende gleichnamige {@code <name>.png} (im selben drop ODER neben der quelldatei) als
 *      {@code model.png} hineinkopieren
 *   4. {@code config.json} mit texture-link schreiben
 * BBS' watchdog ({@code ModelManager implements IWatchDogListener}) erkennt die neuen dateien und lädt
 * das modell LIVE — kein restart. Bulk fällt gratis raus (schleife über context.files).
 *
 * TEXTUR-ZUORDNUNG: eine .png die exakt so heißt wie eine modell-datei (basename) ist deren textur.
 * Lose .png ohne passendes modell fassen wir NICHT an — die laufen weiter durch BBS' PNGImporter
 * (deshalb greift {@link #canImport} nur wenn mindestens EINE modell-datei dabei ist).
 */
public class ModelDropImporter implements IImporter
{
    /** Alle modell-endungen die BBS' ModelManager als relodable registriert (setupLoaders). */
    public static final String[] MODEL_EXTENSIONS = {
        ".bobj", ".gltf", ".glb", ".vox", ".mimodel", ".bbs.json", ".geo.json", ".obj"
    };

    @Override
    public IKey getName()
    {
        /* Kein eigener l10n-key nötig — der name erscheint nur in der "X importiert!"-notification. */
        return IKey.raw("Model");
    }

    @Override
    public File getDefaultFolder()
    {
        /* Ziel ist der models-ordner, nicht der assets-root. */
        return new File(BBSMod.getAssetsFolder(), "models");
    }

    private static boolean isModelFile(File file)
    {
        String name = file.getName().toLowerCase(Locale.ROOT);

        for (String ext : MODEL_EXTENSIONS)
        {
            if (name.endsWith(ext))
            {
                return true;
            }
        }

        return false;
    }

    /** basename ohne modell-endung ("verity.bobj" → "verity", "cat.geo.json" → "cat"). */
    private static String baseName(File file)
    {
        String name = file.getName();
        String lower = name.toLowerCase(Locale.ROOT);

        for (String ext : MODEL_EXTENSIONS)
        {
            if (lower.endsWith(ext))
            {
                return name.substring(0, name.length() - ext.length());
            }
        }

        int dot = name.lastIndexOf('.');

        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** die tatsächliche endung inkl. punkt ("verity.bobj" → ".bobj", "cat.geo.json" → ".geo.json"). */
    private static String modelExt(File file)
    {
        String lower = file.getName().toLowerCase(Locale.ROOT);

        for (String ext : MODEL_EXTENSIONS)
        {
            if (lower.endsWith(ext))
            {
                return ext;
            }
        }

        return "";
    }

    @Override
    public boolean canImport(ImporterContext context)
    {
        /* Greifen NUR wenn mindestens eine modell-datei dabei ist. Reine .png/.wav-drops überlassen
         * wir den bestehenden BBS-importern (die in Importers.getImporters() VOR uns geprüft werden). */
        for (File file : context.files)
        {
            if (isModelFile(file))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void importFiles(ImporterContext context)
    {
        File modelsRoot = getDefaultFolder();

        modelsRoot.mkdirs();

        /* Alle dateien im drop, damit wir begleitende texturen den modellen zuordnen können. */
        List<File> allFiles = context.files;

        for (File file : allFiles)
        {
            /* pivot-sidecars (.bbs.json neben gleichnamiger .obj im drop) werden NICHT als
             * eigenes modell installiert — ObjPivotSidecar legt sie in den OBJ-modell-ordner
             * (dort merged CubicModelLoader sie per gruppen-name = pivots überleben). */
            if (!isModelFile(file) || ObjPivotSidecar.isObjSidecar(file, allFiles))
            {
                continue;
            }

            try
            {
                this.installModel(file, allFiles, modelsRoot);
            }
            catch (Exception e)
            {
                System.err.println("[ModelDropImporter] konnte modell nicht installieren: " + file);
                e.printStackTrace();
            }
        }
    }

    /**
     * Generische export-namen die nichts über das modell aussagen. Viele exporter (Blender-default,
     * tools) schreiben so — dann taugt der dateiname NICHT als modell-name (alles hieße "model" und
     * kollidierte). In dem fall nehmen wir stattdessen den namen des quell-ORDNERS (z.B.
     * "VerityFullV2/model.bobj" → modell "VerityFullV2").
     */
    private static final java.util.Set<String> GENERIC_NAMES = new java.util.HashSet<>(java.util.Arrays.asList(
        "model", "default", "output", "untitled", "export", "scene", "mesh", "unnamed"
    ));

    /** Der modell-name: dateiname ohne endung — außer der ist generisch, dann der quell-ordner-name. */
    private static String modelName(File modelFile)
    {
        String base = baseName(modelFile);

        if (GENERIC_NAMES.contains(base.toLowerCase(Locale.ROOT)))
        {
            File parent = modelFile.getParentFile();

            if (parent != null && parent.getName() != null && !parent.getName().isEmpty())
            {
                return sanitize(parent.getName());
            }
        }

        return sanitize(base);
    }

    /** Ordner-namen sicher machen (BBS-model-id: keine leer/sonderzeichen, klein wär schön aber nicht pflicht). */
    private static String sanitize(String name)
    {
        String s = name.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");

        return s.isEmpty() ? "model" : s;
    }

    private void installModel(File modelFile, List<File> allFiles, File modelsRoot) throws IOException
    {
        String base = modelName(modelFile);
        String ext = modelExt(modelFile);

        /* eindeutigen ordner-namen finden (verity, verity_1, ... wenn schon vorhanden) */
        File targetDir = uniqueDir(modelsRoot, base);
        String id = targetDir.getName();

        targetDir.mkdirs();

        /* modell-datei als model.<ext> — BOBJModelLoader sucht diese konvention, andere loader
         * scannen ihre endung im ordner (per links), name egal. "model" ist der neutrale kanon. */
        File destModel = new File(targetDir, "model" + ext);
        Files.copy(modelFile, destModel);

        /* begleitende textur finden: eine .png die exakt wie das modell heißt (basename), entweder
         * im selben drop ODER direkt neben der quelldatei auf der platte. */
        /* OBJ trägt format-bedingt KEINE pivots (nur v/vt/vn/f/o/g/usemtl…) — die reisen in
         * der .bbs.json-sidecar. Mitkopieren (+ model.mtl), sonst baut CubicModelLoader
         * default-gruppen mit pivot (0,0,0). */
        if (ext.equals(".obj"))
        {
            ObjPivotSidecar.installCompanions(modelFile, allFiles, targetDir);
        }

        File texture = findTexture(modelFile, base, allFiles);
        boolean hasTexture = false;

        if (texture != null && texture.exists())
        {
            Files.copy(texture, new File(targetDir, "model.png"));
            hasTexture = true;
        }

        /* config.json — texture-link wenn vorhanden, culling false (MC-look default). */
        writeConfig(targetDir, id, hasTexture);

        System.out.println("[ModelDropImporter] modell installiert: models/" + id + "/model" + ext
            + (hasTexture ? " (+textur)" : " (ohne textur)")
            + " — BBS-watchdog lädt es live");
    }

    private static File uniqueDir(File root, String base)
    {
        File dir = new File(root, base);

        if (!dir.exists())
        {
            return dir;
        }

        for (int i = 1; i < 10000; i++)
        {
            File candidate = new File(root, base + "_" + i);

            if (!candidate.exists())
            {
                return candidate;
            }
        }

        return dir;
    }

    /**
     * Textur zum modell: (1) gleichnamige .png im selben drop, (2) gleichnamige .png neben der
     * quelldatei auf der platte, (3) eine bereits {@code model.png}/{@code <name>.png} genannte datei
     * neben der quelle. Gibt null wenn nichts passt (modell wird ohne textur installiert).
     */
    private static File findTexture(File modelFile, String base, List<File> allFiles)
    {
        String wantLower = (base + ".png").toLowerCase(Locale.ROOT);

        /* (1) im selben drop */
        for (File f : allFiles)
        {
            if (f != modelFile && f.getName().toLowerCase(Locale.ROOT).equals(wantLower))
            {
                return f;
            }
        }

        /* (2)/(3) neben der quelldatei */
        File dir = modelFile.getParentFile();

        if (dir != null)
        {
            File sameName = new File(dir, base + ".png");

            if (sameName.exists())
            {
                return sameName;
            }

            File modelPng = new File(dir, "model.png");

            if (modelPng.exists())
            {
                return modelPng;
            }
        }

        return null;
    }

    /**
     * Schreibt config.json im BBS-format. Wir schreiben minimales JSON von hand (kein BBS-DataToString-
     * abhängig-machen) — der loader liest den {@code texture}-key + {@code culling}. Ist eine textur da,
     * zeigen wir auf {@code assets:models/<id>/model.png}; sonst lassen wir den key weg (loader nimmt
     * dann die neben-datei model.png automatisch, falls doch eine existiert).
     */
    private static void writeConfig(File targetDir, String id, boolean hasTexture) throws IOException
    {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");

        if (hasTexture)
        {
            sb.append("\t\"texture\": \"assets:models/").append(id).append("/model.png\",\n");
        }

        sb.append("\t\"culling\": false\n");
        sb.append("}\n");

        File config = new File(targetDir, "config.json");

        java.nio.file.Files.write(config.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
