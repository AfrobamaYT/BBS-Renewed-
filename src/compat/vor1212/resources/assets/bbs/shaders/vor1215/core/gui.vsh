#version 150

/* vor1212: 1.21.1 hat noch keinen Kern-Shader "gui" (kam mit 1.21.2). Eigene Fassung fuer die
 * GUI-Pipeline der GL-Schicht: Position + Vertexfarbe. */
in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 farbe;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    farbe = Color;
}
