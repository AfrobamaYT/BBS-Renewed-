#version 330

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

in vec2 texCoord0;

out vec4 fragColor;

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
     * werden -- deshalb faehrt die Pipeline ohne Blending (ModelVAOPipeline.PICKER_*). */
    int totalIndex = Modus.y;
    float r = float(totalIndex & 0xff) / 255.0;
    float g = float((totalIndex >> 8) & 0xff) / 255.0;
    float b = float((totalIndex >> 16) & 0xff) / 255.0;

    fragColor = vec4(r, g, b, 1.0);
}
