/* ⛔ NICHT AUF 26.2 UMGESTELLT -- und das ist eine Entwurfsfrage, keine vergessene Zeile.
 *
 * Dieser Shader braucht mehr eigene Werte, als in der Oberflaeche unterzubringen sind:
 * TargetMin, OutlineColor (4), Thickness und Schritt (2) -- zusammen acht.
 *
 * In 26.2 kann ein GUI-Element KEINE eigenen Uniforms binden (GuiElementRenderState hat
 * vier Methoden, keine davon fuer einen Uniform-Puffer). Der uebliche Ausweg -- Werte in
 * die Vertexfarbe packen -- geht hier NICHT: sie fasst vier Bytes, und vertexColor wird
 * von diesem Shader bereits als Farbe benutzt.
 *
 * Drei Wege stehen offen, alle drei sind Entwurf und keiner ist Uebersetzung:
 *   1. PictureInPictureRenderer -- Mojangs eigener Weg fuer GUI-Inhalte mit eigener
 *      Zeichenlogik (Entity-Vorschau, Buch, Skin). Rendert in eine eigene Textur und
 *      blittet sie; dort ist ein voller Render-Pass samt Uniforms erlaubt.
 *   2. Die Werte reduzieren, bis sie in vier Bytes passen.
 *   3. Ausserhalb der GUI zeichnen -- dann aber VOR dem GUI-Stapel, also nur brauchbar,
 *      wenn das Ergebnis ohnehin hinter der Oberflaeche liegen soll.
 *
 * Bis dahin bleibt die Datei auf #version 150 und dieser Shader laedt nicht. Er ist damit
 * sichtbar kaputt statt still falsch -- das ist Absicht.
 */
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
