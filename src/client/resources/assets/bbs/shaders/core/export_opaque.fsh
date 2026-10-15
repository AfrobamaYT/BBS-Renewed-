#version 330
/* separate_shader_objects: erst ab 26.3 */

uniform sampler2D InSampler;
/*location 0*/ in vec2 texCoord;
/*location 0*/ out vec4 fragColor;

void main() {
    fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
}
