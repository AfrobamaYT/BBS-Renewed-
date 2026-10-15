// SPDX-License-Identifier: MIT
// Per-pixel fragment-list layout: eight header words, pixel heads, then twelve-word nodes.
// Nodes use links plus one to reserve zero for an empty head. Resolve owns visibility testing.
layout(std430) buffer BbsEmissionFragments { uint bbs_emission_words[]; };
layout(std140) uniform BbsFog {
    vec4 bbs_emission_fog_colour;
    float bbs_emission_environment_start;
    float bbs_emission_environment_end;
    float bbs_emission_distance_start;
    float bbs_emission_distance_end;
    float bbs_emission_sky_end;
    float bbs_emission_cloud_end;
};
in vec4 BbsEmissionColour;
in vec2 BbsEmissionFogDistances;

float bbs_emission_fog(float distanceValue, float startValue, float endValue) {
    if (distanceValue <= startValue) return 0.0;
    if (distanceValue >= endValue) return 1.0;
    return (distanceValue - startValue) / (endValue - startValue);
}

void bbs_emission_capture(float alphaValue, float finalDepth) {
    float factor = BbsEmissionColour.a;
    if (isnan(factor) || isinf(factor) || factor < 0.0 || factor > 1.0) {
        atomicOr(bbs_emission_words[4], 64u); return;
    }
    if (factor <= 0.0) return;
    uint widthValue = bbs_emission_words[0], heightValue = bbs_emission_words[1];
    uvec2 pixelValue = uvec2(gl_FragCoord.xy);
    if (pixelValue.x >= widthValue || pixelValue.y >= heightValue) {
        atomicOr(bbs_emission_words[4], 2u); return;
    }
    uint node = atomicAdd(bbs_emission_words[3], 1u);
    if (node >= bbs_emission_words[2]) { atomicOr(bbs_emission_words[4], 1u); return; }
    uint pixelIndex = pixelValue.y * widthValue + pixelValue.x;
    uint start = 8u + widthValue * heightValue + node * 12u;
    vec3 rawColour = texture(BbsAlphaTexture, BbsAlphaCoordinate.xy).rgb * BbsEmissionColour.rgb;
    float fogValue = max(bbs_emission_fog(BbsEmissionFogDistances.x,
        bbs_emission_environment_start, bbs_emission_environment_end),
        bbs_emission_fog(BbsEmissionFogDistances.y, bbs_emission_distance_start, bbs_emission_distance_end));
    rawColour = mix(rawColour, bbs_emission_fog_colour.rgb, fogValue * bbs_emission_fog_colour.a);
    bbs_emission_words[start + 1u] = floatBitsToUint(finalDepth);
    bbs_emission_words[start + 2u] = floatBitsToUint(rawColour.r);
    bbs_emission_words[start + 3u] = floatBitsToUint(rawColour.g);
    bbs_emission_words[start + 4u] = floatBitsToUint(rawColour.b);
    bbs_emission_words[start + 5u] = floatBitsToUint(factor);
    bbs_emission_words[start + 6u] = floatBitsToUint(alphaValue);
    bbs_emission_words[start + 7u] = pixelIndex;
    bbs_emission_words[start + 8u] = uint(BbsPassMode);
    bbs_emission_words[start + 9u] = BBS_CAPTURE_PACK_DEPTH;
    bbs_emission_words[start + 10u] = 0u;
    bbs_emission_words[start + 11u] = 0u;
    bbs_emission_words[start] = atomicExchange(bbs_emission_words[8u + pixelIndex], node + 1u);
}
