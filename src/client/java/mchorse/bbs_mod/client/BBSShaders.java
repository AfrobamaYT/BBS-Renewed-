package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Optional;

public class BBSShaders
{
    private static ShaderProgram extrudedProgram;

    static
    {
        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            extrudedProgram = new ShaderProgram(factory, "extruded", VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static ShaderProgram getExtrudedProgram()
    {
        return extrudedProgram;
    }

    private static class ProxyResourceFactory implements ResourceFactory
    {
        private ResourceManager manager;

        public ProxyResourceFactory(ResourceManager manager)
        {
            this.manager = manager;
        }

        @Override
        public Optional<Resource> getResource(Identifier id)
        {
            if (id.getPath().contains("/core/"))
            {
                return this.manager.getResource(new Identifier(BBSMod.MOD_ID, id.getPath()));
            }

            return this.manager.getResource(id);
        }
    }
}
