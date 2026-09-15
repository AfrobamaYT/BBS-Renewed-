#version 330

#moj_import <minecraft:fog.glsl>

/* Derselbe Vertrag wie in model.vsh und ModelVAORenderer.schreibeBlock. Hier wird nur
 * Modus.x gebraucht, der Block muss aber vollstaendig und in gleicher Reihenfolge stehen. */
layout(std140) uniform ModelInfo {
    vec4 NormalMat0;
    vec4 NormalMat1;
    vec4 NormalMat2;
    ivec4 LichtOverlay;
    ivec4 Modus;
    vec4 Material;
};

uniform sampler2D Sampler0;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec4 unlitColor;
in float emissionMix;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main()
{
    vec4 sampled = texture(Sampler0, texCoord0);

    if (sampled.a < 0.1)
    {
        discard;
    }

    /* ⚠️ NUR mit vertexColor, NICHT zusaetzlich mit ColorModulator: die Modellfarbe steckt
     * seit dem 26.2-Port schon in vertexColor (siehe model.vsh). Ein zweites Multiplizieren
     * wuerde jede Farbe quadrieren — dunkler, aber nicht offensichtlich falsch. */
    vec4 color = sampled * vertexColor;

    /* Two-pass translucency: pass 1 keeps only opaque texels (they write depth), pass 2 —
     * deferred, sorted far-to-near, no depth write — keeps only the semi-transparent ones.
     * The test uses final alpha, so form/bone color alpha counts too. Modus.x 0 = single pass. */
    if (Modus.x == 1 && color.a < 0.999)
    {
        discard;
    }

    if (Modus.x == 2 && color.a >= 0.999)
    {
        discard;
    }

    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    /* Blender's exported material is a Mix Shader: Diffuse in socket 1, Emission in
     * socket 2. Material.x is that exact factor, so 0 keeps every old BBS material and
     * 1 produces the original texture independent of directional/world lighting. */
    color = mix(color, sampled * unlitColor, emissionMix);

    /* 26.2: linear_fog(...) gibt es nicht mehr. apply_fog nimmt beide Distanzen und die
     * vier Grenzen aus dem Fog-Block, den Vanilla selbst fuellt. */
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd,
        FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
