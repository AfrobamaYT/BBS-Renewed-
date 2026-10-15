#version 330
/* separate_shader_objects: erst ab 26.3 */

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

layout(std140) uniform ModelInfo {
    vec4 NormalMat0;
    vec4 NormalMat1;
    vec4 NormalMat2;
    ivec4 LichtOverlay;
    ivec4 Modus;
    vec4 Material;
};

/* 1024 matrices x 3 vec4 rows = 48 KiB; 26.2 requires a 64 KiB-capable device. */
layout(std140) uniform SkinMatrices {
    vec4 SkinRows[3072];
};

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

/*location 0*/ in vec3 Position;
/*location 2*/ in vec2 UV0;
/*location 1*/ in vec3 Normal;
/*location 3*/ in ivec2 UV2;
/*location 4*/ in ivec4 SkinJoints0;
/*location 5*/ in vec4 SkinWeights0;
/*location 6*/ in ivec4 SkinJoints1;
/*location 7*/ in vec4 SkinWeights1;

/*location 0*/ out float sphericalVertexDistance;
/*location 1*/ out float cylindricalVertexDistance;
/*location 2*/ out vec4 vertexColor;
/*location 3*/ out vec4 unlitColor;
/*location 4*/ out float emissionMix;
/*location 5*/ out vec4 lightMapColor;
/*location 6*/ out vec4 overlayColor;
/*location 7*/ out vec2 texCoord0;

void main()
{
    vec4 rest = vec4(Position, 1.0);
    vec3 skinnedPosition = vec3(0.0);
    vec3 skinnedNormal = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < 8; i++) {
        float weight = i < 4 ? SkinWeights0[i] : SkinWeights1[i - 4];
        if (weight <= 0.0) continue;
        int joint = i < 4 ? SkinJoints0[i] : SkinJoints1[i - 4];
        int row = joint * 3;
        vec4 r0 = SkinRows[row];
        vec4 r1 = SkinRows[row + 1];
        vec4 r2 = SkinRows[row + 2];
        skinnedPosition += vec3(dot(r0, rest), dot(r1, rest), dot(r2, rest)) * weight;
        skinnedNormal += vec3(dot(r0.xyz, Normal), dot(r1.xyz, Normal), dot(r2.xyz, Normal)) * weight;
        totalWeight += weight;
    }

    if (totalWeight > 0.0) {
        skinnedPosition /= totalWeight;
    } else {
        skinnedPosition = Position;
        skinnedNormal = Normal;
    }

    gl_Position = ProjMat * ModelViewMat * vec4(skinnedPosition, 1.0);
    sphericalVertexDistance = fog_spherical_distance(skinnedPosition);
    cylindricalVertexDistance = fog_cylindrical_distance(skinnedPosition);

    mat3 NormalMat = mat3(NormalMat0.xyz, NormalMat1.xyz, NormalMat2.xyz);
    vec3 fixNormal = normalize(NormalMat * skinnedNormal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, fixNormal, ColorModulator);
    unlitColor = ColorModulator;
    emissionMix = Material.x;
    lightMapColor = sample_lightmap(Sampler2, LichtOverlay.xy);
    overlayColor = texelFetch(Sampler1, LichtOverlay.zw, 0);
    texCoord0 = UV0;
}
