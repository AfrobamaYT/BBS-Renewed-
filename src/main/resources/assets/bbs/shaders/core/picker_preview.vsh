#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/* Bildfuellendes Viereck eines Filterdurchgangs (siehe Bildfilter). Es hat KEINE
 * Vertexfarbe: die Hervorhebungsfarbe kommt aus dem eigenen Uniform-Block im
 * Fragmentshader, und genau dafuer laeuft dieser Shader ausserhalb der Oberflaeche. */
in vec3 Position;
in vec2 UV0;

out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
}
