#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main()
{
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;

    /* == 0.0, nicht < 0.1 wie in model.fsh: das war die Schwelle von Vanillas
     * position_tex_color, ueber das dieser Weg bis 1.21.1 lief. Eine hoehere Schwelle wuerde
     * halbdurchsichtige Texel wegschneiden, die vorher gezeichnet wurden. */
    if (color.a == 0.0)
    {
        discard;
    }

    /* ⚠️ Weder Lichtkarte noch Overlay noch Nebel -- alle drei fehlten auch vorher, weil
     * position_tex_color sie nicht kannte. Wer sie hier ergaenzt, aendert das Aussehen JEDER
     * Form mit abgeschalteter Schattierung: "shading aus" heisst in BBS flach und voll hell,
     * unabhaengig vom Weltlicht. Das ist das Feature, nicht ein vergessener Fall.
     *
     * Darum fehlt hier auch der ModelInfo-Block mit Modus.x (Zwei-Pass-Durchsichtigkeit):
     * die zugehoerige Pipeline steht bewusst nicht in ModelVAOPipeline.istModellPipeline,
     * genau wie position_tex_color das Uniform "PassMode" nie hatte. Der Fall lief immer
     * einpassig. */
    fragColor = color;
}
