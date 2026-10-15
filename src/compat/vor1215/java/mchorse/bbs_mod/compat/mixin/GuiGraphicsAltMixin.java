package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.compat.GuiGraphicsAlt;
import mchorse.bbs_mod.compat.gpu.Pose2D;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsAltMixin implements GuiGraphicsAlt
{
    @Shadow @Final private PoseStack pose;

    @Unique
    private Pose2D bbs$pose2d;

    @Override
    public Matrix3x2fStack bbsPose()
    {
        if (this.bbs$pose2d == null)
        {
            this.bbs$pose2d = new Pose2D(this.pose);
        }

        return this.bbs$pose2d.einlesen();
    }
}
