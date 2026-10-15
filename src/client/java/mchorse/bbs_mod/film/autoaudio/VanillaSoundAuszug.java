package mchorse.bbs_mod.film.autoaudio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Holt einen Minecraft-ton als DATEI in BBS' asset-ordner und gibt den {@link Link} darauf.
 *
 * <p>Warum ueberhaupt kopieren: ein {@code AudioClip} verweist ueber einen {@link Link} auf
 * eine datei unter {@code config/bbs/assets/}. Er kann NICHT auf eine ressource im
 * Minecraft-jar zeigen — und das ist gut so, denn genau dadurch klingt der fertige film
 * ueberall gleich und die audio-spur laesst sich exportieren.</p>
 *
 * <p>Der weg ist derselbe, den der nutzer im "Minecraft sounds"-reiter des ton-fensters
 * ({@code UIVanillaSoundList}) per hand geht: ton-ereignis → {@code sounds.json} →
 * die erste echte ogg-datei → nach {@code config/bbs/assets/audio/} kopieren.</p>
 *
 * <h2>⚠ Der name ist STABIL, nicht eindeutig</h2>
 * {@code UIVanillaSoundList} haengt bei einem namenskonflikt absichtlich {@code _1},
 * {@code _2} … an — beim manuellen download will man beide behalten. Auto Audio will das
 * GEGENTEIL: laeuft es zehnmal, darf es nicht zehn kopien derselben explosion anlegen.
 * Darum ein fester, aus der ton-id abgeleiteter name; existiert die datei schon, wird sie
 * wiederverwendet. Das {@code auto_audio_}-praefix haelt sie zugleich von den handverlesenen
 * dateien des nutzers getrennt, die es niemals ueberschreiben darf.
 */
public class VanillaSoundAuszug
{
    public static final String PRAEFIX = "auto_audio_";

    /** {@code minecraft:sounds.json}, einmal gelesen — die datei hat ueber 1900 eintraege. */
    private static JsonObject soundsJson;

    private VanillaSoundAuszug()
    {}

    /**
     * @param soundEreignis id eines {@code SoundEvent}, z.b. {@code minecraft:entity.generic.explode}
     * @return Link auf eine ogg unter {@code assets:audio/}, oder {@code null} wenn zu diesem
     *         ereignis keine abspielbare datei gefunden wurde (z.b. reine ereignis-verweise
     *         oder ein ton aus einem resourcepack, das gerade nicht geladen ist).
     */
    public static Link holen(ResourceLocation soundEreignis)
    {
        if (soundEreignis == null)
        {
            return null;
        }

        String dateiName = PRAEFIX + soundEreignis.getPath().replace('.', '_').replace('/', '_');
        File ordner = BBSMod.getAudioFolder();
        File ziel = new File(ordner, dateiName + ".ogg");

        if (ziel.exists())
        {
            return Link.assets("audio/" + dateiName + ".ogg");
        }

        ResourceLocation quelle = oggRessource(soundEreignis);

        if (quelle == null)
        {
            return null;
        }

        try
        {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(quelle);

            if (resource.isEmpty())
            {
                return null;
            }

            ordner.mkdirs();

            try (InputStream in = resource.get().open())
            {
                Files.copy(in, ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            return Link.assets("audio/" + dateiName + ".ogg");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Von der ton-EREIGNIS-id zur ton-DATEI. Ein ereignis listet in {@code sounds.json}
     * mehrere varianten; genommen wird die erste echte datei.
     *
     * <p>⚠ Eintraege mit {@code "type": "event"} sind verweise auf ein ANDERES ereignis,
     * keine dateien — wer sie mitnimmt, baut einen pfad wie {@code sounds/entity.zombie.hurt.ogg},
     * den es nicht gibt. Derselbe filter steht in {@code UIVanillaSoundList}.</p>
     */
    private static ResourceLocation oggRessource(ResourceLocation soundEreignis)
    {
        JsonObject json = soundsJson();

        if (json == null)
        {
            return null;
        }

        JsonElement eintrag = json.get(soundEreignis.getPath());

        if (eintrag == null || !eintrag.isJsonObject())
        {
            return null;
        }

        JsonElement liste = eintrag.getAsJsonObject().get("sounds");

        if (liste == null || !liste.isJsonArray())
        {
            return null;
        }

        JsonArray sounds = liste.getAsJsonArray();

        for (int i = 0; i < sounds.size(); i++)
        {
            JsonElement ton = sounds.get(i);
            String pfad = null;

            if (ton.isJsonPrimitive())
            {
                pfad = ton.getAsString();
            }
            else if (ton.isJsonObject())
            {
                JsonObject objekt = ton.getAsJsonObject();

                if (objekt.has("type") && "event".equals(objekt.get("type").getAsString()))
                {
                    continue;
                }

                if (objekt.has("name"))
                {
                    pfad = objekt.get("name").getAsString();
                }
            }

            if (pfad == null || pfad.isEmpty())
            {
                continue;
            }

            /* Ein pfad darf seinen eigenen namensraum mitbringen ("mod:sub/ton"). Ohne diese
             * trennung landet der doppelpunkt mitten im ressourcen-pfad und ResourceLocation.parse
             * wirft. */
            String namensraum = soundEreignis.getNamespace();
            int doppelpunkt = pfad.indexOf(':');

            if (doppelpunkt >= 0)
            {
                namensraum = pfad.substring(0, doppelpunkt);
                pfad = pfad.substring(doppelpunkt + 1);
            }

            if (!pfad.endsWith(".ogg"))
            {
                pfad = pfad + ".ogg";
            }

            return ResourceLocation.fromNamespaceAndPath(namensraum, "sounds/" + pfad);
        }

        return null;
    }

    private static JsonObject soundsJson()
    {
        if (soundsJson == null)
        {
            try
            {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("minecraft", "sounds.json");
                Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);

                if (resource.isPresent())
                {
                    try (InputStream in = resource.get().open())
                    {
                        soundsJson = JsonParser.parseString(IOUtils.readText(in)).getAsJsonObject();
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return soundsJson;
    }

    /** Nach einem resourcepack-wechsel zeigt der alte inhalt auf dateien, die es nicht mehr gibt. */
    public static void neuLaden()
    {
        soundsJson = null;
    }
}
