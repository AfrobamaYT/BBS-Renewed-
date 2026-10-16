#version 330
#extension GL_ARB_separate_shader_objects : require

/* Regiehilfe, Lichtstrahl ueber einem Ort-Marker (Material xh des Designs). Das Band steht
 * zur Kamera gedreht (auf der CPU gebaut); vUv.x quer 0..1, vUv.y Hoehe 0..1. */

layout(std140) uniform RegieInfo {
    vec4 Farbe;   /* rgb, Deckkraft */
    vec4 Werte;   /* Staerke, Puls, Abstand zur Kamera, - */
    vec4 Lauf;    /* Zeit, verdeckt (0/1), -, - */
};

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;

void main() {
    float x = vUv.x * 2.0 - 1.0;
    float core = exp(-x * x * 22.0);
    float hot = exp(-x * x * 260.0);
    float halo = exp(-x * x * 4.0);
    float y = vUv.y;
    float fade = pow(1.0 - y, 1.35) * smoothstep(0.0, 0.025, y);
    float flow = 0.78 + 0.22 * sin(y * 36.0 - Lauf.x * 2.6);
    float near = smoothstep(1.6, 6.5, Werte.z);
    float k = Werte.x * (1.0 + Werte.y * 0.5) * near * fade * Farbe.a;
    k *= mix(1.0, 0.55, Lauf.y);
    vec3 c = Farbe.rgb * (core * 0.95 * flow + halo * 0.42) + vec3(1.0) * hot * 0.5;
    float a = core * 0.5 + halo * 0.14;
    fragColor = vec4(c * k, a * k);
}
