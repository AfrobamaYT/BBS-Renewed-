// Coded by Afrobama/Claude — central state for BBSTools FX uniforms.
// Lives outside the mixin/post-shader packages because both Java
// renderers AND post-FX shaders read these values per frame.
//
// Why: destroying-mc pushes per-effect uniforms (supernovaTimer,
// flashTimer, laserPos) directly into the master shadows.fsh shader.
// We mirror that pattern with this state holder + BBSFxUniformPusher
// reading from here.
package com.afrobama.bbstools.client.fx;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class BBSFxState
{
    /* Supernova effect — 0..1 progress, 0=idle 1=peak whiteout. */
    public static volatile float supernovaTimer = 0F;
    public static volatile Vector3f supernovaPos = new Vector3f(0F, 100F, 0F);

    /* Sun-pose for the deferred-lighting shadows.fsh. Computed by
     *  BBSFxShadowmapPrePass per frame from the world's sky-angle and
     *  the camera position. Identity by default — shadows.fsh's
     *  graceful-fallback branch returns "no shadow" when shadowScreenPos
     *  lands outside [0,1], so an identity matrix is safe before the
     *  pre-pass has run.
     *  shadowViewMatrix  : world  -> light-view
     *  IShadowViewMatrix : inverse (light-view -> world), used to
     *                      recover sunDir = view * (0,0,1) in shader
     *  shadowProjMat     : light-view -> light-clip (ortho) */
    public static volatile Matrix4f shadowViewMatrix  = new Matrix4f();
    public static volatile Matrix4f iShadowViewMatrix = new Matrix4f();
    public static volatile Matrix4f shadowProjMat     = new Matrix4f();

    /* Nuke whiteout flash — 0..1, 1 = full white. Used by final.fsh
     *  for the intuitive instant-flash overlay. */
    public static volatile float nukeFlash = 0F;

    /* Nuke world position (where the mushroom cloud rises). */
    public static volatile Vector3f nukePos = new Vector3f(0F, 64F, 0F);

    /* Smoke-rise progress 0..1. Drives the SDF scale of the cylinder
     *  + torus + cone in bbstools:fx/nuke so the cloud grows visibly
     *  over the effect's lifetime. */
    public static volatile float smokeRiseTimer = 0F;

    /* Meteor world-space position (animated over time by MeteorEffect).
     *  Reads back from shadows.fsh as a tight HDR-bright emission spot;
     *  by the time it reaches the impact point, MeteorEffect hands off
     *  to NukeEffect for the on-ground explosion. */
    public static volatile Vector3f meteorPos = new Vector3f(0F, 256F, 0F);

    /* Meteor brightness 0..1. 0 = no meteor on screen. Driven by
     *  MeteorEffect: ramps from ~0.2 (just visible) at trail start to
     *  ~0.8 just before impact, then snaps to 0 at impact. */
    public static volatile float meteorIntensity = 0F;

    /* REVEAL LIGHT — sustained bright spot for "Tunnel-Ende-Licht"
     *  cinematic reveal scenes. Tight 2-block exp falloff in shadows.fsh
     *  so only pixels at the light source (i.e. seen through an opening)
     *  glow; wall pixels in front of the light stay dark. Driven by
     *  RevealLightEffect: fade-in / sustain / fade-out controlled by
     *  /bbstools fx reveal-light + reveal-light-off commands. */
    public static volatile Vector3f revealLightPos = new Vector3f(0F, 64F, 0F);
    public static volatile float revealLightIntensity = 0F;

    /* REALITY GLITCH: world-space center of the corruption tear. */
    public static volatile Vector3f glitchPos = new Vector3f(0F, 64F, 0F);

    /* REALITY GLITCH: current expansion radius in world blocks.
     *  Driven by GlitchEffect: 0 → MAX over the growth phase,
     *  sustain at MAX, then dissolve back to 0. */
    public static volatile float glitchRadius = 0F;

    /* REALITY GLITCH: strength multiplier 0..1. Scales all visual
     *  layers (RGB-shift, mosaic, tears, invert, static). 0 = no
     *  visible glitch even if radius > 0. */
    public static volatile float glitchIntensity = 0F;

    /* ANTIMATTER IMPLOSION: world-space center of the vacuum. */
    public static volatile Vector3f antimatterPos = new Vector3f(0F, 64F, 0F);

    /* ANTIMATTER IMPLOSION: 0..1 progress. 0 = no effect, 1 = peak
     *  vacuum (everything around the point pulled in + darkened).
     *  AntimatterEffect.java hands off to NukeEffect at peak so the
     *  implosion physically releases as an outward blast. */
    public static volatile float antimatterIntensity = 0F;

    /* CRYSTALLIZATION WAVE: world-space center of the expanding wave. */
    public static volatile Vector3f crystalPos = new Vector3f(0F, 64F, 0F);

    /* CRYSTALLIZATION WAVE: current expansion radius in world blocks.
     *  Driven by CrystalEffect: 0 → MAX over grow, sustain at MAX,
     *  then dissolve back to 0. */
    public static volatile float crystalRadius = 0F;

    /* CRYSTALLIZATION WAVE: strength multiplier 0..1. Scales tint,
     *  ring brightness, facet contrast, sparkle density. */
    public static volatile float crystalIntensity = 0F;

    /* Sun-flash brief overlay — 0..N (decays over 1.0). */
    public static volatile float flashTimer = 0F;

    /* Vibrance + exposure — defaults match destroying-mc soft look. */
    public static volatile float vibranceAmount = 0.20F;
    public static volatile float tonemapExposure = 1.0F;

    /* Sun direction for the shadowmap pre-pass (degrees). Mutable so
     *  /bbstools fx sundir can park different angles per cinematic
     *  shot. Defaults match destroying-mc's default ShaderType rotation
     *  (rotate X by 20°, then Z by -75°). */
    public static volatile float sunRotXDeg = 20F;
    public static volatile float sunRotZDeg = -75F;

    private BBSFxState() {}

}
