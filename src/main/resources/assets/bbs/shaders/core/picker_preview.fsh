/* ⛔ NICHT AUF 26.2 UMGESTELLT -- und das ist eine Entwurfsfrage, keine vergessene Zeile.
 *
 * Dieser Shader braucht mehr eigene Werte, als in der Oberflaeche unterzubringen sind:
 * Target (Kennung) und HighlightColor (4 Werte) -- zusammen fuenf.
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
uniform int Target;
uniform vec4 HighlightColor;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;

    if (color.a < 1.0)
    {
        discard;
    }

    int totalIndex = int(color.r * 255.0) | (int(color.g * 255.0) << 8) | (int(color.b * 255.0) << 16);

    if (totalIndex == Target)
    {
        color = HighlightColor;
    }
    else
    {
        discard;
    }

    fragColor = color * ColorModulator;
}
