#version 150

in vec4 farbe;

uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    if (farbe.a == 0.0) {
        discard;
    }

    fragColor = farbe * ColorModulator;
}
