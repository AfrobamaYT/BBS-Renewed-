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

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;
out vec4 vertexColor;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;
}
