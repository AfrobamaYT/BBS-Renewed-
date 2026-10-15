package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(VulkanRenderPass.class)
public abstract class VulkanDescriptorPushMixin
{
    @WrapOperation(method = "pushDescriptors", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/vulkan/KHRPushDescriptor;vkCmdPushDescriptorSetKHR(Lorg/lwjgl/vulkan/VkCommandBuffer;IJILorg/lwjgl/vulkan/VkWriteDescriptorSet$Buffer;)V"))
    private void bbs$pushNonemptyDescriptors(VkCommandBuffer command, int bindPoint,
        long layout, int set, VkWriteDescriptorSet.Buffer writes, Operation<Void> original) throws Throwable
    {
        MixinBruecke.rufePflicht("NativeBackendLogik", "pushNonemptyDescriptors", command, bindPoint, layout, set, writes, original);
    }
}
