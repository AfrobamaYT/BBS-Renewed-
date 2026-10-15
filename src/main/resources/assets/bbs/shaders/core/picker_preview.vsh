#version 330
/* separate_shader_objects: erst ab 26.3 */

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/* Bildfuellendes Viereck eines Filterdurchgangs (siehe Bildfilter). Es hat KEINE
 * Vertexfarbe: die Hervorhebungsfarbe kommt aus dem eigenen Uniform-Block im
 * Fragmentshader, und genau dafuer laeuft dieser Shader ausserhalb der Oberflaeche. */
/*location 0*/ in vec3 Position;
/*location 1*/ in vec2 UV0;

/*location 0*/ out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
}
