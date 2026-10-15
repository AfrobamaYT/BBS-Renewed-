#version 330
#extension GL_ARB_separate_shader_objects : require

/* Regiehilfe, Startring mit Richtungspfeil (Material vh des Designs). Flaeche ist 3R gross,
 * p in Einheiten von R; Werte.x dreht den Pfeil in die Blickrichtung. */

layout(std140) uniform RegieInfo {
    vec4 Farbe;   /* rgb, Deckkraft */
    vec4 Werte;   /* Gier (Bogenmass), Puls, -, - */
    vec4 Lauf;    /* Zeit, verdeckt (0/1), -, - */
};

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 fragColor;

void main() {
    vec2 p = (vUv * 2.0 - 1.0) * 1.5;
    float r = length(p);
    float aa = max(fwidth(r) * 1.3, 0.004);
    float cs = cos(Werte.x), sn = sin(Werte.x);
    vec2 q = vec2(cs * p.x - sn * p.y, sn * p.x + cs * p.y);
    vec3 col = vec3(0.0);
    float alpha = 0.0;
    alpha += (1.0 - smoothstep(0.86, 0.98, r)) * 0.32;
    float ring = smoothstep(0.72 - aa, 0.72 + aa, r) * (1.0 - smoothstep(0.86 - aa, 0.86 + aa, r));
    float a01 = fract(atan(p.x, p.y) / 6.2831853 + 1.0);
    float dash = mix(1.0, step(0.35, fract(a01 * 24.0)), 0.6);
    col += Farbe.rgb * ring * dash * (0.75 + 0.25 * Werte.y);
    alpha += ring * 0.4;
    float ay = q.y;
    float head = step(0.98, ay) * step(ay, 1.48) * step(abs(q.x), (1.48 - ay) * 0.62);
    float edgeSoft = smoothstep(0.0, 0.03, (1.48 - ay) * 0.62 - abs(q.x));
    col += Farbe.rgb * head * edgeSoft * 0.95;
    alpha += head * edgeSoft * 0.45;
    float pip = 1.0 - smoothstep(0.16, 0.2, r);
    col += Farbe.rgb * pip * 0.7;
    alpha += pip * 0.25;
    float fade = Farbe.a * mix(1.0, 0.25, Lauf.y);
    if (alpha < 0.002 && length(col) < 0.002) discard;
    fragColor = vec4(col * fade, clamp(alpha, 0.0, 0.9) * fade);
}
