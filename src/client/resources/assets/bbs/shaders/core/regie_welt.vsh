#version 330
/* separate_shader_objects: erst ab 26.3 */

/* Regiehilfe, Weltschicht: gemeinsamer Vertex-Shader. Ecken kameranah, UV traegt die
 * Flaechen- bzw. Bandkoordinaten fuer die Fragment-Shader. */

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/*location 0*/ in vec3 Position;
/*location 1*/ in vec2 UV0;

/*location 0*/ out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vUv = UV0;
}
