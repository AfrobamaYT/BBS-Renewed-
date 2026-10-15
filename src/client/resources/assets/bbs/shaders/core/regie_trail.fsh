#version 330
#extension GL_ARB_separate_shader_objects : require

/* Regiehilfe, Trail-Band (Material _h des Designs). vUv.x = Strecke S, vUv.y = quer V (-1..1).
 * Verdeckt kommt aus dem zweiten Durchgang (Tiefentest umgekehrt): gestrichelt und gedaempft. */

layout(std140) uniform RegieInfo {
    vec4 Farbe;   /* rgb, Deckkraft */
    vec4 Werte;   /* Laenge, Phase, Helligkeit, Dimmen */
    vec4 Lauf;    /* Zeit, verdeckt (0/1), Kopf-Ausblendung, - */
};

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;

void main() {
    float s = vUv.x;
    float v = abs(vUv.y);
    float aa = max(fwidth(vUv.y) * 1.4, 0.01);
    float edge = 1.0 - smoothstep(1.0 - aa * 2.0, 1.0, v);
    float core = 1.0 - smoothstep(0.56 - aa, 0.56 + aa, v);
    float border = (1.0 - smoothstep(0.82 - aa, 0.82 + aa, v)) * smoothstep(0.68 - aa, 0.68 + aa, v);
    float ph = fract((s - Werte.y) / 1.3);
    float d = ph - (0.62 - v * 0.5);
    float chev = (1.0 - smoothstep(0.035, 0.11, abs(d))) * core;
    float fade = smoothstep(0.0, Lauf.z, s) * (1.0 - smoothstep(Werte.x - 0.5, Werte.x + 0.05, s)) * Farbe.a;
    float dashes = step(0.55, fract(s / 0.7));
    float vis = mix(1.0, dashes * 0.35 * core, Lauf.y);
    vec3 col = Farbe.rgb * (core * 0.16 + border * 0.95 + chev * (1.15 - 0.6 * Werte.w)) * Werte.z;
    col += vec3(1.0) * chev * 0.18 * Werte.z * (1.0 - Werte.w);
    float alpha = edge * 0.44 + core * 0.1 + chev * 0.32;
    fragColor = vec4(col * fade * vis, alpha * fade * vis);
}
