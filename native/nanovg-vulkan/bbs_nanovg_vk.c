/*
 * BBS NanoVG Vulkan adapter, 2026-10-10.
 * The renderer is SubiyaCryolite/nanovg_vulkan, Zlib; see upstream/LICENSE.txt.
 * The five matrix functions are copied from LWJGL's NanoVG core and verified
 * byte-for-byte against LWJGL 3.4.3, commit 30fac9b95f99cda97312232be25ba55297bf9951, under
 * Mikko Mononen's Zlib notice. The context and every drawing/font operation
 * remain in LWJGL, including its allocator.
 */
#include <jni.h>
#define VK_NO_PROTOTYPES
#include <vulkan/vulkan.h>
#include <setjmp.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "upstream/nanovg.h"
#include "upstream/nanovg_vk.h"
#include "vulkan_dispatch.h"

#define BBS_NANOVG_ABI_VERSION 2

#if defined(__GNUC__)
#define BBS_NOINLINE __attribute__((noinline))
#else
#define BBS_NOINLINE
#endif
typedef struct BBSBridge BBSBridge;
typedef struct BBSRecording BBSRecording;
static BBS_THREAD_LOCAL BBSBridge *bbs_creating;
static BBS_THREAD_LOCAL jmp_buf *bbs_failure;
static BBS_THREAD_LOCAL JNIEnv *bbs_env;
static BBSBridge *bbs_bridges;
static void bbs_check(VkResult result, const char *operation)
{
    if (result == VK_SUCCESS) return;
    char text[512];
    snprintf(text, sizeof(text), "NanoVG Vulkan: %s returned VkResult %d", operation, result);
    if (bbs_env != NULL && !(*bbs_env)->ExceptionCheck(bbs_env))
    {
        jclass type = (*bbs_env)->FindClass(bbs_env, "java/lang/IllegalStateException");
        if (type != NULL) (*bbs_env)->ThrowNew(bbs_env, type, text);
    }
    if (bbs_failure != NULL) longjmp(*bbs_failure, 1);
}
#define NVGVK_CHECK_RESULT(call) bbs_check((call), #call)

static void bbs_nvgTransformIdentity(float* t)
{
	t[0] = 1.0f; t[1] = 0.0f;
	t[2] = 0.0f; t[3] = 1.0f;
	t[4] = 0.0f; t[5] = 0.0f;
}

static void bbs_nvgTransformTranslate(float* t, float tx, float ty)
{
	t[0] = 1.0f; t[1] = 0.0f;
	t[2] = 0.0f; t[3] = 1.0f;
	t[4] = tx; t[5] = ty;
}

static void bbs_nvgTransformScale(float* t, float sx, float sy)
{
	t[0] = sx; t[1] = 0.0f;
	t[2] = 0.0f; t[3] = sy;
	t[4] = 0.0f; t[5] = 0.0f;
}

static void bbs_nvgTransformMultiply(float* t, const float* s)
{
	float t0 = t[0] * s[0] + t[1] * s[2];
	float t2 = t[2] * s[0] + t[3] * s[2];
	float t4 = t[4] * s[0] + t[5] * s[2] + s[4];
	t[1] = t[0] * s[1] + t[1] * s[3];
	t[3] = t[2] * s[1] + t[3] * s[3];
	t[5] = t[4] * s[1] + t[5] * s[3] + s[5];
	t[0] = t0;
	t[2] = t2;
	t[4] = t4;
}

static int bbs_nvgTransformInverse(float* inv, const float* t)
{
	double invdet, det = (double)t[0] * t[3] - (double)t[2] * t[1];
	if (det > -1e-6 && det < 1e-6) {
		bbs_nvgTransformIdentity(inv);
		return 0;
	}
	invdet = 1.0 / det;
	inv[0] = (float)(t[3] * invdet);
	inv[2] = (float)(-t[2] * invdet);
	inv[4] = (float)(((double)t[2] * t[5] - (double)t[3] * t[4]) * invdet);
	inv[1] = (float)(-t[1] * invdet);
	inv[3] = (float)(t[0] * invdet);
	inv[5] = (float)(((double)t[1] * t[4] - (double)t[0] * t[5]) * invdet);
	return 1;
}

#define nvgTransformTranslate bbs_nvgTransformTranslate
#define nvgTransformScale bbs_nvgTransformScale
#define nvgTransformMultiply bbs_nvgTransformMultiply
#define nvgTransformInverse bbs_nvgTransformInverse
static NVGcontext *bbs_coreCreate(NVGparams *params);
static NVGparams *bbs_coreParams(NVGcontext *context);
static void bbs_coreDelete(NVGcontext *context);
static int bbs_renderCreateTexture(void *uptr, int type, int width, int height, int flags, const unsigned char *data);
static int bbs_renderUpdateTexture(void *uptr, int image, int x, int y, int width, int height, const unsigned char *data);
static int bbs_renderDeleteTexture(void *uptr, int image);
static void bbs_renderFlush(void *uptr);
#define nvgCreateInternal bbs_coreCreate
#define nvgInternalParams bbs_coreParams
#define nvgDeleteInternal bbs_coreDelete
#include "upstream/nanovg_vk.c"

struct BBSRecording
{
    VKNVGFrame frame;
    VkDescriptorPool pool;
    VkDescriptorSet *textures;
    VkDescriptorSet *uniforms;
    uint32_t capacity;
    VkImage stencilImage;
    VkDeviceMemory stencilMemory;
    VkImageView stencilView;
    VkFramebuffer framebuffer;
    uint32_t width;
    uint32_t height;
    BBSRecording *next;
};
struct BBSBridge
{
    BBSVulkanDispatch dispatch;
    JavaVM *vm;
    jobject images;
    jmethodID createImage;
    jmethodID updateImage;
    jmethodID deleteImage;
    NVGcontext *(*createCore)(NVGparams *);
    NVGparams *(*paramsCore)(NVGcontext *);
    void (*deleteCore)(NVGcontext *);
    VKNVGcontext *vk;
    NVGcontext *context;
    VkDevice device;
    VkPhysicalDevice physical;
    VkFormat stencilFormat;
    VkRenderPass renderPass;
    VkCommandBuffer command;
    uint32_t slot;
    VkImageView target;
    uint32_t width;
    uint32_t height;
    int insidePass;
    int buffersAttached;
    BBSRecording *recorded;
    BBSRecording *available;
    BBSBridge *next;
};

static BBS_NOINLINE BBSBridge *bbs_bridge(void *vk)
{
    for (BBSBridge *bridge = bbs_bridges; bridge != NULL; bridge = bridge->next)
        if (bridge->vk == vk) { bbs_dispatch = &bridge->dispatch; return bridge; }
    if (bbs_creating != NULL) bbs_dispatch = &bbs_creating->dispatch;
    return bbs_creating;
}
static JNIEnv *bbs_environment(BBSBridge *bridge)
{
    JNIEnv *env = NULL;
    if ((*bridge->vm)->GetEnv(bridge->vm, (void **)&env, JNI_VERSION_1_8) != JNI_OK)
        return NULL;
    return env;
}
static int bbs_rendererCreated(void *uptr)
{
    (void)uptr;
    return 1;
}
static void bbs_rendererDelete(void *uptr)
{
    BBSBridge *bridge = bbs_bridge(uptr);
    vknvg_renderDelete(uptr);
    bridge->vk = NULL;
}
static NVGcontext *bbs_coreCreate(NVGparams *params)
{
    BBSBridge *bridge = bbs_creating;
    bridge->vk = params->userPtr;
    params->renderCreateTexture = bbs_renderCreateTexture;
    params->renderUpdateTexture = bbs_renderUpdateTexture;
    params->renderDeleteTexture = bbs_renderDeleteTexture;
    params->renderFlush = bbs_renderFlush;
    params->renderDelete = bbs_rendererDelete;
    /* Initialize GPU resources before allocating the core context, so failure
     * cleanup never has to recover a core pointer from an interrupted call. */
    if (!params->renderCreate(params->userPtr) || (*bbs_env)->ExceptionCheck(bbs_env)) return NULL;
    params->renderCreate = bbs_rendererCreated;
    return bridge->createCore(params);
}
static NVGparams *bbs_coreParams(NVGcontext *context)
{
    for (BBSBridge *bridge = bbs_bridges; bridge != NULL; bridge = bridge->next)
        if (bridge->context == context) return bridge->paramsCore(context);
    return bbs_creating == NULL ? NULL : bbs_creating->paramsCore(context);
}
static void bbs_coreDelete(NVGcontext *context)
{
    for (BBSBridge *bridge = bbs_bridges; bridge != NULL; bridge = bridge->next)
        if (bridge->context == context) { bridge->deleteCore(context); return; }
    if (bbs_creating != NULL) bbs_creating->deleteCore(context);
}
static int bbs_renderCreateTexture(void *uptr, int type, int width, int height, int flags, const unsigned char *data)
{
    BBSBridge *bridge = bbs_bridge(uptr);
    JNIEnv *env = bbs_environment(bridge);
    VKNVGcontext *vk = uptr;
    VKNVGtexture *texture = vknvg_allocTexture(vk);
    if (env == NULL || texture == NULL || (*env)->ExceptionCheck(env)) return 0;
    int id = vknvg_textureId(vk, texture);
    jlongArray handles = (*env)->CallObjectMethod(env, bridge->images, bridge->createImage,
        id, type, width, height, flags, (jlong)(uintptr_t)data);
    if ((*env)->ExceptionCheck(env) || handles == NULL) return 0;
    if ((*env)->GetArrayLength(env, handles) != 3)
    {
        (*env)->DeleteLocalRef(env, handles);
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ThrowNew(env, error, "NanoVG image callback must return image, view, sampler");
        return 0;
    }
    jlong values[3];
    (*env)->GetLongArrayRegion(env, handles, 0, 3, values);
    (*env)->DeleteLocalRef(env, handles);
    /* The Java callback may have created another NanoVG texture; recover the slot. */
    texture = vknvg_findTexture(vk, id);
    texture->image = (VkImage)(uintptr_t)values[0];
    texture->view = (VkImageView)(uintptr_t)values[1];
    texture->sampler = (VkSampler)(uintptr_t)values[2];
    texture->imageLayout = VK_IMAGE_LAYOUT_GENERAL;
    texture->width = width;
    texture->height = height;
    texture->type = type;
    texture->flags = flags;
    texture->borrowed = true;
    return id;
}
static int bbs_renderUpdateTexture(void *uptr, int image, int x, int y, int width, int height, const unsigned char *data)
{
    BBSBridge *bridge = bbs_bridge(uptr);
    JNIEnv *env = bbs_environment(bridge);
    if (env == NULL) return 0;
    (*env)->CallVoidMethod(env, bridge->images, bridge->updateImage,
        image, x, y, width, height, (jlong)(uintptr_t)data);
    return !(*env)->ExceptionCheck(env);
}
static int bbs_renderDeleteTexture(void *uptr, int image)
{
    BBSBridge *bridge = bbs_bridge(uptr);
    VKNVGcontext *vk = uptr;
    JNIEnv *env = bbs_environment(bridge);
    VKNVGtexture *texture = vknvg_findTexture(vk, image);
    if (texture == NULL || env == NULL) return 0;
    if ((*env)->ExceptionCheck(env)) return vknvg_deleteTexture(vk, texture);
    (*env)->CallVoidMethod(env, bridge->images, bridge->deleteImage, image);
    if ((*env)->ExceptionCheck(env)) return 0;
    return vknvg_deleteTexture(vk, texture);
}
static void bbs_destroyRecording(BBSBridge *bridge, BBSRecording *recording)
{
    if (recording->framebuffer != VK_NULL_HANDLE)
        vkDestroyFramebuffer(bridge->device, recording->framebuffer, NULL);
    if (recording->stencilView != VK_NULL_HANDLE)
        vkDestroyImageView(bridge->device, recording->stencilView, NULL);
    if (recording->stencilImage != VK_NULL_HANDLE)
        vkDestroyImage(bridge->device, recording->stencilImage, NULL);
    if (recording->stencilMemory != VK_NULL_HANDLE)
        vkFreeMemory(bridge->device, recording->stencilMemory, NULL);
    if (recording->pool != VK_NULL_HANDLE)
        vkDestroyDescriptorPool(bridge->device, recording->pool, NULL);
    vknvg_destroyBuffer(bridge->device, NULL, &recording->frame.vertex);
    vknvg_destroyBuffer(bridge->device, NULL, &recording->frame.fragUniform);
    free(recording->textures);
    free(recording->uniforms);
    free(recording);
}
static BBS_NOINLINE void bbs_stencil(BBSBridge *bridge, BBSRecording *recording)
{
    if (recording->width == bridge->width && recording->height == bridge->height) return;
    if (recording->stencilView != VK_NULL_HANDLE)
    {
        vkDestroyImageView(bridge->device, recording->stencilView, NULL);
        vkDestroyImage(bridge->device, recording->stencilImage, NULL);
        vkFreeMemory(bridge->device, recording->stencilMemory, NULL);
        recording->stencilView = VK_NULL_HANDLE;
        recording->stencilImage = VK_NULL_HANDLE;
        recording->stencilMemory = VK_NULL_HANDLE;
    }
    VkImageCreateInfo image = {VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO};
    image.imageType = VK_IMAGE_TYPE_2D;
    image.format = bridge->stencilFormat;
    image.extent = (VkExtent3D){bridge->width, bridge->height, 1};
    image.mipLevels = 1;
    image.arrayLayers = 1;
    image.samples = VK_SAMPLE_COUNT_1_BIT;
    image.tiling = VK_IMAGE_TILING_OPTIMAL;
    image.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
    image.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    NVGVK_CHECK_RESULT(vkCreateImage(bridge->device, &image, NULL, &recording->stencilImage));
    VkMemoryRequirements needed;
    vkGetImageMemoryRequirements(bridge->device, recording->stencilImage, &needed);
    uint32_t index = 0;
    NVGVK_CHECK_RESULT(vknvg_memory_type_from_properties(bridge->vk->memoryProperties,
        needed.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, &index));
    VkMemoryAllocateInfo allocation = {VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
    allocation.allocationSize = needed.size;
    allocation.memoryTypeIndex = index;
    NVGVK_CHECK_RESULT(vkAllocateMemory(bridge->device, &allocation, NULL, &recording->stencilMemory));
    NVGVK_CHECK_RESULT(vkBindImageMemory(bridge->device, recording->stencilImage, recording->stencilMemory, 0));
    VkImageViewCreateInfo view = {VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO};
    view.image = recording->stencilImage;
    view.viewType = VK_IMAGE_VIEW_TYPE_2D;
    view.format = bridge->stencilFormat;
    view.subresourceRange.aspectMask = VK_IMAGE_ASPECT_STENCIL_BIT;
    if (bridge->stencilFormat != VK_FORMAT_S8_UINT)
        view.subresourceRange.aspectMask |= VK_IMAGE_ASPECT_DEPTH_BIT;
    view.subresourceRange.levelCount = 1;
    view.subresourceRange.layerCount = 1;
    NVGVK_CHECK_RESULT(vkCreateImageView(bridge->device, &view, NULL, &recording->stencilView));
    recording->width = bridge->width;
    recording->height = bridge->height;
}
static void bbs_barrier(VkCommandBuffer command)
{
    VkMemoryBarrier barrier = {VK_STRUCTURE_TYPE_MEMORY_BARRIER};
    barrier.srcAccessMask = VK_ACCESS_MEMORY_WRITE_BIT;
    barrier.dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT;
    vkCmdPipelineBarrier(command, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 1, &barrier, 0, NULL, 0, NULL);
}
static void bbs_detachRecording(BBSBridge *bridge)
{
    if (!bridge->buffersAttached) return;
    VKNVGcontext *vk = bridge->vk;
    BBSRecording *recording = bridge->recorded;
    recording->pool = vk->descPool;
    recording->textures = vk->textureDescriptorSet;
    recording->uniforms = vk->ssboDescriptorSet;
    recording->capacity = vk->cdescPool;
    vk->frames = NULL;
    vk->descPool = VK_NULL_HANDLE;
    vk->textureDescriptorSet = NULL;
    vk->ssboDescriptorSet = NULL;
    vk->cdescPool = 0;
    bridge->buffersAttached = 0;
}
static void bbs_renderFlush(void *uptr)
{
    BBSBridge *bridge = bbs_bridge(uptr);
    VKNVGcontext *vk = uptr;
    JNIEnv *env = bbs_environment(bridge);
    jmp_buf failure;
    bbs_env = env;
    bbs_failure = &failure;
    if (setjmp(failure) != 0)
    {
        bbs_detachRecording(bridge);
        if (bridge->insidePass) vkCmdEndRenderPass(bridge->command);
        bridge->insidePass = 0;
        bbs_failure = NULL;
        return;
    }
    if (bridge->command == VK_NULL_HANDLE || bridge->recorded != NULL)
    {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ThrowNew(env, error, "NanoVG Vulkan flush requires exactly one prepared recording");
        bbs_failure = NULL;
        return;
    }
    BBSRecording **link = &bridge->available;
    while (*link != NULL && ((*link)->width != bridge->width || (*link)->height != bridge->height))
        link = &(*link)->next;
    if (*link == NULL) link = &bridge->available;
    BBSRecording *recording = *link;
    if (recording != NULL) *link = recording->next;
    else recording = calloc(1, sizeof(*recording));
    if (recording == NULL) { bbs_check(VK_ERROR_OUT_OF_HOST_MEMORY, "allocate recording"); return; }
    recording->next = NULL;
    bridge->recorded = recording;
    bbs_stencil(bridge, recording);
    VkImageView views[] = {bridge->target, recording->stencilView};
    VkFramebufferCreateInfo framebuffer = {VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO};
    framebuffer.renderPass = bridge->renderPass;
    framebuffer.attachmentCount = 2;
    framebuffer.pAttachments = views;
    framebuffer.width = bridge->width;
    framebuffer.height = bridge->height;
    framebuffer.layers = 1;
    NVGVK_CHECK_RESULT(vkCreateFramebuffer(bridge->device, &framebuffer, NULL, &recording->framebuffer));
    bbs_barrier(bridge->command);
    VkClearValue clear[2] = {0};
    VkRenderPassBeginInfo begin = {VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO};
    begin.renderPass = bridge->renderPass;
    begin.framebuffer = recording->framebuffer;
    begin.renderArea.extent = (VkExtent2D){bridge->width, bridge->height};
    begin.clearValueCount = 2;
    begin.pClearValues = clear;
    vkCmdBeginRenderPass(bridge->command, &begin, VK_SUBPASS_CONTENTS_INLINE);
    bridge->insidePass = 1;
    VkViewport viewport = {0, 0, (float)bridge->width, (float)bridge->height, 0, 1};
    VkRect2D scissor = {{0, 0}, {bridge->width, bridge->height}};
    vkCmdSetViewport(bridge->command, 0, 1, &viewport);
    vkCmdSetScissor(bridge->command, 0, 1, &scissor);
    vk->frames = &recording->frame;
    vk->descPool = recording->pool;
    vk->textureDescriptorSet = recording->textures;
    vk->ssboDescriptorSet = recording->uniforms;
    vk->cdescPool = recording->capacity;
    bridge->buffersAttached = 1;
    vknvg_renderFlush(uptr);
    bbs_detachRecording(bridge);
    vkCmdEndRenderPass(bridge->command);
    bridge->insidePass = 0;
    bbs_barrier(bridge->command);
    bbs_failure = NULL;
}
static void bbs_makePass(BBSBridge *bridge, VkFormat color)
{
    VkFormat formats[] = {VK_FORMAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT_S8_UINT};
    for (size_t i = 0; i < sizeof(formats) / sizeof(formats[0]); i++)
    {
        VkFormatProperties properties;
        vkGetPhysicalDeviceFormatProperties(bridge->physical, formats[i], &properties);
        if (properties.optimalTilingFeatures & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT)
        { bridge->stencilFormat = formats[i]; break; }
    }
    if (bridge->stencilFormat == VK_FORMAT_UNDEFINED)
        bbs_check(VK_ERROR_FORMAT_NOT_SUPPORTED, "no supported eight-bit stencil format");
    VkAttachmentDescription attachments[2] = {0};
    attachments[0].format = color;
    attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[0].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachments[0].initialLayout = VK_IMAGE_LAYOUT_GENERAL;
    attachments[0].finalLayout = VK_IMAGE_LAYOUT_GENERAL;
    attachments[1].format = bridge->stencilFormat;
    attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
    VkAttachmentReference colorReference = {0, VK_IMAGE_LAYOUT_GENERAL};
    VkAttachmentReference stencilReference = {1, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL};
    VkSubpassDescription subpass = {0};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorReference;
    subpass.pDepthStencilAttachment = &stencilReference;
    VkRenderPassCreateInfo create = {VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO};
    create.attachmentCount = 2;
    create.pAttachments = attachments;
    create.subpassCount = 1;
    create.pSubpasses = &subpass;
    NVGVK_CHECK_RESULT(vkCreateRenderPass(bridge->device, &create, NULL, &bridge->renderPass));
}

JNIEXPORT jint JNICALL Java_com_mcstudio_nano_NanoVkNative_abiVersion(JNIEnv *env, jclass type)
{
    (void)env; (void)type;
    return BBS_NANOVG_ABI_VERSION;
}
JNIEXPORT jlong JNICALL Java_com_mcstudio_nano_NanoVkNative_create(JNIEnv *env, jclass type,
    jobject images, jlong instance, jlong physical, jlong device, jlong instanceProc, jlong deviceProc,
    jlong createCore, jlong deleteCore, jlong paramsCore, jint color)
{
    (void)type;
    if (images == NULL || instance == 0 || physical == 0 || device == 0 || instanceProc == 0
        || deviceProc == 0 || createCore == 0 || deleteCore == 0 || paramsCore == 0)
    {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        (*env)->ThrowNew(env, error, "NanoVG Vulkan requires the existing instance, device, loader and core addresses");
        return 0;
    }
    BBSBridge *bridge = calloc(1, sizeof(*bridge));
    if (bridge == NULL) return 0;
    (*env)->GetJavaVM(env, &bridge->vm);
    bridge->images = (*env)->NewGlobalRef(env, images);
    jclass owner = (*env)->GetObjectClass(env, images);
    bridge->createImage = (*env)->GetMethodID(env, owner, "create", "(IIIIIJ)[J");
    bridge->updateImage = (*env)->GetMethodID(env, owner, "update", "(IIIIIJ)V");
    bridge->deleteImage = (*env)->GetMethodID(env, owner, "delete", "(I)V");
    (*env)->DeleteLocalRef(env, owner);
    if ((*env)->ExceptionCheck(env)) { (*env)->DeleteGlobalRef(env, bridge->images); free(bridge); return 0; }
    bridge->createCore = (NVGcontext *(*)(NVGparams *))(uintptr_t)createCore;
    bridge->deleteCore = (void (*)(NVGcontext *))(uintptr_t)deleteCore;
    bridge->paramsCore = (NVGparams *(*)(NVGcontext *))(uintptr_t)paramsCore;
    bridge->device = (VkDevice)(uintptr_t)device;
    bridge->physical = (VkPhysicalDevice)(uintptr_t)physical;
    bbs_creating = bridge;
    bbs_dispatch = &bridge->dispatch;
    bbs_env = env;
    jmp_buf failure;
    bbs_failure = &failure;
    if (setjmp(failure) == 0)
    {
        const char *missing = bbs_loadDispatch(&bridge->dispatch, (VkInstance)(uintptr_t)instance,
            bridge->device, (PFN_vkGetInstanceProcAddr)(uintptr_t)instanceProc,
            (PFN_vkGetDeviceProcAddr)(uintptr_t)deviceProc);
        if (missing != NULL) bbs_check(VK_ERROR_INITIALIZATION_FAILED, missing);
        bbs_makePass(bridge, color);
        VKNVGCreateInfo create = {0};
        create.device = bridge->device;
        create.gpu = bridge->physical;
        create.renderpass = bridge->renderPass;
        create.cmdBuffer = &bridge->command;
        create.currentFrame = &bridge->slot;
        create.frameCount = 1;
        bridge->context = nvgCreateVk(&create, NVG_ANTIALIAS | NVG_STENCIL_STROKES, VK_NULL_HANDLE);
    }
    bbs_failure = NULL;
    if (bridge->context == NULL || (*env)->ExceptionCheck(env))
    {
        if (bridge->context != NULL) bridge->deleteCore(bridge->context);
        else if (bridge->vk != NULL) vknvg_renderDelete(bridge->vk);
        if (bridge->renderPass != VK_NULL_HANDLE) vkDestroyRenderPass(bridge->device, bridge->renderPass, NULL);
        (*env)->DeleteGlobalRef(env, bridge->images);
        free(bridge);
        bbs_creating = NULL;
        bbs_dispatch = NULL;
        return 0;
    }
    bbs_creating = NULL;
    bridge->next = bbs_bridges;
    bbs_bridges = bridge;
    return (jlong)(uintptr_t)bridge->context;
}
JNIEXPORT void JNICALL Java_com_mcstudio_nano_NanoVkNative_prepare(JNIEnv *env, jclass type,
    jlong context, jlong command, jlong target, jint width, jint height)
{
    (void)type;
    NVGparams *params = bbs_coreParams((NVGcontext *)(uintptr_t)context);
    BBSBridge *bridge = params == NULL ? NULL : bbs_bridge(params->userPtr);
    if (bridge == NULL || command == 0 || target == 0 || width <= 0 || height <= 0 || bridge->command != VK_NULL_HANDLE)
    {
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ThrowNew(env, error, "Invalid or overlapping NanoVG Vulkan recording");
        return;
    }
    bridge->command = (VkCommandBuffer)(uintptr_t)command;
    bridge->target = (VkImageView)(uintptr_t)target;
    bridge->width = (uint32_t)width;
    bridge->height = (uint32_t)height;
}
JNIEXPORT jlong JNICALL Java_com_mcstudio_nano_NanoVkNative_takeRecording(JNIEnv *env, jclass type, jlong context)
{
    (void)env; (void)type;
    NVGparams *params = bbs_coreParams((NVGcontext *)(uintptr_t)context);
    BBSBridge *bridge = params == NULL ? NULL : bbs_bridge(params->userPtr);
    if (bridge == NULL) return 0;
    BBSRecording *recording = bridge->recorded;
    bridge->recorded = NULL;
    bridge->command = VK_NULL_HANDLE;
    return (jlong)(uintptr_t)recording;
}
JNIEXPORT void JNICALL Java_com_mcstudio_nano_NanoVkNative_releaseRecording(JNIEnv *env, jclass type, jlong context, jlong handle)
{
    (void)env; (void)type;
    NVGparams *params = bbs_coreParams((NVGcontext *)(uintptr_t)context);
    BBSBridge *bridge = params == NULL ? NULL : bbs_bridge(params->userPtr);
    BBSRecording *recording = (BBSRecording *)(uintptr_t)handle;
    if (bridge == NULL || recording == NULL) return;
    if (recording->framebuffer != VK_NULL_HANDLE)
    { vkDestroyFramebuffer(bridge->device, recording->framebuffer, NULL); recording->framebuffer = VK_NULL_HANDLE; }
    recording->next = bridge->available;
    bridge->available = recording;
}
JNIEXPORT void JNICALL Java_com_mcstudio_nano_NanoVkNative_destroy(JNIEnv *env, jclass type, jlong context)
{
    (void)type;
    BBSBridge **link = &bbs_bridges;
    while (*link != NULL && (*link)->context != (NVGcontext *)(uintptr_t)context) link = &(*link)->next;
    BBSBridge *bridge = *link;
    if (bridge == NULL) return;
    bbs_dispatch = &bridge->dispatch;
    for (BBSRecording *recording = bridge->available; recording != NULL;)
    { BBSRecording *next = recording->next; bbs_destroyRecording(bridge, recording); recording = next; }
    if (bridge->recorded != NULL) bbs_destroyRecording(bridge, bridge->recorded);
    bridge->deleteCore(bridge->context);
    vkDestroyRenderPass(bridge->device, bridge->renderPass, NULL);
    *link = bridge->next;
    (*env)->DeleteGlobalRef(env, bridge->images);
    free(bridge);
    bbs_dispatch = NULL;
}
