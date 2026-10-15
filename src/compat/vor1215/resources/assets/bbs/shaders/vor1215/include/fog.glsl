#version 330

/* BBS, Schicht vor 1.21.5: Minecraft 1.21.4 hat diesen Uniform-Block nicht; die eigene GL-Schicht
 * fuellt ihn (gleiche Namen und std140-Anordnung wie ab 1.21.6), eigene Fassung. */
layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

float bbs_fog_anteil(float abstand, float anfang, float ende) {
    return clamp((abstand - anfang) / max(ende - anfang, 1e-6), 0.0, 1.0);
}

/* Umgebungsnebel nach Kugelabstand, Sichtweitennebel nach Zylinderabstand; der staerkere gilt. */
vec4 apply_fog(vec4 farbe, float kugel, float zylinder, float umgebungAnfang, float umgebungEnde, float sichtAnfang, float sichtEnde, vec4 nebel) {
    float anteil = max(bbs_fog_anteil(kugel, umgebungAnfang, umgebungEnde), bbs_fog_anteil(zylinder, sichtAnfang, sichtEnde));
    return vec4(mix(farbe.rgb, nebel.rgb, anteil * nebel.a), farbe.a);
}

float fog_spherical_distance(vec3 lage) {
    return length(lage);
}

float fog_cylindrical_distance(vec3 lage) {
    return max(length(lage.xz), abs(lage.y));
}
