#version 450

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/*location 0*/ in vec3 Position;
/*location 1*/ in vec2 UV0;
/*location 2*/ in vec4 Color;

/*location 0*/ out vec2 texCoord0;
/*location 1*/ out float vertexAlpha;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexAlpha = Color.a;
}
