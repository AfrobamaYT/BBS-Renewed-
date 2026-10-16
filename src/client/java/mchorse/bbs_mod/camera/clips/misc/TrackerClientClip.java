package mchorse.bbs_mod.camera.clips.misc;

import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.modifiers.TrackerClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.forms.entities.IEntity;
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
import org.joml.Matrix4f;
import org.joml.Vector3d;

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
 *
 * ⚠ WARUM DIE MASCHINERIE HIER LIEGT UND NICHT IN EIGENEN DATEIEN (2026-07-28):
 * die statischen felder/methoden sind KEIN reiner java-aufruf-graph. An diesem
 * KLASSENNAMEN haengen:
 *   - {@code TrackerSprintRampMixin} (@Mixin(TrackerClientClip.class), zielt auf
 *     updateSprintFov) — ein fehlendes ziel laesst mixin beim laden hart scheitern,
 *   - vier reflection-stellen in {@code com.afrobama.blendertransform}, die
 *     {@code Class.forName("...TrackerClientClip")} + feldnamen benutzen und ihre
 *     fehler SCHLUCKEN (sprintFovFactor, currentEntity).
 * Ein umzug muss diese fuenf stellen im selben schritt mitnehmen, sonst faellt das
 * sprint-FOV lautlos aus. Darum getrennt als eigene aufgabe, nicht im merge.
 */
public class TrackerClientClip extends TrackerClip
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
    private static float savedHealth;
    private static int savedFoodLevel;
    private static boolean entityStateSwapped;

    /* === Hand swap state === */
    private static boolean handStateSwapped;
    private static TrackedHandExtraction handExtraction;
    private static ItemStack[] lastTrackedHotbar;
    private static int[] hotbarChangeTick;

    /* === Held-item-renderer reflection === */

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
    /**
     * ⛔ NICHT EIN BOOLEAN, SONDERN DER KONTEXT SELBST (2026-07-31).
     *
     * <p>Vorher stand hier {@code boolean hudMatrixPushed}. Der Absturz
     * crash-2026-07-31_17.53 zeigt, warum das zu wenig ist:
     *
     * <pre>IllegalStateException: already at the bottom of the stack</pre>
     *
     * <p>Der Merker sagte "ich habe gepusht", der Matrizenstapel war trotzdem leer. In 26.2
     * ist die Oberflaechen-Matrix ein {@code Matrix3x2fStack}, der PRO BILD neu aufgebaut
     * wird -- ein Merker, der ueber Bildgrenzen hinweg lebt, kann davon nichts wissen. Kommt
     * das Ende in einem anderen Durchlauf an als der Anfang, poppt es einen fremden (leeren)
     * Stapel.
     *
     * <p>Gemerkt wird darum der KONTEXT, in dem gepusht wurde. Nur wenn das Ende denselben
     * bekommt, gehoert das Gegenstueck dorthin. Das ist derselbe Fehlertyp, den der
     * Netz-Waechter schon einmal produziert hat (Absturzhistorie G9): eine Pruefung, die
     * etwas anderes misst als das, worauf es ankommt.
     */
    private static GuiGraphicsExtractor hudMatrixKontext;
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
            if (handStateSwapped) restoreHandState();
            if (entityStateSwapped) restorePlayer();
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
        matrices.rotate(Axis.ZP.rotationDegrees(
            (float) (Math.sin((double) g * Math.PI) * (double) h * 3D)));
        matrices.rotate(Axis.XP.rotationDegrees(
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

                int entitySlot = currentEntity.getSelectedSlot();
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
        if (handStateSwapped) throw new IllegalStateException("Tracked hand extraction scopes cannot overlap");
        updateEquipProgress();
        handExtraction = TrackedHandExtraction.open(currentEntity);
        handStateSwapped = true;
    }

    private static void restoreHandState()
    {
        if (!handStateSwapped) return;
        handExtraction.close();
        handExtraction = null;
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

        int slot = currentEntity.getSelectedSlot();
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

    public static boolean beginPlayerStateExtraction()
    {
        if (!controllerRanThisFrame || currentEntity == null || !shouldRenderHand())
        {
            return false;
        }

        advanceShowTransitions();

        try
        {
            swapHandState();
            return handStateSwapped;
        }
        catch (RuntimeException error)
        {
            restoreHandState();
            throw error;
        }
    }

    public static void endPlayerStateExtraction()
    {
        restoreHandState();
    }

    public static void applyExtractedHandState(PlayerRenderState state)
    {
        if (!state.hasPlayer || state.avatarRenderState == null)
        {
            throw new IllegalStateException("Tracked hands require an extracted player render state");
        }

        FirstPersonHandsAndItemsRenderState hands = state.firstPersonHandsAndItems;
        hands.mainHandHeight = entityEquipMain * handShowProgress;
        hands.oldMainHandHeight = entityPrevEquipMain * prevHandShowProgress;
        hands.offHandHeight = entityEquipOff * handShowProgress;
        hands.oldOffHandHeight = entityPrevEquipOff * prevHandShowProgress;

        float entitySwing = currentEntity.getHandSwingProgress(currentTickDelta);
        state.avatarRenderState.swingAnimation = Math.max(state.avatarRenderState.swingAnimation, entitySwing);

        if (entitySwing > 0F)
        {
            LivingEntity.SwingDescription swing = currentEntity instanceof MCEntity mcEntity
                && mcEntity.getMcEntity() instanceof LivingEntity living ? living.getCurrentSwing() : null;

            if (swing == null)
            {
                swing = new LivingEntity.SwingDescription(InteractionHand.MAIN_HAND,
                    SwingAnimation.DEFAULT, SwingAnimation.DEFAULT.duration());
            }

            state.avatarRenderState.currentSwing = swing;
            hands.attackHand = swing.hand();
        }

        Minecraft.getInstance().gameRenderer.gameRenderState().optionsRenderState.cameraType = CameraType.FIRST_PERSON;
    }

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
        }

        levelRenderingActive = true;
    }

    public static void endLevelRendering()
    {
        if (!levelRenderingActive) return;
        levelRenderingActive = false;


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
        if (!handStateSwapped && !handProjectionSwapped) return;

        if (!levelRenderingActive)
        {
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
            /* 26.2: die GUI-Matrix ist ein org.joml.Matrix3x2fStack, kein PoseStack mehr —
             * die Oberflaeche rechnet nur noch in 2D. Deshalb pushMatrix/popMatrix statt
             * pushPose/popPose und ein Verschieben mit ZWEI Werten; die dritte Achse gab es
             * hier ohnehin nur als 0. */
            drawContext.pose().pushMatrix();
            float offset = (1F - hudShowProgress) * (float) Minecraft.getInstance().getWindow().getGuiScaledHeight();
            drawContext.pose().translate(0F, offset);
            hudMatrixKontext = drawContext;
        }
    }

    public static void endHudRendering(GuiGraphicsExtractor drawContext)
    {
        if (!entityStateSwapped && hudMatrixKontext == null && !scaleChanged) return;

        /* Nur poppen, wenn der Anfang in DIESEM Kontext lag -- sonst gehoert der Stapel
         * einem anderen Bild und ist leer. */
        if (drawContext != null && hudMatrixKontext == drawContext)
        {
            drawContext.pose().popMatrix();
        }

        hudMatrixKontext = null;
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
            /* Math.ceil == vanilla parity: Window.setScaleFactor computes
             * scaledWidth = fbw/sf rounded UP (int-div, +1 on remainder) — verified via bytecode. */
            int sw = (int) Math.ceil((double) fbw / (double) origSF);
            int sh = (int) Math.ceil((double) fbh / (double) origSF);
            windowScaledWidthField.setInt(window, sw);
            windowScaledHeightField.setInt(window, sh);

            /* FALLE: DrawContext-vertices werden beim SUBMIT in gui-koordinaten gebacken,
             * aber erst beim naechsten draw()-flush unter der DANN aktiven projektion
             * gerastert. Alles was noch unter der alten ortho gebaut wurde, MUSS vor dem
             * projektions-wechsel geflusht werden — sonst rendert es halb-so-gross/verschoben
             * (hotbar-drift beim 4K-export, hearts/food links aus dem rahmen). */
            /* 26.2: der Aufruf ist ersatzlos entfallen, und mit ihm die Falle, die der Kommentar
             * darueber beschreibt. Es gibt keinen halbfertigen Stapel mehr, den ein Wechsel der
             * Projektion falsch rastern koennte: eingereicht wird ein AUFTRAG, gezeichnet wird er
             * spaeter mit dem Zustand, den sein eigener Durchgang mitbringt. */

            mchorse.bbs_mod.graphics.Projektion.sichern();
            Matrix4f ortho = new Matrix4f().setOrtho(0F, (float) sw, (float) sh, 0F, 1000F, 21000F);
            mchorse.bbs_mod.graphics.Projektion.setzenUndUmkehren(ortho, com.mojang.blaze3d.ProjectionType.ORTHOGRAPHIC);
            scaleChanged = true;

            /* 4K-HUD-FIX: acceleratedrendering tauscht MCs BufferSource gegen ihren beschleunigten
             * (core.mixins.buffers.BufferSourceMixin#initAcceleration). Die HUD-quads landen dann
             * in IHREN puffern und rastern erst NACH endHudScaleRestore — unter der ortho, die
             * gerade oben mit der override-breite gesetzt wurde. Sichtbar als nach links
             * verschobene und zu kleine hotbar/status-bars beim 4K-export.
             *
             * Ab hier läuft das HUD unbeschleunigt, damit die quads im normalen puffer bleiben.
             *
             * DIREKT AN scaleChanged GEKOPPELT: endHudScaleRestore prüft dieselbe variable, damit
             * push und pop nicht auseinanderlaufen können. Weiter oben stünde der push hinter
             * mehreren frühen returns, bei denen scaleChanged false bleibt — der stack der mod
             * würde dann pro frame wachsen.
             *
             * NICHT flushen: der erste versuch (31436f3e7, revertet c28140bed) rief
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
            /* Erst flushen, DANN projektion zuruecksetzen: status-bars/xp/item-count-text
             * des vanilla-HUD liegen zu diesem zeitpunkt (InGameHud.render TAIL) noch
             * ungeflusht im immediate-buffer, in gui-koordinaten der OVERRIDE-scale gebacken.
             * Ohne flush rastern sie nach restoreProjectionMatrix unter der editor-ortho
             * (z.B. 960 statt 480 gui breit) = halbe groesse, nach links/oben verschoben.
             * DAS war der 4K-export-hotbar-bug (gemessen, siehe wiki hud-scale-restore). */
            /* 26.2: der Aufruf ist ersatzlos entfallen, und mit ihm die Falle, die der Kommentar
             * darueber beschreibt. Es gibt keinen halbfertigen Stapel mehr, den ein Wechsel der
             * Projektion falsch rastern koennte: eingereicht wird ein AUFTRAG, gezeichnet wird er
             * spaeter mit dem Zustand, den sein eigener Durchgang mitbringt. */

            windowScaleFactorField.setDouble(window, savedScaleFactor);
            windowScaledWidthField.setInt(window, savedScaledWidth);
            windowScaledHeightField.setInt(window, savedScaledHeight);
            mchorse.bbs_mod.graphics.Projektion.zurueck();
        }
        catch (Exception ignore) {}
    }

    /* ========================================================
     * Reset (called between films)
     * ======================================================== */

    public static void resetBob()
    {
        if (handStateSwapped) restoreHandState();
        if (entityStateSwapped) restorePlayer();
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
        hudMatrixKontext = null;
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
     * Kamera-platzierung (+ flag-set fuer hand/HUD)
     * ======================================================== */

    /**
     * Position produced by the clips underneath at the tick this clip was last
     * applied at. Relative mode measures the camera's travel against it, and the
     * camera editor needs the same measurement to invert the placement — see
     * {@link mchorse.bbs_mod.ui.film.clips.UITrackerClip}.
     */
    private final Position underneath = new Position();

    private boolean evaluated;

    /**
     * Whether this clip has been applied at least once, i.e. whether
     * {@link #getUnderneath()} holds anything meaningful.
     */
    public boolean isEvaluated()
    {
        return this.evaluated;
    }

    public Position getUnderneath()
    {
        return this.underneath;
    }

    @Override
    protected void applyClip(ClipContext context, Position position)
    {
        List<IEntity> entities = this.getEntities(context);
        IEntity entity = entities.isEmpty() ? null : entities.get(0);

        if (entity != null) currentEntity = entity;

        if (this.showHand.get() && (entity == null || entity.getShowHand())) renderHand = true;
        if (this.showHud.get() && (entity == null || entity.getShowHud())) renderHud = true;

        if (entity == null) return;

        /* Hand-anchor mode: when showHand is on and no group is set, anchor camera to entity head.
         *
         * MERGE fsgh/2.5: dieser zweig kehrt VOR underneath/evaluated zurueck, also bleibt
         * isEvaluated() hier false. Gewollt: das neue editClip rechnet eine geflogene kamera
         * ueber den KNOCHEN-frame zurueck, den es im hand-anchor-modus gar nicht gibt
         * (TrackerFrame.resolve gibt bei leerer gruppe null). Es passiert also nichts,
         * statt falsche werte in offset/winkel zu schreiben. */
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

        /* MERGE fsgh/2.5: die matrix-rechnung von hand steht jetzt in TrackerFrame —
         * dieselbe klasse liefert auch die UMKEHRUNG, mit der UITrackerClip eine
         * geflogene kamera in offset+winkel zurueckrechnet. Beide richtungen an einem
         * ort, damit sie bei der naechsten aenderung nicht auseinanderlaufen. */
        TrackerFrame frame = TrackerFrame.resolve(
            ((CameraClipContext) context).entities,
            entities.get(0),
            this.group.get(),
            position.point.x,
            position.point.y,
            position.point.z,
            context.transition
        );

        if (frame == null)
        {
            return;
        }

        if (this.relative.get())
        {
            frame.relative(position, this.position);
        }

        this.underneath.copy(position);
        this.evaluated = true;

        Point offset = this.offset.get();
        Point angle = this.angle.get();
        boolean lookAt = this.lookAt.get();
        Angle newAngle = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);

        if (!lookAt)
        {
            Vector3d newPosition = frame.position(offset);

            position.point.x = this.isActive(0) ? newPosition.x : position.point.x;
            position.point.y = this.isActive(1) ? newPosition.y : position.point.y;
            position.point.z = this.isActive(2) ? newPosition.z : position.point.z;
        }

        position.angle.yaw = this.isActive(3) ? newAngle.yaw : position.angle.yaw;
        position.angle.pitch = this.isActive(4) ? newAngle.pitch : position.angle.pitch;
        position.angle.roll = this.isActive(5) ? newAngle.roll : position.angle.roll;
        position.angle.fov = this.isActive(6) ? this.fov.get() : position.angle.fov;
    }

    public boolean isActive(int bit)
    {
        return (this.active.get() >> bit & 1) == 1;
    }

    @Override
    protected Clip create()
    {
        return new TrackerClientClip();
    }
}
