#version 330
#extension GL_ARB_separate_shader_objects : require

/* Regiehilfe, Weltschicht: gemeinsamer Vertex-Shader. Ecken kameranah, UV traegt die
 * Flaechen- bzw. Bandkoordinaten fuer die Fragment-Shader. */

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
}
