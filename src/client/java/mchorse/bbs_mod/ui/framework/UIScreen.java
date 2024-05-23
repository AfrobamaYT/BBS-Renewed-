package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

public class UIScreen extends Screen implements IFileDropListener
{
    private UIBaseMenu menu;
    private UIRenderingContext context;

    private int lastGuiScale;

    public static void open(UIBaseMenu menu)
    {
        MinecraftClient.getInstance().setScreen(new UIScreen(Text.empty(), menu));
    }

    public static UIBaseMenu getCurrentMenu()
    {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (currentScreen instanceof UIScreen uiScreen)
        {
            return uiScreen.menu;
        }

        return null;
    }

    public UIScreen(Text title, UIBaseMenu menu)
    {
        super(title);

        MinecraftClient mc = MinecraftClient.getInstance();

        this.menu = menu;
        this.context = new UIRenderingContext(new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers()));

        this.menu.context.setup(this.context);
    }

    public UIBaseMenu getMenu()
    {
        return this.menu;
    }

    public void update()
    {
        this.menu.update();
    }

    public void renderInWorld(WorldRenderContext context)
    {
        this.menu.renderInWorld(context);
    }

    @Override
    public void filesDragged(List<Path> paths)
    {
        super.filesDragged(paths);

        String[] filePaths = new String[paths.size()];
        int i = 0;

        for (Path path : paths)
        {
            filePaths[i] = path.toAbsolutePath().toString();

            i += 1;
        }

        this.acceptFilePaths(filePaths);
    }

    @Override
    public void removed()
    {
        MinecraftClient.getInstance().options.getGuiScale().setValue(this.lastGuiScale);
        MinecraftClient.getInstance().onResolutionChanged();

        super.removed();

        this.menu.onClose(null);

        if (this.menu.canHideHUD())
        {
            MinecraftClient.getInstance().options.hudHidden = false;
        }
    }

    @Override
    public void onDisplayed()
    {
        this.lastGuiScale = MinecraftClient.getInstance().options.getGuiScale().getValue();

        MinecraftClient.getInstance().options.getGuiScale().setValue(BBSModClient.getGUIScale());
        MinecraftClient.getInstance().onResolutionChanged();

        super.onDisplayed();

        this.menu.onOpen(null);

        if (this.menu.canHideHUD())
        {
            MinecraftClient.getInstance().options.hudHidden = true;
        }
    }

    @Override
    public boolean shouldPause()
    {
        return this.menu.canPause();
    }

    @Override
    protected void init()
    {
        super.init();

        this.menu.resize(this.width, this.height);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height)
    {
        super.resize(client, width, height);

        this.menu.resize(width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return this.menu.mouseClicked((int) mouseX, (int) mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        return this.menu.mouseScrolled((int) mouseX, (int) mouseY, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return this.menu.mouseReleased((int) mouseX, (int) mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return this.menu.handleKey(keyCode, scanCode, GLFW.GLFW_PRESS, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        return this.menu.handleKey(keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers)
    {
        this.menu.handleTextInput(chr);

        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        super.render(context, mouseX, mouseY, delta);

        this.menu.context.setTransition(this.client.getTickDelta());
        this.menu.renderMenu(this.context, mouseX, mouseY);
    }

    @Override
    public void acceptFilePaths(String[] paths)
    {
        if (this.menu != null)
        {
            for (IFileDropListener listener : this.menu.getRoot().getChildren(IFileDropListener.class))
            {
                listener.acceptFilePaths(paths);
            }
        }
    }
}