#version 330
#extension GL_ARB_separate_shader_objects : require

/* Regiehilfe, Bodenplatte eines Ort-Markers (Material gh des Designs): Zeitring in Segmenten,
 * leuchtender Kopf, Wellen nach innen solange das Fenster laeuft, Fuellung bei Ankunft.
 * Verdeckt (zweiter Durchgang) nur gedaempft. */

layout(std140) uniform RegieInfo {
    vec4 Farbe;   /* rgb, Deckkraft */
    vec4 Werte;   /* Fortschritt, aktiv, erledigt, drin */
    vec4 Lauf;    /* Zeit, verdeckt (0/1), -, - */
};

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = vUv * 2.0 - 1.0;
    float r = length(p);
    if (r > 1.0) discard;
    float aa = max(fwidth(r) * 1.3, 0.004);
    float a01 = fract(atan(p.x, p.y) / 6.2831853 + 1.0);
    vec3 col = vec3(0.0);
    float alpha = 0.0;
    alpha += (1.0 - smoothstep(0.86, 0.985, r)) * 0.34;
    float band = smoothstep(0.70 - aa, 0.70 + aa, r) * (1.0 - smoothstep(0.88 - aa, 0.88 + aa, r));
    float seg = fract(a01 * 40.0);
    float gap = smoothstep(0.0, 0.1, seg) * (1.0 - smoothstep(0.9, 1.0, seg));
    float filled = 1.0 - smoothstep(Werte.x - 0.003, Werte.x + 0.003, a01);
    col += Farbe.rgb * band * 0.14;
    alpha += band * 0.22;
    float arc = band * filled * mix(1.0, gap, 0.7);
    col += Farbe.rgb * arc * (0.9 + 0.35 * Werte.y);
    alpha += arc * 0.42;
    float headD = abs(a01 - Werte.x);
    col += vec3(1.0) * band * (1.0 - smoothstep(0.0, 0.012, headD)) * 0.8 * step(0.002, Werte.x) * step(Werte.x, 0.998);
    float outer = exp(-pow((r - 0.925) / 0.022, 2.0));
    col += Farbe.rgb * outer * (0.45 + 0.45 * Werte.y);
    alpha += outer * 0.25;
    float w = fract(r * 2.4 + Lauf.x * 0.85);
    float rip = smoothstep(0.0, 0.07, w) * (1.0 - smoothstep(0.07, 0.2, w)) * (1.0 - smoothstep(0.5, 0.66, r));
    col += Farbe.rgb * rip * 0.42 * Werte.y;
    float inner = 1.0 - smoothstep(0.62 - aa, 0.64, r);
    col += Farbe.rgb * inner * (0.2 + 0.08 * sin(Lauf.x * 6.0)) * Werte.w;
    alpha += inner * 0.2 * Werte.w;
    float pip = 1.0 - smoothstep(0.055, 0.075, r);
    col += mix(Farbe.rgb, vec3(1.0), 0.4) * pip;
    alpha += pip * 0.3;
    float fade = Farbe.a * (1.0 - 0.72 * Werte.z) * mix(1.0, 0.22, Lauf.y);
    fragColor = vec4(col * fade, clamp(alpha, 0.0, 0.92) * fade);
}
