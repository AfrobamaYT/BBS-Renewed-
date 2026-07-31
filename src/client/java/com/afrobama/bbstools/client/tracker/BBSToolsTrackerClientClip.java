package com.afrobama.bbstools.client.tracker;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import com.afrobama.bbstools.tracker.BBSToolsTrackerClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.utils.MatrixUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Camera-tracker clip with afrobama's HUD/hand rendering subsystem.
 * Recovered from .modified jar via CFR decompile + Yarn translation.
 *
 * Public API surface (used by InGameHudMixin, GameRendererMixin,
 * CameraWorkCameraController):
 * - resetRenderFlags: per-frame reset, called by camera controller before clips
 * - shouldRenderHand/shouldRenderHud: queries with ease-in/out smoothing
 * - applyBob, updateBob, clearBobState: walk-distance-based hand bobbing
 * - updateSprintFov, resetSprintFov, adjustFovForSprint: sprint FOV interp
 * - captureOriginalGuiScale, beginHudRendering/endHudRendering: HUD scale
 * - beginLevelRendering/endLevelRendering: level render lifecycle hooks
 * - beginHandRendering/endHandRendering: hand render lifecycle hooks
 * - currentEntity, currentTickDelta: per-frame state set by applyClip
 *
 * Heavy use of reflection on MinecraftClient internals (HeldItemRenderer,
 * Window, InGameHud) to swap items/state mid-frame. Reflection cache
 * resolves field names dynamically to survive obfuscation across remappings.
 */
public class BBSToolsTrackerClientClip extends BBSToolsTrackerClip
{
    /* === Public flag/state surface === */
    public static volatile boolean renderHand;
    public static volatile boolean renderHud;
    public static volatile IEntity currentEntity;
    public static volatile float currentTickDelta;
    public static int originalGuiScale = -1;
    public static boolean scaleChanged;

    /* === Bob state === */
    public static float bobAmount;
    public static float lastBobAmount;
    public static float walkDistance;
    public static float lastWalkDistance;
    public static boolean isSprinting;
    public static float tickSpeed;
    public static int lastUpdateTick = -1;

    /* === Sprint FOV state === */
    public static float sprintFovFactor = 1F;
    public static float lastSprintFovFactor = 1F;
    private static int lastFovTick = -1;
    public static int zeroSpeedTicks;
    public static double lastBaseFov = 70D;

    /* === Render state machine (entity-to-player swap) === */
    private static Matrix4f savedWorldProjection;
    private static boolean handProjectionSwapped;
    private static ItemStack[] savedHotbarItems;
    private static ItemStack savedOffHandItem;
    private static int savedSelectedSlot;
    private static int savedHurtTime;
    private static float savedSwingProgress;
    private static float savedLastSwingProgress;
    private static boolean savedHandSwinging;
    private static float savedHealth;
    private static int savedFoodLevel;
    private static boolean entityStateSwapped;

    /* === Hand swap state === */
    private static boolean handStateSwapped;
    private static int handSavedSelectedSlot;
    private static int handSwappedSlotIndex = -1;
    private static ItemStack handSavedSlotItem;
    private static ItemStack handSavedOffHandItem;
    private static ItemStack[] lastTrackedHotbar;
    private static int[] hotbarChangeTick;

    /* === Held-item-renderer reflection === */
    private static Object cachedHeldItemRenderer;
    private static Field heldMainHandField;
    private static Field heldOffHandField;
    private static Field heldEquipMainField;
    private static Field heldPrevEquipMainField;
    private static Field heldEquipOffField;
    private static Field heldPrevEquipOffField;
    private static boolean heldItemReflectionInit;
    private static ItemStack savedHeldMainHand;
    private static ItemStack savedHeldOffHand;
    private static float savedEquipMain;
    private static float savedPrevEquipMain;
    private static float savedEquipOff;
    private static float savedPrevEquipOff;

    /* === HUD tooltip reflection === */
    private static Field hudTooltipFadeField;
    private static Field hudCurrentStackField;
    private static boolean hudTooltipReflectionInit;
    private static int savedTooltipFade;
    private static ItemStack savedCurrentStack;
    private static ItemStack lastEntitySelectedItem;
    private static int entityTooltipFade;
    private static int lastTooltipTick = -1;
    private static float entityEquipMain = 1F;
    private static float entityPrevEquipMain = 1F;
    private static float entityEquipOff = 1F;
    private static float entityPrevEquipOff = 1F;
    private static ItemStack lastEntityMainItem;
    private static ItemStack lastEntityOffItem;
    private static int lastEquipTick = -1;

    /* === Level/HUD lifecycle === */
    private static CameraType savedPerspectiveForLevel;
    private static boolean savedHudHidden;
    private static boolean savedHudHiddenForLevel;
    private static boolean levelRenderingActive;
    private static boolean prevFrameRenderHand;
    private static boolean prevFrameRenderHud;
    private static float handShowProgress;
    private static float prevHandShowProgress;
    private static float hudShowProgress;
    private static float prevHudShowProgress;
    private static int lastTransitionTick = -1;
    private static boolean hudMatrixPushed;
    private static boolean controllerRanThisFrame;

    /* === Window scale reflection === */
    private static double savedScaleFactor;
    private static int savedScaledWidth;
    private static int savedScaledHeight;
    private static Field windowScaleFactorField;
    private static Field windowScaledWidthField;
    private static Field windowScaledHeightField;
    private static boolean windowReflectionInitialized;

    /* ========================================================
     * Public lifecycle API
     * ======================================================== */

    public static void resetRenderFlags()
    {
        controllerRanThisFrame = true;

        if (!levelRenderingActive)
        {
            if (entityStateSwapped) restorePlayer();
            if (handStateSwapped) restoreHandState();
        }

        prevFrameRenderHand = renderHand;
        prevFrameRenderHud = renderHud;
        renderHand = false;
        renderHud = false;
        currentEntity = null;
    }

    public static boolean shouldRenderHand()
    {
        return renderHand || handShowProgress > 0.01F;
    }

    public static boolean shouldRenderHud()
    {
        return renderHud || hudShowProgress > 0.01F;
    }

    private static void advanceShowTransitions()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int tick = mc.player.tickCount;
        if (tick == lastTransitionTick) return;
        lastTransitionTick = tick;

        if (renderHand && !prevFrameRenderHand) handShowProgress = 0F;
        if (renderHud && !prevFrameRenderHud) hudShowProgress = 0F;

        prevHandShowProgress = handShowProgress;
        float handTarget = renderHand ? 1F : 0F;
        handShowProgress += Math.max(-0.2F, Math.min(0.2F, handTarget - handShowProgress));
        if (Math.abs(handShowProgress - handTarget) < 0.01F) handShowProgress = handTarget;

        prevHudShowProgress = hudShowProgress;
        float hudTarget = renderHud ? 1F : 0F;
        hudShowProgress += Math.max(-0.25F, Math.min(0.25F, hudTarget - hudShowProgress));
        if (Math.abs(hudShowProgress - hudTarget) < 0.01F) hudShowProgress = hudTarget;
    }

    /* ========================================================
     * Bobbing
     * ======================================================== */

    public static void applyBob(PoseStack matrices, float tickDelta)
    {
        float h = lastBobAmount + (bobAmount - lastBobAmount) * tickDelta;
        if (h < 0.002F) return;

        float f = walkDistance - lastWalkDistance;
        float g = -(walkDistance + f * tickDelta);

        matrices.translate(
            (float) (Math.sin((double) g * Math.PI) * (double) h * 0.5),
            (float) (-Math.abs(Math.cos((double) g * Math.PI) * (double) h)),
            0F
        );
        matrices.mulPose(Axis.ZP.rotationDegrees(
            (float) (Math.sin((double) g * Math.PI) * (double) h * 3D)));
        matrices.mulPose(Axis.XP.rotationDegrees(
            (float) (Math.abs(Math.cos((double) g * Math.PI - 0.2D) * (double) h) * 5D)));
    }

    public static void updateBob(float prevX, float prevZ, float curX, float curZ, int tick)
    {
        if (tick == lastUpdateTick) return;
        lastUpdateTick = tick;

        lastBobAmount = bobAmount;
        lastWalkDistance = walkDistance;

        float dx = curX - prevX;
        float dz = curZ - prevZ;
        tickSpeed = (float) Math.sqrt(dx * dx + dz * dz);

        if (tickSpeed > 2F)
        {
            bobAmount = 0F;
            lastBobAmount = 0F;
            return;
        }

        float target = Math.min(0.1F, tickSpeed);
        bobAmount += (target - bobAmount) * 0.4F;
        walkDistance += tickSpeed * 0.6F;
    }

    public static void clearBobState()
    {
        bobAmount = 0F;
        lastBobAmount = 0F;
        walkDistance = 0F;
        lastWalkDistance = 0F;
        tickSpeed = 0F;
        lastUpdateTick = -1;
        zeroSpeedTicks = 0;
    }

    /* ========================================================
     * Sprint FOV
     * ======================================================== */

    public static void updateSprintFov(boolean entitySprinting, int tick)
    {
        if (tick == lastFovTick) return;
        lastFovTick = tick;

        lastSprintFovFactor = sprintFovFactor;
        isSprinting = entitySprinting;

        float targetFov = entitySprinting ? 1.15F : 1F;
        sprintFovFactor += (targetFov - sprintFovFactor) * 0.2F;
    }

    public static void resetSprintFov()
    {
        isSprinting = false;
        sprintFovFactor = 1F;
        lastSprintFovFactor = 1F;
        lastFovTick = -1;
    }

    public static double adjustFovForSprint(double fov)
    {
        lastBaseFov = fov;
        float smoothed = lastSprintFovFactor + (sprintFovFactor - lastSprintFovFactor) * currentTickDelta;
        return fov * (double) smoothed;
    }

    /* ========================================================
     * Entity-to-player swap (HUD render)
     * ======================================================== */

    private static void swapEntityToPlayer()
    {
        if (entityStateSwapped) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || currentEntity == null) return;

        if (handStateSwapped) restoreHandState();

        if (lastTrackedHotbar == null)
        {
            lastTrackedHotbar = new ItemStack[9];
            hotbarChangeTick = new int[9];
            for (int i = 0; i < 9; i++) lastTrackedHotbar[i] = ItemStack.EMPTY;
        }

        int currentTick = player.tickCount;
        savedHotbarItems = new ItemStack[9];

        for (int i = 0; i < 9; i++)
        {
            savedHotbarItems[i] = player.getInventory().getItem(i).copy();

            ItemStack entityItem = currentEntity.getHotbarItem(i);
            ItemStack toSet = entityItem != null ? entityItem.copy() : ItemStack.EMPTY;

            if (!stacksMatch(toSet, lastTrackedHotbar[i]))
            {
                hotbarChangeTick[i] = currentTick;
                lastTrackedHotbar[i] = toSet.isEmpty() ? ItemStack.EMPTY : toSet.copy();
            }

            int bobTime = 5 - (currentTick - hotbarChangeTick[i]);
            if (bobTime > 0 && !toSet.isEmpty())
            {
                /* visual hotbar-change bob (small bounce) */
                try { toSet.setPopTime(bobTime); }
                catch (Throwable ignore) {}
            }

            player.getInventory().setItem(i, toSet);
        }

        savedOffHandItem = player.getInventory().getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND).copy();
        ItemStack entityOffHand = currentEntity.getEquipmentStack(EquipmentSlot.OFFHAND);
        player.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, entityOffHand != null ? entityOffHand : ItemStack.EMPTY);

        savedSelectedSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(currentEntity.getSelectedSlot());

        savedHurtTime = player.hurtTime;
        player.hurtTime = currentEntity.getHurtTimer();

        savedHealth = player.getHealth();
        player.setHealth(currentEntity.getHealth());

        savedFoodLevel = player.getFoodData().getFoodLevel();
        player.getFoodData().setFoodLevel(currentEntity.getHunger());

        initHudTooltipReflection();
        if (hudTooltipFadeField != null && hudCurrentStackField != null)
        {
            try
            {
                Gui hud = Minecraft.getInstance().gui;
                savedTooltipFade = hudTooltipFadeField.getInt(hud);
                savedCurrentStack = (ItemStack) hudCurrentStackField.get(hud);

                int entitySlot = currentEntity.selected;
                ItemStack entityHeld = currentEntity.getHotbarItem(entitySlot);
                if (entityHeld == null) entityHeld = ItemStack.EMPTY;

                if (lastEntitySelectedItem == null || !stacksMatch(entityHeld, lastEntitySelectedItem))
                {
                    entityTooltipFade = 40;
                    lastEntitySelectedItem = entityHeld.isEmpty() ? ItemStack.EMPTY : entityHeld.copy();
                }

                if (currentTick != lastTooltipTick)
                {
                    lastTooltipTick = currentTick;
                    if (entityTooltipFade > 0) entityTooltipFade--;
                }

                hudCurrentStackField.set(hud, entityHeld);
                hudTooltipFadeField.setInt(hud, entityTooltipFade);
            }
            catch (Exception ignore) {}
        }

        entityStateSwapped = true;
    }

    private static void restorePlayer()
    {
        if (!entityStateSwapped) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null)
        {
            for (int i = 0; i < 9; i++)
            {
                player.getInventory().setItem(i, savedHotbarItems[i]);
            }
            player.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, savedOffHandItem);
            player.getInventory().setSelectedSlot(savedSelectedSlot);
            player.hurtTime = savedHurtTime;
            player.setHealth(savedHealth);
            player.getFoodData().setFoodLevel(savedFoodLevel);

            if (hudTooltipFadeField != null && hudCurrentStackField != null)
            {
                try
                {
                    Gui hud = Minecraft.getInstance().gui;
                    hudTooltipFadeField.setInt(hud, savedTooltipFade);
                    hudCurrentStackField.set(hud, savedCurrentStack);
                }
                catch (Exception ignore) {}
            }
        }

        entityStateSwapped = false;
    }

    /* ========================================================
     * Hand swap (per-frame, for hand rendering)
     * ======================================================== */

    private static void swapHandState()
    {
        if (handStateSwapped) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || currentEntity == null) return;

        int entitySlot = currentEntity.selected;
        ItemStack entityMain = currentEntity.getHotbarItem(entitySlot);
        boolean entityHasMainItem = entityMain != null && !entityMain.isEmpty();

        handSavedSelectedSlot = player.getInventory().getSelectedSlot();

        if (entityHasMainItem)
        {
            player.getInventory().setSelectedSlot(entitySlot);
            handSwappedSlotIndex = entitySlot;
            handSavedSlotItem = player.getInventory().getItem(entitySlot).copy();
            player.getInventory().setItem(entitySlot, entityMain.copy());
        }
        else
        {
            handSwappedSlotIndex = -1;
        }

        ItemStack entityOff = currentEntity.getEquipmentStack(EquipmentSlot.OFFHAND);
        boolean entityHasOffItem = entityOff != null && !entityOff.isEmpty();
        handSavedOffHandItem = player.getInventory().getItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND).copy();
        if (entityHasOffItem)
        {
            player.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, entityOff.copy());
        }

        savedHurtTime = player.hurtTime;
        player.hurtTime = currentEntity.getHurtTimer();

        savedSwingProgress = player.attackAnim;
        savedLastSwingProgress = player.oAttackAnim;
        savedHandSwinging = player.swinging;

        float entitySwing = currentEntity.getHandSwingProgress(currentTickDelta);
        player.attackAnim = Math.max(player.attackAnim, entitySwing);
        player.oAttackAnim = Math.max(player.oAttackAnim, entitySwing);

        if (player.attackAnim > 0F || entitySwing > 0F) player.swinging = true;

        savedHealth = player.getHealth();
        player.setHealth(currentEntity.getHealth());

        updateEquipProgress();
        initHeldItemReflection();

        if (cachedHeldItemRenderer != null && heldMainHandField != null)
        {
            try
            {
                savedHeldMainHand = (ItemStack) heldMainHandField.get(cachedHeldItemRenderer);
                savedHeldOffHand = (ItemStack) heldOffHandField.get(cachedHeldItemRenderer);
                heldMainHandField.set(cachedHeldItemRenderer, player.getMainHandItem());
                heldOffHandField.set(cachedHeldItemRenderer, player.getOffhandItem());

                if (heldEquipMainField != null)
                {
                    savedEquipMain = heldEquipMainField.getFloat(cachedHeldItemRenderer);
                    savedPrevEquipMain = heldPrevEquipMainField.getFloat(cachedHeldItemRenderer);
                    savedEquipOff = heldEquipOffField.getFloat(cachedHeldItemRenderer);
                    savedPrevEquipOff = heldPrevEquipOffField.getFloat(cachedHeldItemRenderer);

                    heldEquipMainField.setFloat(cachedHeldItemRenderer, entityEquipMain * handShowProgress);
                    heldPrevEquipMainField.setFloat(cachedHeldItemRenderer, entityPrevEquipMain * prevHandShowProgress);
                    heldEquipOffField.setFloat(cachedHeldItemRenderer, entityEquipOff * handShowProgress);
                    heldPrevEquipOffField.setFloat(cachedHeldItemRenderer, entityPrevEquipOff * prevHandShowProgress);
                }
            }
            catch (Exception ignore) {}
        }

        handStateSwapped = true;
    }

    private static void restoreHandState()
    {
        if (!handStateSwapped) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null)
        {
            if (handSwappedSlotIndex >= 0)
            {
                player.getInventory().setItem(handSwappedSlotIndex, handSavedSlotItem);
            }
            player.getInventory().setItem(net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND, handSavedOffHandItem);
            player.getInventory().setSelectedSlot(handSavedSelectedSlot);
            player.hurtTime = savedHurtTime;
            player.attackAnim = savedSwingProgress;
            player.oAttackAnim = savedLastSwingProgress;
            player.swinging = savedHandSwinging;
            player.setHealth(savedHealth);

            if (cachedHeldItemRenderer != null && heldMainHandField != null)
            {
                try
                {
                    heldMainHandField.set(cachedHeldItemRenderer, savedHeldMainHand);
                    heldOffHandField.set(cachedHeldItemRenderer, savedHeldOffHand);
                    if (heldEquipMainField != null)
                    {
                        heldEquipMainField.setFloat(cachedHeldItemRenderer, savedEquipMain);
                        heldPrevEquipMainField.setFloat(cachedHeldItemRenderer, savedPrevEquipMain);
                        heldEquipOffField.setFloat(cachedHeldItemRenderer, savedEquipOff);
                        heldPrevEquipOffField.setFloat(cachedHeldItemRenderer, savedPrevEquipOff);
                    }
                }
                catch (Exception ignore) {}
            }
        }

        handStateSwapped = false;
    }

    /* ========================================================
     * Equipment-progress smoothing
     * ======================================================== */

    private static void updateEquipProgress()
    {
        if (currentEntity == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int tick = mc.player.tickCount;
        if (tick == lastEquipTick) return;
        lastEquipTick = tick;

        int slot = currentEntity.selected;
        ItemStack newMain = currentEntity.getHotbarItem(slot);
        if (newMain == null) newMain = ItemStack.EMPTY;

        ItemStack newOff = currentEntity.getEquipmentStack(EquipmentSlot.OFFHAND);
        if (newOff == null) newOff = ItemStack.EMPTY;

        entityPrevEquipMain = entityEquipMain;
        boolean mainMatch = lastEntityMainItem != null && stacksMatch(newMain, lastEntityMainItem);
        float mainTarget = mainMatch ? 1F : 0F;
        entityEquipMain += Math.max(-0.4F, Math.min(0.4F, mainTarget - entityEquipMain));
        if (entityEquipMain < 0.1F) lastEntityMainItem = newMain.copy();
        if (lastEntityMainItem == null) lastEntityMainItem = newMain.copy();

        entityPrevEquipOff = entityEquipOff;
        boolean offMatch = lastEntityOffItem != null && stacksMatch(newOff, lastEntityOffItem);
        float offTarget = offMatch ? 1F : 0F;
        entityEquipOff += Math.max(-0.4F, Math.min(0.4F, offTarget - entityEquipOff));
        if (entityEquipOff < 0.1F) lastEntityOffItem = newOff.copy();
        if (lastEntityOffItem == null) lastEntityOffItem = newOff.copy();
    }

    private static boolean stacksMatch(ItemStack a, ItemStack b)
    {
        if (a.isEmpty()) return b.isEmpty();
        if (b.isEmpty()) return false;
        return ItemStack.isSameItem(a, b) && a.getCount() == b.getCount();
    }

    /* ========================================================
     * Reflection caches
     * ======================================================== */

    private static void initHudTooltipReflection()
    {
        if (hudTooltipReflectionInit) return;
        hudTooltipReflectionInit = true;

        try
        {
            Field[] fields = Gui.class.getDeclaredFields();

            for (Field f : fields)
            {
                String n = f.getName();
                if (n.equals("heldItemTooltipFade") || n.equals("f_92995_") || n.equals("toolHighlightTimer"))
                {
                    f.setAccessible(true);
                    hudTooltipFadeField = f;
                    continue;
                }
                if (!n.equals("currentStack") && !n.equals("f_92996_") && !n.equals("lastToolHighlight")) continue;
                f.setAccessible(true);
                hudCurrentStackField = f;
            }

            if (hudTooltipFadeField != null && hudCurrentStackField != null) return;

            /* Fallback: scan for last int + first ItemStack pair */
            Field lastIntField = null;
            for (Field f : fields)
            {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == Integer.TYPE)
                {
                    lastIntField = f;
                    continue;
                }
                if (f.getType() == ItemStack.class && lastIntField != null)
                {
                    lastIntField.setAccessible(true);
                    f.setAccessible(true);
                    hudTooltipFadeField = lastIntField;
                    hudCurrentStackField = f;
                    break;
                }
                lastIntField = null;
            }
        }
        catch (Exception ignore) {}
    }

    private static void initHeldItemReflection()
    {
        if (heldItemReflectionInit) return;
        heldItemReflectionInit = true;

        try
        {
            GameRenderer gr = Minecraft.getInstance().gameRenderer;

            for (Field f : gr.getClass().getDeclaredFields())
            {
                Class<?> type = f.getType();
                if (type.isPrimitive() || type == String.class || type.isInterface()) continue;

                int itemStackCount = 0;
                for (Field tf : type.getDeclaredFields())
                {
                    if (Modifier.isStatic(tf.getModifiers()) || tf.getType() != ItemStack.class) continue;
                    itemStackCount++;
                }
                if (itemStackCount < 2) continue;

                f.setAccessible(true);
                cachedHeldItemRenderer = f.get(gr);

                int itemIdx = 0;
                int floatIdx = 0;
                for (Field tf : type.getDeclaredFields())
                {
                    if (Modifier.isStatic(tf.getModifiers())) continue;
                    tf.setAccessible(true);

                    if (tf.getType() == ItemStack.class)
                    {
                        if (itemIdx == 0) heldMainHandField = tf;
                        else if (itemIdx == 1) heldOffHandField = tf;
                        itemIdx++;
                        continue;
                    }

                    if (tf.getType() != Float.TYPE) continue;
                    if (floatIdx == 0) heldEquipMainField = tf;
                    else if (floatIdx == 1) heldPrevEquipMainField = tf;
                    else if (floatIdx == 2) heldEquipOffField = tf;
                    else if (floatIdx == 3) heldPrevEquipOffField = tf;
                    floatIdx++;
                }
                break;
            }
        }
        catch (Exception ignore) {}
    }

    private static void initWindowReflection(Window window)
    {
        if (windowReflectionInitialized) return;
        windowReflectionInitialized = true;

        try
        {
            Class<?> wc = window.getClass();

            for (Field f : wc.getDeclaredFields())
            {
                if (f.getType() != Double.TYPE || Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                windowScaleFactorField = f;
                break;
            }

            int sw = window.getGuiScaledWidth();
            int sh = window.getGuiScaledHeight();

            for (Field f : wc.getDeclaredFields())
            {
                if (f.getType() == Integer.TYPE && !Modifier.isStatic(f.getModifiers()))
                {
                    f.setAccessible(true);
                    int val = f.getInt(window);
                    if (val == sw && windowScaledWidthField == null) windowScaledWidthField = f;
                    else if (val == sh && windowScaledHeightField == null) windowScaledHeightField = f;
                }
                if (windowScaledWidthField != null && windowScaledHeightField != null) break;
            }
        }
        catch (Exception ignore) {}
    }

    /* ========================================================
     * Level/HUD/hand render lifecycle
     * ======================================================== */

    public static void beginLevelRendering()
    {
        if (!controllerRanThisFrame)
        {
            renderHand = false;
            renderHud = false;
            handShowProgress = 0F;
            hudShowProgress = 0F;
            return;
        }
        controllerRanThisFrame = false;

        boolean handNeeded = shouldRenderHand();
        if (!handNeeded && !shouldRenderHud()) return;
        if (levelRenderingActive) return;

        advanceShowTransitions();
        Minecraft mc = Minecraft.getInstance();
        savedHudHiddenForLevel = mchorse.bbs_mod.client.HudSichtbarkeit.istVersteckt();

        if (handNeeded)
        {
            mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(false);
            swapHandState();
            savedPerspectiveForLevel = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }

        levelRenderingActive = true;
    }

    public static void endLevelRendering()
    {
        if (!levelRenderingActive) return;
        levelRenderingActive = false;

        if (handStateSwapped) restoreHandState();

        if (savedPerspectiveForLevel != null)
        {
            Minecraft.getInstance().options.setCameraType(savedPerspectiveForLevel);
            savedPerspectiveForLevel = null;
        }

        Minecraft mc = Minecraft.getInstance();
        mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(shouldRenderHud() ? false : savedHudHiddenForLevel);
    }

    public static void beginHandRendering()
    {
        if (!shouldRenderHand()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!levelRenderingActive)
        {
            savedHudHidden = mchorse.bbs_mod.client.HudSichtbarkeit.istVersteckt();
            mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(false);
            swapHandState();
        }

        float smoothed = lastSprintFovFactor + (sprintFovFactor - lastSprintFovFactor) * currentTickDelta;
        if (Math.abs(smoothed - 1F) < 0.001F)
        {
            handProjectionSwapped = false;
            return;
        }

        handProjectionSwapped = true;
        savedWorldProjection = new Matrix4f(mchorse.bbs_mod.graphics.Projektion.lesen());

        double baseFovRad = lastBaseFov * Math.PI / 180D;
        double sprintFovRad = lastBaseFov * (double) smoothed * Math.PI / 180D;
        float ratio = (float) (Math.tan(sprintFovRad / 2D) / Math.tan(baseFovRad / 2D));

        Matrix4f handProj = new Matrix4f(savedWorldProjection);
        handProj.m00(handProj.m00() * ratio);
        handProj.m11(handProj.m11() * ratio);
        mchorse.bbs_mod.graphics.Projektion.setzen(handProj, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
    }

    public static void endHandRendering()
    {
        if (!levelRenderingActive)
        {
            restoreHandState();
            mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(savedHudHidden);
        }
        if (handProjectionSwapped)
        {
            mchorse.bbs_mod.graphics.Projektion.setzen(savedWorldProjection, com.mojang.blaze3d.ProjectionType.PERSPECTIVE);
            handProjectionSwapped = false;
        }
    }

    public static void beginHudRendering(GuiGraphicsExtractor drawContext)
    {
        advanceShowTransitions();
        swapEntityToPlayer();
        beginHudScaleRestore();

        if (hudShowProgress < 0.999F && drawContext != null)
        {
            drawContext.pose().pushPose();
            float offset = (1F - hudShowProgress) * (float) Minecraft.getInstance().getWindow().getGuiScaledHeight();
            drawContext.pose().translate(0F, offset, 0F);
            hudMatrixPushed = true;
        }
    }

    public static void endHudRendering(GuiGraphicsExtractor drawContext)
    {
        if (hudMatrixPushed && drawContext != null)
        {
            drawContext.pose().popPose();
            hudMatrixPushed = false;
        }
        endHudScaleRestore();
        restorePlayer();
        mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(savedHudHiddenForLevel);
    }

    /* ========================================================
     * GUI scale capture/restore (forces native scale during HUD)
     * ======================================================== */

    public static void captureOriginalGuiScale()
    {
        if (originalGuiScale >= 0) return;

        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null)
        {
            try
            {
                for (Class<?> clazz = screen.getClass(); clazz != null && !clazz.getName().equals("java.lang.Object"); clazz = clazz.getSuperclass())
                {
                    for (Field f : clazz.getDeclaredFields())
                    {
                        if (!f.getName().equals("lastGuiScale") || f.getType() != Integer.TYPE) continue;
                        f.setAccessible(true);
                        originalGuiScale = f.getInt(screen);
                        return;
                    }
                }
            }
            catch (Exception ignore) {}
        }

        if (originalGuiScale < 0)
        {
            try
            {
                originalGuiScale = Minecraft.getInstance().options.guiScale().get();
            }
            catch (Exception e)
            {
                originalGuiScale = 0;
            }
        }
    }

    public static void beginHudScaleRestore()
    {
        if (scaleChanged || !renderHud) return;

        captureOriginalGuiScale();
        if (originalGuiScale < 0) return;

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        initWindowReflection(window);

        if (windowScaleFactorField == null) return;

        try
        {
            int fbw = window.getWidth();
            int fbh = window.getHeight();
            int gs = originalGuiScale;
            int origSF;

            if (gs == 0)
            {
                origSF = 1;
                while (origSF < fbw && origSF < fbh && fbw / (origSF + 1) >= 320 && fbh / (origSF + 1) >= 240) origSF++;
            }
            else
            {
                origSF = 1;
                while (origSF != gs && origSF < fbw && origSF < fbh && fbw / (origSF + 1) >= 320 && fbh / (origSF + 1) >= 240) origSF++;
            }

            double currentSF = windowScaleFactorField.getDouble(window);
            if ((int) currentSF == origSF) return;

            savedScaleFactor = currentSF;
            savedScaledWidth = windowScaledWidthField.getInt(window);
            savedScaledHeight = windowScaledHeightField.getInt(window);

            windowScaleFactorField.setDouble(window, origSF);
            int sw = (int) Math.ceil((double) fbw / (double) origSF);
            int sh = (int) Math.ceil((double) fbh / (double) origSF);
            windowScaledWidthField.setInt(window, sw);
            windowScaledHeightField.setInt(window, sh);

            RenderSystem.backupProjectionMatrix();
            Matrix4f ortho = new Matrix4f().setOrtho(0F, (float) sw, (float) sh, 0F, 1000F, 21000F);
            mchorse.bbs_mod.graphics.Projektion.setzen(ortho, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            scaleChanged = true;

            /* 4K-HUD-FIX: acceleratedrendering tauscht MCs BufferSource gegen ihren beschleunigten
             * (core.mixins.buffers.BufferSourceMixin#initAcceleration). Die HUD-quads landen dann
             * in IHREN puffern und rastern erst NACH endHudScaleRestore — unter der ortho, die
             * gerade oben mit der override-breite gesetzt wurde. Sichtbar als verschobene und zu
             * kleine status-bars beim 4K-export.
             *
             * Ab hier läuft das HUD unbeschleunigt, damit die quads im normalen puffer bleiben.
             *
             * DIREKT AN scaleChanged GEKOPPELT: endHudScaleRestore prüft dieselbe variable, damit
             * push und pop nicht auseinanderlaufen können. Weiter oben stünde der push hinter
             * mehreren frühen returns, bei denen scaleChanged false bleibt — der stack der mod
             * würde dann pro frame wachsen.
             *
             * NICHT flushen: der erste versuch (cml-fresh 31436f3e7, revertet c28140bed) rief
             * drawBuffers(ALL) und rasterte damit auch die welt-puffer mitten im tracker-frame →
             * viewport-flip bei hand+HUD. Gar nicht erst beschleunigen hat dieses problem nicht. */
            mchorse.bbs_mod.compat.AccelGuiGuard.push();
        }
        catch (Exception ignore) {}
    }

    public static void endHudScaleRestore()
    {
        if (!scaleChanged) return;
        scaleChanged = false;

        /* Gegenstück zum push in beginHudScaleRestore — VOR dem projektions-restore, damit die
         * beschleunigung exakt für die dauer des skalierten HUD-abschnitts aus war. */
        mchorse.bbs_mod.compat.AccelGuiGuard.pop();

        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();

        try
        {
            windowScaleFactorField.setDouble(window, savedScaleFactor);
            windowScaledWidthField.setInt(window, savedScaledWidth);
            windowScaledHeightField.setInt(window, savedScaledHeight);
            RenderSystem.restoreProjectionMatrix();
        }
        catch (Exception ignore) {}
    }

    /* ========================================================
     * Reset (called between films)
     * ======================================================== */

    public static void resetBob()
    {
        if (entityStateSwapped) restorePlayer();
        if (handStateSwapped) restoreHandState();
        clearBobState();

        entityEquipMain = 1F;
        entityPrevEquipMain = 1F;
        entityEquipOff = 1F;
        entityPrevEquipOff = 1F;
        lastEntityMainItem = null;
        lastEntityOffItem = null;
        lastEquipTick = -1;
        lastEntitySelectedItem = null;
        entityTooltipFade = 0;
        lastTooltipTick = -1;
        levelRenderingActive = false;
        handShowProgress = 0F;
        prevHandShowProgress = 0F;
        hudShowProgress = 0F;
        prevHudShowProgress = 0F;
        prevFrameRenderHand = false;
        prevFrameRenderHud = false;
        lastTransitionTick = -1;
        hudMatrixPushed = false;
    }

    public static double extrapolate(double prev, double current, double tickDelta)
    {
        return current + (current - prev) * tickDelta;
    }

    public static float extrapolatef(float prev, float current, float tickDelta)
    {
        return current + (current - prev) * tickDelta;
    }

    /* ========================================================
     * applyClip (camera tracking + flag-set for hand/HUD)
     * ======================================================== */

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        List<IEntity> entities = this.getEntities(context);
        IEntity entity = entities.isEmpty() ? null : entities.get(0);

        if (entity != null) currentEntity = entity;

        if (this.showHand.get() && (entity == null || entity.getShowHand())) renderHand = true;
        if (this.showHud.get() && (entity == null || entity.getShowHud())) renderHud = true;

        if (entity == null) return;

        /* Hand-anchor mode: when showHand is on and no group is set, anchor camera to entity head */
        if (this.showHand.get())
        {
            String grp = this.group.get();
            boolean hasGroup = grp != null && !grp.isEmpty();

            if (!hasGroup)
            {
                float t = context.transition;
                double eyeX = Lerps.lerp(entity.getPrevX(), entity.getX(), (double) t);
                double eyeY = Lerps.lerp(entity.getPrevY(), entity.getY(), (double) t) + entity.getEyeHeight();
                double eyeZ = Lerps.lerp(entity.getPrevZ(), entity.getZ(), (double) t);
                float headYaw = Lerps.lerp(entity.getPrevHeadYaw(), entity.getHeadYaw(), t);
                float pitch = Lerps.lerp(entity.getPrevPitch(), entity.getPitch(), t);

                double ox = this.offset.get().x;
                double oy = this.offset.get().y;
                double oz = this.offset.get().z;
                float yawRad = (float) Math.toRadians(headYaw);

                position.point.x = eyeX + (ox * Math.cos(yawRad) - oz * Math.sin(yawRad));
                position.point.y = eyeY + oy;
                position.point.z = eyeZ + (ox * Math.sin(yawRad) + oz * Math.cos(yawRad));
                position.angle.yaw = headYaw + 180F + (float) this.angle.get().y;
                position.angle.pitch = pitch + (float) this.angle.get().x;
                position.angle.roll = (float) this.angle.get().z;
                position.angle.fov = this.fov.get();
                return;
            }
        }

        if (!context.applyUnderneath(this.tick.get(), 0F, this.position))
        {
            this.position.copy(position);
        }

        Form form = entity.getForm();
        if (form == null) return;

        MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, context.transition);
        Vector3f relativeFormPos = new Vector3f();
        String targetGroup = this.group.get();

        if (!map.has(targetGroup)) return;

        Matrix4f formTransform = BaseFilmController.getMatrixForRenderWithRotation(entity, position.point.x, position.point.y, position.point.z, context.transition);
        Pair<Matrix4f, Float> totalMatrix = BaseFilmController.getTotalMatrix(((CameraClipContext) context).entities, form.anchor.get(), formTransform, position.point.x, position.point.y, position.point.z, context.transition, 0);

        if (totalMatrix.a != null) formTransform = totalMatrix.a;

        formTransform.mul(map.get(targetGroup).matrix());
        formTransform.getTranslation(relativeFormPos);

        Matrix3d trackerRot = new Matrix3d(formTransform);
        Vector3d relativeTrackerPos = new Vector3d(this.offset.get().x, this.offset.get().y, this.offset.get().z);
        trackerRot.transform(relativeTrackerPos);
        relativeTrackerPos.add(relativeFormPos);

        Vector3d firstCamPos = new Vector3d(this.position.point.x, this.position.point.y, this.position.point.z);
        Vector3d currentCamPos = new Vector3d(position.point.x, position.point.y, position.point.z);
        Vector3d newAngle = new Vector3d();
        Vector3d newPosition = new Vector3d();

        if (this.lookAt.get())
        {
            Angle lookAtAngle = Angle.angle(relativeTrackerPos.x, relativeTrackerPos.y, relativeTrackerPos.z);
            newAngle.set(lookAtAngle.pitch, lookAtAngle.yaw, lookAtAngle.roll)
                .add(this.angle.get().x, this.angle.get().y, this.angle.get().z);
        }
        else
        {
            relativeTrackerPos.add(currentCamPos);

            if (this.relative.get())
            {
                Vector3d camPosDelta = new Vector3d(currentCamPos).sub(firstCamPos);
                trackerRot.transform(camPosDelta);
                relativeTrackerPos.add(camPosDelta);
            }

            Vector3d offsetAngle = new Vector3d(this.angle.get().x, this.angle.get().y, this.angle.get().z);
            offsetAngle.set(Math.toRadians(offsetAngle.x), Math.toRadians(offsetAngle.y), Math.toRadians(offsetAngle.z));
            trackerRot.mul(MatrixUtils.RotationOrder.YXZ.getRotationMatrix(offsetAngle.y, offsetAngle.x, offsetAngle.z));

            if (this.relative.get())
            {
                double angleDeltaX = Math.toRadians(position.angle.pitch - this.position.angle.pitch);
                double angleDeltaY = Math.toRadians(position.angle.yaw - this.position.angle.yaw);
                double angleDeltaZ = Math.toRadians(position.angle.roll - this.position.angle.roll);
                trackerRot.mul(MatrixUtils.RotationOrder.YXZ.getRotationMatrix(angleDeltaY, angleDeltaX, angleDeltaZ));
            }

            Vector3f globalEulerAngles = MatrixUtils.cast3dTo3f(MatrixUtils.RotationOrder.YXZ.getEulerAngles(trackerRot));
            newAngle.set(Math.toDegrees(globalEulerAngles.x), Math.toDegrees(-globalEulerAngles.y) - 180D, Math.toDegrees(globalEulerAngles.z));
            newPosition.set(relativeTrackerPos);
        }

        if (!this.lookAt.get())
        {
            position.point.x = this.isActive(0) ? newPosition.x : position.point.x;
            position.point.y = this.isActive(1) ? newPosition.y : position.point.y;
            position.point.z = this.isActive(2) ? newPosition.z : position.point.z;
        }

        position.angle.yaw = this.isActive(3) ? (float) newAngle.y : position.angle.yaw;
        position.angle.pitch = this.isActive(4) ? (float) newAngle.x : position.angle.pitch;
        position.angle.roll = this.isActive(5) ? (float) newAngle.z : position.angle.roll;
        position.angle.fov = this.isActive(6) ? this.fov.get() : position.angle.fov;
    }

    public boolean isActive(int bit)
    {
        return (this.active.get() >> bit & 1) == 1;
    }

    @Override
    protected Clip create()
    {
        return new BBSToolsTrackerClientClip();
    }

}
