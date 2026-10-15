#version 330
/* separate_shader_objects: erst ab 26.3 */
#moj_import <minecraft:fog.glsl>
uniform sampler2D Sampler0;
/*location 0*/ in vec2 texCoord0;
/*location 1*/ in vec4 vertexColor;
/*location 2*/ in vec4 overlayColor;
/*location 3*/ in float sphericalVertexDistance;
/*location 4*/ in float cylindricalVertexDistance;
/*location 0*/ out vec4 fragColor;
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.1) discard;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
        FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
        FogRenderDistanceEnd, FogColor);
}
