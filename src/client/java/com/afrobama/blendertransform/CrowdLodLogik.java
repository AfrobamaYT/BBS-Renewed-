package com.afrobama.blendertransform;

import mchorse.bbs_mod.crowd.CrowdRegistry;
import mchorse.bbs_mod.crowd.CrowdTags;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import com.afrobama.bbstools.client.mixin.ModelPartAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logik des crowd-LOD-mixins — herausgelöst aus {@code CrowdLODMixin} (2026-07-26), damit sie
 * hotswapbar ist.
 *
 * <p>Distanz-basiertes render-LOD für crowd-getaggte entities. Bricht das rendern jenseits
 * von 320 blöcken komplett ab, versteckt körperteile (nose, hat_rim, hat, jacket, arms) in
 * progressiven distanz-bändern. Spart ~25-35% vertex-transforms bei 5000+ getaggten
 * villagern.</p>
 *
 * <p>Migriert aus dem BBS-CML-EDITION-fork's mchorse.bbs_mod.mixin.client.CrowdLODMixin.</p>
 *
 * <p>Das {@code @Shadow}-feld {@code model} bleibt im mixin (rule 3) und wird als argument
 * durchgereicht — {@link ModelPartAccessor} ist ein mixin-accessor-interface im stabilen
 * loader, kein kernel-typ.</p>
 */
public final class CrowdLodLogik
{
    private CrowdLodLogik() {}

    private static final Logger BBS_LOG = LoggerFactory.getLogger("bbstools/crowd-lod");
    private static final AtomicBoolean BBS_LOGGED_ONCE = new AtomicBoolean(false);

    private static final double[] BAND_DIST_SQ = {
        48.0D * 48.0D,
        64.0D * 64.0D,
        96.0D * 96.0D,
        128.0D * 128.0D,
        192.0D * 192.0D,
        320.0D * 320.0D
    };

    private static final String[][] BAND_HIDE_PARTS = {
        { },
        { "nose" },
        { "nose", "hat_rim" },
        { "nose", "hat_rim", "hat" },
        { "nose", "hat_rim", "hat", "jacket" },
        { "nose", "hat_rim", "hat", "jacket", "arms" }
    };

    private static final double CULL_DISTANCE_SQ = 320.0D * 320.0D;

    /* Prozess-globaler zwischenspeicher, wer gerade versteckt wurde — nicht an eine
     * mixin-instanz gebunden, deshalb hier statt im mixin. */
    private static final ThreadLocal<Map<ModelPart, Boolean>> savedHidden =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * @return {@code true}, wenn der aufrufer {@code ci.cancel()} rufen soll (entity zu weit weg)
     */
    public static boolean crowdLODCullAndHide(LivingEntity entity, EntityModel<?> model)
    {
        if (!CrowdRegistry.contains(entity.getUUID()) && !entity.entityTags().contains(CrowdTags.LOD))
        {
            return false;
        }

        if (BBS_LOGGED_ONCE.compareAndSet(false, true))
        {
            dumpModelTree(entity, model);
        }

        Minecraft client = Minecraft.getInstance();
        if (client.gameRenderer == null) return false;

        Camera camera = client.gameRenderer.mainCamera();
        if (camera == null) return false;

        Vec3 camPos = camera.position();
        double dx = entity.getX() - camPos.x;
        double dy = entity.getY() - camPos.y;
        double dz = entity.getZ() - camPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > CULL_DISTANCE_SQ)
        {
            return true;
        }

        if (distSq <= BAND_DIST_SQ[0])
        {
            return false;
        }

        applyPartLOD(distSq, model);
        return false;
    }

    public static void crowdLODRestore()
    {
        Map<ModelPart, Boolean> saved = savedHidden.get();
        if (saved.isEmpty()) return;

        for (Map.Entry<ModelPart, Boolean> e : saved.entrySet())
        {
            e.getKey().skipDraw = e.getValue();
        }
        saved.clear();
    }

    private static void dumpModelTree(LivingEntity entity, EntityModel<?> model)
    {
        BBS_LOG.info("=== CrowdLODMixin FIRED ===");
        BBS_LOG.info("Entity: {} ({})", entity.getClass().getSimpleName(), entity.entityTags());
        BBS_LOG.info("Model: {}", model == null ? "null" : model.getClass().getName());

        /* ⚠️ HierarchicalModel gibt es in 26.2 nicht mehr — root() sitzt jetzt direkt an
         * Model, also an JEDEM EntityModel. Der instanceof-Test entfaellt ersatzlos. */
        if (model != null)
        {
            ModelPart root = model.root();
            BBS_LOG.info("Part tree:");
            logTree(root, 0);
        }
    }

    private static void logTree(ModelPart part, int depth)
    {
        Map<String, ModelPart> children = ((ModelPartAccessor)(Object) part).bbstools$getChildren();
        if (children == null) return;

        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < depth; i++) pad.append("  ");

        for (Map.Entry<String, ModelPart> e : children.entrySet())
        {
            BBS_LOG.info("  {}- {}", pad, e.getKey());
            logTree(e.getValue(), depth + 1);
        }
    }

    private static void applyPartLOD(double distSq, EntityModel<?> model)
    {
        /* ⚠️ VERHALTENSAENDERUNG: frueher bekamen nur HierarchicalModel-Modelle ein LOD, alle
         * anderen fielen hier raus. Den Typ gibt es nicht mehr (root() sitzt an Model), also
         * bleibt als Ausschluss nur noch "kein Modell". Praktisch heisst das: Modelle, die
         * frueher kein HierarchicalModel waren, werden jetzt ebenfalls geLODet — die
         * Teilenamen aus BAND_HIDE_PARTS treffen dort in der Regel nichts, findRecursive
         * liefert dann null und die Schleife ueberspringt sie. */
        if (model == null) return;

        int bandIdx = 0;
        for (int i = 0; i < BAND_DIST_SQ.length; i++)
        {
            if (distSq > BAND_DIST_SQ[i]) bandIdx = i;
        }
        if (bandIdx >= BAND_HIDE_PARTS.length) bandIdx = BAND_HIDE_PARTS.length - 1;

        String[] hideList = BAND_HIDE_PARTS[bandIdx];
        if (hideList.length == 0) return;

        ModelPart root = model.root();
        Map<ModelPart, Boolean> saved = savedHidden.get();

        for (String name : hideList)
        {
            ModelPart child = findRecursive(root, name);
            if (child == null) continue;
            saved.put(child, child.skipDraw);
            child.skipDraw = true;
        }
    }

    private static ModelPart findRecursive(ModelPart parent, String name)
    {
        Map<String, ModelPart> children = ((ModelPartAccessor)(Object) parent).bbstools$getChildren();
        if (children == null || children.isEmpty()) return null;

        ModelPart direct = children.get(name);
        if (direct != null) return direct;

        for (ModelPart child : children.values())
        {
            ModelPart found = findRecursive(child, name);
            if (found != null) return found;
        }
        return null;
    }
}
