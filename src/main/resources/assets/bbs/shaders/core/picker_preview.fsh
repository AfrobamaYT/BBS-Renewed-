#version 330

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
layout(std140) uniform FilterInfo {
    ivec4 Ziel;
    vec4 Hervorhebung;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;

out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    /* Alpha unter 1 heisst: an dieser Stelle steht ueberhaupt keine Kennung. */
    if (color.a < 1.0)
    {
        discard;
    }

    int index = int(color.r * 255.0) | (int(color.g * 255.0) << 8) | (int(color.b * 255.0) << 16);

    if (index != Ziel.x)
    {
        discard;
    }

    fragColor = Hervorhebung;
}
