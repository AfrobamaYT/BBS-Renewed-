#version 330
#extension GL_ARB_separate_shader_objects : require

/* ⛔ WARUM DIESER SHADER NICHT MEHR IN DIE OBERFLAECHE ZEICHNET (Entscheidung 2026-07-31)
 *
 * Er braucht fuenf eigene Werte: die gesuchte Kennung und die Hervorhebungsfarbe. In 26.2
 * kann ein GUI-Element keine eigenen Uniforms binden -- GuiRenderer bindet je Durchgang
 * die Standard-Uniforms, EINEN eigenen Block und bis zu drei Sampler, mehr ist nicht
 * vorgesehen. Werte in die Vertexfarbe zu packen (Vanillas Ausweg) scheitert an vier
 * Kanaelen fuer fuenf Werte.
 *
 * Statt Werte wegzulassen laeuft dieser Durchgang jetzt VORHER und in eine eigene Textur;
 * die Oberflaeche blittet danach nur noch ein fertiges Bild. Der Durchgang gehoert BBS
 * allein und darf darum haben, was ein GUI-Element nicht haben kann. Siehe Bildfilter.
 *
 * Der Block muss Zeile fuer Zeile zu Bildfilter.schreibeBlock passen -- std140 richtet
 * Felder je nach Typ unterschiedlich aus, darum steht alles als volle 16-Byte-Zeile. */
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

layout(location = 0) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    /* Alpha unter 1 heisst: an dieser Stelle steht ueberhaupt keine Kennung. */
    if (color.a < 1.0)
    {
        discard;
    }

    int index = int(color.r * 255.0) | (int(color.g * 255.0) << 8) | (int(color.b * 255.0) << 16);

    if (index != Ganz.x)
    {
        discard;
    }

    fragColor = Farbe;
}
