package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;

/* Vitrail laeuft auf Vulkan, das Minecraft erst ab 26.1 hat (vor 26.1 ist die Integration
 * ausgelassen, build.gradle nurAb261). Diese Fassade beantwortet die Fragen des gemeinsamen Codes
 * so, wie es die echte ohne geladenes Vitrail tut ("nicht aktiv"); Handlungen, die nur bei
 * aktivem Vitrail vorkommen, scheitern laut. */
public final class VitrailImmediateModels
{
    private VitrailImmediateModels() {}
    /** Without Vitrail the shared version returns the entity format. */
    public static VertexFormat format() { return DefaultVertexFormat.NEW_ENTITY; }
    public static boolean retain(BufferBuilder builder, PoseStack stack, Texture texture, boolean culling, StencilMap picking) { return false; }
}
