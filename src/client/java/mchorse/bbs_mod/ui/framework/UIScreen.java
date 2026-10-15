package mchorse.bbs_mod.ui.framework;

import com.mojang.blaze3d.platform.InputConstants;
import mchorse.bbs_mod.BBSModClient;
import com.blenderbridge.actors.ActorLeaseAccess;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.importers.IImportPathProvider;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.Importers;
import mchorse.bbs_mod.importers.types.IImporter;
import mchorse.bbs_mod.mixin.client.RenderTickCounterAccessor;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.FFMpegUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if >=26.1 {
import net.minecraft.client.gui.Hud;
//?}
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?} else {
/*
*///?}
import net.minecraft.network.chat.Component;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UIScreen extends Screen implements IFileDropListener
{
    private UIBaseMenu menu;
    private UIRenderingContext context;

    public static void open(UIBaseMenu menu)
    {
        Minecraft.getInstance().setScreenAndShow(new UIScreen(Component.empty(), menu));
    }

    public static UIBaseMenu getCurrentMenu()
    {
        Screen currentScreen = Minecraft.getInstance().gui.screen();

        if (currentScreen instanceof UIScreen uiScreen)
        {
            return uiScreen.menu;
        }

        return null;
    }

    public UIScreen(Component title, UIBaseMenu menu)
    {
        /* Screen.minecraft is final in 26.2 — it is set through this constructor instead of
         * being assigned afterwards. */
        //? if >=1.21.9 {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, title);
        //?} else {
        /*super(title);
        *///?}

        this.menu = menu;

        /* 26.2: there is no GuiGraphicsExtractor to build up front any more. The GuiGraphicsExtractor
         * only exists for the duration of one extraction pass and is handed in by vanilla, so
         * the batcher starts without one and gets it in extractRenderState(). */
        this.context = new UIRenderingContext(null);

        this.menu.context.setup(this.context);
    }

    public UIBaseMenu getMenu()
    {
        return this.menu;
    }

    public void update()
    {
        ActorLeaseAccess.ui(() ->
        {
            this.menu.update();
            return true;
        });
    }

    public void renderInWorld(LevelRenderContext context)
    {
        this.menu.renderInWorld(context);
    }

    /**
     * 26.2 dropped {@code Options.hideGui}; the flag lives on {@link Hud} now and is only
     * reachable through a toggle, so it is set by comparing first.
     */
    private static void setHudHidden(boolean hidden)
    {
        //? if >=26.1 {
        Hud hud = Minecraft.getInstance().gui.hud;

        if (hud.isHidden() != hidden)
        {
            hud.toggle();
        }
        //?} else {
        /*Minecraft.getInstance().options.hideGui = hidden;
        *///?}
    }

    @Override
    public void onFilesDrop(List<Path> paths)
    {
        super.onFilesDrop(paths);

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
        this.menu.context.unfocus();

        BBSModClient.setCustomGUIScale(false);
        Minecraft.getInstance().resizeGui();

        super.removed();

        this.menu.onClose(null);

        if (this.menu.canHideHUD())
        {
            setHudHidden(false);
        }
    }

    @Override
    public void added()
    {
        BBSModClient.setCustomGUIScale(true);
        Minecraft.getInstance().resizeGui();

        super.added();

        this.menu.onOpen(null);

        if (this.menu.canHideHUD())
        {
            setHudHidden(true);
        }
    }

    @Override
    public boolean isPauseScreen()
    {
        return this.menu.canPause();
    }

    @Override
    protected void init()
    {
        super.init();

        this.menu.resize(UISkala.groesse(this.width), UISkala.groesse(this.height));
    }

    //? if >=1.21.9 {
    @Override
    public void resize(int width, int height)
    {
        super.resize(width, height);

        this.menu.resize(UISkala.groesse(width), UISkala.groesse(height));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        return ActorLeaseAccess.ui(() -> this.menu.mouseClicked(UISkala.nachBbs(event.x()), UISkala.nachBbs(event.y()), Window.legacyMouseButton(event.button())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        return ActorLeaseAccess.ui(() -> this.menu.mouseScrolled(UISkala.nachBbs(mouseX), UISkala.nachBbs(mouseY), horizontalAmount, verticalAmount));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        return ActorLeaseAccess.ui(() -> this.menu.mouseReleased(UISkala.nachBbs(event.x()), UISkala.nachBbs(event.y()), Window.legacyMouseButton(event.button())));
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        return ActorLeaseAccess.ui(() -> this.menu.handleKey(event.key(), event.scancode(), BBSRendering.lastAction, event.modifiers()));
    }

    @Override
    public boolean keyReleased(KeyEvent event)
    {
        return ActorLeaseAccess.ui(() -> this.menu.handleKey(event.key(), event.scancode(), InputConstants.RELEASE, event.modifiers()));
    }

    @Override
    public boolean charTyped(CharacterEvent event)
    {
        /* 26.2 delivers a codepoint instead of a char; BBS' text input is char based, so
         * anything outside the BMP (surrogate pairs) is not representable here. */
        ActorLeaseAccess.ui(() ->
        {
            this.menu.handleTextInput((char) event.codepoint());
            return true;
        });

        return true;
    }
    //?} else {
    /*@Override
    public void resize(Minecraft minecraft, int width, int height)
    {
        /^ added() ruft resizeGui() noch VOR init auf; vor 1.21.9 ist minecraft dann null
         * (Screen.setInitialFocus warf, gemessen). Die Groesse setzt danach init selbst. ^/
        if (this.minecraft == null)
        {
            return;
        }

        super.resize(minecraft, width, height);

        this.menu.resize(width, height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return ActorLeaseAccess.ui(() -> this.menu.mouseClicked((int) mouseX, (int) mouseY, button));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        return ActorLeaseAccess.ui(() -> this.menu.mouseScrolled((int) mouseX, (int) mouseY, horizontalAmount, verticalAmount));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return ActorLeaseAccess.ui(() -> this.menu.mouseReleased((int) mouseX, (int) mouseY, button));
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers)
    {
        return ActorLeaseAccess.ui(() -> this.menu.handleKey(key, scancode, BBSRendering.lastAction, modifiers));
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers)
    {
        return ActorLeaseAccess.ui(() -> this.menu.handleKey(key, scancode, InputConstants.RELEASE, modifiers));
    }

    @Override
    public boolean charTyped(char character, int modifiers)
    {
        ActorLeaseAccess.ui(() ->
        {
            this.menu.handleTextInput(character);
            return true;
        });

        return true;
    }
    *///?}

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {}

    /**
     * 26.2: {@code Screen.render(GuiGraphicsExtractor, ...)} is gone. Drawing happens in two phases —
     * this pass only APPENDS elements to the GuiRenderState behind {@code context};
     * {@code GuiRenderer.render()} draws them afterwards.
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta)
    {
        super.extractRenderState(context, mouseX, mouseY, delta);

        /* The extractor is per pass, so hand this frame's one to the batcher before drawing. */
        this.context.batcher.setContext(context);

        DeltaTracker tick = this.minecraft.getDeltaTracker();

        this.menu.context.setTransition(tick instanceof RenderTickCounterAccessor accessor ? accessor.bbs$getTickDelta() : tick.getGameTimeDeltaPartialTick(false));

        /* TEST-AUTONOMIE: normalfall = beide aufrufe geben mouseX/mouseY unveraendert zurueck
         * (ein volatile-read). Nur waehrend eines programmatischen zuges liefern sie die
         * gesetzte position — ohne das koennte kein test etwas ZIEHEN, weil ziehende elemente
         * die maus pro frame hier neu lesen. Siehe VirtuelleMaus. */
        /* Zwischenwerte beim UI-Scale: Minecraft rechnet mit der naechsthoeheren ganzen Zahl,
         * BBS verkleinert sich hier um den Rest (UISkala). Vanilla rechnet die Matrix selbst
         * in seine Ausschnitte ein. */
        float rest = UISkala.rest();

        context.pose().pushMatrix();
        context.pose().scale(rest, rest);

        ActorLeaseAccess.ui(() ->
        {
            this.menu.renderMenu(this.context,
                mchorse.bbs_mod.sonden.Sonden.eingabe.mausX(UISkala.nachBbs(mouseX)),
                mchorse.bbs_mod.sonden.Sonden.eingabe.mausY(UISkala.nachBbs(mouseY)));
            this.menu.context.render.executeRunnables();
            return true;
        });

        context.pose().popMatrix();
    }

    @Override
    public void acceptFilePaths(String[] paths)
    {
        if (this.menu != null)
        {
            if (!FFMpegUtils.checkFFMPEG())
            {
                this.menu.context.notifyError(UIKeys.IMPORTER_FFMPEG_NOTIFICATION);

                return;
            }

            File directory = null;
            boolean open = true;

            for (IImportPathProvider provider : this.menu.getRoot().getChildren(IImportPathProvider.class))
            {
                directory = provider.getImporterPath();

                if (directory != null)
                {
                    open = false;

                    break;
                }
            }

            List<File> files = new ArrayList<>();

            for (String path : paths)
            {
                File file = new File(path);

                if (file.exists())
                {
                    files.add(file);
                }
            }

            ImporterContext context = new ImporterContext(files, directory);

            for (IImporter importer : Importers.getImporters())
            {
                if (importer.canImport(context))
                {
                    importer.importFiles(context);

                    if (open)
                    {
                        UIUtils.openFolder(context.getDestination(importer));
                    }

                    this.menu.context.notifySuccess(UIKeys.IMPORTER_SUCCESS_NOTIFICATION.format(importer.getName()));

                    return;
                }
            }
        }
    }
}
