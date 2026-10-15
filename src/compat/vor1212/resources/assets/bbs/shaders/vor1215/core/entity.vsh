#version 150

/* vor1212: 1.21.1 kennt nur die einzelnen rendertype_entity_*-Shader; der vereinheitlichte
 * Kern-Shader "entity" (mit Defines) kam mit 1.21.2. Eigene Fassung fuer ENTITY_SNIPPET der
 * GL-Schicht, gleiche Eingaben, Uniforms und Defines wie dort benutzt. */
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 TextureMat;
uniform int FogShape;

uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float abstand;
out vec4 farbe;
#ifndef EMISSIVE
out vec4 lichtkarte;
#endif
#ifndef NO_OVERLAY
out vec4 ueberlagerung;
#endif
out vec2 uv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    abstand = fog_distance(Position, FogShape);

#ifdef NO_CARDINAL_LIGHTING
    farbe = Color;
#else
    farbe = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
#endif
#ifndef EMISSIVE
    lichtkarte = texelFetch(Sampler2, UV2 / 16, 0);
#endif
#ifndef NO_OVERLAY
    ueberlagerung = texelFetch(Sampler1, UV1, 0);
#endif

#ifdef APPLY_TEXTURE_MATRIX
    uv = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#else
    uv = UV0;
#endif
}
