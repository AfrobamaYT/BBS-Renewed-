#version 330

uniform sampler2D Sampler0;

/* ⛔ 26.2: TextureSize war ein Uniform -- GLSL kann die Groesse selbst erfragen, also
 * braucht es dafuer keinen Kanal. Das ist der Grund, warum dieser Shader ohne eigenen
 * Uniform-Block auskommt, waehrend die drei Geschwister (multilink, picker_preview,
 * replay_outline) es nicht tun: ihre Werte lassen sich nicht aus dem Bild ableiten.
 *
 * Blur reist in der Vertexfarbe mit: R = Staerke x, G = Staerke y, jeweils Zehntel
 * (Wert/10 ergibt Pixel). Gepackt wird das an der Aufrufstelle in UISubtitleRenderer. */
#define TextureSize vec2(textureSize(Sampler0, 0))
#define Blur (vertexColor.rg * 25.5)

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec4 texture_blur(sampler2D tex, vec2 uv, float blur)
{
    float tau = 6.28318530718;

    float directions = 24.0;
    float quality = 3.0;

    vec2 radius = blur / TextureSize;
    vec4 color = texture(tex, uv);

    for (float d = 0.0; d < tau; d += tau / directions)
    {
        for (float i= 1.0 / quality; i <= 1.0; i += 1.0 / quality)
        {
            color += texture(tex, uv + vec2(cos(d), sin(d)) * radius * i);
        }
    }

    // Output to screen
    color /= quality * directions - 15.0;

    return color;
}

void texture_opaque_blur(inout vec4 out_color, sampler2D tex, vec2 uv, float blur)
{
    if (out_color.a < 1)
    {
        vec2 radius = blur / TextureSize;

        for (float x = -blur; x <= blur; x++)
        {
            for (float y = -blur; y <= blur; y++)
            {
                if (texture(Sampler0, texCoord0 + radius * vec2(x, y)).a >= 1)
                {
                    out_color.rgb = vec3(0, 0, 0);
                    out_color.a = 1.0;

                    return;
                }
            }
        }
    }
}

void main()
{
    vec4 color = texture(Sampler0, texCoord0);

    float blur = Blur.x;
    float opaque = Blur.y;

    if (blur > 0)
    {
        if (opaque > 0)
        {
            texture_opaque_blur(color, Sampler0, texCoord0, blur);
        }
        else
        {
            vec4 blurred_color = texture_blur(Sampler0, texCoord0, blur);

            if (color.a < 1)
            {
                blurred_color.rgb = vec3(0, 0, 0);
                blurred_color.a *= 0.5;
                color = blurred_color;
            }
        }
    }

    fragColor = color * vertexColor;
}
