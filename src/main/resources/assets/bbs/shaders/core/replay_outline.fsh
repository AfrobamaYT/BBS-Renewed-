#version 330

/* ⛔ WARUM DIESER SHADER NICHT MEHR IN DIE OBERFLAECHE ZEICHNET (Entscheidung 2026-07-31)
 *
 * Er braucht acht eigene Werte. In 26.2 kann ein GUI-Element keine eigenen Uniforms
 * binden; die Begruendung und die drei verworfenen Auswege stehen in Bildfilter. Der
 * Durchgang laeuft jetzt vorher in eine eigene Textur, die die Oberflaeche danach als
 * ganz normales Bild blittet.
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

in vec2 texCoord0;

out vec4 fragColor;

/* Das Auswahlbild traegt den Objekt-Index in RGB, Alpha 1 wo ueberhaupt etwas steht. */
int indexVon(vec4 c)
{
    return int(c.r * 255.0) | (int(c.g * 255.0) << 8) | (int(c.b * 255.0) << 16);
}

/* Alles AB Ganz.x gehoert zum bearbeiteten Replay: dessen Index zaehlt beim Zeichnen
 * pro Knochen hoch, damit man Knochen anklicken kann. Ganz.z (sonst Radiergummi-Feld)
 * ist hier die AUSSCHLIESSENDE Obergrenze: ab dort beginnen die Widget-Kennungen
 * (Rig-Controls, Marker), und Blender umrandet Controls nie. 0 = kein Limit. */
bool gehoertDazu(vec2 uv)
{
    vec4 c = texture(Sampler0, uv);
    int index = indexVon(c);

    return c.a >= 1.0 && index >= Ganz.x && (Ganz.z <= 0 || index < Ganz.z);
}

void main()
{
    /* Innen wird nicht eingefaerbt -- Blender umrandet, es fuellt nicht. */
    if (gehoertDazu(texCoord0))
    {
        discard;
    }

    /* ⚠ Masse.xy ist EIN BILDSCHIRMPIXEL in UV-Einheiten, nicht ein Texel. Das
     * Auswahlbild hat Videoaufloesung (2560x1440), das Viereck auf dem Schirm ist gut
     * halb so breit -- mit 1.0/textureSize lag der Nachbar im selben Bildschirmpixel und
     * erreichte die Figur nie. Gemessen am 2026-07-29: +-1 Texel fand 402 Randpixel, +-4
     * weitere 978, +-12 nochmal 2375. Die Linie war also gestrichelt statt durchgezogen. */
    for (int y = -Ganz.y; y <= Ganz.y; y++)
    {
        for (int x = -Ganz.y; x <= Ganz.y; x++)
        {
            if (x == 0 && y == 0)
            {
                continue;
            }

            /* Ein Pixel gehoert zur Linie, wenn er selbst draussen ist und in Reichweite
             * ein Nachbar drinnen liegt. Quadratische Nachbarschaft statt Kreuz, sonst
             * franst die Linie an schraegen Kanten aus. */
            if (gehoertDazu(texCoord0 + vec2(float(x), float(y)) * Masse.xy))
            {
                fragColor = Farbe;

                return;
            }
        }
    }

    discard;
}
