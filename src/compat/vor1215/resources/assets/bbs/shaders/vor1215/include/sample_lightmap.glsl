#version 330

/* Vor 26.1 bringt Minecraft diese Include-Datei nicht mit (26.1 hat sie aus block.vsh
 * ausgelagert); BBS-Shader importieren sie. Dieselbe Rechnung wie minecraft_sample_lightmap. */
vec4 sample_lightmap(sampler2D lightMap, ivec2 uv) {
    return texture(lightMap, clamp((uv / 256.0) + 0.5 / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
}
