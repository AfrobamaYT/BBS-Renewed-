package com.afrobama.bbstools.client.fracture;

import com.afrobama.bbstools.fracture.ModelFractureActionClip;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Editor UI panel for {@link ModelFractureActionClip}. Lays out the
 * configurable fields as labelled controls in the action-clip editor
 * (right-hand inspector when the clip is selected on the timeline).
 *
 * <p>Layout matches BBS's existing action-clip UIs (Chat, Command,
 * etc.) — text fields stack vertically with a label above each.</p>
 */
public class UIModelFractureClip extends UIActionClip<ModelFractureActionClip>
{
    public UITextbox groupName;
    public mchorse.bbs_mod.ui.framework.elements.buttons.UIButton triggerMode;
    public UITrackpad forceJitter;
    public UIToggle inheritVelocity;
    public UITrackpad lifetime;
    public UITrackpad velocityDropThreshold;
    public UITrackpad gravity;
    public UITrackpad linearDamping;
    public UITrackpad angularDamping;
    public UITrackpad groundBounce;
    public UITrackpad angularJitter;
    public UIToggle enableImpactDust;
    public UIToggle enableImpactSound;
    public UIToggle enableSmokeTrail;
    public UITrackpad impulseX;
    public UITrackpad impulseY;
    public UITrackpad impulseZ;
    public UITrackpad velocityMultiplier;
    public UITrackpad pieceCount;
    public UIToggle enableCameraShake;
    public UITrackpad jointStiffness;
    public UITrackpad slowMotion;
    public UIToggle enableBlockShatter;
    public UITrackpad blockShatterCount;
    public UIToggle enablePropagation;
    public UITrackpad propagationRadius;
    public UITrackpad propagationSpeed;
    public UIToggle destroyBlocks;

    public UIModelFractureClip(ModelFractureActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.groupName = new UITextbox(64, (t) -> this.clip.groupName.set(t));
        this.groupName.tooltip(IKey.constant("Fracture group name from the model's fracture_groups.json. Empty = whole model."));

        this.triggerMode = new mchorse.bbs_mod.ui.framework.elements.buttons.UIButton(
            IKey.constant("manual"),
            (b) -> {
                String cur = this.clip.triggerMode.get();
                String next;
                if (cur == null || com.afrobama.bbstools.fracture.ModelFractureActionClip.TRIGGER_MANUAL.equals(cur))
                    next = com.afrobama.bbstools.fracture.ModelFractureActionClip.TRIGGER_ON_COLLISION;
                else if (com.afrobama.bbstools.fracture.ModelFractureActionClip.TRIGGER_ON_COLLISION.equals(cur))
                    next = com.afrobama.bbstools.fracture.ModelFractureActionClip.TRIGGER_ON_VELOCITY_DROP;
                else
                    next = com.afrobama.bbstools.fracture.ModelFractureActionClip.TRIGGER_MANUAL;
                this.clip.triggerMode.set(next);
                this.triggerMode.label = IKey.constant(next);
            });
        this.triggerMode.tooltip(IKey.constant("Click to cycle: manual → on_collision → on_velocity_drop"));

        this.forceJitter = new UITrackpad((v) -> this.clip.forceJitter.set(v.floatValue()));
        this.forceJitter.tooltip(IKey.constant("Random force scatter added to each fragment (0..2)"));
        this.forceJitter.limit(0F, 2F).increment(0.05F);

        this.inheritVelocity = new UIToggle(IKey.constant("Inherit Velocity"),
            (b) -> this.clip.inheritVelocity.set(b.getValue()));
        this.inheritVelocity.tooltip(IKey.constant("Fragments inherit the actor's keyframe velocity at trigger tick"));

        this.lifetime = new UITrackpad((v) -> this.clip.lifetime.set(v.intValue()));
        this.lifetime.tooltip(IKey.constant("Seconds before fragments auto-despawn (1..120)"));
        this.lifetime.limit(1, 120, true).increment(1F);

        this.velocityDropThreshold = new UITrackpad((v) -> this.clip.velocityDropThreshold.set(v.floatValue()));
        this.velocityDropThreshold.tooltip(IKey.constant("Velocity drop threshold for on_velocity_drop mode (blocks/tick)"));
        this.velocityDropThreshold.limit(0.05F, 5F).increment(0.05F);

        this.gravity = new UITrackpad((v) -> this.clip.gravity.set(v.floatValue()));
        this.gravity.tooltip(IKey.constant("Per-tick downward gravity acceleration (default 0.04 = vanilla MC)"));
        this.gravity.limit(0F, 0.5F).increment(0.005F);

        this.linearDamping = new UITrackpad((v) -> this.clip.linearDamping.set(v.floatValue()));
        this.linearDamping.tooltip(IKey.constant("Linear velocity multiplier per tick (1=no drag, 0.99=light drag, 0.9=heavy)"));
        this.linearDamping.limit(0.5F, 1F).increment(0.005F);

        this.angularDamping = new UITrackpad((v) -> this.clip.angularDamping.set(v.floatValue()));
        this.angularDamping.tooltip(IKey.constant("Rotation velocity multiplier per tick — lower = faster spin slow-down"));
        this.angularDamping.limit(0.5F, 1F).increment(0.005F);

        this.groundBounce = new UITrackpad((v) -> this.clip.groundBounce.set(v.floatValue()));
        this.groundBounce.tooltip(IKey.constant("Y-velocity multiplier on ground hit (0 = no bounce, 0.5 = bouncy)"));
        this.groundBounce.limit(0F, 1F).increment(0.05F);

        this.angularJitter = new UITrackpad((v) -> this.clip.angularJitter.set(v.floatValue()));
        this.angularJitter.tooltip(IKey.constant("Random initial spin magnitude — higher = pieces tumble faster"));
        this.angularJitter.limit(0F, 1F).increment(0.05F);

        this.enableImpactDust = new UIToggle(IKey.constant("Impact Dust"),
            (b) -> this.clip.enableImpactDust.set(b.getValue()));
        this.enableImpactDust.tooltip(IKey.constant("Spawn block-textured dust burst on hard ground hits"));

        this.enableImpactSound = new UIToggle(IKey.constant("Impact Sound"),
            (b) -> this.clip.enableImpactSound.set(b.getValue()));
        this.enableImpactSound.tooltip(IKey.constant("Play surface-correct thud on first ground hit"));

        this.enableSmokeTrail = new UIToggle(IKey.constant("Smoke Trail"),
            (b) -> this.clip.enableSmokeTrail.set(b.getValue()));
        this.enableSmokeTrail.tooltip(IKey.constant("Smoke particle trail behind fast-moving pieces"));

        this.impulseX = new UITrackpad((v) -> this.clip.impulseX.set(v.floatValue()));
        this.impulseX.tooltip(IKey.constant("Initial-velocity X impulse added to every fragment (blocks/tick)"));
        this.impulseX.limit(-10F, 10F).increment(0.1F);

        this.impulseY = new UITrackpad((v) -> this.clip.impulseY.set(v.floatValue()));
        this.impulseY.tooltip(IKey.constant("Initial-velocity Y impulse — positive launches pieces upward"));
        this.impulseY.limit(-10F, 10F).increment(0.1F);

        this.impulseZ = new UITrackpad((v) -> this.clip.impulseZ.set(v.floatValue()));
        this.impulseZ.tooltip(IKey.constant("Initial-velocity Z impulse"));
        this.impulseZ.limit(-10F, 10F).increment(0.1F);

        this.velocityMultiplier = new UITrackpad((v) -> this.clip.velocityMultiplier.set(v.floatValue()));
        this.velocityMultiplier.tooltip(IKey.constant("Multiplier on inherited actor velocity (1=normal, 2=explosion-out, 0.3=fall-in-place)"));
        this.velocityMultiplier.limit(0F, 5F).increment(0.1F);

        this.pieceCount = new UITrackpad((v) -> this.clip.pieceCount.set(v.intValue()));
        this.pieceCount.tooltip(IKey.constant("Pieces per bone (1=just the bone, 8=bone + 7 debris chunks per bone). Higher = more spectacular crash, more particles."));
        this.pieceCount.limit(1, 30, true).increment(1F);

        this.enableCameraShake = new UIToggle(IKey.constant("Camera Shake"),
            (b) -> this.clip.enableCameraShake.set(b.getValue()));
        this.enableCameraShake.tooltip(IKey.constant("Cinematic camera-shake on heavy fragment impacts (off by default)"));

        this.jointStiffness = new UITrackpad((v) -> this.clip.jointStiffness.set(v.floatValue()));
        this.jointStiffness.tooltip(IKey.constant("Ragdoll mode: 0=fly apart freely, 0.5=soft ragdoll, 1.0=rigid chain. Use 0.5-0.8 for humanoid actor falls; 0 for vehicle crashes."));
        this.jointStiffness.limit(0F, 1F).increment(0.05F);

        this.slowMotion = new UITrackpad((v) -> this.clip.slowMotion.set(v.floatValue()));
        this.slowMotion.tooltip(IKey.constant("Slow-mo / fast-forward physics speed. 1.0=real time, 0.25=bullet-time, 0.1=extreme bullet-time, 2.0=fast-forward. Affects ONLY fracture pieces — rest of world stays at normal speed."));
        this.slowMotion.limit(0.05F, 3F).increment(0.05F);

        this.enableBlockShatter = new UIToggle(IKey.constant("Block Shatter"),
            (b) -> this.clip.enableBlockShatter.set(b.getValue()));
        this.enableBlockShatter.tooltip(IKey.constant("Walls/floors that bone bodies impact spawn PhysicsMod debris matching their material. Block stays put — purely visual."));

        this.blockShatterCount = new UITrackpad((v) -> this.clip.blockShatterCount.set(v.intValue()));
        this.blockShatterCount.tooltip(IKey.constant("How many debris chunks per block-impact event. 0=off; 6=default; 20=very intense"));
        this.blockShatterCount.limit(0, 30, true).increment(1F);

        this.enablePropagation = new UIToggle(IKey.constant("Impact propagation"),
            (b) -> this.clip.enablePropagation.set(b.getValue()));
        this.enablePropagation.tooltip(IKey.constant("On hard impact, fragment surrounding blocks in a radial wave (BFS expanding outward). Tree/wall cracks block-by-block from the impact site."));

        this.propagationRadius = new UITrackpad((v) -> this.clip.propagationRadius.set(v.intValue()));
        this.propagationRadius.tooltip(IKey.constant("Manhattan radius the propagation wave can reach (1-20 blocks)"));
        this.propagationRadius.limit(1, 20, true).increment(1F);

        this.propagationSpeed = new UITrackpad((v) -> this.clip.propagationSpeed.set(v.intValue()));
        this.propagationSpeed.tooltip(IKey.constant("Blocks per tick the wave processes. 1=very slow cascade, 4=default, 30=near-instant burst."));
        this.propagationSpeed.limit(1, 30, true).increment(1F);

        this.destroyBlocks = new UIToggle(IKey.constant("Actually destroy blocks"),
            (b) -> this.clip.destroyBlocks.set(b.getValue()));
        this.destroyBlocks.tooltip(IKey.constant("Snapshot + remove blocks during cinematic; restored on rewind / fracture-clear / world unload. Singleplayer only."));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(UI.label(IKey.constant("Group name")).marginTop(12), this.groupName);
        this.panels.add(UI.label(IKey.constant("Trigger mode")).marginTop(8), this.triggerMode);
        this.panels.add(UI.label(IKey.constant("Force jitter")).marginTop(8), this.forceJitter);
        this.panels.add(this.inheritVelocity.marginTop(8));
        this.panels.add(UI.label(IKey.constant("Lifetime (s)")).marginTop(8), this.lifetime);
        this.panels.add(UI.label(IKey.constant("Velocity drop threshold")).marginTop(8), this.velocityDropThreshold);

        this.panels.add(UI.label(IKey.constant("§7PHYSICS  TUNING")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Gravity")).marginTop(8), this.gravity);
        this.panels.add(UI.label(IKey.constant("Linear damping")).marginTop(8), this.linearDamping);
        this.panels.add(UI.label(IKey.constant("Angular damping")).marginTop(8), this.angularDamping);
        this.panels.add(UI.label(IKey.constant("Ground bounce")).marginTop(8), this.groundBounce);
        this.panels.add(UI.label(IKey.constant("Angular jitter")).marginTop(8), this.angularJitter);

        this.panels.add(UI.label(IKey.constant("§7EFFECTS")).marginTop(16));
        this.panels.add(this.enableImpactDust.marginTop(8));
        this.panels.add(this.enableImpactSound.marginTop(4));
        this.panels.add(this.enableSmokeTrail.marginTop(4));

        this.panels.add(UI.label(IKey.constant("§7IMPULSE")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Velocity multiplier")).marginTop(8), this.velocityMultiplier);
        this.panels.add(UI.label(IKey.constant("Impulse X")).marginTop(8), this.impulseX);
        this.panels.add(UI.label(IKey.constant("Impulse Y")).marginTop(8), this.impulseY);
        this.panels.add(UI.label(IKey.constant("Impulse Z")).marginTop(8), this.impulseZ);

        this.panels.add(UI.label(IKey.constant("§7EXTRA  DEBRIS")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Pieces per bone")).marginTop(8), this.pieceCount);
        this.panels.add(this.enableCameraShake.marginTop(8));

        this.panels.add(UI.label(IKey.constant("§7RAGDOLL  MODE")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Joint stiffness")).marginTop(8), this.jointStiffness);

        this.panels.add(UI.label(IKey.constant("§7TIME")).marginTop(16));
        this.panels.add(UI.label(IKey.constant("Slow motion")).marginTop(8), this.slowMotion);

        this.panels.add(UI.label(IKey.constant("§7BLOCK  SHATTER")).marginTop(16));
        this.panels.add(this.enableBlockShatter.marginTop(8));
        this.panels.add(UI.label(IKey.constant("Chunks per impact")).marginTop(8), this.blockShatterCount);
        this.panels.add(this.enablePropagation.marginTop(8));
        this.panels.add(UI.label(IKey.constant("Propagation radius")).marginTop(8), this.propagationRadius);
        this.panels.add(UI.label(IKey.constant("Propagation speed")).marginTop(8), this.propagationSpeed);
        this.panels.add(this.destroyBlocks.marginTop(8));
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.groupName.setText(this.clip.groupName.get());
        this.triggerMode.label = IKey.constant(this.clip.triggerMode.get());
        this.forceJitter.setValue(this.clip.forceJitter.get());
        this.inheritVelocity.setValue(this.clip.inheritVelocity.get());
        this.lifetime.setValue(this.clip.lifetime.get());
        this.velocityDropThreshold.setValue(this.clip.velocityDropThreshold.get());
        this.gravity.setValue(this.clip.gravity.get());
        this.linearDamping.setValue(this.clip.linearDamping.get());
        this.angularDamping.setValue(this.clip.angularDamping.get());
        this.groundBounce.setValue(this.clip.groundBounce.get());
        this.angularJitter.setValue(this.clip.angularJitter.get());
        this.enableImpactDust.setValue(this.clip.enableImpactDust.get());
        this.enableImpactSound.setValue(this.clip.enableImpactSound.get());
        this.enableSmokeTrail.setValue(this.clip.enableSmokeTrail.get());
        this.impulseX.setValue(this.clip.impulseX.get());
        this.impulseY.setValue(this.clip.impulseY.get());
        this.impulseZ.setValue(this.clip.impulseZ.get());
        this.velocityMultiplier.setValue(this.clip.velocityMultiplier.get());
        this.pieceCount.setValue(this.clip.pieceCount.get());
        this.enableCameraShake.setValue(this.clip.enableCameraShake.get());
        this.jointStiffness.setValue(this.clip.jointStiffness.get());
        this.slowMotion.setValue(this.clip.slowMotion.get());
        this.enableBlockShatter.setValue(this.clip.enableBlockShatter.get());
        this.blockShatterCount.setValue(this.clip.blockShatterCount.get());
        this.enablePropagation.setValue(this.clip.enablePropagation.get());
        this.propagationRadius.setValue(this.clip.propagationRadius.get());
        this.propagationSpeed.setValue(this.clip.propagationSpeed.get());
        this.destroyBlocks.setValue(this.clip.destroyBlocks.get());
    }

}
