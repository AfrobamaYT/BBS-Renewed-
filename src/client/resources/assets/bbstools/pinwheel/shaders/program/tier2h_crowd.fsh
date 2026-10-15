#version 330 core
uniform sampler2D Sampler0;
in vec2 texCoord0;
in vec4 vertexColor;
layout(location = 0) out vec4 fragColor;
void main() {
    fragColor = texture(Sampler0, texCoord0) * vertexColor;
    if (fragColor.a < 0.1) discard;
}
