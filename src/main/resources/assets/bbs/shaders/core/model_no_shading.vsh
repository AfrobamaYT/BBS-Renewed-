#version 330
/* separate_shader_objects: erst ab 26.3 */

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/* Die unbeschattete Zwillingsvariante von model.vsh -- gleiche drei Vertexbindungen
 * (Position an 0, Normale an 1, UV0 an 2), aber KEIN Richtungslicht.
 *
 * ⛔ WARUM ES DIESEN SHADER GEBEN MUSS: bis 1.21.1 lief dieser Fall ueber Vanillas
 * position_tex_color. Das ging, weil ein Vertexattribut damals ueber seinen NAMEN gebunden
 * wurde -- BBS' reiner Positionspuffer landete auf "Position", die Formfarbe kam als
 * konstantes Attribut "Color" dazu, der Rest blieb leer. In 26.2 beschreibt die PIPELINE die
 * Puffer, und eine Vanilla-Pipeline erklaert EINE verschraenkte Bindung 0. Mit ihr liest der
 * Shader BBS' Positionspuffer als ganzen verschraenkten Vertex: kein Fehler, sondern Muell
 * im Bild.
 *
 * ⚠️ Die Normale wird absichtlich NICHT gelesen -- "shading aus" heisst genau das. Ihre
 * Bindung bleibt trotzdem in der Pipeline stehen, weil ModelVAO alle drei Puffer setzt und
 * eine Luecke in der Bindungsnummerierung ein zweiter Fehler waere. Ein unbenutztes
 * Attribut kostet nichts: die Belegung passiert ueber glBindAttribLocation VOR dem Linken,
 * ein Name, den das Programm nicht kennt, laeuft ins Leere. */

/*location 0*/ in vec3 Position;
/*location 2*/ in vec2 UV0;

/*location 0*/ out vec2 texCoord0;
/*location 1*/ out vec4 vertexColor;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;

    /* Frueher: vertexColor = Color (konstantes Attribut = Formfarbe), und der Fragmentshader
     * multiplizierte danach noch einmal mit ColorModulator (das BBS auf Weiss setzte). Heute
     * traegt ColorModulator die Formfarbe SELBST -- ModelVAORenderer schreibt r/g/b/a ueber
     * writeTransform genau dorthin. Ein zweites Multiplizieren wuerde sie quadrieren:
     * dunkler, aber nicht offensichtlich falsch. */
    vertexColor = ColorModulator;
}
