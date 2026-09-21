#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

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

in vec3 Position;
in vec2 UV0;
in vec3 Normal;
in ivec2 UV2;
in ivec4 SkinJoints0;
in vec4 SkinWeights0;
in ivec4 SkinJoints1;
in vec4 SkinWeights1;

out vec2 texCoord0;
flat out ivec2 texCoord2;

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
