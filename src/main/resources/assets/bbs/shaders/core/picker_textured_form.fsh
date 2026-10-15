#version 450

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

/*location 0*/ in vec2 texCoord0;
/*location 1*/ in float vertexAlpha;

/*location 0*/ out vec4 fragColor;

void main()
{
    if (texture(Sampler0, texCoord0).a * vertexAlpha < 0.1)
    {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb, 1.0);
}
