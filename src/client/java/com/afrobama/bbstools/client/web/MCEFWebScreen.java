// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.web;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCEF (Chromium Embedded Framework) full-screen browser.
 *
 * Renders a real HTML/CSS/JS page 1:1 by drawing the off-screen Chromium texture
 * across the whole screen, and bridges mouse/keyboard input to the browser. This is
 * the prototype path for showing the Claude HTML mockups exactly as designed, instead
 * of reimplementing them in BBS's Java flex UI.
 *
 * MCEF is an optional dependency — only instantiate this after a
 * {@code FabricLoader.isModLoaded("mcef")} check (see {@link MCEFWebCommand}).
 */
public class MCEFWebScreen extends Screen
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools-web");

    private final String url;

    private MCEFBrowser browser;
    private boolean loggedFrame;

    public MCEFWebScreen(String url)
    {
        super(Text.literal("BBS Web"));

        this.url = url;
    }

    @Override
    protected void init()
    {
        super.init();

        if (this.browser == null)
        {
            try
            {
                /* Register the cefQuery bridge before the page loads so window.cefQuery exists. */
                BBSWebBridge.install();

                this.browser = MCEF.createBrowser(this.url, true);
                this.resizeBrowser();
                LOG.info("[web] init: browser created for {} (size {}x{})", this.url, this.width, this.height);
            }
            catch (Throwable t)
            {
                LOG.error("[web] init: createBrowser failed for {}", this.url, t);
            }
        }
    }

    private int scale(double value)
    {
        return (int) (value * this.client.getWindow().getScaleFactor());
    }

    private void resizeBrowser()
    {
        if (this.browser != null && this.width > 0 && this.height > 0)
        {
            this.browser.resize(this.scale(this.width), this.scale(this.height));
        }
    }

    @Override
    public void resize(MinecraftClient mc, int w, int h)
    {
        super.resize(mc, w, h);

        this.resizeBrowser();
    }

    @Override
    public void close()
    {
        if (this.browser != null)
        {
            this.browser.close();
            this.browser = null;
        }

        super.close();
    }

    @Override
    public boolean shouldPause()
    {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        if (!this.loggedFrame)
        {
            this.loggedFrame = true;
            LOG.info("[web] first render: browser={}, texId={}", this.browser != null, this.browser == null ? -1 : this.browser.getRenderer().getTextureID());
        }

        if (this.browser == null)
        {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("MCEF browser unavailable"), this.width / 2, this.height / 2, 0xFFFF5555);

            return;
        }

        /* Transparent compositing: the page's own background is stripped (see the HTML), so the
           browser texture blends over whatever is behind (the world / the BBS editor). */
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, this.browser.getRenderer().getTextureID());

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        buffer.vertex(matrix, 0F, this.height, 0F).texture(0F, 1F).color(255, 255, 255, 255);
        buffer.vertex(matrix, this.width, this.height, 0F).texture(1F, 1F).color(255, 255, 255, 255);
        buffer.vertex(matrix, this.width, 0F, 0F).texture(1F, 0F).color(255, 255, 255, 255);
        buffer.vertex(matrix, 0F, 0F, 0F).texture(0F, 0F).color(255, 255, 255, 255);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if (this.browser != null)
        {
            this.browser.sendMousePress(this.scale(mouseX), this.scale(mouseY), button);
            this.browser.setFocus(true);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if (this.browser != null)
        {
            this.browser.sendMouseRelease(this.scale(mouseX), this.scale(mouseY), button);
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY)
    {
        if (this.browser != null)
        {
            this.browser.sendMouseMove(this.scale(mouseX), this.scale(mouseY));
        }

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        if (this.browser != null)
        {
            this.browser.sendMouseWheel(this.scale(mouseX), this.scale(mouseY), verticalAmount, 0);
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        if (this.browser != null)
        {
            this.browser.sendKeyPress(keyCode, scanCode, modifiers);
            this.browser.setFocus(true);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        if (this.browser != null)
        {
            this.browser.sendKeyRelease(keyCode, scanCode, modifiers);
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers)
    {
        if (this.browser != null && codePoint != 0)
        {
            this.browser.sendKeyTyped(codePoint, modifiers);
            this.browser.setFocus(true);
        }

        return super.charTyped(codePoint, modifiers);
    }

}
