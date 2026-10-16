#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

/* Bildfuellendes Viereck eines Filterdurchgangs (siehe Bildfilter). Keine Vertexfarbe:
 * die eigenen Werte kommen aus dem Uniform-Block im Fragmentshader, und genau dafuer
 * laeuft dieser Shader ausserhalb der Oberflaeche. */
layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
}
