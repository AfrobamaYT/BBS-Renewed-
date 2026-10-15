// SPDX-License-ResourceLocation: LGPL-3.0-only
// Tangent/SNORM contract adapted from Vitrail EntityFrame, commit f42e5489c6abff655781b40bebc70338bada3039.
// BBS changes: GPU-deformed triangle fetches, eight influences, real native instances/VAT, FP32 arithmetic.
in uint BbsCorner;
uniform usamplerBuffer BbsIndexBuffer;
layout(std140) uniform BbsDraw {
    vec4 bbs_native_colour;
    ivec4 bbs_native_light_overlay;
    uvec4 bbs_native_ids;
    mat4 bbs_native_model_world;
    ivec4 bbs_native_mode;
};
out vec3 BbsAlphaCoordinate;
flat out int BbsPassMode;
#ifdef BBS_NATIVE_EMISSION_CAPTURE
out vec4 BbsEmissionColour;
out vec2 BbsEmissionFogDistances;
#endif
#ifdef BBS_NATIVE_CROWD
uniform samplerBuffer BbsGeometryBuffer;
uniform sampler2D BbsPalette;
in vec4 BbsWorld0;
in vec4 BbsWorld1;
in vec4 BbsWorld2;
in vec4 BbsWorld3;
in uint BbsPackedLight;
in uint BbsPackedColor;
in uint BbsPaletteRow;
in uint BbsPackedOverlay;
#else
uniform samplerBuffer BbsPositionBuffer;
uniform samplerBuffer BbsNormalBuffer;
uniform samplerBuffer BbsUvBuffer;
#endif
#ifdef BBS_NATIVE_SKIN
uniform usamplerBuffer BbsJoints0;
uniform usamplerBuffer BbsJoints1;
uniform samplerBuffer BbsWeights0;
uniform samplerBuffer BbsWeights1;
layout(std140) uniform BbsBones { vec4 bbs_native_bones[3072]; };
#endif

uint bbs_native_vertex(uint corner) { return texelFetch(BbsIndexBuffer, int(corner)).r; }
float bbs_native_scalar(samplerBuffer bufferValue, uint index) {
    return texelFetch(bufferValue, int(index)).r;
}
#ifdef BBS_NATIVE_CROWD
mat4 bbs_native_transform(uint vertex) {
    int bone = int(floatBitsToUint(bbs_native_scalar(BbsGeometryBuffer, vertex * 9u + 8u))) * 4;
    int row = int(BbsPaletteRow);
    return mat4(BbsWorld0, BbsWorld1, BbsWorld2, BbsWorld3)
        * mat4(texelFetch(BbsPalette, ivec2(bone, row), 0),
               texelFetch(BbsPalette, ivec2(bone + 1, row), 0),
               texelFetch(BbsPalette, ivec2(bone + 2, row), 0),
               texelFetch(BbsPalette, ivec2(bone + 3, row), 0));
}
#endif
#ifdef BBS_NATIVE_SKIN
mat4 bbs_native_transform(uint vertex) {
    vec4 rows[3]; rows[0] = vec4(0); rows[1] = vec4(0); rows[2] = vec4(0);
    for (int influence = 0; influence < 8; influence++) {
        int at = int(vertex * 4u) + (influence & 3);
        float weight = influence < 4 ? texelFetch(BbsWeights0, at).r : texelFetch(BbsWeights1, at).r;
        if (weight <= 0.0) continue;
        int bone = int(influence < 4 ? texelFetch(BbsJoints0, at).r : texelFetch(BbsJoints1, at).r) * 3;
        rows[0] += weight * bbs_native_bones[bone];
        rows[1] += weight * bbs_native_bones[bone + 1];
        rows[2] += weight * bbs_native_bones[bone + 2];
    }
    return transpose(mat4(rows[0], rows[1], rows[2], vec4(0, 0, 0, 1)));
}
#endif
vec3 bbs_native_position(uint corner) {
    uint vertex = bbs_native_vertex(corner);
#ifdef BBS_NATIVE_CROWD
    uint at = vertex * 9u;
    vec3 value = vec3(bbs_native_scalar(BbsGeometryBuffer, at),
        bbs_native_scalar(BbsGeometryBuffer, at + 1u), bbs_native_scalar(BbsGeometryBuffer, at + 2u));
#else
    uint at = vertex * 3u;
    vec3 value = vec3(bbs_native_scalar(BbsPositionBuffer, at),
        bbs_native_scalar(BbsPositionBuffer, at + 1u), bbs_native_scalar(BbsPositionBuffer, at + 2u));
#endif
#if defined(BBS_NATIVE_SKIN) || defined(BBS_NATIVE_CROWD)
    value = (bbs_native_transform(vertex) * vec4(value, 1)).xyz;
#endif
#if defined(BBS_NATIVE_SHADOW) && !defined(BBS_NATIVE_CROWD)
    value = (bbs_native_model_world * vec4(value, 1)).xyz;
#endif
    return value;
}
vec3 bbs_native_normal(uint corner) {
    uint vertex = bbs_native_vertex(corner);
#ifdef BBS_NATIVE_CROWD
    uint at = vertex * 9u + 5u;
    vec3 value = vec3(bbs_native_scalar(BbsGeometryBuffer, at),
        bbs_native_scalar(BbsGeometryBuffer, at + 1u), bbs_native_scalar(BbsGeometryBuffer, at + 2u));
    value = normalize(transpose(inverse(mat3(bbs_native_transform(vertex)))) * value);
#else
    uint at = vertex * 3u;
    vec3 value = vec3(bbs_native_scalar(BbsNormalBuffer, at),
        bbs_native_scalar(BbsNormalBuffer, at + 1u), bbs_native_scalar(BbsNormalBuffer, at + 2u));
#ifdef BBS_NATIVE_SKIN
    value = normalize(mat3(bbs_native_transform(vertex)) * value);
#endif
#ifdef BBS_NATIVE_SHADOW
    value = normalize(transpose(inverse(mat3(bbs_native_model_world))) * value);
#endif
#endif
    return vec3(ivec3(clamp(value, -1.0, 1.0) * 127.0)) / 127.0;
}
vec2 bbs_native_uv(uint corner) {
    uint vertex = bbs_native_vertex(corner);
#ifdef BBS_NATIVE_CROWD
    uint at = vertex * 9u + 3u;
    return vec2(bbs_native_scalar(BbsGeometryBuffer, at), bbs_native_scalar(BbsGeometryBuffer, at + 1u));
#else
    uint at = vertex * 2u;
    return vec2(bbs_native_scalar(BbsUvBuffer, at), bbs_native_scalar(BbsUvBuffer, at + 1u));
#endif
}
vec4 bbs_native_tangent(vec3 normalValue, uint first) {
    precise vec3 a = bbs_native_position(first), b = bbs_native_position(first + 1u), c = bbs_native_position(first + 2u);
    a -= dot(a, normalValue) * normalValue;
    b -= dot(b, normalValue) * normalValue;
    c -= dot(c, normalValue) * normalValue;
    precise vec3 e1 = b - a, e2 = c - a;
    precise vec2 uv1 = bbs_native_uv(first + 1u) - bbs_native_uv(first);
    precise vec2 uv2 = bbs_native_uv(first + 2u) - bbs_native_uv(first);
    precise float determinant = uv1.x * uv2.y - uv2.x * uv1.y;
    precise float scale = determinant == 0.0 ? 1.0 : 1.0 / determinant;
    precise vec3 tangentValue = scale * (uv2.y * e1 - uv1.y * e2);
    precise float square = dot(tangentValue, tangentValue);
    if (square <= 1e-12) return vec4(1, 0, 0, 1);
    tangentValue *= 1.0 / sqrt(square);
    precise vec3 bitangent = scale * (-uv2.x * e1 + uv1.x * e2);
    float signValue = dot(bitangent, cross(tangentValue, normalValue)) < 0.0 ? -1.0 : 1.0;
    return vec4(ivec4(clamp(vec4(tangentValue, signValue), -1.0, 1.0) * 127.0)) / 127.0;
}
void bbs_native_prepare() {
    uint corner = BbsCorner;
    uint first = corner - corner % 3u;
    Position = bbs_native_position(corner);
    Normal = vec4(bbs_native_normal(corner), 0.0);
    UV0 = bbs_native_uv(corner);
    MidTexCoord = (bbs_native_uv(first) + bbs_native_uv(first + 1u) + bbs_native_uv(first + 2u)) / 3.0;
    Tangent = bbs_native_tangent(Normal.xyz, first);
    EntityIds = bbs_native_ids & uvec4(65535u);
#ifdef BBS_NATIVE_CROWD
    Color = vec4(float((BbsPackedColor >> 16u) & 255u), float((BbsPackedColor >> 8u) & 255u),
                 float(BbsPackedColor & 255u), float(BbsPackedColor >> 24u)) / 255.0;
    UV1 = ivec2(int(BbsPackedOverlay & 65535u), int(BbsPackedOverlay >> 16u));
    UV2 = ivec2(int(BbsPackedLight & 65535u), int(BbsPackedLight >> 16u));
#else
    Color = bbs_native_colour;
    UV1 = bbs_native_light_overlay.zw;
    UV2 = bbs_native_light_overlay.xy;
#endif
    BbsAlphaCoordinate = vec3(UV0, Color.a);
    BbsPassMode = bbs_native_mode.x;
#ifdef BBS_NATIVE_EMISSION_CAPTURE
    BbsEmissionColour = vec4(Color.rgb, intBitsToFloat(bbs_native_mode.y));
    BbsEmissionFogDistances = vec2(length(Position), max(length(Position.xz), abs(Position.y)));
#endif
}
