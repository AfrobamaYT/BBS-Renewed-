#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

/* ⛔ DIESER BLOCK IST EIN VERTRAG MIT ModelVAORenderer.schreibeBlock — Reihenfolge und
 * Groesse muessen dort und hier gleich sein, und kein Compiler prueft das.
 *
 * Absichtlich nur volle 16-Byte-Zeilen: std140 richtet Felder je nach Typ unterschiedlich
 * aus, ein ivec2 nach einer mat3 landet nicht dort, wo man es naiv erwartet. Die
 * Normalenmatrix steht darum als drei vec4 (die w-Komponente ist Fuellung), Licht und
 * Overlay teilen sich eine ivec4, die Durchgangsart bekommt eine eigene. */
layout(std140) uniform ModelInfo {
    vec4 NormalMat0;
    vec4 NormalMat1;
    vec4 NormalMat2;
    ivec4 LichtOverlay;
    ivec4 Modus;
    vec4 Material;
};

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 1) in vec3 Normal;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec4 unlitColor;
layout(location = 4) out float emissionMix;
layout(location = 5) out vec4 lightMapColor;
layout(location = 6) out vec4 overlayColor;
layout(location = 7) out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    /* 26.2 rechnet mit ZWEI Nebeldistanzen statt einer: kugelfoermig fuer den
     * Umgebungsnebel, zylindrisch fuer die Sichtweite. Das alte fog_distance(pos, FogShape)
     * gibt es nicht mehr. */
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    mat3 NormalMat = mat3(NormalMat0.xyz, NormalMat1.xyz, NormalMat2.xyz);
    vec3 fixNormal = normalize(NormalMat * Normal);

    /* Die Modellfarbe kommt aus ColorModulator (DynamicTransforms), nicht mehr aus einem
     * Vertexattribut: ModelVAORenderer schreibt r/g/b/a ueber writeTransform genau dorthin.
     * ⚠️ Darum darf der Fragmentshader NICHT noch einmal mit ColorModulator multiplizieren
     * — die Farbe steckt bereits in vertexColor. */
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, ColorModulator);
    unlitColor = ColorModulator;
    emissionMix = Material.x;

    /* Licht und Overlay waren frueher konstante Vertexattribute (UV2/UV1). Ein Attribut
     * ohne Puffer gibt es in 26.2 nicht — die Koordinaten stehen jetzt im Block. */
    lightMapColor = sample_lightmap(Sampler2, LichtOverlay.xy);
    overlayColor = texelFetch(Sampler1, LichtOverlay.zw, 0);

    texCoord0 = UV0;
}
