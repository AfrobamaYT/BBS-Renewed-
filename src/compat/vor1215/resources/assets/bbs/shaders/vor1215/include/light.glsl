#version 330

/* BBS, Schicht vor 1.21.5: Minecraft 1.21.4 hat diesen Uniform-Block nicht; die eigene GL-Schicht
 * fuellt ihn (gleiche Namen und std140-Anordnung wie ab 1.21.6), eigene Fassung. */
layout(std140) uniform Lighting {
    vec3 Light0_Direction;
    vec3 Light1_Direction;
};

/* Zwei Richtungslichter (Anteil 0.6) plus Grundlicht 0.4, wie Minecrafts Gegenstandslicht. */
vec4 minecraft_mix_light(vec3 licht0, vec3 licht1, vec3 normale, vec4 farbe) {
    vec2 anteil = max(vec2(0.0), vec2(dot(licht0, normale), dot(licht1, normale)));
    return vec4(farbe.rgb * min(1.0, (anteil.x + anteil.y) * 0.6 + 0.4), farbe.a);
}
