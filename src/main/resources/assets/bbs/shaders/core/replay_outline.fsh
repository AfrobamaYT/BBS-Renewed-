#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform int TargetMin;
uniform vec4 OutlineColor;
uniform int Thickness;
uniform vec2 Schritt;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

/* Der Stencil traegt den Objekt-Index in RGB, Alpha 1 wo ueberhaupt etwas steht. */
int indexVon(vec4 c)
{
    return int(c.r * 255.0) | (int(c.g * 255.0) << 8) | (int(c.b * 255.0) << 16);
}

/* Alles AB TargetMin gehoert zum bearbeiteten Replay: dessen Index zaehlt beim Zeichnen
 * pro Knochen hoch, damit man Knochen anklicken kann. */
bool gehoertDazu(vec2 uv)
{
    vec4 c = texture(Sampler0, uv);

    return c.a >= 1.0 && indexVon(c) >= TargetMin;
}

void main()
{
    /* Innen wird nicht eingefaerbt — Blender umrandet, es fuellt nicht. */
    if (gehoertDazu(texCoord0))
    {
        discard;
    }

    /* ⚠ Schritt kommt von aussen und ist EIN BILDSCHIRMPIXEL in UV-Einheiten, nicht ein
     * Texel. Der Stencil hat Video-Aufloesung (2560x1440), das Viereck auf dem Schirm ist
     * gut halb so breit — mit 1.0/textureSize lag der Nachbar im selben Bildschirmpixel und
     * erreichte die Figur nie. Gemessen am 2026-07-29: ±1 Texel fand 402 Randpixel, ±4 weitere
     * 978, ±12 nochmal 2375. Die Linie war also gestrichelt statt durchgezogen. */
    for (int y = -Thickness; y <= Thickness; y++)
    {
        for (int x = -Thickness; x <= Thickness; x++)
        {
            if (x == 0 && y == 0)
            {
                continue;
            }

            /* Ein Pixel gehoert zur Linie, wenn er selbst draussen ist und in Reichweite ein
             * Nachbar drinnen liegt. Quadratische Nachbarschaft statt Kreuz, sonst franst die
             * Linie an schraegen Kanten aus. */
            if (gehoertDazu(texCoord0 + vec2(float(x), float(y)) * Schritt))
            {
                fragColor = OutlineColor * ColorModulator;

                return;
            }
        }
    }

    discard;
}
