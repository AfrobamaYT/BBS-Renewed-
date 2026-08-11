package mchorse.bbs_mod.film.autoaudio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.forms.forms.utils.ParticleSettings;
import mchorse.bbs_mod.utils.IOUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Welcher Minecraft-ton zu welchem partikel-typ gehoert.
 *
 * <p>⛔ Minecraft hat KEINE tabelle "partikel → ton" (nachgemessen an 26.2: ParticleTypes
 * nennt nirgends einen sound, alle 112 partikel-definitionen enthalten nur texturen). Die
 * kopplung entsteht in vanilla an der AUFRUFSTELLE (Level#explode nimmt beides nebeneinander).
 * Jede zeile in {@code assets/bbs/auto_audio/partikel_sounds.json} ist deshalb eine
 * 26.2-klasse, in der partikel- und ton-konstante nachweislich GEMEINSAM vorkommen (14 belegt,
 * belegklasse je zeile im _belege-block; 16 unbelegte kandidaten sind bewusst NICHT dabei —
 * messprotokoll in werkbank/features/auto-audio/). Alle ton-ids gegen die echte sounds.json
 * geprueft; ein erfundener name ist dabei geflogen.</p>
 *
 * <p>Block-partikel (block, falling_dust, dust_pillar, block_crumble) tragen ihren block →
 * {@code BlockState.getSoundType().getBreakSound()} — dieselbe ableitung, die vanilla selbst
 * im fall PARTICLES_DESTROY_BLOCK macht. Sand klingt nach sand, ohne tabellenzeile, auch bei
 * modded bloecken.</p>
 *
 * <p>⛔ Unbekannt heisst STILL, nicht "irgendwas aehnliches" — namensaehnlichkeit ist
 * GEMESSEN unbrauchbar (explosion trifft nichts, der ton heisst generic_explode). Dann
 * liefert {@link #finde} {@code null} und der nutzer erfaehrt, welcher partikel es war.
 * {@code config/bbs/auto_audio.json} ueberschreibt/ergaenzt die tabelle; leerer wert =
 * "ausdruecklich kein ton".</p>
 */
public class PartikelSoundKatalog
{
    public static final Identifier RESSOURCE = Identifier.fromNamespaceAndPath("bbs", "auto_audio/partikel_sounds.json");
    public static final String NUTZER_DATEI = "auto_audio.json";

    /** {@code null} bis zum ersten zugriff; {@link #neuLaden()} wirft ihn weg. */
    private static Map<Identifier, Identifier> tabelle;

    private PartikelSoundKatalog() {}

    /** @return id des passenden {@link SoundEvent}, oder {@code null} wenn zu diesem
     *          partikel kein ton BEKANNT ist (nicht: keiner existiert). */
    public static Identifier finde(ParticleSettings settings)
    {
        if (settings == null || settings.particle == null)
        {
            return null;
        }

        Identifier ausBlock = ausBlockPartikel(settings);

        if (ausBlock != null)
        {
            return ausBlock;
        }

        Identifier ausTabelle = tabelle().get(settings.particle);

        /* Existiert der ton in DIESER version? Eine zeile aus nutzerdatei/aelterer version
         * darf nicht durchgehen — sonst scheitert erst der datei-auszug, fern der ursache. */
        if (ausTabelle != null && BuiltInRegistries.SOUND_EVENT.getValue(ausTabelle) == null)
        {
            System.err.println("[Auto Audio] Ton-id '" + ausTabelle + "' fuer partikel '" + settings.particle + "' gibt es in dieser Minecraft-version nicht — uebersprungen.");

            return null;
        }

        return ausTabelle;
    }

    /**
     * Block-partikel tragen ihren block; sein {@link SoundType} ist Minecrafts aussage, wie
     * das material klingt. ⚠ Argumente werden GENAUSO gelesen wie in
     * {@code VanillaParticleFormRenderer.createEffect} (nackte id, keine geschweifte
     * klammer) — wer anders parst, waehlt den ton eines anderen blocks. {@code {...}} wird
     * bewusst nicht gedeutet: lieber kein ton als der falsche.
     */
    private static Identifier ausBlockPartikel(ParticleSettings settings)
    {
        if (!istBlockPartikel(settings.particle))
        {
            return null;
        }

        String args = settings.arguments == null ? "" : settings.arguments.trim();

        if (args.isEmpty() || args.charAt(0) == '{')
        {
            return null;
        }

        try
        {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(args));

            /* BLOCK ist eine defaulted registry: ein unbekannter name liefert LUFT, nicht null.
             * Luft hat SoundType.EMPTY — ein clip daraus waere stumm und wuerde wie ein bug
             * aussehen. Also nur akzeptieren, wenn der nutzer wirklich luft gemeint hat. */
            if (block == Blocks.AIR && !args.endsWith("air"))
            {
                return null;
            }

            BlockState state = block.defaultBlockState();
            SoundType typ = state.getSoundType();
            SoundEvent ton = typ == null ? null : typ.getBreakSound();

            return ton == null ? null : ton.location();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static boolean istBlockPartikel(Identifier partikel)
    {
        if (!"minecraft".equals(partikel.getNamespace()))
        {
            return false;
        }

        String pfad = partikel.getPath();

        return pfad.equals("block") || pfad.equals("falling_dust") || pfad.equals("dust_pillar") || pfad.equals("block_crumble");
    }

    public static void neuLaden()
    {
        tabelle = null;
    }

    private static Map<Identifier, Identifier> tabelle()
    {
        if (tabelle == null)
        {
            Map<Identifier, Identifier> geladen = new HashMap<>();

            einlesen(geladen, ressourceLesen());
            einlesen(geladen, nutzerDateiLesen());

            tabelle = geladen;
        }

        return tabelle;
    }

    /** Schluessel mit fuehrendem {@code _} sind kommentare (JSON kennt keine). Ein leerer
     *  wert LOESCHT einen eintrag — eine nutzerdatei kann einen ton damit abschalten. */
    private static void einlesen(Map<Identifier, Identifier> ziel, JsonObject json)
    {
        if (json == null)
        {
            return;
        }

        for (Map.Entry<String, JsonElement> eintrag : json.entrySet())
        {
            String schluessel = eintrag.getKey();

            if (schluessel.startsWith("_") || !eintrag.getValue().isJsonPrimitive())
            {
                continue;
            }

            try
            {
                Identifier partikel = Identifier.parse(schluessel);
                String wert = eintrag.getValue().getAsString().trim();

                if (wert.isEmpty())
                {
                    ziel.remove(partikel);
                }
                else
                {
                    ziel.put(partikel, Identifier.parse(wert));
                }
            }
            catch (Exception e)
            {
                System.err.println("[Auto Audio] Zeile '" + schluessel + "' in der partikel-sound-tabelle ist keine gueltige id — uebersprungen.");
            }
        }
    }

    private static JsonObject ressourceLesen()
    {
        try
        {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(RESSOURCE);

            if (resource.isPresent())
            {
                try (InputStream in = resource.get().open())
                {
                    return JsonParser.parseString(IOUtils.readText(in)).getAsJsonObject();
                }
            }

            System.err.println("[Auto Audio] " + RESSOURCE + " fehlt — ohne tabelle kennt Auto Audio nur block-partikel.");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private static JsonObject nutzerDateiLesen()
    {
        File datei = BBSMod.getSettingsPath(NUTZER_DATEI);

        if (!datei.exists())
        {
            return null;
        }

        try
        {
            return JsonParser.parseString(IOUtils.readText(datei)).getAsJsonObject();
        }
        catch (Exception e)
        {
            /* LAUT scheitern: eine kaputte nutzerdatei still zu ignorieren hiesse, der
             * nutzer aendert etwas und sieht keine wirkung und keinen grund. */
            System.err.println("[Auto Audio] " + datei + " ist kein gueltiges JSON — wird ignoriert: " + e.getMessage());
        }

        return null;
    }
}
