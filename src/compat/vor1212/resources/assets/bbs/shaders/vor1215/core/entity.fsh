#version 150

#moj_import <minecraft:fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float abstand;
in vec4 farbe;
#ifndef EMISSIVE
in vec4 lichtkarte;
#endif
#ifndef NO_OVERLAY
in vec4 ueberlagerung;
#endif
in vec2 uv;

out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, uv);
#ifdef ALPHA_CUTOUT
    if (c.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    c *= farbe * ColorModulator;
#ifndef NO_OVERLAY
    c.rgb = mix(ueberlagerung.rgb, c.rgb, ueberlagerung.a);
#endif
#ifndef EMISSIVE
    c *= lichtkarte;
#endif
    fragColor = linear_fog(c, abstand, FogStart, FogEnd, FogColor);
}
