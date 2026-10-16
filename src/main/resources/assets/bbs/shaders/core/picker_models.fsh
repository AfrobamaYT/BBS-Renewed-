#version 330
#extension GL_ARB_separate_shader_objects : require

/* ⛔ OHNE DIESEN IMPORT KOMPILIERT DER SHADER NICHT (2026-08-01 im Log gefunden):
 *     Couldn't compile fragment shader (bbs:core/picker_models):
 *     0(24) : error C1503: undefined variable "ColorModulator"
 * und damit auch nicht die Pipeline bbs:pipeline/picker_models_opaque.
 *
 * ColorModulator steckt seit 26.2 im Vanilla-Block DynamicTransforms. Der Kommentar
 * darunter sagte das bereits richtig -- nur der Import dazu fehlte. */
#include <minecraft:dynamictransforms.glsl>

/* Derselbe Block wie im Vertexshader -- Reihenfolge und Groesse muessen mit
 * ModelVAORenderer.schreibeBlock uebereinstimmen, kein Compiler prueft das. */
layout(std140) uniform ModelInfo {
    vec4 NormalMat0;
    vec4 NormalMat1;
    vec4 NormalMat2;
    ivec4 LichtOverlay;
    ivec4 Modus;
    vec4 Material;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) flat in ivec2 texCoord2;

layout(location = 0) out vec4 fragColor;

void main()
{
    /* Die Modellfarbe steckt seit dem 26.2-Port in ColorModulator (DynamicTransforms),
     * nicht mehr in einem Vertexattribut. Hier zaehlt nur ihr Alphawert: er entscheidet,
     * ob das Texel ueberhaupt anklickbar ist. */
    vec4 color = texture(Sampler0, texCoord0) * ColorModulator;

    if (color.a < 0.1)
    {
        discard;
    }

    /* ⛔ KENNUNG, NICHT FARBE. Die drei Kanaele tragen eine 24-Bit-Zahl, die der
     * Auswahl-Durchgang zurueckliest. Sie darf darum weder gemischt noch gefiltert
     * werden -- deshalb faehrt die Pipeline ohne Blending (ModelVAOPipeline.PICKER_*).
     *
     * texCoord2.x ist der KNOCHENTEIL je Ecke (1.21.1: Target + texCoord2.x). Der
     * fruehere Port-Stand las stattdessen LichtOverlay.x — ein Uniform, EIN Wert pro
     * Zug: damit trugen alle Pixel die Basis, und ein Klick fand nie einen Knochen. */
    int totalIndex = Modus.y + texCoord2.x;
    float r = float(totalIndex & 0xff) / 255.0;
    float g = float((totalIndex >> 8) & 0xff) / 255.0;
    float b = float((totalIndex >> 16) & 0xff) / 255.0;

    fragColor = vec4(r, g, b, 1.0);
}
