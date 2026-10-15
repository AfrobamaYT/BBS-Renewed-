/* Private CPU fixture: invoke the real adapter's buffer functions, never create a GPU.
 * Vulkan calls below record requested resource size independently from memory rounding. */
#include "../bbs_nanovg_vk.c"
#include <assert.h>

typedef struct TestMemory {
    VkDeviceSize size;
    unsigned char *bytes;
    int mapped;
} TestMemory;
typedef struct TestBuffer {
    VkDeviceSize capacity;
    TestMemory *memory;
} TestBuffer;
static int created, destroyed, allocated, freed, flushed;

static VKAPI_ATTR VkResult VKAPI_CALL test_createBuffer(VkDevice device,
    const VkBufferCreateInfo *info, const VkAllocationCallbacks *allocator, VkBuffer *result)
{
    (void)device; (void)allocator;
    TestBuffer *buffer = calloc(1, sizeof *buffer);
    assert(buffer != NULL && info->size > 0);
    buffer->capacity = info->size;
    *result = (VkBuffer)(uintptr_t)buffer;
    created++;
    return VK_SUCCESS;
}
static VKAPI_ATTR void VKAPI_CALL test_requirements(VkDevice device, VkBuffer handle, VkMemoryRequirements *result)
{
    (void)device;
    TestBuffer *buffer = (TestBuffer *)(uintptr_t)handle;
    result->size = (buffer->capacity + 255) / 256 * 256;
    result->alignment = 256;
    result->memoryTypeBits = 1;
}
static VKAPI_ATTR VkResult VKAPI_CALL test_allocate(VkDevice device,
    const VkMemoryAllocateInfo *info, const VkAllocationCallbacks *allocator, VkDeviceMemory *result)
{
    (void)device; (void)allocator;
    TestMemory *memory = calloc(1, sizeof *memory);
    assert(memory != NULL); memory->size = info->allocationSize;
    memory->bytes = calloc(1, (size_t)memory->size); assert(memory->bytes != NULL);
    *result = (VkDeviceMemory)(uintptr_t)memory; allocated++;
    return VK_SUCCESS;
}
static VKAPI_ATTR VkResult VKAPI_CALL test_map(VkDevice device, VkDeviceMemory handle,
    VkDeviceSize offset, VkDeviceSize size, VkMemoryMapFlags flags, void **result)
{
    (void)device; (void)flags;
    TestMemory *memory = (TestMemory *)(uintptr_t)handle;
    assert(offset == 0 && size == memory->size && !memory->mapped);
    memory->mapped = 1; *result = memory->bytes;
    return VK_SUCCESS;
}
static VKAPI_ATTR VkResult VKAPI_CALL test_bind(VkDevice device, VkBuffer handle, VkDeviceMemory memory, VkDeviceSize offset)
{
    (void)device;
    assert(offset == 0);
    ((TestBuffer *)(uintptr_t)handle)->memory = (TestMemory *)(uintptr_t)memory;
    return VK_SUCCESS;
}
static VKAPI_ATTR VkResult VKAPI_CALL test_flush(VkDevice device, uint32_t count, const VkMappedMemoryRange *ranges)
{
    (void)device;
    assert(count == 1 && ranges[0].offset == 0 && ranges[0].size == VK_WHOLE_SIZE);
    assert(((TestMemory *)(uintptr_t)ranges[0].memory)->mapped);
    flushed++; return VK_SUCCESS;
}
static VKAPI_ATTR void VKAPI_CALL test_unmap(VkDevice device, VkDeviceMemory handle)
{
    (void)device;
    TestMemory *memory = (TestMemory *)(uintptr_t)handle;
    assert(memory->mapped); memory->mapped = 0;
}
static VKAPI_ATTR void VKAPI_CALL test_destroyBuffer(VkDevice device, VkBuffer handle, const VkAllocationCallbacks *allocator)
{
    (void)device; (void)allocator;
    TestBuffer *buffer = (TestBuffer *)(uintptr_t)handle;
    assert(buffer->memory != NULL && !buffer->memory->mapped);
    free(buffer); destroyed++;
}
static VKAPI_ATTR void VKAPI_CALL test_free(VkDevice device, VkDeviceMemory handle, const VkAllocationCallbacks *allocator)
{
    (void)device; (void)allocator;
    TestMemory *memory = (TestMemory *)(uintptr_t)handle;
    assert(!memory->mapped); free(memory->bytes); free(memory); freed++;
}

static void update(VKNVGBuffer *buffer, VkPhysicalDeviceMemoryProperties properties, uint32_t size, int expectedCreates)
{
    unsigned char data[1024]; memset(data, (int)(size % 251), sizeof data);
    vknvg_UpdateBuffer(VK_NULL_HANDLE, NULL, buffer, properties,
        VK_BUFFER_USAGE_STORAGE_BUFFER_BIT, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT, data, size);
    TestBuffer *actual = (TestBuffer *)(uintptr_t)buffer->buffer;
    if (actual->capacity < size || created != expectedCreates) {
        fprintf(stderr, "CAPACITY_FAIL requested=%u actualVkBuffer=%llu allocation=%llu creates=%d expected=%d\n",
            size, (unsigned long long)actual->capacity,
            (unsigned long long)actual->memory->size, created, expectedCreates);
        exit(1);
    }
    assert(memcmp(actual->memory->bytes, data, size) == 0);
}

int main(void)
{
    BBSVulkanDispatch dispatch = {0};
    dispatch.CreateBuffer = test_createBuffer; dispatch.GetBufferMemoryRequirements = test_requirements;
    dispatch.AllocateMemory = test_allocate; dispatch.MapMemory = test_map; dispatch.BindBufferMemory = test_bind;
    dispatch.FlushMappedMemoryRanges = test_flush; dispatch.UnmapMemory = test_unmap;
    dispatch.DestroyBuffer = test_destroyBuffer; dispatch.FreeMemory = test_free;
    bbs_dispatch = &dispatch;
    VkPhysicalDeviceMemoryProperties properties = {0}; properties.memoryTypeCount = 1;
    properties.memoryTypes[0].propertyFlags = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
    VKNVGBuffer buffer = {0};
    update(&buffer, properties, 528, 1); // 3 real 176-byte fragment blocks, 768-byte fixture allocation
    update(&buffer, properties, 704, 2); // 4 blocks: must grow even though 704 <= allocation768
    update(&buffer, properties, 176, 2); // shrink reuses the genuine 704-byte buffer
    update(&buffer, properties, 768, 3); // exact allocation boundary must still grow the VkBuffer
    update(&buffer, properties, 768, 3); // exact genuine buffer capacity reuses it
    update(&buffer, properties, 769, 4); // allocation-boundary growth maps/flushed whole1024
    assert(flushed == 6);
    vknvg_destroyBuffer(VK_NULL_HANDLE, NULL, &buffer); vknvg_destroyBuffer(VK_NULL_HANDLE, NULL, &buffer);
    assert(created == 4 && destroyed == 4 && allocated == 4 && freed == 4 && !buffer.initialised);
    puts("CAPACITY_PASS actual adapter UpdateBuffer: 528->704 within allocation768, shrink/exact/grow, full map/flush, destroy once; gpu_created=false");
    return 0;
}
