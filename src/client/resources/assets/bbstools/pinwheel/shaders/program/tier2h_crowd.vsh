#version 330 core
/*location 0*/ in vec3 Position;
/*location 1*/ in vec2 UV0;
/*location 2*/ in vec3 Normal;
/*location 3*/ in uint BoneIndex;
/*location 4*/ in vec4 World0;
/*location 5*/ in vec4 World1;
/*location 6*/ in vec4 World2;
/*location 7*/ in vec4 World3;
/*location 8*/ in uint PackedLight;
/*location 9*/ in uint PackedColor;
/*location 10*/ in uint PaletteRow;
uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform sampler2D BoneTex;
uniform int UseVAT;
out vec2 texCoord0;
out vec4 vertexColor;
void main() {
    int x = int(BoneIndex) * 4;
    int y = UseVAT == 0 ? gl_InstanceID : int(PaletteRow);
    mat4 bone = mat4(texelFetch(BoneTex, ivec2(x, y), 0),
                     texelFetch(BoneTex, ivec2(x + 1, y), 0),
                     texelFetch(BoneTex, ivec2(x + 2, y), 0),
                     texelFetch(BoneTex, ivec2(x + 3, y), 0));
    gl_Position = ProjMat * ModelViewMat * mat4(World0, World1, World2, World3)
        * bone * vec4(Position, 1.0);
    vertexColor = vec4(float((PackedColor >> 16u) & 255u), float((PackedColor >> 8u) & 255u),
        float(PackedColor & 255u), float(PackedColor >> 24u)) / 255.0;
    texCoord0 = UV0;
}
