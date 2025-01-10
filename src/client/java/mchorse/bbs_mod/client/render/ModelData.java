package mchorse.bbs_mod.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public record  ModelData(float[] vertices, float[] normals, float[] tangents, float[] texCoords, int[] indices, int colorTexture, int normalTexture, int specularTexture)
{
    public static ModelData createCube() {
        float[] vertices = {
                // Front face
                -0.5f, -0.5f, 0.5f,  // Bottom-left
                0.5f, -0.5f, 0.5f,  // Bottom-right
                0.5f, 0.5f, 0.5f,  // Top-right
                -0.5f, 0.5f, 0.5f,  // Top-left

                // Back face
                -0.5f, -0.5f, -0.5f,  // Bottom-left
                0.5f, -0.5f, -0.5f,  // Bottom-right
                0.5f, 0.5f, -0.5f,  // Top-right
                -0.5f, 0.5f, -0.5f,  // Top-left

                // Left face
                -0.5f, -0.5f, -0.5f,  // Bottom-left
                -0.5f, -0.5f, 0.5f,  // Bottom-right
                -0.5f, 0.5f, 0.5f,  // Top-right
                -0.5f, 0.5f, -0.5f,  // Top-left

                // Right face
                0.5f, -0.5f, -0.5f,  // Bottom-left
                0.5f, -0.5f, 0.5f,  // Bottom-right
                0.5f, 0.5f, 0.5f,  // Top-right
                0.5f, 0.5f, -0.5f,  // Top-left

                // Top face
                -0.5f, 0.5f, 0.5f,  // Bottom-left
                0.5f, 0.5f, 0.5f,  // Bottom-right
                0.5f, 0.5f, -0.5f,  // Top-right
                -0.5f, 0.5f, -0.5f,  // Top-left

                // Bottom face
                -0.5f, -0.5f, 0.5f,  // Bottom-left
                0.5f, -0.5f, 0.5f,  // Bottom-right
                0.5f, -0.5f, -0.5f,  // Top-right
                -0.5f, -0.5f, -0.5f   // Top-left
        };

        // Нормали (x, y, z) для каждой вершины
        float[] normals = {
                // Front face
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,

                // Back face
                0.0f, 0.0f, -1.0f,
                0.0f, 0.0f, -1.0f,
                0.0f, 0.0f, -1.0f,
                0.0f, 0.0f, -1.0f,

                // Left face
                -1.0f, 0.0f, 0.0f,
                -1.0f, 0.0f, 0.0f,
                -1.0f, 0.0f, 0.0f,
                -1.0f, 0.0f, 0.0f,

                // Right face
                1.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,

                // Top face
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f,

                // Bottom face
                0.0f, -1.0f, 0.0f,
                0.0f, -1.0f, 0.0f,
                0.0f, -1.0f, 0.0f,
                0.0f, -1.0f, 0.0f
        };

        float[] tangents = {
                // Front face
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f, 1f,
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f,

                // Back face
                -1.0f, 0.0f, 0.0f, 1f,
                -1.0f, 0.0f, 0.0f, 1f,
                -1.0f, 0.0f, 0.0f, 1f,
                -1.0f, 0.0f, 0.0f, 1f,

                // Left face
                0.0f, 0.0f, -1.0f, 1f,
                0.0f, 0.0f, -1.0f, 1f,
                0.0f, 0.0f, -1.0f, 1f,
                0.0f, 0.0f, -1.0f, 1f,

                // Right face
                0.0f, 0.0f, 1.0f, 1f,
                0.0f, 0.0f, 1.0f, 1f,
                0.0f, 0.0f, 1.0f, 1f,
                0.0f, 0.0f, 1.0f, 1f,

                // Top face
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f,

                // Bottom face
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f,
                1.0f, 0.0f, 0.0f, 1f
        };

        // Текстурные координаты (u, v) для каждой вершины
        float[] texCoords = {
                // Front face
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,

                // Back face
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,

                // Left face
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,

                // Right face
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,

                // Top face
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f,

                // Bottom face
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f,
                0.0f, 1.0f
        };

        // Индексы (для отрисовки граней через треугольники)
        int[] indices = {
                // Front face
                0, 1, 2,
                2, 3, 0,

                // Back face
                4, 5, 6,
                6, 7, 4,

                // Left face
                8, 9, 10,
                10, 11, 8,

                // Right face
                12, 13, 14,
                14, 15, 12,

                // Top face
                16, 17, 18,
                18, 19, 16,

                // Bottom face
                20, 21, 22,
                22, 23, 20
        };

        var colorTexture = MinecraftClient.getInstance().getTextureManager().getTexture(new Identifier("minecraft:textures/block/stone.png")).getGlId();

        return new ModelData(vertices, normals, tangents, texCoords, indices, colorTexture, 0, 0);
    }
}
