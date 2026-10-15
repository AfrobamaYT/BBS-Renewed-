package mchorse.bbs_mod.compat.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BBS draws with Vulkan only from Minecraft 26.3 on (user decision 2026-10-12): before that
 * it is built for OpenGL, and its native Vulkan paths crash on the first world frame. When
 * loading finishes, before the title screen, quick play or an automatic world join, the
 * graphics setting goes back to OpenGL and the game asks for a restart, so nobody reaches a
 * world in that state.
 */
@Mixin(Minecraft.class)
public abstract class VulkanErstAb263Mixin
{
    @Inject(method = "onGameLoadFinished", at = @At("HEAD"), cancellable = true)
    private void bbs$nurOpenGl(net.minecraft.client.GameLoadCookie cookie, CallbackInfo info)
    {
        if (!mchorse.bbs_mod.graphics.GpuBackend.isVulkan())
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        mc.options.preferredGraphicsBackend().set(PreferredGraphicsApi.OPENGL);
        mc.options.save();
        mc.gui.setScreen(new AlertScreen(mc::stop,
            Component.literal("BBS needs OpenGL on this Minecraft version"),
            Component.literal("BBS supports Vulkan from Minecraft 26.3 on. The graphics backend has been set back to OpenGL - restart the game to continue."),
            Component.literal("Quit game"), false));
        info.cancel();
    }
}
