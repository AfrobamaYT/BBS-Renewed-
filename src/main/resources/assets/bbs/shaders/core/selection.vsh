#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;

    /* 26.2: ModelViewMat und ProjMat kommen aus den Standard-Bloecken, nicht mehr aus
     * einzeln deklarierten Uniforms. Die Farbe wird durchgereicht, weil sie hier keine
     * Farbe ist, sondern die beiden Werte traegt, die es frueher als Uniforms gab --
     * siehe selection.fsh. */
    vertexColor = Color;
}
