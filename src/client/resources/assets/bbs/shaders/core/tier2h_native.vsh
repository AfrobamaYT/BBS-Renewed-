#version 330
/* separate_shader_objects: erst ab 26.3 */
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
uniform sampler2D BoneTex;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
/*location 0*/ in vec3 Position;
/*location 1*/ in vec2 UV0;
/*location 2*/ in vec3 Normal;
/*location 3*/ in uint BoneIndex;
/*location 4*/ in vec4 World0;
/*location 5*/ in vec4 World1;
/*location 6*/ in vec4 World2;
/*location 7*/ in vec4 World3;
/*location 8*/ in uint PackedLight;
/*location 9*/ in uint PackedColor;
/*location 10*/ in uint PaletteRow;
/*location 11*/ in uint PackedOverlay;
/*location 0*/ out vec2 texCoord0;
/*location 1*/ out vec4 vertexColor;
/*location 2*/ out vec4 overlayColor;
/*location 3*/ out float sphericalVertexDistance;
/*location 4*/ out float cylindricalVertexDistance;
void main() {
    int x = int(BoneIndex) * 4;
    int y = int(PaletteRow);
    mat4 bone = mat4(texelFetch(BoneTex, ivec2(x, y), 0),
                     texelFetch(BoneTex, ivec2(x + 1, y), 0),
                     texelFetch(BoneTex, ivec2(x + 2, y), 0),
                     texelFetch(BoneTex, ivec2(x + 3, y), 0));
    mat4 world = mat4(World0, World1, World2, World3);
    vec4 worldPosition = world * bone * vec4(Position, 1.0);
    gl_Position = ProjMat * ModelViewMat * worldPosition;
    vec3 normal = normalize(transpose(inverse(mat3(world * bone))) * Normal);
    vec4 color = vec4(float((PackedColor >> 16u) & 255u),
                      float((PackedColor >> 8u) & 255u),
                      float(PackedColor & 255u), float(PackedColor >> 24u)) / 255.0;
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, color * ColorModulator)
        * sample_lightmap(Sampler2, ivec2(int(PackedLight & 65535u), int(PackedLight >> 16u)));
    overlayColor = texelFetch(Sampler1, ivec2(int(PackedOverlay & 65535u), int(PackedOverlay >> 16u)), 0);
    sphericalVertexDistance = fog_spherical_distance(worldPosition.xyz);
    cylindricalVertexDistance = fog_cylindrical_distance(worldPosition.xyz);
    texCoord0 = UV0;
}
