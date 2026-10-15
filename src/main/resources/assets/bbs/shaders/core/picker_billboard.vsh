#version 330
/* separate_shader_objects: erst ab 26.3 */

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/* Derselbe Block wie in model.vsh -- der Auswahl-Durchgang laeuft ueber dieselbe
 * Pipeline-Familie (ModelVAOPipeline) und damit ueber denselben Vertrag.
 * Modus.x ist die Durchgangsart, Modus.y die Kennung dieses Durchgangs (frueher das
 * Uniform "Target"). Gefuellt wird der Block von ModelVAORenderer.schreibeBlock. */
layout(std140) uniform ModelInfo {
    vec4 NormalMat0;
    vec4 NormalMat1;
    vec4 NormalMat2;
    ivec4 LichtOverlay;
    ivec4 Modus;
    vec4 Material;
};

/*location 0*/ in vec3 Position;
/*location 1*/ in vec2 UV0;
/*location 2*/ in vec3 Normal;

/*location 0*/ out vec2 texCoord0;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
}
