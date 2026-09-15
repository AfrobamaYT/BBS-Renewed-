#version 330

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

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

/* Knochenteil der Pick-Kennung je Ecke (BINDUNG_LICHT). 1.21.1 nannte es texCoord2;
 * ohne dieses Attribut kannte der Auswahlpuffer nur die Replay-Basis und ein Klick
 * fand nie einen Knochen (Port-Regression, 2026-08-13 wiedereingebaut). */
in ivec2 UV2;

out vec2 texCoord0;
flat out ivec2 texCoord2;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    texCoord2 = UV2;
}
