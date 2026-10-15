// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.web;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
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
        super(Component.literal("BBS Web"));

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
        return (int) (value * this.minecraft.getWindow().getGuiScale());
    }

    private void resizeBrowser()
    {
        if (this.browser != null && this.width > 0 && this.height > 0)
        {
            this.browser.resize(this.scale(this.width), this.scale(this.height));
        }
    }

    @Override
    public void resize(Minecraft mc, int w, int h)
    {
        super.resize(mc, w, h);

        this.resizeBrowser();
    }

    @Override
    public void onClose()
    {
        if (this.browser != null)
        {
            this.browser.close();
            this.browser = null;
        }

        super.onClose();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta)
    {
        if (!this.loggedFrame)
        {
            this.loggedFrame = true;
            LOG.info("[web] first render: browser={}, texId={}", this.browser != null, this.browser == null ? -1 : this.browser.getRenderer().getTextureID());
        }

        if (this.browser == null)
        {
            context.text(this.font, Component.literal("MCEF browser unavailable"), this.width / 2, this.height / 2, 0xFFFF5555);

            return;
        }

        /* Transparent compositing: the page's own background is stripped (see the HTML), so the
           browser texture blends over whatever is behind (the world / the BBS editor). */
        /* 26.2: disableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefentest aus. */
        /* 26.2: enableBlend ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: mischen an. */
        /* 26.2: defaultBlendFunc ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Standardmischung (Quell-Alpha ueber Gegen-Alpha). */
        /* 26.2: setShader ist entfallen -- der Shader steht in der Pipeline, die der
         * Zeichenaufruf waehlt. Frueher galt er fuer alles Folgende. */
        /* 26.2: setShaderTexture ist entfallen -- eine Textur wird im Durchgang
         * gebunden (TextureSetup/bindTexture), nicht global vorher. */

        Matrix4f matrix = new Matrix4f().mul(context.pose());
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        buffer.addVertex(matrix, 0F, this.height, 0F).setUv(0F, 1F).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, this.width, this.height, 0F).setUv(1F, 1F).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, this.width, 0F, 0F).setUv(1F, 0F).setColor(255, 255, 255, 255);
        buffer.addVertex(matrix, 0F, 0F, 0F).setUv(0F, 0F).setColor(255, 255, 255, 255);

        mchorse.bbs_mod.compat.vertex.BauerAlt.zeichnen(buffer.buildOrThrow());

        /* 26.2: setShaderTexture ist entfallen -- eine Textur wird im Durchgang
         * gebunden (TextureSetup/bindTexture), nicht global vorher. */
        /* 26.2: disableBlend ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: mischen aus. */
        /* 26.2: enableDepthTest ist entfallen -- der Zustand steckt in der Pipeline
         * des Zuges. Absicht war: Tiefentest an. */
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
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount)
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
