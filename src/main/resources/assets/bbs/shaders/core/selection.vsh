#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

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
