#version 330

/* BBS, Schicht vor 1.21.5: Minecraft 1.21.4 hat diesen Uniform-Block nicht; die eigene GL-Schicht
 * fuellt ihn (gleiche Namen und std140-Anordnung wie ab 1.21.6), eigene Fassung. */
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
