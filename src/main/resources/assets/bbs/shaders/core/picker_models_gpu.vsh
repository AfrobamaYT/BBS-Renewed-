#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(std140) uniform ModelInfo {
    vec4 NormalMat0;
    vec4 NormalMat1;
    vec4 NormalMat2;
    ivec4 LichtOverlay;
    ivec4 Modus;
    vec4 Material;
};

layout(std140) uniform SkinMatrices {
    vec4 SkinRows[3072];
};

layout(location = 0) in vec3 Position;
layout(location = 2) in vec2 UV0;
layout(location = 1) in vec3 Normal;
layout(location = 3) in ivec2 UV2;
layout(location = 4) in ivec4 SkinJoints0;
layout(location = 5) in vec4 SkinWeights0;
layout(location = 6) in ivec4 SkinJoints1;
layout(location = 7) in vec4 SkinWeights1;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) flat out ivec2 texCoord2;

void main()
{
    vec4 rest = vec4(Position, 1.0);
    vec3 skinnedPosition = vec3(0.0);
    float totalWeight = 0.0;

    for (int i = 0; i < 8; i++) {
        float weight = i < 4 ? SkinWeights0[i] : SkinWeights1[i - 4];
        if (weight <= 0.0) continue;
        int joint = i < 4 ? SkinJoints0[i] : SkinJoints1[i - 4];
        int row = joint * 3;
        skinnedPosition += vec3(dot(SkinRows[row], rest),
            dot(SkinRows[row + 1], rest), dot(SkinRows[row + 2], rest)) * weight;
        totalWeight += weight;
    }

    skinnedPosition = totalWeight > 0.0 ? skinnedPosition / totalWeight : Position;
    gl_Position = ProjMat * ModelViewMat * vec4(skinnedPosition, 1.0);
    texCoord0 = UV0;
    texCoord2 = UV2;
}
