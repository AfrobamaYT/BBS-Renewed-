#version 460
layout(std430) readonly buffer BbsEmissionFragments { uint bbs_emission_words[]; };
layout(location = 0) out vec4 fragColor;
void main() {
    uint flags = bbs_emission_words[4];
    fragColor = vec4(float(flags & 255u), float((flags >> 8u) & 255u),
        float((flags >> 16u) & 255u), float((flags >> 24u) & 255u)) / 255.0;
}
