#version 330

/* BBS, Schicht vor 1.21.5: Minecraft 1.21.4 hat diesen Uniform-Block nicht; die eigene GL-Schicht
 * fuellt ihn (gleiche Namen und std140-Anordnung wie ab 1.21.6), eigene Fassung. */
layout(std140) uniform Projection {
    mat4 ProjMat;
};

/* Clip-Raum nach Bildschirm-Texturkoordinaten (0..w), w bleibt erhalten. */
vec4 projection_from_position(vec4 clip) {
    return vec4((clip.xy + clip.ww) * 0.5, clip.zw);
}
