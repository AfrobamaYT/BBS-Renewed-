package mchorse.bbs_mod.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vor 1.21.5: 1.21.4 fordert einen OpenGL-3.2-Core-Kontext an, die eigene GL-Schicht braucht
 * 3.3 (glVertexAttribDivisor, Sampler-Objekte). Gemessen: GL_VERSION "3.2.0 NVIDIA",
 * GLCapabilities.OpenGL33 = false, Absturz in glVertexAttribDivisor. Darum 3.3 statt 3.2.
 */
@Mixin(Window.class)
public abstract class WindowKontextMixin
{
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V"))
    private void bbs$kontext33(int hinweis, int wert, Operation<Void> original)
    {
        original.call(hinweis, hinweis == GLFW.GLFW_CONTEXT_VERSION_MINOR && wert < 3 ? 3 : wert);
    }
}
