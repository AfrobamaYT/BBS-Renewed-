#version 330

uniform sampler2D Sampler0;

in vec2 texCoord0;
flat in vec4 pickColor;
out vec4 fragColor;

void main()
{
    if (texture(Sampler0, texCoord0).a * pickColor.a < 0.1)
    {
        discard;
    }

    fragColor = vec4(pickColor.rgb, 1.0);
}
