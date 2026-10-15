#version 460
uniform sampler2D CompletedScene;
uniform sampler2D SceneDepth;
layout(std430) buffer BbsEmissionFragments { uint bbs_emission_words[]; };
/*location 0*/ out vec4 fragColor;

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 lit = texelFetch(CompletedScene, pixel, 0);
    fragColor = lit;
    if (bbs_emission_words[4] != 0u) return;
    uint widthValue = bbs_emission_words[0], heightValue = bbs_emission_words[1];
    if (pixel.x < 0 || pixel.y < 0 || uint(pixel.x) >= widthValue || uint(pixel.y) >= heightValue) {
        atomicOr(bbs_emission_words[4], 2u); return;
    }
    uint pixels = widthValue * heightValue;
    uint link = bbs_emission_words[8u + uint(pixel.y) * widthValue + uint(pixel.x)];
    uint capacity = bbs_emission_words[2];
    uint count = min(bbs_emission_words[3], capacity);
    float sceneDepth = texelFetch(SceneDepth, pixel, 0).r;
    uint selected = 0u, walked = 0u;
    while (link != 0u) {
        if (link > count || walked++ >= count) { atomicOr(bbs_emission_words[4], 8u); return; }
        uint node = 8u + pixels + (link - 1u) * 12u;
        float actualDepth = uintBitsToFloat(bbs_emission_words[node + 1u]);
        /* Vitrail PackDepth uses ClipSpace.REVERSED.z=-1,w=1; compare in that same stored convention. */
        float packedDepth = 1.0 - actualDepth;
        if (packedDepth == sceneDepth) {
            if (bbs_emission_words[node + 9u] != 0u) { atomicOr(bbs_emission_words[4], 16u); return; }
            if (uintBitsToFloat(bbs_emission_words[node + 6u]) < 0.999) {
                atomicOr(bbs_emission_words[4], 4u); return;
            }
            if (selected != 0u) {
                /* Equal-depth fragments with different material contributions need actual draw order. */
                for (uint field = 2u; field <= 5u; field++) {
                    if (bbs_emission_words[selected + field] != bbs_emission_words[node + field]) {
                        atomicOr(bbs_emission_words[4], 32u); return;
                    }
                }
            } else selected = node;
        }
        link = bbs_emission_words[node];
    }
    if (selected != 0u) {
        vec3 rawColour = vec3(uintBitsToFloat(bbs_emission_words[selected + 2u]),
            uintBitsToFloat(bbs_emission_words[selected + 3u]), uintBitsToFloat(bbs_emission_words[selected + 4u]));
        float factor = uintBitsToFloat(bbs_emission_words[selected + 5u]);
        if (isnan(factor) || isinf(factor) || factor < 0.0 || factor > 1.0
            || any(isnan(rawColour)) || any(isinf(rawColour))) {
            atomicOr(bbs_emission_words[4], 64u); return;
        }
        fragColor = vec4(mix(lit.rgb, rawColour, factor), lit.a);
    }
}
