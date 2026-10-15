#version 330
/* separate_shader_objects: erst ab 26.3 */

/* ⛔ WARUM DIESER SHADER NICHT MEHR IN DIE OBERFLAECHE ZEICHNET (Entscheidung 2026-07-31)
 *
 * Er braucht sechs eigene Werte und zwei Quelltexturen. In 26.2 kann ein GUI-Element
 * keine eigenen Uniforms binden; Begruendung und verworfene Auswege stehen in Bildfilter.
 *
 * ⚠️ Der Atlas liegt jetzt auf Sampler1, nicht mehr auf Sampler3. Der alte Platz kam vom
 * globalen setShaderTexture(3, ...), das es nicht mehr gibt -- die Bindegruppe des
 * Filters zaehlt ihre Sampler ab null.
 */
/* Der gemeinsame Block ALLER Filter. Er muss vollstaendig und in dieser Reihenfolge
 * stehen, auch die Felder, die dieser Shader nicht liest -- std140 richtet sonst anders
 * aus und der Shader liest lautlos falsche Zahlen. Vertrag: Bildfilter.schreibeBlock.
 *
 *   Ganz.x  gesuchte Kennung bzw. kleinster Index, ab dem etwas dazugehoert
 *   Ganz.y  Dicke der Umrandung bzw. Kantenlaenge der Verpixelung
 *   Ganz.z  Radiergummi an/aus
 *   Farbe   Hervorhebungs- bzw. Umrissfarbe
 *   Masse.xy  Schrittweite in UV bzw. Groesse in Texeln
 */
layout(std140) uniform FilterInfo {
    ivec4 Ganz;
    vec4 Farbe;
    vec4 Masse;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

/*location 0*/ in vec2 texCoord0;

/*location 0*/ out vec4 fragColor;

void main()
{
    vec2 coord = texCoord0 * Masse.xy;

    coord.x = floor(coord.x);
    coord.y = floor(coord.y);

    if (Ganz.z == 1)
    {
        /* Radiergummi: das Schachbrettmuster steht unten links im Symbol-Atlas. */
        coord.x = mod(coord.x, 16);
        coord.y = mod(coord.y, 16) + 240;
        coord /= vec2(256, 256);

        fragColor = (texture(Sampler0, texCoord0).a > 0.6 ? 1 : 0) * texture(Sampler1, coord);
    }
    else
    {
        coord.x -= mod(coord.x, float(Ganz.y));
        coord.y -= mod(coord.y, float(Ganz.y));
        coord /= Masse.xy;

        fragColor = texture(Sampler0, coord);
    }
}
