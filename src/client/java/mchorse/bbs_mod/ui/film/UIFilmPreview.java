package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;

public class UIFilmPreview extends UIElement
{
    private UIFilmPanel panel;

    private float size = 0.25F;
    private boolean editing;

    public UIFilmPreview(UIFilmPanel panel)
    {
        this.panel = panel;
    }

    public Area getViewport()
    {
        int width = BBSRendering.getVideoWidth();
        int height = BBSRendering.getVideoHeight();
        int w = this.area.w - 10;
        int h = this.area.h - 10;

        Camera camera = new Camera();

        camera.copy(this.panel.getWorldCamera());
        camera.updatePerspectiveProjection(width, height);

        if (width > height)
        {
            w *= this.size;
        }
        else
        {
            h *= this.size;
        }

        Vector2i size = Vectors.resize(width / (float) height, w, h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(this.area.x + 5, this.area.ey() - size.y - 5);

        return area;
    }

    private boolean isInCorner(UIContext context, Area viewport)
    {
        Area.SHARED.set(viewport.ex() - 20, viewport.y, 20, 20);

        return Area.SHARED.isInside(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
            if (isInCorner(context, area))
            {
                this.editing = true;

                return true;
            }

            this.panel.replayEditor.clickViewport(context, area);
            this.panel.dashboard.orbitUI.mouseClicked(context);

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
            this.panel.dashboard.orbitUI.mouseScrolled(context);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.editing = false;

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        Texture texture = BBSRendering.getTexture();
        Area area = this.getViewport();
        Camera camera = this.panel.getCamera();

        if (this.editing)
        {
            float x = (context.mouseX - (this.area.x + 5)) / (float) (this.area.w - 10);
            float y = 1F - (context.mouseY - (this.area.y + 5)) / (float) (this.area.h - 10);

            this.size = MathUtils.clamp(area.w > area.h ? x : y, 0.1F, 0.9F);
        }

        camera.copy(this.panel.getWorldCamera());
        camera.view.set(this.panel.lastView);
        camera.projection.set(this.panel.lastProjection);
        context.batcher.flush();

        if (texture != null)
        {
            context.batcher.dropShadow(area.x + 1, area.y + 1, area.ex() - 1, area.ey() - 1, 5, Colors.A50, 0);
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        /* Render rule of thirds */
        if (BBSSettings.editorRuleOfThirds.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();

            context.batcher.box(area.x + area.w / 3 - 1, area.y, area.x + area.w / 3, area.y + area.h, guidesColor);
            context.batcher.box(area.x + area.w - area.w / 3, area.y, area.x + area.w - area.w / 3 + 1, area.y + area.h, guidesColor);

            context.batcher.box(area.x, area.y + area.h / 3 - 1, area.x + area.w, area.y + area.h / 3, guidesColor);
            context.batcher.box(area.x, area.y + area.h - area.h / 3, area.x + area.w, area.y + area.h - area.h / 3 + 1, guidesColor);
        }

        if (BBSSettings.editorCenterLines.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();
            int x = area.mx();
            int y = area.my();

            context.batcher.box(area.x, y, area.ex(), y + 1, guidesColor);
            context.batcher.box(x, area.y, x + 1, area.ey(), guidesColor);
        }

        if (BBSSettings.editorCrosshair.get())
        {
            int x = area.mx() + 1;
            int y = area.my() + 1;

            context.batcher.box(x - 4, y - 1, x + 3, y, Colors.setA(Colors.WHITE, 0.5F));
            context.batcher.box(x - 1, y - 4, x, y + 3, Colors.setA(Colors.WHITE, 0.5F));
        }

        this.panel.getController().renderHUD(context, area);

        if (this.panel.replayEditor.isVisible())
        {
            int w = (int) (area.w * BBSSettings.audioWaveformWidth.get());
            int x = area.x(0.5F, w);

            AudioRenderer.renderAll(context.batcher, x, area.y + 10, w, BBSSettings.audioWaveformHeight.get(), context.menu.width, context.menu.height);
        }

        if (this.editing || this.isInCorner(context, area))
        {
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            RenderSystem.disableDepthTest();
            context.batcher.icon(Icons.MAXIMIZE, area.ex() - 18, area.y + 2);
        }
    }
}