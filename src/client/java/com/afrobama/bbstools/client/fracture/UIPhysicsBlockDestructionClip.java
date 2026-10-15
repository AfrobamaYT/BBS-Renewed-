package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Editor UI for {@link PhysicsBlockDestructionActionClip}. Lets the
 * user define the destructible region (two corners) and impact point
 * either by typing world coordinates or by clicking "Set from
 * looked-at block" buttons that capture the currently-targeted block
 * via raycast from the camera.
 */
public class UIPhysicsBlockDestructionClip extends UIActionClip<PhysicsBlockDestructionActionClip>
{
    public UITrackpad minX, minY, minZ;
    public UITrackpad maxX, maxY, maxZ;
    public UITrackpad impactX, impactY, impactZ;
    public UITrackpad scatterForce, propagationSpeed, maxRadius, chunksPerBlock;
    public UITrackpad upwardForce, directionX, directionY, directionZ;
    public UITrackpad directionForce;
    public UITrackpad scatterJitter, gravityBias;
    public UIToggle destroyBlocks;
    public UIButton setMinFromLook, setMaxFromLook, setImpactFromLook;
    public UIButton useWorldEditSelection;
    public mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox impactReplayName;
    public UIButton pickImpactReplay;
    public UITrackpad tickInterval;
    public UIToggle showWireframe;
    public UIButton destructionMode;
    public UIButton blockMode;
    public UITrackpad physicsScale;
    public UITrackpad debrisLifetime;
    public UIToggle noInterCollision;

    public UIPhysicsBlockDestructionClip(PhysicsBlockDestructionActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.minX = intInput((v) -> this.clip.minX.set(v.intValue()), -30000000, 30000000);
        this.minY = intInput((v) -> this.clip.minY.set(v.intValue()), -64, 320);
        this.minZ = intInput((v) -> this.clip.minZ.set(v.intValue()), -30000000, 30000000);
        this.maxX = intInput((v) -> this.clip.maxX.set(v.intValue()), -30000000, 30000000);
        this.maxY = intInput((v) -> this.clip.maxY.set(v.intValue()), -64, 320);
        this.maxZ = intInput((v) -> this.clip.maxZ.set(v.intValue()), -30000000, 30000000);

        this.impactX = intInput((v) -> this.clip.impactX.set(v.intValue()), -30000000, 30000000);
        this.impactY = intInput((v) -> this.clip.impactY.set(v.intValue()), -64, 320);
        this.impactZ = intInput((v) -> this.clip.impactZ.set(v.intValue()), -30000000, 30000000);

        this.scatterForce = new UITrackpad((v) -> this.clip.scatterForce.set(v.floatValue()));
        this.scatterForce.tooltip(IKey.constant("Radial outward scatter from impact point. 0=soft drift, 1=brisk, 3=explosive, 10=BFG-blast"));
        this.scatterForce.limit(0F, 10F).increment(0.1F);

        this.upwardForce = new UITrackpad((v) -> this.clip.upwardForce.set(v.floatValue()));
        this.upwardForce.tooltip(IKey.constant("Vertical kick added to every chunk. Positive=lift up (ground-katapult effect), negative=collapse downward"));
        this.upwardForce.limit(-5F, 10F).increment(0.1F);

        this.directionX = new UITrackpad((v) -> this.clip.directionX.set(v.floatValue()));
        this.directionX.tooltip(IKey.constant("Force direction X (extra velocity vector pushed onto every chunk regardless of position)"));
        this.directionX.limit(-10F, 10F).increment(0.1F);

        this.directionY = new UITrackpad((v) -> this.clip.directionY.set(v.floatValue()));
        this.directionY.tooltip(IKey.constant("Force direction Y. Combine with directionX/Z to push debris in a specific axis (set scatterForce=0 for purely directional blast)"));
        this.directionY.limit(-10F, 10F).increment(0.1F);

        this.directionZ = new UITrackpad((v) -> this.clip.directionZ.set(v.floatValue()));
        this.directionZ.tooltip(IKey.constant("Force direction Z"));
        this.directionZ.limit(-10F, 10F).increment(0.1F);

        this.directionForce = new UITrackpad((v) -> this.clip.directionForce.set(v.floatValue()));
        this.directionForce.tooltip(IKey.constant("Magnitude of the directional force in blocks/tick. The (X,Y,Z) direction fields define the AXIS — this slider sets HOW STRONG. 0=disabled (use raw XYZ as legacy), 5=strong push, 20=explosion-tier. Combine with scatter_force=0 for purely directional blast."));
        this.directionForce.limit(0F, 30F).increment(0.5F);

        this.scatterJitter = new UITrackpad((v) -> this.clip.scatterJitter.set(v.floatValue()));
        this.scatterJitter.tooltip(IKey.constant("Random per-chunk velocity noise. 0=perfectly uniform, 0.3=natural look, 1+=very chaotic"));
        this.scatterJitter.limit(0F, 3F).increment(0.05F);

        this.gravityBias = new UITrackpad((v) -> this.clip.gravityBias.set(v.floatValue()));
        this.gravityBias.tooltip(IKey.constant("Initial -Y bias for 'heavier' debris feel. 0=neutral, 0.5=heavy chunks, -0.5=floaty/weightless"));
        this.gravityBias.limit(-2F, 2F).increment(0.1F);

        this.propagationSpeed = new UITrackpad((v) -> this.clip.propagationSpeed.set(v.intValue()));
        this.propagationSpeed.tooltip(IKey.constant("Blocks per tick the wave processes. 1=slow cascade, 30=instant"));
        this.propagationSpeed.limit(1, 30, true).increment(1F);

        this.maxRadius = new UITrackpad((v) -> this.clip.maxRadius.set(v.intValue()));
        this.maxRadius.tooltip(IKey.constant("Max distance from impact point the wave can reach (manhattan blocks)"));
        this.maxRadius.limit(1, 100, true).increment(1F);

        this.chunksPerBlock = new UITrackpad((v) -> this.clip.chunksPerBlock.set(v.intValue()));
        this.chunksPerBlock.tooltip(IKey.constant("PhysicsMod debris chunks spawned per processed block"));
        this.chunksPerBlock.limit(1, 20, true).increment(1F);

        this.destroyBlocks = new UIToggle(IKey.constant("Actually destroy blocks"),
            (b) -> this.clip.destroyBlocks.set(b.getValue()));
        this.destroyBlocks.tooltip(IKey.constant("Snapshot+remove blocks during cinematic; restored on rewind. Singleplayer only."));

        this.setMinFromLook = new UIButton(IKey.constant("Set Min from looked-at"),
            (b) -> setFromLook(true, false, false));
        this.setMinFromLook.tooltip(IKey.constant("Capture the block you're currently looking at as the region's MIN corner"));

        this.setMaxFromLook = new UIButton(IKey.constant("Set Max from looked-at"),
            (b) -> setFromLook(false, true, false));
        this.setMaxFromLook.tooltip(IKey.constant("Capture the block you're currently looking at as the region's MAX corner"));

        this.setImpactFromLook = new UIButton(IKey.constant("Set Impact from looked-at"),
            (b) -> setFromLook(false, false, true));
        this.setImpactFromLook.tooltip(IKey.constant("Capture the block you're currently looking at as the impact point"));

        this.useWorldEditSelection = new UIButton(IKey.constant("Use WorldEdit selection"),
            (b) -> applyWorldEditSelection());
        this.useWorldEditSelection.tooltip(IKey.constant("Read pos1/pos2 from your current WorldEdit selection and write them to Min/Max corners. Requires WorldEdit-Fabric installed."));

        this.impactReplayName = new mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox(64,
            (t) -> this.clip.impactReplayName.set(t));
        this.impactReplayName.tooltip(IKey.constant("Optional: name of an existing replay whose live X/Y/Z keyframes drive the impact point at trigger time. Leave empty to use the static Impact XYZ fields above."));

        this.pickImpactReplay = new UIButton(IKey.constant("Pick replay…"),
            (b) -> openImpactReplayPicker());
        this.pickImpactReplay.tooltip(IKey.constant("Open a searchable list of replays in the current film and pick one — same UX as Tracker clip's selector."));

        this.tickInterval = new UITrackpad((v) -> this.clip.tickInterval.set(v.intValue()));
        this.tickInterval.tooltip(IKey.constant("Wave-step interval in client ticks. 1=process every tick (default), 5=every 5th tick (slow cascade), 20=once per second. Combine with low Propagation speed for super-slow block-by-block destruction."));
        this.tickInterval.limit(1, 40, true).increment(1F);

        this.showWireframe = new UIToggle(IKey.constant("Show wireframe"),
            (b) -> {
                this.clip.showWireframe.set(b.getValue());
                BlockDestructionWireframe.setVisible(this.clip, b.getValue());
            });
        this.showWireframe.tooltip(IKey.constant("Render an in-world overlay: white box = region, yellow cross = impact point, orange arrow = direction, cyan line = upward force. Only visible while editing this clip."));

        this.destructionMode = new UIButton(IKey.constant(this.clip.destructionMode.get()),
            (b) -> cycleDestructionMode());
        this.destructionMode.tooltip(IKey.constant(
            "Click to cycle: radial → directional → top_down → bottom_up → instant → random.\n" +
            "  radial: BFS wave outward from impact (default)\n" +
            "  directional: only blocks 'ahead' of impact along the Direction vector\n" +
            "  top_down: collapsing roof — high Y first\n" +
            "  bottom_up: eruption — low Y first\n" +
            "  instant: entire region in one tick\n" +
            "  random: shuffled chaotic order"));

        this.physicsScale = new UITrackpad((v) -> this.clip.physicsScale.set(v.floatValue()));
        this.physicsScale.tooltip(IKey.constant("PhysicsMod debris size multiplier. 0.5=small chunks, 1.0=block-sized (default), 2.0=big chunky debris. Same as the physics block emitter form's physicsScale."));
        this.physicsScale.limit(0.1F, 5F).increment(0.1F);

        this.debrisLifetime = new UITrackpad((v) -> this.clip.debrisLifetime.set(v.intValue()));
        this.debrisLifetime.tooltip(IKey.constant("Per-debris lifetime in ticks (20=1sec). 200=10sec default, 600=30sec, 1200=60sec. Lower = debris despawns sooner."));
        this.debrisLifetime.limit(20, 1200, true).increment(20F);

        this.noInterCollision = new UIToggle(IKey.constant("No inter-debris collision"),
            (b) -> this.clip.noInterCollision.set(b.getValue()));
        this.noInterCollision.tooltip(IKey.constant("When ON, spawned chunks pass through each other (still hit walls/floor). Lets force/scatter reach full magnitude without piles of debris ramming each other and braking. Off = natural pile-up."));

        this.blockMode = new UIButton(IKey.constant(this.clip.blockMode.get()),
            (b) -> cycleBlockMode());
        this.blockMode.tooltip(IKey.constant(
            "PhysicsMod debris-shape override. Click to cycle:\n" +
            "  default: use whatever your PhysicsMod settings are configured for\n" +
            "  blocky: full cubes (boxy chunks)\n" +
            "  fractured: realistic shattered chunks\n" +
            "  fractured_voxel: blocky shattered chunks (mid-detail)\n" +
            "  particles: small sparkly particles\n" +
            "Override is applied per-spawn with try/finally so your global PhysicsMod settings stay intact."));

        /* Auto-enable wireframe rendering for whichever clip the user
         *  currently has open in the editor — registered here in
         *  registerUI() which fires when the panel is constructed. */
        if (this.clip != null && this.clip.showWireframe.get())
        {
            BlockDestructionWireframe.setVisible(this.clip, true);
        }
    }

    /** Open a UIStringOverlayPanel listing every replay in the film
     *  the editor is currently working with. Selecting a replay sets
     *  impactReplayName to its name. */
    private void openImpactReplayPicker()
    {
        java.util.List<String> replayNames = collectReplayNames();
        if (replayNames.isEmpty())
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null)
            {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e[BBSTools] §cNo replays in the current film. Add a replay first, then come back."));
            }
            return;
        }

        mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel panel =
            new mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel(
                IKey.constant("Pick impact replay"),
                replayNames,
                (chosen) -> {
                    if (chosen == null) return;
                    this.clip.impactReplayName.set(chosen);
                    this.impactReplayName.setText(chosen);
                });
        panel.set(this.clip.impactReplayName.get());

        try
        {
            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(this.getContext(), panel);
        }
        catch (Throwable t)
        {
            /* Some BBS contexts may reject overlays when the editor
             *  isn't fully initialised. Fall back to chat instructions. */
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null)
            {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e[BBSTools] §cCouldn't open picker — type the replay name manually. Available: " + String.join(", ", replayNames)));
            }
        }
    }

    /** Walk the editor delegate's currently-edited Film and return
     *  every replay's name (or "replay #N" if unnamed). Best-effort —
     *  if the delegate's film is unreachable, returns empty. */
    private java.util.List<String> collectReplayNames()
    {
        java.util.List<String> names = new java.util.ArrayList<>();
        try
        {
            mchorse.bbs_mod.film.Film film = this.editor.getFilm();
            if (film == null) return names;
            int idx = 0;
            for (mchorse.bbs_mod.film.replays.Replay r : film.replays.getList())
            {
                if (r == null) { idx++; continue; }
                String n;
                try { n = r.getName(); } catch (Throwable ignored) { n = null; }
                if (n == null || n.isEmpty()) n = "replay #" + idx;
                names.add(n);
                idx++;
            }
        }
        catch (Throwable t)
        {
            /* delegate.getData() may not return Film — fall through to empty. */
        }
        return names;
    }

    private void applyWorldEditSelection()
    {
        net.minecraft.core.BlockPos[] sel = WorldEditBridge.getCurrentSelection();
        if (sel == null || sel.length < 2)
        {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.player != null)
            {
                String reason = WorldEditBridge.isAvailable()
                    ? "no WorldEdit selection set — use //pos1 + //pos2 first"
                    : "WorldEdit-Fabric not installed";
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[BBSTools] §c" + reason));
            }
            return;
        }
        net.minecraft.core.BlockPos min = sel[0];
        net.minecraft.core.BlockPos max = sel[1];
        this.clip.minX.set(min.getX()); this.clip.minY.set(min.getY()); this.clip.minZ.set(min.getZ());
        this.clip.maxX.set(max.getX()); this.clip.maxY.set(max.getY()); this.clip.maxZ.set(max.getZ());
        this.minX.setValue(min.getX()); this.minY.setValue(min.getY()); this.minZ.setValue(min.getZ());
        this.maxX.setValue(max.getX()); this.maxY.setValue(max.getY()); this.maxZ.setValue(max.getZ());

        /* Auto-set Impact to region center if it's still at the
         *  default world-origin (0,0,0). Lets the user just hit play
         *  after WE-importing without manually setting Impact too —
         *  region-center is a sane default for radial waves. */
        if (this.clip.impactX.get() == 0 && this.clip.impactY.get() == 0 && this.clip.impactZ.get() == 0)
        {
            int cx = (min.getX() + max.getX()) / 2;
            int cy = (min.getY() + max.getY()) / 2;
            int cz = (min.getZ() + max.getZ()) / 2;
            this.clip.impactX.set(cx); this.clip.impactY.set(cy); this.clip.impactZ.set(cz);
            this.impactX.setValue(cx); this.impactY.setValue(cy); this.impactZ.setValue(cz);
        }

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.player != null)
        {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "§e[BBSTools] §aRegion set from WorldEdit: " + min + " → " + max));
        }
    }

    private static UITrackpad intInput(java.util.function.Consumer<Double> setter, int lo, int hi)
    {
        UITrackpad t = new UITrackpad((v) -> setter.accept(v.doubleValue()));
        t.limit(lo, hi, true).increment(1F);
        return t;
    }

    private void setFromLook(boolean asMin, boolean asMax, boolean asImpact)
    {
        BlockPos pos = lookedAtBlock();
        if (pos == null) return;

        if (asMin)
        {
            this.clip.minX.set(pos.getX());
            this.clip.minY.set(pos.getY());
            this.clip.minZ.set(pos.getZ());
            this.minX.setValue(pos.getX()); this.minY.setValue(pos.getY()); this.minZ.setValue(pos.getZ());
        }
        if (asMax)
        {
            this.clip.maxX.set(pos.getX());
            this.clip.maxY.set(pos.getY());
            this.clip.maxZ.set(pos.getZ());
            this.maxX.setValue(pos.getX()); this.maxY.setValue(pos.getY()); this.maxZ.setValue(pos.getZ());
        }
        if (asImpact)
        {
            this.clip.impactX.set(pos.getX());
            this.clip.impactY.set(pos.getY());
            this.clip.impactZ.set(pos.getZ());
            this.impactX.setValue(pos.getX()); this.impactY.setValue(pos.getY()); this.impactZ.setValue(pos.getZ());
        }
    }

    private void cycleDestructionMode()
    {
        String cur = this.clip.destructionMode.get();
        String next;
        if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_RADIAL.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_DIRECTIONAL;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_DIRECTIONAL.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_TOP_DOWN;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_TOP_DOWN.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_BOTTOM_UP;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_BOTTOM_UP.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_INSTANT;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_INSTANT.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_RANDOM;
        else
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.MODE_RADIAL;

        this.clip.destructionMode.set(next);
        this.destructionMode.label = IKey.constant(next);
    }

    private void cycleBlockMode()
    {
        String cur = this.clip.blockMode.get();
        String next;
        if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_DEFAULT.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_BLOCKY;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_BLOCKY.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_FRACTURED;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_FRACTURED.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_FRACTURED_VOXEL;
        else if (com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_FRACTURED_VOXEL.equals(cur))
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_PARTICLES;
        else
            next = com.afrobama.bbstools.fracture.PhysicsBlockDestructionActionClip.BLOCK_MODE_DEFAULT;

        this.clip.blockMode.set(next);
        this.blockMode.label = IKey.constant(next);
    }

    private static BlockPos lookedAtBlock()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.hitResult == null) return null;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return null;
        return ((BlockHitResult) mc.hitResult).getBlockPos();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UI.label(IKey.constant("§7REGION  (inclusive AABB)")).marginTop(16));
        this.panels.add(this.useWorldEditSelection.marginTop(8));
        this.panels.add(this.setMinFromLook.marginTop(4));
        this.panels.add(UI.label(IKey.constant("Min X")), this.minX);
        this.panels.add(UI.label(IKey.constant("Min Y")), this.minY);
        this.panels.add(UI.label(IKey.constant("Min Z")), this.minZ);
        this.panels.add(this.setMaxFromLook.marginTop(8));
        this.panels.add(UI.label(IKey.constant("Max X")), this.maxX);
        this.panels.add(UI.label(IKey.constant("Max Y")), this.maxY);
        this.panels.add(UI.label(IKey.constant("Max Z")), this.maxZ);

        this.panels.add(UI.label(IKey.constant("§7IMPACT  POINT")).marginTop(16));
        this.panels.add(this.setImpactFromLook.marginTop(8));
        this.panels.add(UI.label(IKey.constant("Impact X")), this.impactX);
        this.panels.add(UI.label(IKey.constant("Impact Y")), this.impactY);
        this.panels.add(UI.label(IKey.constant("Impact Z")), this.impactZ);
        this.panels.add(UI.label(IKey.constant("Or impact_replay name (optional)")).marginTop(8), this.impactReplayName);
        this.panels.add(this.pickImpactReplay.marginTop(4));

        this.panels.add(UI.label(IKey.constant("§7FORCE  +  DIRECTION")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Scatter force (radial)")), this.scatterForce);
        this.panels.add(UI.label(IKey.constant("Upward force")), this.upwardForce);
        this.panels.add(UI.label(IKey.constant("Direction X")), this.directionX);
        this.panels.add(UI.label(IKey.constant("Direction Y")), this.directionY);
        this.panels.add(UI.label(IKey.constant("Direction Z")), this.directionZ);
        this.panels.add(UI.label(IKey.constant("Direction force")), this.directionForce);
        this.panels.add(UI.label(IKey.constant("Scatter jitter")), this.scatterJitter);
        this.panels.add(UI.label(IKey.constant("Gravity bias")), this.gravityBias);

        this.panels.add(UI.label(IKey.constant("§7WAVE  PROPAGATION")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Propagation speed")), this.propagationSpeed);
        this.panels.add(UI.label(IKey.constant("Tick interval")), this.tickInterval);
        this.panels.add(UI.label(IKey.constant("Max radius")), this.maxRadius);
        this.panels.add(UI.label(IKey.constant("Chunks per block")), this.chunksPerBlock);
        this.panels.add(this.destroyBlocks.marginTop(8));
        this.panels.add(this.showWireframe.marginTop(4));
        this.panels.add(UI.label(IKey.constant("Destruction mode")).marginTop(8), this.destructionMode);
        this.panels.add(UI.label(IKey.constant("Block mode (debris shape)")).marginTop(8), this.blockMode);
        this.panels.add(UI.label(IKey.constant("Physics scale")).marginTop(8), this.physicsScale);
        this.panels.add(UI.label(IKey.constant("Debris lifetime (ticks)")).marginTop(4), this.debrisLifetime);
        this.panels.add(this.noInterCollision.marginTop(4));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.minX.setValue(this.clip.minX.get());
        this.minY.setValue(this.clip.minY.get());
        this.minZ.setValue(this.clip.minZ.get());
        this.maxX.setValue(this.clip.maxX.get());
        this.maxY.setValue(this.clip.maxY.get());
        this.maxZ.setValue(this.clip.maxZ.get());
        this.impactX.setValue(this.clip.impactX.get());
        this.impactY.setValue(this.clip.impactY.get());
        this.impactZ.setValue(this.clip.impactZ.get());
        this.scatterForce.setValue(this.clip.scatterForce.get());
        this.upwardForce.setValue(this.clip.upwardForce.get());
        this.directionX.setValue(this.clip.directionX.get());
        this.directionY.setValue(this.clip.directionY.get());
        this.directionZ.setValue(this.clip.directionZ.get());
        this.directionForce.setValue(this.clip.directionForce.get());
        this.scatterJitter.setValue(this.clip.scatterJitter.get());
        this.gravityBias.setValue(this.clip.gravityBias.get());
        this.propagationSpeed.setValue(this.clip.propagationSpeed.get());
        this.maxRadius.setValue(this.clip.maxRadius.get());
        this.chunksPerBlock.setValue(this.clip.chunksPerBlock.get());
        this.destroyBlocks.setValue(this.clip.destroyBlocks.get());
        this.impactReplayName.setText(this.clip.impactReplayName.get());
        this.tickInterval.setValue(this.clip.tickInterval.get());
        this.showWireframe.setValue(this.clip.showWireframe.get());
        BlockDestructionWireframe.setVisible(this.clip, this.clip.showWireframe.get());
        this.destructionMode.label = IKey.constant(this.clip.destructionMode.get());
        this.blockMode.label = IKey.constant(this.clip.blockMode.get());
        this.physicsScale.setValue(this.clip.physicsScale.get());
        this.debrisLifetime.setValue(this.clip.debrisLifetime.get());
        this.noInterCollision.setValue(this.clip.noInterCollision.get());
    }

}
