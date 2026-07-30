package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.io.IOException;
import java.util.Optional;

public class BBSShaders
{
    private static ShaderInstance model;
    private static ShaderInstance multiLink;
    private static ShaderInstance subtitles;
    private static ShaderInstance selection;

    private static ShaderInstance pickerPreview;
    private static ShaderInstance replayOutline;
    private static ShaderInstance pickerBillboard;
    private static ShaderInstance pickerBillboardNoShading;
    private static ShaderInstance pickerParticles;
    private static ShaderInstance pickerModels;

    static
    {
        setup();
    }

    public static void setup()
    {
        if (model != null) model.close();
        if (subtitles != null) subtitles.close();
        if (selection != null) selection.close();

        if (pickerPreview != null) pickerPreview.close();
        if (replayOutline != null) replayOutline.close();
        if (pickerBillboard != null) pickerBillboard.close();
        if (pickerBillboardNoShading != null) pickerBillboardNoShading.close();
        if (pickerParticles != null) pickerParticles.close();
        if (pickerModels != null) pickerModels.close();

        try
        {
            ResourceProvider factory = new ProxyResourceFactory(Minecraft.getInstance().getResourceManager());

            model = new ShaderInstance(factory, "model", DefaultVertexFormat.NEW_ENTITY);
            multiLink = new ShaderInstance(factory, "multilink", DefaultVertexFormat.POSITION_TEX_COLOR);
            subtitles = new ShaderInstance(factory, "subtitles", DefaultVertexFormat.POSITION_TEX_COLOR);
            selection = new ShaderInstance(factory, "selection", DefaultVertexFormat.POSITION_TEX_COLOR);

            pickerPreview = new ShaderInstance(factory, "picker_preview", DefaultVertexFormat.POSITION_TEX_COLOR);
            replayOutline = new ShaderInstance(factory, "replay_outline", DefaultVertexFormat.POSITION_TEX_COLOR);
            pickerBillboard = new ShaderInstance(factory, "picker_billboard", DefaultVertexFormat.NEW_ENTITY);
            pickerBillboardNoShading = new ShaderInstance(factory, "picker_billboard_no_shading", DefaultVertexFormat.POSITION_TEX_LIGHTMAP_COLOR);
            pickerParticles = new ShaderInstance(factory, "picker_particles", DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
            pickerModels = new ShaderInstance(factory, "picker_models", DefaultVertexFormat.NEW_ENTITY);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static ShaderInstance getModel()
    {
        return model;
    }

    public static ShaderInstance getMultilinkProgram()
    {
        return multiLink;
    }

    public static ShaderInstance getSubtitlesProgram()
    {
        return subtitles;
    }

    public static ShaderInstance getSelectionProgram()
    {
        return selection;
    }

    public static ShaderInstance getPickerPreviewProgram()
    {
        return pickerPreview;
    }

    /** Umriss um das bearbeitete Replay — liest die Kante aus demselben Stencil-Bild. */
    public static ShaderInstance getReplayOutlineProgram()
    {
        return replayOutline;
    }

    public static ShaderInstance getPickerBillboardProgram()
    {
        return pickerBillboard;
    }

    public static ShaderInstance getPickerBillboardNoShadingProgram()
    {
        return pickerBillboardNoShading;
    }

    public static ShaderInstance getPickerParticlesProgram()
    {
        return pickerParticles;
    }

    public static ShaderInstance getPickerModelsProgram()
    {
        return pickerModels;
    }

    private static class ProxyResourceFactory implements ResourceProvider
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
                return this.manager.getResource(Identifier.fromNamespaceAndPath(BBSMod.MOD_ID, id.getPath()));
            }

            return this.manager.getResource(id);
        }
    }
}
