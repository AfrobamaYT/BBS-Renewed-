package mchorse.bbs_mod.utils.resources;

import mchorse.bbs_mod.cubic.jem.CemNames;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which texture a pack model wears. A .jem never says — OptiFine dresses it in the vanilla one — so the
 * entity's name is all there is to go on, and a pack's own repaint wins by sitting at the same path.
 * From BBS FS 4d7aa6a43, 37a9fc3a2, 0c34fc1ce, 9afb17bfc, c4ddca499, 125a63d1d, 572ca409c
 * (Wemppy4/bbs-fs, MIT). Paths are relative to {@link #TEXTURES}.
 */
final class CemTexturen
{
    /** Where the entity textures a model may use live */
    static final String TEXTURES = "textures/entity";

    private static final String[] PREFIXES = {"cold_", "warm_"};

    /** Entity Texture Features' emissive overlay: a texture drawn over the entity, not a coat for it */
    private static final String EMISSIVE = "_e";

    /** Suffixes naming a layer over an entity rather than an entity; taken off one at a time */
    private static final String[] LAYERS = {
        "_outer", "_saddle", "_armor", "_decor", "_patch", "_collar", "_wool", "_charge",
        "_undercoat", "_harness", "_ropes", "_big", "_medium", "_small", "_pattern_a", "_pattern_b",
        "_a", "_b", "_left", "_right", "_layer"
    };

    /** Entities vanilla files under another name entirely; everything a rule can reach is left to the rules */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("bogged_outer", "skeleton/bogged_overlay.png"), Map.entry("chest", "chest/normal.png"),
        Map.entry("chest_large", "chest/normal_left.png"), Map.entry("chest_raft", "chest_boat/bamboo.png"),
        Map.entry("drowned_outer", "zombie/drowned_outer_layer.png"), Map.entry("ender_chest", "chest/ender.png"),
        Map.entry("trapped_chest", "chest/trapped.png"), Map.entry("trapped_chest_large", "chest/trapped_left.png"),
        Map.entry("elder_guardian", "guardian_elder.png"), Map.entry("giant", "zombie/zombie.png"),
        Map.entry("horse_armor", "horse/armor/horse_armor_iron.png"), Map.entry("llama_decor", "llama/decor/white.png"),
        Map.entry("magma_cube", "slime/magmacube.png"), Map.entry("mooshroom", "cow/red_mooshroom.png"),
        Map.entry("player", "player/wide/steve.png"), Map.entry("player_slim", "player/slim/steve.png"),
        Map.entry("puffer_fish", "fish/pufferfish.png"), Map.entry("raft", "boat/bamboo.png"),
        Map.entry("sheep_wool", "sheep/sheep_fur.png"), Map.entry("shulker_box", "shulker/shulker.png"),
        Map.entry("skeleton_horse", "horse/horse_skeleton.png"), Map.entry("stray_outer", "skeleton/stray_overlay.png"),
        Map.entry("trader_llama", "llama/creamy.png"), Map.entry("trader_llama_decor", "llama/decor/trader_llama.png"),
        Map.entry("tropical_fish", "fish/tropical_a.png"), Map.entry("zombie_horse", "horse/horse_zombie.png")
    );

    final Map<String, Identifier> byPath = new LinkedHashMap<>();
    /** Every texture of a file name, in path order: {@code creeper.png} is a banner pattern before it is a creeper */
    final Map<String, List<Identifier>> byName = new LinkedHashMap<>();
    final Map<String, List<Identifier>> byFolder = new LinkedHashMap<>();

    /** The entity textures of the enabled packs, sorted so a name two packs answer resolves the same way twice */
    CemTexturen(ResourceManager manager)
    {
        for (Identifier id : new TreeMap<>(manager.listResources(TEXTURES, (l) -> CemPaket.isMinecraft(l) && l.getPath().endsWith(".png"))).keySet())
        {
            String path = id.getPath().substring(TEXTURES.length() + 1);
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 4);

            this.byPath.putIfAbsent(path, id);
            this.byName.computeIfAbsent(name, (k) -> new ArrayList<>()).add(id);

            for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1))
            {
                this.byFolder.computeIfAbsent(path.substring(0, slash), (k) -> new ArrayList<>()).add(id);
            }
        }
    }

    /**
     * The texture an entity wears: the alias table, then a file called after the entity, then the
     * entity's own folder for the ones that come in variants. A variant's default is a guess; the point
     * is a model that arrives wearing something rather than the checkerboard.
     */
    Identifier resolve(String entity)
    {
        Collection<String> names = variants(entity);

        for (String name : names)
        {
            Identifier id = this.byPath.get(ALIASES.getOrDefault(name, ""));

            if (id != null) return id;
        }

        for (String name : names)
        {
            Identifier id = this.pickByName(name);

            if (id != null) return id;
        }

        for (String name : names)
        {
            Identifier id = this.pickFromFolder(name);

            if (id != null) return id;
        }

        return null;
    }

    /** The entity's own folder wins, then a file at the root of the entity textures, then the first */
    private Identifier pickByName(String name)
    {
        List<Identifier> all = this.byName.get(name);

        if (all == null) return null;

        for (Identifier id : all) if (folderName(id).equals(name)) return id;
        for (Identifier id : all) if (folderName(id).isEmpty()) return id;

        return all.get(0);
    }

    private static String folderName(Identifier id)
    {
        String path = id.getPath().substring(TEXTURES.length() + 1);
        int slash = path.lastIndexOf('/');

        return slash < 0 ? "" : path.substring(path.lastIndexOf('/', slash - 1) + 1, slash);
    }

    /**
     * The names to look an entity up under, from itself down to the entity it is a variant of: the young
     * and the numbered first ({@code pig_baby_saddle} is a saddled pig), then the climate prefix
     * ({@code cold_cow_baby} wears {@code cold_cow}'s), then the layers one at a time.
     */
    private static Collection<String> variants(String entity)
    {
        Set<String> names = new LinkedHashSet<>();
        String name = entity;

        names.add(name);
        names.add(name = CemNames.stripDigits(name.replace("_baby", "")));

        for (String prefix : PREFIXES)
        {
            if (name.startsWith(prefix)) names.add(name = name.substring(prefix.length()));
        }

        for (String shorter = peel(name); shorter != null; shorter = peel(name)) names.add(name = shorter);

        /* Every minecart is drawn on the one texture, whatever it carries */
        if (name.endsWith("_minecart")) names.add("minecart");

        return names;
    }

    private static String peel(String name)
    {
        for (String suffix : LAYERS)
        {
            if (name.endsWith(suffix) && name.length() > suffix.length()) return CemNames.stripDigits(name.substring(0, name.length() - suffix.length()));
        }

        return name.startsWith("head_") ? name.substring(5) : null;
    }

    /**
     * The other textures of the chosen one's folder — the cat's twelve coats, the horse's armours — to
     * pick from in the form's texture picker. A layer over the entity and an emissive overlay are left out.
     */
    List<Identifier> alternatives(Identifier texture)
    {
        String path = texture.getPath().substring(TEXTURES.length() + 1);
        int slash = path.lastIndexOf('/');

        if (slash < 0) return Collections.emptyList();

        List<Identifier> alternatives = new ArrayList<>();

        for (Identifier id : this.direct(path.substring(0, slash)))
        {
            String name = fileName(id);

            if (!id.equals(texture) && !isLayer(name) && !name.endsWith(EMISSIVE)) alternatives.add(id);
        }

        return alternatives;
    }

    /** The one named after the folder, else one named after it plus something that is no layer, else the first; direct files first */
    private Identifier pickFromFolder(String folder)
    {
        List<Identifier> all = this.byFolder.get(folder);

        if (all == null) return null;

        List<Identifier> direct = this.direct(folder);
        List<Identifier> pool = direct.isEmpty() ? all : direct;
        String name = folder.substring(folder.lastIndexOf('/') + 1);

        for (Identifier id : pool) if (fileName(id).equals(name)) return id;

        for (Identifier id : pool)
        {
            String file = fileName(id);

            if (file.startsWith(name) && !isLayer(file)) return id;
        }

        return pool.get(0);
    }

    private List<Identifier> direct(String folder)
    {
        List<Identifier> direct = new ArrayList<>();
        List<Identifier> all = this.byFolder.get(folder);

        if (all == null) return direct;

        int depth = folder.length() - folder.replace("/", "").length();

        for (Identifier id : all)
        {
            String path = id.getPath().substring(TEXTURES.length() + 1);

            if (path.length() - path.replace("/", "").length() == depth + 1) direct.add(id);
        }

        return direct;
    }

    static String fileName(Identifier id)
    {
        String path = id.getPath();

        return path.substring(path.lastIndexOf('/') + 1, path.length() - 4);
    }

    private static boolean isLayer(String name)
    {
        for (String suffix : LAYERS) if (name.endsWith(suffix)) return true;

        return false;
    }
}
