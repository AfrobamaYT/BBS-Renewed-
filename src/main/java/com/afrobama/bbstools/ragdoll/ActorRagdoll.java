package com.afrobama.bbstools.ragdoll;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-actor ragdoll state. Captures the BBS actor's bone world-positions at
 * the moment of {@code /ragdoll} activation; thereafter verlet-integrates
 * each bone position with gravity + damping + parent-child distance
 * constraints + ground collision.
 *
 * <p>Captures state from the actor's CURRENT pose — including any active
 * animation, IK chain, or pose-track keyframe — so the ragdoll falls out
 * of whatever pose the actor was holding (e.g. arms-up dance freezes mid-air
 * then collapses). Photopea-equivalent for animation.</p>
 */
public final class ActorRagdoll
{
    public static final class BoneNode
    {
        /** Bone name in the BOBJ skeleton. */
        public final String name;

        /** Parent bone name; null for root. */
        public final String parent;

        /** Distance from this bone to its parent at the moment of capture.
         *  Verlet distance constraint preserves this length. */
        public final float restLength;

        /** Current world-space position. Verlet-integrated. */
        public final Vector3f position = new Vector3f();

        /** Previous world-space position. Verlet uses (position - prevPosition)
         *  as implicit velocity. */
        public final Vector3f prevPosition = new Vector3f();

        /** Captured rest-pose position at /ragdoll start. Render-override
         *  uses this as the reference for computing the local-space
         *  offset: translate = (current - initial) * 16. Without this we'd
         *  treat absolute world positions as offsets, sending bones into
         *  outer space. */
        public final Vector3f initialPosition = new Vector3f();

        public BoneNode(String name, String parent, float restLength)
        {
            this.name = name;
            this.parent = parent;
            this.restLength = restLength;
        }
    }

    public final Map<String, BoneNode> bones = new HashMap<>();

    /** Damping factor 0..1. 1 = no damping, 0 = freeze. Per verlet step. */
    public float damping = 0.94F;

    /** World-space gravity (m / tick^2). Y is up. */
    public final Vector3f gravity = new Vector3f(0F, -0.04F, 0F);

    /** Constraint relaxation iterations per tick. More = stiffer chains. */
    public int constraintIterations = 6;

    /** Ground Y level — bones below this get clamped + bounce damped. */
    public float groundY;

    /** Ticks remaining before the ragdoll auto-cancels and the actor returns
     *  to normal animation. -1 = no auto-cancel. */
    public int ticksRemaining = 600;

    /** Active flag — false bones are skipped in physics + render override. */
    public boolean active = true;

    /** One-shot log marker so the renderer's match log fires once per ragdoll. */
    public boolean loggedMatch = false;

    /** One-shot log marker for the first physics tick after activation. */
    public boolean loggedTick = false;

    /** One-shot log marker for the first render-pass that picks up this ragdoll. */
    public boolean loggedRender = false;

    /** One-shot log marker for the first applyToModel invocation. */
    public boolean loggedApply = false;

    public BoneNode add(String name, String parent, Vector3f initialPos, float restLength)
    {
        BoneNode node = new BoneNode(name, parent, restLength);
        node.position.set(initialPos);
        node.prevPosition.set(initialPos);
        node.initialPosition.set(initialPos);
        this.bones.put(name, node);
        return node;
    }

    public BoneNode get(String name)
    {
        return this.bones.get(name);
    }

}
