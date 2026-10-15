// Coded by Afrobama/Claude — Tier 1 fast-path routing decision.
//
// Decides whether a given replay-render call should be diverted into the
// BBSTools fast-path (post-animator bone-matrix capture + batched GPU draw)
// or fall through to BBS's vanilla per-replay render.
//
// Decision is data-driven, conservative-fallback by design: any unusual
// state (equipment, stencil map, picker pass, hero-shot flag) routes back
// to vanilla. The fast-path is for crowd-fill scale, never for hero shots.
//
// Tier 1: measure-only mode. shouldRoute() returns true when criteria met,
// but the mixin currently does NOT cancel render3D — both code paths run
// (vanilla render + fast-path batch submit) so we can validate the gating
// logic against actual frame data without breaking visibility.
//
// Tier 1.5: flip to render-replace mode, the mixin will cancel render3D
// on a true result.
package com.afrobama.bbstools.client.bbs.fastpath;

import com.afrobama.bbstools.perf.BBSPerfConfig;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrowdRouter
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/fast-path/router");

    /* Reason buckets for /bbsdebug fast-path stats. Bumped on every
     * rejection so we can see WHY routing is failing in production. */
    public static volatile long reasonDisabled        = 0L;
    public static volatile long reasonNullForm        = 0L;
    public static volatile long reasonNullInstance    = 0L;
    public static volatile long reasonNullModel       = 0L;
    public static volatile long reasonEquipment       = 0L;
    public static volatile long reasonStencilMap      = 0L;
    public static volatile long reasonBoneCountHigh   = 0L;
    public static volatile long reasonHasBodyParts    = 0L;
    public static volatile long reasonHasActiveItems  = 0L;
    public static volatile long reasonShadowPass      = 0L;
    public static volatile long reasonAccepted        = 0L;

    /* Pass-axis diagnostic counters — incremented on EVERY classify call
     * regardless of accept/reject so we can see WHICH render passes hit
     * render3D. Used to track down the "2× call rate vs replay count"
     * mystery (101 replays but ~200 calls/frame). */
    public static volatile long passMain    = 0L;
    public static volatile long passUi      = 0L;
    public static volatile long passShadow  = 0L;
    public static volatile long passPicker  = 0L;
    public static volatile long passOther   = 0L;
    public static volatile long passRelative = 0L;
    public static volatile long passModelRenderer = 0L;

    /* FormRenderType-axis counters — sister to pass counters. Bumped
     * once per classify(). Helps distinguish ENTITY vs ITEM vs PREVIEW
     * vs MODEL_BLOCK calls all hitting our hook. */
    public static volatile long typeEntity        = 0L;
    public static volatile long typeItem          = 0L;
    public static volatile long typeItemFp        = 0L;
    public static volatile long typeItemTp        = 0L;
    public static volatile long typeItemInventory = 0L;
    public static volatile long typeModelBlock    = 0L;
    public static volatile long typePreview       = 0L;
    public static volatile long typeNull          = 0L;

    /* IEntity class + form-id diversity counters. Track WHICH entity
     * impl + which form-id hit the hook. If 101 villagers produce 201
     * calls/frame, the entity-class map will show whether two distinct
     * impls (replay-stub + ActorEntity) are firing OR same class twice.
     * Top-20 only to keep snapshot output bounded. */
    public static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> entityClassCounts
        = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> formIdCounts
        = new java.util.concurrent.ConcurrentHashMap<>();

    /* One-shot log: print the first ACCEPTED route + the first rejection
     * of each reason. After that, counters are silent — JFR has the rest. */
    private static volatile boolean loggedFirstAccept;
    /* Sized to enum length below — keep in sync with Reason.values().length. */
    private static volatile boolean[] loggedReasonOnce = new boolean[Reason.values().length];

    private CrowdRouter() {}

    public enum Reason
    {
        DISABLED, NULL_FORM, NULL_INSTANCE, NULL_MODEL,
        EQUIPMENT, STENCIL_MAP, BONE_COUNT_HIGH,
        HAS_BODY_PARTS, HAS_ACTIVE_ITEMS, SHADOW_PASS,
        ACCEPTED
    }

    public static Reason classify(ModelForm form, ModelInstance instance, FormRenderingContext context)
    {
        /* Pass + type diagnostics fire BEFORE early-return so even the
         * disabled state shows pass-axis distribution. */
        bumpPassCounters(context);
        bumpTypeCounters(context);
        bumpEntityClass(context);
        bumpFormId(form);

        if (!BBSPerfConfig.ENABLE_FAST_PATH)
        {
            reasonDisabled++;
            return Reason.DISABLED;
        }
        if (form == null)
        {
            reasonNullForm++;
            return Reason.NULL_FORM;
        }
        if (instance == null)
        {
            reasonNullInstance++;
            return Reason.NULL_INSTANCE;
        }

        IModel model = instance.model;
        if (model == null)
        {
            reasonNullModel++;
            return Reason.NULL_MODEL;
        }

        /* Equipment diagnostics: count replays with item-slot config
         * populated, but DO NOT reject. The slot lists get populated at
         * BBS model load time even when the slots themselves are empty —
         * a fresh ModelInstance for a "Villager" rig has ~4-6 ArmorSlot
         * entries created during fromData even though no item is bound.
         * Real has-equipment determination requires runtime ActorEntity
         * inventory inspection, which Tier 1.5 will add at flush time
         * (route equipment-bearing replays to vanilla fallback).
         *
         * Tier 1 routes EVERY ModelInstance so the measurement reflects
         * max-routable potential. */
        if ((instance.config.getItemsMain() != null && !instance.config.getItemsMain().isEmpty())
            || (instance.config.getItemsOff() != null && !instance.config.getItemsOff().isEmpty())
            || (instance.config.getArmorSlots() != null && !instance.config.getArmorSlots().isEmpty()))
        {
            /* counter only — informational for Tier 1.5 split decision */
            reasonEquipment++;
        }

        /* Stencil-map opt-out: any active stencil pass needs the vanilla
         * per-replay path (form-stack walker writes stencil bits). */
        if (context != null && context.stencilMap != null)
        {
            reasonStencilMap++;
            return Reason.STENCIL_MAP;
        }

        /* Bone budget: hard cap to avoid blowing the per-instance SSBO
         * size in Tier 2. Computed cheap via BOBJ armature length. Cubic
         * models use group count as bone-equivalent. */
        int boneCount = estimateBoneCount(model);
        if (boneCount > BBSPerfConfig.FAST_PATH_MAX_BONES)
        {
            reasonBoneCountHigh++;
            return Reason.BONE_COUNT_HIGH;
        }

        /* Shadow pass: Iris shaderpack renders entities twice (gbuffer
         * + shadow). Our fast-path skips state setup that the shadow pass
         * needs. Defer shadow-pass support to Tier 2 GPU instancing where
         * we can route both passes correctly. */
        if (context != null && mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass())
        {
            reasonShadowPass++;
            return Reason.SHADOW_PASS;
        }

        /* Body parts opt-out: hero replays with attached props (glasses,
         * accessories, sub-form attachments) need vanilla so renderBodyParts
         * + bones MatrixCache stays consistent. Crowd-fill replays have
         * form.parts empty → eligible. Check is O(1) — getAllTyped just
         * returns the underlying list reference. */
        if (form.parts != null && !form.parts.getAllTyped().isEmpty())
        {
            reasonHasBodyParts++;
            return Reason.HAS_BODY_PARTS;
        }

        /* Active item check — RUNTIME inventory inspection. Previously
         * checked ArmorSlot config (group string + transform) which was
         * too strict: BBS villager rigs pre-configure slots so items CAN
         * attach, but no item is bound unless the actor inventory has one.
         *
         * IEntity.getEquipmentStack returns ItemStack per equipment slot.
         * For StubEntity (replay playback) this reads from its `items`
         * Map; for ActorEntity it reads from the server-spawned actor's
         * inventory. Empty stack = no item bound = safe for fast-path
         * (renderItems would render nothing anyway). */
        if (hasAnyEquippedItem(context != null ? context.entity : null))
        {
            reasonHasActiveItems++;
            return Reason.HAS_ACTIVE_ITEMS;
        }

        reasonAccepted++;
        return Reason.ACCEPTED;
    }

    public static void logOnceForReason(Reason reason, String detail)
    {
        int idx = reason.ordinal();
        if (idx >= loggedReasonOnce.length) return;
        if (loggedReasonOnce[idx]) return;
        loggedReasonOnce[idx] = true;
        LOG.info("[fast-path/route] FIRST {} — {}", reason, detail);
    }

    public static void logFirstAccept(ModelForm form, ModelInstance instance)
    {
        if (loggedFirstAccept) return;
        loggedFirstAccept = true;
        IModel model = instance.model;
        LOG.info("[fast-path/route] FIRST ACCEPT — form={} model={} class={}",
            form == null ? "null" : form.getClass().getSimpleName(),
            instance.id,
            model == null ? "null" : model.getClass().getSimpleName());
    }

    public static void resetCounters()
    {
        reasonDisabled = 0L;
        reasonNullForm = 0L;
        reasonNullInstance = 0L;
        reasonNullModel = 0L;
        reasonEquipment = 0L;
        reasonStencilMap = 0L;
        reasonBoneCountHigh = 0L;
        reasonHasBodyParts = 0L;
        reasonHasActiveItems = 0L;
        reasonShadowPass = 0L;
        reasonAccepted = 0L;
        passMain = 0L;
        passUi = 0L;
        passShadow = 0L;
        passPicker = 0L;
        passOther = 0L;
        passRelative = 0L;
        passModelRenderer = 0L;
        typeEntity = 0L;
        typeItem = 0L;
        typeItemFp = 0L;
        typeItemTp = 0L;
        typeItemInventory = 0L;
        typeModelBlock = 0L;
        typePreview = 0L;
        typeNull = 0L;
        entityClassCounts.clear();
        formIdCounts.clear();
        loggedFirstAccept = false;
        for (int i = 0; i < loggedReasonOnce.length; i++) loggedReasonOnce[i] = false;
    }

    public static String snapshotEntityClasses()
    {
        return topN("entityClass", entityClassCounts, 10);
    }

    public static String snapshotFormIds()
    {
        return topN("formId", formIdCounts, 10);
    }

    private static String topN(String label, java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> map, int n)
    {
        java.util.List<java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong>> entries = new java.util.ArrayList<>(map.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
        StringBuilder sb = new StringBuilder(label).append("{");
        int count = 0;
        for (java.util.Map.Entry<String, java.util.concurrent.atomic.AtomicLong> e : entries)
        {
            if (count++ > 0) sb.append(' ');
            sb.append(e.getKey()).append('=').append(e.getValue().get());
            if (count >= n) break;
        }
        sb.append('}');
        if (entries.size() > n) sb.append(" +").append(entries.size() - n).append(" more");
        return sb.toString();
    }

    public static String snapshot()
    {
        long total = reasonDisabled + reasonNullForm + reasonNullInstance + reasonNullModel
            + reasonStencilMap + reasonBoneCountHigh + reasonHasBodyParts + reasonHasActiveItems
            + reasonShadowPass + reasonAccepted;
        return String.format(
            "total=%d accept=%d / reject{disabled=%d nullForm=%d nullInst=%d nullModel=%d stencil=%d boneHigh=%d bodyParts=%d activeItems=%d shadow=%d} info{equipSlots=%d}",
            total, reasonAccepted, reasonDisabled, reasonNullForm, reasonNullInstance, reasonNullModel,
            reasonStencilMap, reasonBoneCountHigh, reasonHasBodyParts, reasonHasActiveItems,
            reasonShadowPass, reasonEquipment);
    }

    public static String snapshotPasses()
    {
        return String.format(
            "pass{main=%d ui=%d shadow=%d picker=%d relative=%d modelRenderer=%d other=%d}",
            passMain, passUi, passShadow, passPicker, passRelative, passModelRenderer, passOther);
    }

    public static String snapshotTypes()
    {
        return String.format(
            "type{ENTITY=%d ITEM=%d ITEM_FP=%d ITEM_TP=%d ITEM_INVENTORY=%d MODEL_BLOCK=%d PREVIEW=%d null=%d}",
            typeEntity, typeItem, typeItemFp, typeItemTp, typeItemInventory, typeModelBlock, typePreview, typeNull);
    }

    private static void bumpPassCounters(FormRenderingContext context)
    {
        if (context == null)
        {
            passOther++;
            return;
        }
        /* Independent axes — a single call can match more than one
         * (e.g. picker + relative). Bump every match so we see all
         * overlapping flags, not just a winner. */
        if (context.stencilMap != null) passPicker++;
        if (mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass())       passShadow++;
        if (context.ui)                 passUi++;
        if (context.relative)           passRelative++;
        if (context.modelRenderer)      passModelRenderer++;
        if (context.stencilMap == null && !mchorse.bbs_mod.client.BBSRendering.isIrisShadowPass() && !context.ui)
        {
            passMain++;
        }
    }

    private static void bumpEntityClass(FormRenderingContext context)
    {
        try
        {
            String key = (context == null || context.entity == null)
                ? "<null>"
                : context.entity.getClass().getSimpleName();
            entityClassCounts.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        }
        catch (Throwable ig) {}
    }

    private static void bumpFormId(ModelForm form)
    {
        try
        {
            String key;
            if (form == null) key = "<null>";
            else if (form.name != null && form.name.get() != null && !form.name.get().isEmpty()) key = form.name.get();
            else key = form.getClass().getSimpleName();
            formIdCounts.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
        }
        catch (Throwable ig) {}
    }

    private static void bumpTypeCounters(FormRenderingContext context)
    {
        mchorse.bbs_mod.forms.renderers.FormRenderType type =
            context == null ? null : context.type;
        if (type == null) { typeNull++; return; }
        switch (type)
        {
            case ENTITY:         typeEntity++;        break;
            case ITEM:           typeItem++;          break;
            case ITEM_FP:        typeItemFp++;        break;
            case ITEM_TP:        typeItemTp++;        break;
            case ITEM_INVENTORY: typeItemInventory++; break;
            case MODEL_BLOCK:    typeModelBlock++;    break;
            case PREVIEW:        typePreview++;       break;
            default:             typeNull++;          break;
        }
    }

    private static boolean hasAnyEquippedItem(IEntity entity)
    {
        if (entity == null) return false;
        try
        {
            for (EquipmentSlot slot : EquipmentSlot.values())
            {
                ItemStack stack = entity.getEquipmentStack(slot);
                if (stack != null && !stack.isEmpty()) return true;
            }
            return false;
        }
        catch (Throwable ig)
        {
            /* Defensive: unknown entity impl = treat as having items so
             * we fall through to vanilla rather than silently breaking. */
            return true;
        }
    }

    private static boolean isSlotActive(ArmorSlot slot)
    {
        if (slot == null) return false;
        try
        {
            String group = slot.group;
            if (group != null && !group.isEmpty()) return true;
            if (slot.transform != null && !slot.transform.isDefault()) return true;
            return false;
        }
        catch (Throwable ig)
        {
            /* Defensive: unknown slot internals = treat as active so we
             * fall through to vanilla rather than silently breaking. */
            return true;
        }
    }

    private static boolean hasAnyActiveSlot(ModelInstance instance)
    {
        if (instance == null) return false;
        try
        {
            if (instance.config.getItemsMain() != null)
            {
                for (ArmorSlot s : instance.config.getItemsMain()) if (isSlotActive(s)) return true;
            }
            if (instance.config.getItemsOff() != null)
            {
                for (ArmorSlot s : instance.config.getItemsOff()) if (isSlotActive(s)) return true;
            }
            if (instance.config.getArmorSlots() != null)
            {
                for (ArmorSlot s : instance.config.getArmorSlots().values()) if (isSlotActive(s)) return true;
            }
            return false;
        }
        catch (Throwable ig)
        {
            return true;
        }
    }

    private static int estimateBoneCount(IModel model)
    {
        if (model instanceof BOBJModel bobj)
        {
            try
            {
                return bobj.getArmature().orderedBones.size();
            }
            catch (Throwable t)
            {
                return Integer.MAX_VALUE; /* Defensive: unknown size = reject */
            }
        }
        /* Cubic Model: groups treated as bone-equivalent. Approximate via
         * topGroups + 1 hop for now; deep hierarchical bone-count would
         * require a recursive walk. Tier 2 may revisit. */
        try
        {
            mchorse.bbs_mod.cubic.data.model.Model cubic = (mchorse.bbs_mod.cubic.data.model.Model) model;
            return cubic.topGroups == null ? 0 : cubic.topGroups.size();
        }
        catch (Throwable t)
        {
            return Integer.MAX_VALUE;
        }
    }

}
