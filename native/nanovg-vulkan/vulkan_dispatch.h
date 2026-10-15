/* BBS Vulkan dispatch: SDK PFN types, supplied by Minecraft's existing loader.
 * Device addresses are retained per bridge; no second Vulkan/MoltenVK library.
 */
#ifndef BBS_VULKAN_DISPATCH_H
#define BBS_VULKAN_DISPATCH_H

#if defined(_MSC_VER)
#define BBS_THREAD_LOCAL __declspec(thread)
#else
#define BBS_THREAD_LOCAL _Thread_local
#endif

typedef struct BBSVulkanDispatch
{
    PFN_vkAllocateCommandBuffers AllocateCommandBuffers;
    PFN_vkAllocateDescriptorSets AllocateDescriptorSets;
    PFN_vkAllocateMemory AllocateMemory;
    PFN_vkBeginCommandBuffer BeginCommandBuffer;
    PFN_vkBindBufferMemory BindBufferMemory;
    PFN_vkBindImageMemory BindImageMemory;
    PFN_vkCmdBeginRenderPass CmdBeginRenderPass;
    PFN_vkCmdBindDescriptorSets CmdBindDescriptorSets;
    PFN_vkCmdBindPipeline CmdBindPipeline;
    PFN_vkCmdBindVertexBuffers CmdBindVertexBuffers;
    PFN_vkCmdDraw CmdDraw;
    PFN_vkCmdEndRenderPass CmdEndRenderPass;
    PFN_vkCmdPipelineBarrier CmdPipelineBarrier;
    PFN_vkCmdPushConstants CmdPushConstants;
    PFN_vkCmdSetScissor CmdSetScissor;
    PFN_vkCmdSetViewport CmdSetViewport;
    PFN_vkCreateBuffer CreateBuffer;
    PFN_vkCreateCommandPool CreateCommandPool;
    PFN_vkCreateDescriptorPool CreateDescriptorPool;
    PFN_vkCreateDescriptorSetLayout CreateDescriptorSetLayout;
    PFN_vkCreateFramebuffer CreateFramebuffer;
    PFN_vkCreateGraphicsPipelines CreateGraphicsPipelines;
    PFN_vkCreateImage CreateImage;
    PFN_vkCreateImageView CreateImageView;
    PFN_vkCreatePipelineLayout CreatePipelineLayout;
    PFN_vkCreateRenderPass CreateRenderPass;
    PFN_vkCreateSampler CreateSampler;
    PFN_vkCreateShaderModule CreateShaderModule;
    PFN_vkDestroyBuffer DestroyBuffer;
    PFN_vkDestroyCommandPool DestroyCommandPool;
    PFN_vkDestroyDescriptorPool DestroyDescriptorPool;
    PFN_vkDestroyDescriptorSetLayout DestroyDescriptorSetLayout;
    PFN_vkDestroyFramebuffer DestroyFramebuffer;
    PFN_vkDestroyImage DestroyImage;
    PFN_vkDestroyImageView DestroyImageView;
    PFN_vkDestroyPipeline DestroyPipeline;
    PFN_vkDestroyPipelineLayout DestroyPipelineLayout;
    PFN_vkDestroyRenderPass DestroyRenderPass;
    PFN_vkDestroySampler DestroySampler;
    PFN_vkDestroyShaderModule DestroyShaderModule;
    PFN_vkEndCommandBuffer EndCommandBuffer;
    PFN_vkFlushMappedMemoryRanges FlushMappedMemoryRanges;
    PFN_vkFreeCommandBuffers FreeCommandBuffers;
    PFN_vkFreeMemory FreeMemory;
    PFN_vkGetBufferMemoryRequirements GetBufferMemoryRequirements;
    PFN_vkGetDeviceProcAddr GetDeviceProcAddr;
    PFN_vkGetImageMemoryRequirements GetImageMemoryRequirements;
    PFN_vkGetImageSubresourceLayout GetImageSubresourceLayout;
    PFN_vkGetPhysicalDeviceFormatProperties GetPhysicalDeviceFormatProperties;
    PFN_vkGetPhysicalDeviceMemoryProperties GetPhysicalDeviceMemoryProperties;
    PFN_vkGetPhysicalDeviceProperties GetPhysicalDeviceProperties;
    PFN_vkMapMemory MapMemory;
    PFN_vkQueueSubmit QueueSubmit;
    PFN_vkQueueWaitIdle QueueWaitIdle;
    PFN_vkResetCommandBuffer ResetCommandBuffer;
    PFN_vkUnmapMemory UnmapMemory;
    PFN_vkUpdateDescriptorSets UpdateDescriptorSets;
} BBSVulkanDispatch;
static BBS_THREAD_LOCAL BBSVulkanDispatch *bbs_dispatch;

static const char *bbs_loadDispatch(BBSVulkanDispatch *table, VkInstance instance, VkDevice device,
    PFN_vkGetInstanceProcAddr instanceProc, PFN_vkGetDeviceProcAddr deviceProc)
{
    if (instanceProc == NULL) return "vkGetInstanceProcAddr";
    if (deviceProc == NULL) return "vkGetDeviceProcAddr";
    table->GetDeviceProcAddr = deviceProc;
    table->AllocateCommandBuffers = (PFN_vkAllocateCommandBuffers)deviceProc(device, "vkAllocateCommandBuffers");
    if (table->AllocateCommandBuffers == NULL) return "vkAllocateCommandBuffers";
    table->AllocateDescriptorSets = (PFN_vkAllocateDescriptorSets)deviceProc(device, "vkAllocateDescriptorSets");
    if (table->AllocateDescriptorSets == NULL) return "vkAllocateDescriptorSets";
    table->AllocateMemory = (PFN_vkAllocateMemory)deviceProc(device, "vkAllocateMemory");
    if (table->AllocateMemory == NULL) return "vkAllocateMemory";
    table->BeginCommandBuffer = (PFN_vkBeginCommandBuffer)deviceProc(device, "vkBeginCommandBuffer");
    if (table->BeginCommandBuffer == NULL) return "vkBeginCommandBuffer";
    table->BindBufferMemory = (PFN_vkBindBufferMemory)deviceProc(device, "vkBindBufferMemory");
    if (table->BindBufferMemory == NULL) return "vkBindBufferMemory";
    table->BindImageMemory = (PFN_vkBindImageMemory)deviceProc(device, "vkBindImageMemory");
    if (table->BindImageMemory == NULL) return "vkBindImageMemory";
    table->CmdBeginRenderPass = (PFN_vkCmdBeginRenderPass)deviceProc(device, "vkCmdBeginRenderPass");
    if (table->CmdBeginRenderPass == NULL) return "vkCmdBeginRenderPass";
    table->CmdBindDescriptorSets = (PFN_vkCmdBindDescriptorSets)deviceProc(device, "vkCmdBindDescriptorSets");
    if (table->CmdBindDescriptorSets == NULL) return "vkCmdBindDescriptorSets";
    table->CmdBindPipeline = (PFN_vkCmdBindPipeline)deviceProc(device, "vkCmdBindPipeline");
    if (table->CmdBindPipeline == NULL) return "vkCmdBindPipeline";
    table->CmdBindVertexBuffers = (PFN_vkCmdBindVertexBuffers)deviceProc(device, "vkCmdBindVertexBuffers");
    if (table->CmdBindVertexBuffers == NULL) return "vkCmdBindVertexBuffers";
    table->CmdDraw = (PFN_vkCmdDraw)deviceProc(device, "vkCmdDraw");
    if (table->CmdDraw == NULL) return "vkCmdDraw";
    table->CmdEndRenderPass = (PFN_vkCmdEndRenderPass)deviceProc(device, "vkCmdEndRenderPass");
    if (table->CmdEndRenderPass == NULL) return "vkCmdEndRenderPass";
    table->CmdPipelineBarrier = (PFN_vkCmdPipelineBarrier)deviceProc(device, "vkCmdPipelineBarrier");
    if (table->CmdPipelineBarrier == NULL) return "vkCmdPipelineBarrier";
    table->CmdPushConstants = (PFN_vkCmdPushConstants)deviceProc(device, "vkCmdPushConstants");
    if (table->CmdPushConstants == NULL) return "vkCmdPushConstants";
    table->CmdSetScissor = (PFN_vkCmdSetScissor)deviceProc(device, "vkCmdSetScissor");
    if (table->CmdSetScissor == NULL) return "vkCmdSetScissor";
    table->CmdSetViewport = (PFN_vkCmdSetViewport)deviceProc(device, "vkCmdSetViewport");
    if (table->CmdSetViewport == NULL) return "vkCmdSetViewport";
    table->CreateBuffer = (PFN_vkCreateBuffer)deviceProc(device, "vkCreateBuffer");
    if (table->CreateBuffer == NULL) return "vkCreateBuffer";
    table->CreateCommandPool = (PFN_vkCreateCommandPool)deviceProc(device, "vkCreateCommandPool");
    if (table->CreateCommandPool == NULL) return "vkCreateCommandPool";
    table->CreateDescriptorPool = (PFN_vkCreateDescriptorPool)deviceProc(device, "vkCreateDescriptorPool");
    if (table->CreateDescriptorPool == NULL) return "vkCreateDescriptorPool";
    table->CreateDescriptorSetLayout = (PFN_vkCreateDescriptorSetLayout)deviceProc(device, "vkCreateDescriptorSetLayout");
    if (table->CreateDescriptorSetLayout == NULL) return "vkCreateDescriptorSetLayout";
    table->CreateFramebuffer = (PFN_vkCreateFramebuffer)deviceProc(device, "vkCreateFramebuffer");
    if (table->CreateFramebuffer == NULL) return "vkCreateFramebuffer";
    table->CreateGraphicsPipelines = (PFN_vkCreateGraphicsPipelines)deviceProc(device, "vkCreateGraphicsPipelines");
    if (table->CreateGraphicsPipelines == NULL) return "vkCreateGraphicsPipelines";
    table->CreateImage = (PFN_vkCreateImage)deviceProc(device, "vkCreateImage");
    if (table->CreateImage == NULL) return "vkCreateImage";
    table->CreateImageView = (PFN_vkCreateImageView)deviceProc(device, "vkCreateImageView");
    if (table->CreateImageView == NULL) return "vkCreateImageView";
    table->CreatePipelineLayout = (PFN_vkCreatePipelineLayout)deviceProc(device, "vkCreatePipelineLayout");
    if (table->CreatePipelineLayout == NULL) return "vkCreatePipelineLayout";
    table->CreateRenderPass = (PFN_vkCreateRenderPass)deviceProc(device, "vkCreateRenderPass");
    if (table->CreateRenderPass == NULL) return "vkCreateRenderPass";
    table->CreateSampler = (PFN_vkCreateSampler)deviceProc(device, "vkCreateSampler");
    if (table->CreateSampler == NULL) return "vkCreateSampler";
    table->CreateShaderModule = (PFN_vkCreateShaderModule)deviceProc(device, "vkCreateShaderModule");
    if (table->CreateShaderModule == NULL) return "vkCreateShaderModule";
    table->DestroyBuffer = (PFN_vkDestroyBuffer)deviceProc(device, "vkDestroyBuffer");
    if (table->DestroyBuffer == NULL) return "vkDestroyBuffer";
    table->DestroyCommandPool = (PFN_vkDestroyCommandPool)deviceProc(device, "vkDestroyCommandPool");
    if (table->DestroyCommandPool == NULL) return "vkDestroyCommandPool";
    table->DestroyDescriptorPool = (PFN_vkDestroyDescriptorPool)deviceProc(device, "vkDestroyDescriptorPool");
    if (table->DestroyDescriptorPool == NULL) return "vkDestroyDescriptorPool";
    table->DestroyDescriptorSetLayout = (PFN_vkDestroyDescriptorSetLayout)deviceProc(device, "vkDestroyDescriptorSetLayout");
    if (table->DestroyDescriptorSetLayout == NULL) return "vkDestroyDescriptorSetLayout";
    table->DestroyFramebuffer = (PFN_vkDestroyFramebuffer)deviceProc(device, "vkDestroyFramebuffer");
    if (table->DestroyFramebuffer == NULL) return "vkDestroyFramebuffer";
    table->DestroyImage = (PFN_vkDestroyImage)deviceProc(device, "vkDestroyImage");
    if (table->DestroyImage == NULL) return "vkDestroyImage";
    table->DestroyImageView = (PFN_vkDestroyImageView)deviceProc(device, "vkDestroyImageView");
    if (table->DestroyImageView == NULL) return "vkDestroyImageView";
    table->DestroyPipeline = (PFN_vkDestroyPipeline)deviceProc(device, "vkDestroyPipeline");
    if (table->DestroyPipeline == NULL) return "vkDestroyPipeline";
    table->DestroyPipelineLayout = (PFN_vkDestroyPipelineLayout)deviceProc(device, "vkDestroyPipelineLayout");
    if (table->DestroyPipelineLayout == NULL) return "vkDestroyPipelineLayout";
    table->DestroyRenderPass = (PFN_vkDestroyRenderPass)deviceProc(device, "vkDestroyRenderPass");
    if (table->DestroyRenderPass == NULL) return "vkDestroyRenderPass";
    table->DestroySampler = (PFN_vkDestroySampler)deviceProc(device, "vkDestroySampler");
    if (table->DestroySampler == NULL) return "vkDestroySampler";
    table->DestroyShaderModule = (PFN_vkDestroyShaderModule)deviceProc(device, "vkDestroyShaderModule");
    if (table->DestroyShaderModule == NULL) return "vkDestroyShaderModule";
    table->EndCommandBuffer = (PFN_vkEndCommandBuffer)deviceProc(device, "vkEndCommandBuffer");
    if (table->EndCommandBuffer == NULL) return "vkEndCommandBuffer";
    table->FlushMappedMemoryRanges = (PFN_vkFlushMappedMemoryRanges)deviceProc(device, "vkFlushMappedMemoryRanges");
    if (table->FlushMappedMemoryRanges == NULL) return "vkFlushMappedMemoryRanges";
    table->FreeCommandBuffers = (PFN_vkFreeCommandBuffers)deviceProc(device, "vkFreeCommandBuffers");
    if (table->FreeCommandBuffers == NULL) return "vkFreeCommandBuffers";
    table->FreeMemory = (PFN_vkFreeMemory)deviceProc(device, "vkFreeMemory");
    if (table->FreeMemory == NULL) return "vkFreeMemory";
    table->GetBufferMemoryRequirements = (PFN_vkGetBufferMemoryRequirements)deviceProc(device, "vkGetBufferMemoryRequirements");
    if (table->GetBufferMemoryRequirements == NULL) return "vkGetBufferMemoryRequirements";
    table->GetImageMemoryRequirements = (PFN_vkGetImageMemoryRequirements)deviceProc(device, "vkGetImageMemoryRequirements");
    if (table->GetImageMemoryRequirements == NULL) return "vkGetImageMemoryRequirements";
    table->GetImageSubresourceLayout = (PFN_vkGetImageSubresourceLayout)deviceProc(device, "vkGetImageSubresourceLayout");
    if (table->GetImageSubresourceLayout == NULL) return "vkGetImageSubresourceLayout";
    table->GetPhysicalDeviceFormatProperties = (PFN_vkGetPhysicalDeviceFormatProperties)instanceProc(instance, "vkGetPhysicalDeviceFormatProperties");
    if (table->GetPhysicalDeviceFormatProperties == NULL) return "vkGetPhysicalDeviceFormatProperties";
    table->GetPhysicalDeviceMemoryProperties = (PFN_vkGetPhysicalDeviceMemoryProperties)instanceProc(instance, "vkGetPhysicalDeviceMemoryProperties");
    if (table->GetPhysicalDeviceMemoryProperties == NULL) return "vkGetPhysicalDeviceMemoryProperties";
    table->GetPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)instanceProc(instance, "vkGetPhysicalDeviceProperties");
    if (table->GetPhysicalDeviceProperties == NULL) return "vkGetPhysicalDeviceProperties";
    table->MapMemory = (PFN_vkMapMemory)deviceProc(device, "vkMapMemory");
    if (table->MapMemory == NULL) return "vkMapMemory";
    table->QueueSubmit = (PFN_vkQueueSubmit)deviceProc(device, "vkQueueSubmit");
    if (table->QueueSubmit == NULL) return "vkQueueSubmit";
    table->QueueWaitIdle = (PFN_vkQueueWaitIdle)deviceProc(device, "vkQueueWaitIdle");
    if (table->QueueWaitIdle == NULL) return "vkQueueWaitIdle";
    table->ResetCommandBuffer = (PFN_vkResetCommandBuffer)deviceProc(device, "vkResetCommandBuffer");
    if (table->ResetCommandBuffer == NULL) return "vkResetCommandBuffer";
    table->UnmapMemory = (PFN_vkUnmapMemory)deviceProc(device, "vkUnmapMemory");
    if (table->UnmapMemory == NULL) return "vkUnmapMemory";
    table->UpdateDescriptorSets = (PFN_vkUpdateDescriptorSets)deviceProc(device, "vkUpdateDescriptorSets");
    if (table->UpdateDescriptorSets == NULL) return "vkUpdateDescriptorSets";
    return NULL;
}

#define vkAllocateCommandBuffers bbs_dispatch->AllocateCommandBuffers
#define vkAllocateDescriptorSets bbs_dispatch->AllocateDescriptorSets
#define vkAllocateMemory bbs_dispatch->AllocateMemory
#define vkBeginCommandBuffer bbs_dispatch->BeginCommandBuffer
#define vkBindBufferMemory bbs_dispatch->BindBufferMemory
#define vkBindImageMemory bbs_dispatch->BindImageMemory
#define vkCmdBeginRenderPass bbs_dispatch->CmdBeginRenderPass
#define vkCmdBindDescriptorSets bbs_dispatch->CmdBindDescriptorSets
#define vkCmdBindPipeline bbs_dispatch->CmdBindPipeline
#define vkCmdBindVertexBuffers bbs_dispatch->CmdBindVertexBuffers
#define vkCmdDraw bbs_dispatch->CmdDraw
#define vkCmdEndRenderPass bbs_dispatch->CmdEndRenderPass
#define vkCmdPipelineBarrier bbs_dispatch->CmdPipelineBarrier
#define vkCmdPushConstants bbs_dispatch->CmdPushConstants
#define vkCmdSetScissor bbs_dispatch->CmdSetScissor
#define vkCmdSetViewport bbs_dispatch->CmdSetViewport
#define vkCreateBuffer bbs_dispatch->CreateBuffer
#define vkCreateCommandPool bbs_dispatch->CreateCommandPool
#define vkCreateDescriptorPool bbs_dispatch->CreateDescriptorPool
#define vkCreateDescriptorSetLayout bbs_dispatch->CreateDescriptorSetLayout
#define vkCreateFramebuffer bbs_dispatch->CreateFramebuffer
#define vkCreateGraphicsPipelines bbs_dispatch->CreateGraphicsPipelines
#define vkCreateImage bbs_dispatch->CreateImage
#define vkCreateImageView bbs_dispatch->CreateImageView
#define vkCreatePipelineLayout bbs_dispatch->CreatePipelineLayout
#define vkCreateRenderPass bbs_dispatch->CreateRenderPass
#define vkCreateSampler bbs_dispatch->CreateSampler
#define vkCreateShaderModule bbs_dispatch->CreateShaderModule
#define vkDestroyBuffer bbs_dispatch->DestroyBuffer
#define vkDestroyCommandPool bbs_dispatch->DestroyCommandPool
#define vkDestroyDescriptorPool bbs_dispatch->DestroyDescriptorPool
#define vkDestroyDescriptorSetLayout bbs_dispatch->DestroyDescriptorSetLayout
#define vkDestroyFramebuffer bbs_dispatch->DestroyFramebuffer
#define vkDestroyImage bbs_dispatch->DestroyImage
#define vkDestroyImageView bbs_dispatch->DestroyImageView
#define vkDestroyPipeline bbs_dispatch->DestroyPipeline
#define vkDestroyPipelineLayout bbs_dispatch->DestroyPipelineLayout
#define vkDestroyRenderPass bbs_dispatch->DestroyRenderPass
#define vkDestroySampler bbs_dispatch->DestroySampler
#define vkDestroyShaderModule bbs_dispatch->DestroyShaderModule
#define vkEndCommandBuffer bbs_dispatch->EndCommandBuffer
#define vkFlushMappedMemoryRanges bbs_dispatch->FlushMappedMemoryRanges
#define vkFreeCommandBuffers bbs_dispatch->FreeCommandBuffers
#define vkFreeMemory bbs_dispatch->FreeMemory
#define vkGetBufferMemoryRequirements bbs_dispatch->GetBufferMemoryRequirements
#define vkGetDeviceProcAddr bbs_dispatch->GetDeviceProcAddr
#define vkGetImageMemoryRequirements bbs_dispatch->GetImageMemoryRequirements
#define vkGetImageSubresourceLayout bbs_dispatch->GetImageSubresourceLayout
#define vkGetPhysicalDeviceFormatProperties bbs_dispatch->GetPhysicalDeviceFormatProperties
#define vkGetPhysicalDeviceMemoryProperties bbs_dispatch->GetPhysicalDeviceMemoryProperties
#define vkGetPhysicalDeviceProperties bbs_dispatch->GetPhysicalDeviceProperties
#define vkMapMemory bbs_dispatch->MapMemory
#define vkQueueSubmit bbs_dispatch->QueueSubmit
#define vkQueueWaitIdle bbs_dispatch->QueueWaitIdle
#define vkResetCommandBuffer bbs_dispatch->ResetCommandBuffer
#define vkUnmapMemory bbs_dispatch->UnmapMemory
#define vkUpdateDescriptorSets bbs_dispatch->UpdateDescriptorSets

#endif
