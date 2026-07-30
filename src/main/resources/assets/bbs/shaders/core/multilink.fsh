/* ⛔ NICHT AUF 26.2 UMGESTELLT -- und das ist eine Entwurfsfrage, keine vergessene Zeile.
 *
 * Dieser Shader braucht mehr eigene Werte, als in der Oberflaeche unterzubringen sind:
 * Size (2 Werte) und Filters (4 Werte) -- zusammen sechs.
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
uniform sampler2D Sampler3;

uniform vec4 ColorModulator;
uniform vec2 Size;
uniform vec4 Filters;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main()
{
    vec2 coord = texCoord0 * Size;

    coord.x = floor(coord.x);
    coord.y = floor(coord.y);

    int pixelate = int(Filters.r);
    int erase = int(Filters.g);

    if (erase == 1)
    {
        coord.x = mod(coord.x, 16);
        coord.y = mod(coord.y, 16) + 240;
        coord /= vec2(256, 256);

        fragColor = (texture(Sampler0, texCoord0).a > 0.6 ? 1 : 0) * texture(Sampler3, coord);
    }
    else
    {
        coord.x -= mod(coord.x, pixelate);
        coord.y -= mod(coord.y, pixelate);
        coord /= Size;

        fragColor = texture(Sampler0, coord) * ColorModulator;
    }

    fragColor *= vertexColor;
}
