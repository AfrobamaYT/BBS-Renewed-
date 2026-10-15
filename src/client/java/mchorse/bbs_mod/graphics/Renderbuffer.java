package mchorse.bbs_mod.graphics;

import org.lwjgl.opengl.GL30;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

public class Renderbuffer
{
    public int id;
    public final int target;
    public final int storage;
    private final boolean openGl;
    private GpuTexture gpu;
    private GpuTextureView view;

    public Renderbuffer()
    {
        this(GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL30.GL_DEPTH24_STENCIL8);
    }

    public Renderbuffer(int target, int storage)
    {
        this.openGl = GpuBackend.isOpenGl();
        this.id = this.openGl ? GL30.glGenRenderbuffers() : -1;
        this.target = target;
        this.storage = storage;
    }

    public void bind()
    {
        if (!this.openGl) throw new IllegalStateException("Native renderbuffers are explicit attachments; use view()");
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, this.id);
    }

    public void unbind()
    {
        if (!this.openGl) throw new IllegalStateException("Native renderbuffers are explicit attachments; use view()");
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
    }

    public void delete()
    {
        if (!this.openGl)
        {
            if (this.view != null) { this.view.close(); this.view = null; }
            if (this.gpu != null) { this.gpu.close(); this.gpu = null; }
            return;
        }
        if (this.id >= 0)
        {
            GL30.glDeleteRenderbuffers(this.id);

            this.id = -1;
        }
    }

    public void resize(int width, int height)
    {
        if (!this.openGl)
        {
            FramebufferUtils.outsidePass("Renderbuffer resize");
            width = Math.max(1, width); height = Math.max(1, height);
            if (this.gpu != null && this.gpu.getWidth(0) == width && this.gpu.getHeight(0) == height) return;
            GpuFormat format = switch (this.storage)
            {
                case GL30.GL_DEPTH_COMPONENT32F -> GpuFormat.D32_FLOAT;
                case GL14.GL_DEPTH_COMPONENT16 -> GpuFormat.D16_UNORM;
                case GL30.GL_DEPTH24_STENCIL8 -> GpuFormat.D24_UNORM_S8_UINT;
                case GL30.GL_DEPTH32F_STENCIL8 -> GpuFormat.D32_FLOAT_S8_UINT;
                default -> throw new IllegalArgumentException("Unported renderbuffer storage: " + this.storage);
            };
            GpuTexture replacement = RenderSystem.getDevice().createTexture("bbs-renderbuffer",
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST, format, width, height, 1, 1);
            GpuTextureView replacementView;
            try { replacementView = RenderSystem.getDevice().createTextureView(replacement, 0, 1); }
            catch (RuntimeException failure) { replacement.close(); throw failure; }
            this.delete();
            this.gpu = replacement; this.view = replacementView;
            return;
        }
        this.bind();

        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, this.storage, width, height);
    }

    public GpuTexture gpu() { return this.gpu; }
    public GpuTextureView view() { return this.view; }
}
