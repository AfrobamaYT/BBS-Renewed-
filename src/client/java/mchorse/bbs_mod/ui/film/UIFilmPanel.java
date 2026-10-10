package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.DataTab;
import mchorse.bbs_mod.ui.dashboard.panels.tabs.UIDataTabs;
import mchorse.bbs_mod.ui.dashboard.utils.IUIOrbitKeysHandler;
import mchorse.bbs_mod.ui.film.audio.UIAudioRecorder;
import mchorse.bbs_mod.ui.film.controller.ControlMouse;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.UIFilmUndoHandler;
import mchorse.bbs_mod.ui.film.dock.DockStackGeometry;
import mchorse.bbs_mod.ui.film.dock.DockStackInfo;
import mchorse.bbs_mod.ui.film.dock.DropZonen;
import mchorse.bbs_mod.ui.film.utils.undo.FilmUndoHistoryDock;
import mchorse.bbs_mod.ui.film.utils.undo.UndoOverlayAndocken;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.film.utils.undo.UndoPersistence;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.presets.PresetManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class UIFilmPanel extends UIDataDashboardPanel<Film> implements IFlightSupported, IUIOrbitKeysHandler, ICursor
{
    private static final int PREVIEW_MODE_EXPORT = 0;
    private static final int PREVIEW_MODE_CUSTOM = 1;
    private static final int PREVIEW_MODE_AUTO = 2;

    private static final Logger LOGGER = LogUtils.getLogger();

    private RunnerCameraController runner;
    private boolean lastRunning;
    private final Position position = new Position(0, 0, 0, 0, 0);
    /** Writes the live camera into the camera clip — while flying, and while a take plays. */
    private final FlightClipRecorder flightRecorder = new FlightClipRecorder(this, this.position);

    public UIFilmSelectionPanel selectionPanel;

    public UIElement main;
    public UIElement editArea;
    /** Zeitleiste im neuen Designsystem — sichtbarer Inhalt der editArea-Flaeche. */
    public UIElement zeitleisteNano;
    /** Gemerktes Layout, solange eine Flaeche maximiert ist; null = normale Aufteilung. */
    private EditorLayoutNode layoutVorMaximieren;
    private final List<UIDraggable> splitterHandles = new ArrayList<>();
    private final List<EditorLayoutNode.SplitterHandleInfo> splitterHandleInfos = new ArrayList<>();
    public UIFilmRecorder recorder;
    public UIFilmPreview preview;

    public UIIcon duplicateFilm;

    /* Main editors */
    public UIClipsPanel cameraEditor;
    public UIReplaysEditor replayEditor;
    public UIClipsPanel actionEditor;

    /* Icon bar buttons */
    public UIIcon openFilmMenu;
    public UIIcon openCameraEditor;

    /** When true, docking is disabled; panel drag handles and their top offset are hidden. */
    private boolean layoutLocked = true;

    private UICopyPasteController layoutPresetsController;

    private Camera camera = new Camera();
    private boolean entered;
    public boolean playerToCamera;

    /* Entity control */
    private UIFilmController controller = new UIFilmController(this);
    private UIFilmUndoHandler undoHandler;

    public final Matrix4f lastView = new Matrix4f();
    public final Matrix4f lastProjection = new Matrix4f();

    private long lastTime;
    private double timeSpentActiveAccumulator;
    private final FilmEditorUserActivity filmUserActivity = new FilmEditorUserActivity();

    private List<UIElement> panels = new ArrayList<>();
    private UIElement secretPlay;

    private boolean newFilm;
    private double timelineXMin = Double.NaN;
    private double timelineXMax = Double.NaN;
    /** Vertical timeline scroll per film, so switching film tabs restores where each one was left. */
    private final Map<String, FilmTimelineScroll> timelineScrollByFilm = new HashMap<>();

    private FilmQueueExporter queueExporter;

    /* Docking: layout panels and drag-to-swap/split */
    private final Map<String, UIElement> panelById = new LinkedHashMap<>();
    private final Map<String, UIDraggable> dragHandlesById = new LinkedHashMap<>();
    private static final float DRAG_HANDLE_HEIGHT_NORM = 0.02F;
    private static final float DRAG_HANDLE_TOP_OFFSET_NORM = 0.01F;
    private static final int SPLITTER_HANDLE_PX = 8; /* ±4: 14 kaperte Kopf-Zuege (Akte 36) */
    private static final int SPLITTER_HANDLE_LINE_PX = 1;
    private static final int SPLITTER_LINK_HITBOX_PADDING_PX = 8;
    private static final int EDITOR_MIN_SIZE_FOR_PX_HANDLES = 10;
    private static final int DOCK_STACK_TABS_HEIGHT_PX = 20;
    private static final String PANEL_MAIN_ID = "main";
    private static final String PANEL_PREVIEW_ID = "preview";
    private static final String PANEL_EDIT_AREA_ID = "editArea";
    private static final String PANEL_REPLAYS_LIST_ID = "replaysList";
    private static final String PANEL_REPLAY_PROPS_ID = "replayProps";
    private static final String PANEL_UNDO_HISTORY_ID = FilmUndoHistoryDock.PANEL_ID;
    /** Top offset (px) for parameters panels when layout is unlocked (space for drag icon). Used for lock button size too. */
    public static final int EDIT_PANEL_TOP_OFFSET_PX = 20;
    private static final int FILM_TOP_BAR_BUTTON_SIZE = UIDataTabs.TABS_HEIGHT_PX;
    private static final int FILM_TOP_BAR_SEPARATOR_WIDTH = 8;
    private static final int FILM_TOP_BAR_ACTIONS_WIDTH = FILM_TOP_BAR_BUTTON_SIZE * 3 + FILM_TOP_BAR_SEPARATOR_WIDTH;
    /** The undo history as a sixth dock panel; it also owns the on/off state and the default slot. */
    private final FilmUndoHistoryDock undoHistoryDock = new FilmUndoHistoryDock(
        () -> this.undoHandler == null ? null : this.undoHandler.getUndoManager(),
        this::getData, this::getCurrentFilmLayoutRoot, this::setCurrentFilmLayoutRoot, this::refreshCurrentFilmLayout);
    private final List<UIDockStackTabs> dockStackTabs = new ArrayList<>();
    private final Map<String, DockStackInfo> dockStackByPanelId = new HashMap<>();
    private UIElement selectedMainEditorPanel;
    private UIElement topBarActions;
    private UIElement topBarSeparator;

    private String draggingPanelId;
    private String dropTargetPanelId;
    private int dropTargetZone = DropZonen.CENTER;

    /* Overlay-Andocken: der Wunsch wird im Maus-Release nur VORGEMERKT und am Anfang des
     * naechsten render() abgearbeitet — nie mitten im Render-/Event-Baumlauf umbauen
     * (die vierfach belegte ConcurrentModification-Klasse dieser Codebasis). */
    private DropZonen.Ziel undoAndockWunsch;
    private int letzteZugMausX;
    private int letzteZugMausY;
    private final List<Integer> draggedSplitterIndices = new ArrayList<>();

    private static class UIDockStackTabs extends UIElement
    {
        private final UIFilmPanel panel;
        private String anchorPanelId;
        private final List<String> panelIds = new ArrayList<>();
        private String activePanelId;

        public UIDockStackTabs(UIFilmPanel panel)
        {
            this.panel = panel;
        }

        public void configure(DockStackInfo info)
        {
            this.anchorPanelId = info.getAnchorPanelId();
            this.panelIds.clear();
            this.panelIds.addAll(info.panelIds);
            this.activePanelId = info.activePanelId;
            this.setVisible(info.isStacked());
        }

        public boolean matches(DockStackInfo info)
        {
            return this.anchorPanelId.equals(info.getAnchorPanelId()) && this.panelIds.equals(info.panelIds);
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (!this.isVisible() || context.mouseButton != 0 || !this.area.isInside(context) || this.panelIds.isEmpty())
            {
                return super.subMouseClicked(context);
            }

            int index = this.getTabIndex(context.mouseX);

            if (index >= 0 && index < this.panelIds.size())
            {
                this.panel.activateDockStackTab(this.anchorPanelId, this.panelIds.get(index));

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            if (!this.isVisible() || this.panelIds.isEmpty())
            {
                return;
            }

            if (this.area.isInside(context))
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            int tabSize = this.getTabSize();
            int hovered = this.area.isInside(context.mouseX, context.mouseY) ? this.getTabIndex(context.mouseX) : -1;
            int y = this.area.y;
            int ey = this.area.ey();

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.chromeSurface());

            for (int i = 0; i < this.panelIds.size(); i++)
            {
                int x = this.area.x + i * tabSize;

                if (x >= this.area.ex())
                {
                    break;
                }

                int ex = Math.min(this.area.ex(), x + tabSize);
                String panelId = this.panelIds.get(i);
                boolean active = panelId.equals(this.activePanelId);
                Icon icon = this.panel.getDockPanelIcon(panelId);

                if (active)
                {
                    Area.SHARED.set(x, y, ex - x, ey - y);
                    UIDashboardPanels.renderHighlight(context.batcher, Area.SHARED, Direction.BOTTOM);
                }

                context.batcher.icon(icon, Colors.WHITE, (x + ex) / 2, (y + ey) / 2, 0.5F, 0.5F);
            }

            super.render(context);
        }

        private int getTabSize()
        {
            return Math.max(1, this.area.h);
        }

        private int getTabIndex(int mouseX)
        {
            int index = (mouseX - this.area.x) / this.getTabSize();

            if (index < 0 || index >= this.panelIds.size())
            {
                return -1;
            }

            return index;
        }

        public String getPanelIdAt(int mouseX)
        {
            if (this.panelIds.isEmpty())
            {
                return this.anchorPanelId;
            }

            int index = this.getTabIndex(mouseX);

            if (index < 0)
            {
                return null;
            }

            return this.panelIds.get(index);
        }
    }

    /**
     * Initialize the camera editor with a camera profile.
     */
    public UIFilmPanel(UIDashboard dashboard)
    {
        super(dashboard);
        this.enableTabs();
        this.playerToCamera = BBSSettings.editorPlayerFollowsCamera.get();

        this.runner = new RunnerCameraController(this, (playing) ->
        {
            this.notifyServer(playing ? ActionState.PLAY : ActionState.PAUSE);
        });
        this.runner.getContext().captureSnapshots();

        this.recorder = new UIFilmRecorder(this);

        this.main = new UIElement();
        this.editArea = new UIElement();
        this.preview = new UIFilmPreview(this);
        this.panelById.put(PANEL_MAIN_ID, this.main);
        this.panelById.put(PANEL_PREVIEW_ID, this.preview);
        this.panelById.put(PANEL_EDIT_AREA_ID, this.editArea);

        /* Zeitleiste im neuen Designsystem als eigene Flaeche (Blender: Dope Sheet ist ein Editor).
         * Sie sitzt in der main-Flaeche — im Entwurf laeuft der Dope Sheet ueber die VOLLE Breite
         * unter dem Viewport, nicht als schmale Spalte rechts. Sie wird nach dem alten Clip-Editor
         * eingehaengt und deckt ihn damit ab; dessen Logik bleibt als Datenhalter erreichbar. */
        this.zeitleisteNano = UIZeitleisteNano.neu();

        /* Editors */
        this.cameraEditor = new UIClipsPanel(this, BBSMod.getFactoryCameraClips()).target(this.editArea);
        /* ⛔ 26 px unter der Kopfzeile beginnen, nicht bei full(). UIClipsPanel bekommt
         * Klicks VOR der spaeter eingehaengten Zeitleiste (Element-Index 5 gegen 7) und
         * schluckte damit jeden Klick auf den Editor-Typ-Waehler — die Zone war da, das
         * Eingabefeld stimmte, der Klick kam nur nie an. Zwei Flaechen duerfen sich nicht
         * ueberlappen, wenn beide Eingaben wollen. */
        this.cameraEditor.relative(this.main).x(0).y(26).w(1F).h(1F, -26);

        this.cameraEditor.clips.context((menu) ->
        {
            UIAudioRecorder.addOption(this, menu);
        });

        this.replayEditor = new UIReplaysEditor(this);
        /* ⛔ WIE DER KAMERA-ZWEIG: unter der Kopfzeile beginnen. `full(main)` legte den
         * Replay-Editor ueber die ganze Flaeche — also auch ueber `UIZeitleisteNano`, die
         * dort den Kopf zeichnet. Beide standen sichtbar auf y=353..486, und der spaeter
         * gezeichnete Replay-Editor uebermalte den Kopf restlos: wer ein Replay auswaehlte,
         * bekam die Timeline OHNE Transport, Marker/Key und Editor-Waehler zu sehen. */
        this.replayEditor.relative(this.main).x(0).y(26).w(1F).h(1F, -26).setVisible(false);
        this.actionEditor = new UIClipsPanel(this, BBSMod.getFactoryActionClips()).target(this.editArea);
        this.actionEditor.setVisible(false);
        this.replayEditor.attachActionTimeline(this.actionEditor);

        this.panelById.put(PANEL_REPLAYS_LIST_ID, this.replayEditor.replaysList);
        this.panelById.put(PANEL_REPLAY_PROPS_ID, this.replayEditor.replayProperties);
        /* Registered unconditionally so it gets a drag handle and can take part in stacks like any
         * other panel; whether it is SHOWN follows from the layout tree (see updateTabVisibility). */
        this.panelById.put(PANEL_UNDO_HISTORY_ID, this.undoHistoryDock.getPanel());
        this.selectedMainEditorPanel = this.cameraEditor;

        /* Film panel keeps common CRUD actions inside film settings menu instead of the sidebar. */
        this.iconBar.remove(this.openOverlay);
        this.iconBar.remove(this.saveIcon);

        /* Icon bar buttons */
        this.openFilmMenu = new UIIcon(Icons.MORE, (b) ->
        {
            this.getContext().replaceContextMenu(this::fillFilmContextMenu);
        });
        this.openCameraEditor = new UIIcon(Icons.FRUSTUM, (b) -> this.showPanel(this.cameraEditor));

        this.layoutPresetsController = new UICopyPasteController(PresetManager.LAYOUTS, "_CopyFilmLayout")
            .supplier(this::getFilmLayoutPresetData)
            .consumer(this::applyFilmLayoutFromPreset);

        this.openFilmMenu.wh(FILM_TOP_BAR_BUTTON_SIZE, FILM_TOP_BAR_BUTTON_SIZE).tooltip(UIKeys.FILM_OPTIONS, Direction.BOTTOM);
        this.openCameraEditor.wh(FILM_TOP_BAR_BUTTON_SIZE, FILM_TOP_BAR_BUTTON_SIZE).tooltip(UIKeys.FILM_OPEN_CAMERA_EDITOR, Direction.BOTTOM);

        this.topBarActions = new UIElement();
        this.topBarActions.relative(this.tabBar).x(1F, -FILM_TOP_BAR_ACTIONS_WIDTH).w(FILM_TOP_BAR_ACTIONS_WIDTH).h(UIDataTabs.TABS_HEIGHT_PX).row(0).resize();
        this.topBarSeparator = new UIElement();
        this.topBarSeparator.wh(FILM_TOP_BAR_SEPARATOR_WIDTH, UIDataTabs.TABS_HEIGHT_PX);
        this.topBarActions.add(new UIRenderable(this::renderTopBarActions), this.openCameraEditor, this.topBarSeparator, this.openFilmMenu);
        this.tabBar.add(this.topBarActions);

        /* Setup elements */

        this.editor.add(new UIRenderable(this::renderPanelSurfaces), this.main, new UIRenderable(this::renderPanelBorders), new UIRenderable(this::renderDropZoneHighlight));
        for (String id : this.panelById.keySet())
        {
            UIDraggable handle = this.createPanelDragHandle(id);
            this.dragHandlesById.put(id, handle);
            this.editor.add(handle);
        }
        /* ⛔ cameraEditor traegt WIEDER die Zeitleiste — mit dem neuen Aussehen.
         *
         * Der Umbau hatte UIClips durch UIZeitleisteNano ersetzt und damit 88 von 89 Methoden
         * verloren: Auswahl, Kollisionen, Einrasten, Ebenen, Kopieren, Rahmenauswahl. Gemessen
         * am 2026-08-20 ist UIClips zu rund 85 % LOGIK und zu 15 % Zeichnen — der Neubau warf
         * die 85 % weg, um die 15 % zu ersetzen, und jede Nutzermeldung seither war ein Stueck
         * davon.
         *
         * Jetzt umgekehrt: UIClips behaelt seine Logik, nur `renderCameraWork` zeichnet ueber
         * NanoVG. Das Aussehen bleibt neu, die Bedienung ist wieder vollstaendig. */
        /* ⛔ BEIDE, verschachtelt — nicht entweder-oder.
         *
         * Erst hatte UIZeitleisteNano UIClips ERSETZT: neues Aussehen, aber 88 von 89 Methoden
         * weg (Auswahl, Kollisionen, Einrasten, Ebenen …). Dann UIClips zurueckgeholt und die
         * Zeitleiste ausgehaengt: Logik vollstaendig, aber Kopfzeile und Kanalliste weg — der
         * Nutzer sah eine nackte alte Zeitachse.
         *
         * Jetzt traegt UIZeitleisteNano die KOPFZEILE (Editor-Typ, Transport, Marker/Key,
         * Loop-Ring) und UIClips die Flaeche darunter. Aussehen neu, Logik vollstaendig. */
        /* ⛔ zeitleisteNano NACH cameraEditor: wer spaeter eingehaengt wird, zeichnet
         * obendrauf. Davor lag die Kopfzeile auf Element-Index 5 und UIClips auf 8 — die
         * Kopfzeile war da (y=162 h=26), wurde aber vom Clip-Panel ueberzeichnet und fehlte
         * im Bild vollstaendig. */
        this.main.add(this.editArea, this.cameraEditor, this.zeitleisteNano, this.replayEditor, this.preview, this.replayEditor.replaysList, this.replayEditor.replayProperties, this.undoHistoryDock.getPanel());
        /* ⛔ Relativ zum cameraEditor, NICHT zu `main`. Bei `relative(main).y(0)` sass die
         * Kopfzeile am oberen Rand des Panels — also unter der Topbar und weit weg von der
         * Zeitleiste, wo sie hingehoert. Im Bild fehlte sie schlicht.
         * 26 px ist NanoDesign.H_KOPF, dieselbe Hoehe, die die Flaeche selbst annimmt. */
        /* Volle Hoehe: die Flaeche traegt die Kopfzeile OBEN und die Kanalspalte LINKS.
         * Die Clip-Flaeche darunter/daneben gehoert UIClips. */
        this.zeitleisteNano.relative(this.cameraEditor).x(0).y(-26).w(1F).h(1F, 26);
        this.add(this.controller);
        this.overlay.namesList.setFileIcon(Icons.FILM);

        /* Register keybinds */
        IKey modes = UIKeys.CAMERA_EDITOR_KEYS_MODES_TITLE;
        IKey editor = UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE;
        IKey looping = UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TITLE;
        Supplier<Boolean> active = () -> !this.isFlying();

        /* Keyframe-Tasten wie in Blender: I setzt, Entf loescht, E fuehrt zu den Kamera-Clips.
         * Am PANEL registriert — die Zeitleiste bekommt den Tastenfokus nur beim Klick in
         * sie hinein, und dann waeren die Tasten je nach letztem Klick mal da, mal nicht. */
        this.keys().register(new mchorse.bbs_mod.ui.utils.keys.KeyCombo("nano_key_insert",
            mchorse.bbs_mod.l10n.keys.IKey.raw("Insert keyframe"), org.lwjgl.glfw.GLFW.GLFW_KEY_I),
            () -> ((UIZeitleisteNano) this.zeitleisteNano).keyframeAmCursor(true)).active(active).category(editor);
        this.keys().register(new mchorse.bbs_mod.ui.utils.keys.KeyCombo("nano_key_delete",
            mchorse.bbs_mod.l10n.keys.IKey.raw("Delete keyframe"), org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE),
            () -> ((UIZeitleisteNano) this.zeitleisteNano).keyframeAmCursor(false)).active(active).category(editor);
        this.keys().register(new mchorse.bbs_mod.ui.utils.keys.KeyCombo("nano_key_camera",
            mchorse.bbs_mod.l10n.keys.IKey.raw("Camera clips"), org.lwjgl.glfw.GLFW.GLFW_KEY_E),
            () -> ((UIZeitleisteNano) this.zeitleisteNano).modusUmschalten(2)).active(active).category(editor);

        this.keys().register(Keys.PLAUSE, () -> this.preview.plause.clickItself()).active(active).category(editor);
        this.keys().register(Keys.NEXT_CLIP, () -> this.setCursor(this.data.camera.findNextTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.PREV_CLIP, () -> this.setCursor(this.data.camera.findPreviousTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.NEXT, () -> this.setCursor(this.getCursor() + 1)).active(active).category(editor);
        this.keys().register(Keys.PREV, () -> this.setCursor(this.getCursor() - 1)).active(active).category(editor);
        this.keys().register(Keys.UNDO, this::undo).category(editor);
        this.keys().register(Keys.REDO, this::redo).category(editor);
        this.keys().register(Keys.FLIGHT, this::toggleFlight).active(() -> this.data != null).category(modes);
        this.keys().register(Keys.LOOPING, () ->
        {
            BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
            this.getContext().notifyInfo(UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TOGGLE_NOTIFICATION);
        }).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MIN, () -> this.cameraEditor.clips.setLoopMin()).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MAX, () -> this.cameraEditor.clips.setLoopMax()).active(active).category(looping);
        this.keys().register(Keys.JUMP_FORWARD, () -> this.setCursor(this.getCursor() + BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.JUMP_BACKWARD, () -> this.setCursor(this.getCursor() - BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_CYCLE_EDITORS, () ->
        {
            this.showPanel(MathUtils.cycler(this.getPanelIndex() + 1, this.panels));
            UIUtils.playClick();
        }).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_TOGGLE_ACTIONS, () ->
        {
            this.replayEditor.setActionsMode(!this.replayEditor.isActionsMode());
            UIUtils.playClick();
        }).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_NEXT_DOCK_TAB, () ->
        {
            if (this.cycleDockStackTab(1))
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_PREV_DOCK_TAB, () ->
        {
            if (this.cycleDockStackTab(-1))
            {
                UIUtils.playClick();
            }
        }).active(active).category(editor);

        this.selectionPanel = new UIFilmSelectionPanel(this);

        this.fill(null);

        this.setupEditorFlex(false);
        this.flightRecorder.mark();

        this.panels.add(this.cameraEditor);
        this.panels.add(this.replayEditor);

        this.secretPlay = new UIElement();
        this.secretPlay.keys().register(Keys.PLAUSE, () -> this.preview.plause.clickItself()).active(() -> !this.isFlying() && !this.canBeSeen() && this.data != null).category(editor);

        this.setUndoId("film_panel");
        this.cameraEditor.setUndoId("camera_editor");
        this.replayEditor.setUndoId("replay_editor");
        this.actionEditor.setUndoId("action_editor");

        UIElement element = new UIElement()
        {
            @Override
            protected boolean subMouseScrolled(UIContext context)
            {
                if (Window.isCtrlPressed() && !UIFilmPanel.this.isFlying() && UIFilmPanel.this.isCursorOverTimeline(context))
                {
                    int magnitude = Window.isShiftPressed() ? BBSSettings.editorJump.get() : 1;
                    int newCursor = UIFilmPanel.this.getCursor() + (int) Math.copySign(magnitude, context.mouseWheel);

                    UIFilmPanel.this.setCursor(newCursor);

                    return true;
                }

                return super.subMouseScrolled(context);
            }
        };

        this.add(element);
        this.add(new UIFilmPanelUndoKeys(this).full(this));

        IValueListener refreshPreviewOnVideoResolution = (v, f) ->
        {
            if (this.isVisible()) this.applyPreviewSizeToBBS();
        };
        BBSSettings.videoWidth.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.videoHeight.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewSizeMode.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewCustomWidth.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewCustomHeight.postCallback(refreshPreviewOnVideoResolution);
        BBSSettings.editorPreviewResolutionScale.postCallback(refreshPreviewOnVideoResolution);

        this.selectionPanel.relative(this).y(UIDataTabs.TABS_HEIGHT_PX).wTo(this.iconBar.area).h(1F, -UIDataTabs.TABS_HEIGHT_PX);
        this.add(this.selectionPanel);
    }

    private boolean isCursorOverTimeline(UIContext context)
    {
        return this.isCursorOverClipsTimeline(this.cameraEditor, context)
            || this.isCursorOverClipsTimeline(this.actionEditor, context)
            || this.isCursorOverReplayTimeline(context);
    }

    private boolean isCursorOverClipsTimeline(UIClipsPanel panel, UIContext context)
    {
        return panel != null
            && panel.isVisible()
            && panel.clips != null
            && panel.clips.isVisible()
            && panel.clips.area.isInside(context);
    }

    private boolean isCursorOverReplayTimeline(UIContext context)
    {
        return this.replayEditor != null
            && this.replayEditor.isVisible()
            && this.replayEditor.keyframeEditor != null
            && this.replayEditor.keyframeEditor.view != null
            && this.replayEditor.keyframeEditor.view.isVisible()
            && this.replayEditor.keyframeEditor.view.area.isInside(context);
    }

    public boolean isLayoutLocked()
    {
        return this.layoutLocked;
    }

    /** Top offset (px) for parameters panels; 0 when layout locked. */
    public int getEditPanelTopOffsetPx()
    {
        return this.layoutLocked ? 0 : EDIT_PANEL_TOP_OFFSET_PX;
    }

    @Override
    protected int getSidebarWidthPx()
    {
        return 0;
    }

    @Override
    protected int getTabsRightInsetPx()
    {
        return FILM_TOP_BAR_ACTIONS_WIDTH;
    }

    @Override
    public IKey getNewTabLabel()
    {
        return UIKeys.FILM_TABS_NEW_TAB;
    }

    @Override
    public Icon getTabIcon(DataTab tab)
    {
        return tab != null && tab.dataId == null ? Icons.SEARCH : Icons.FILM;
    }

    public void renameFilmId(String from, String to)
    {
        if (from == null || to == null || from.equals(to))
        {
            return;
        }

        if (this.data != null && from.equals(this.data.getId()))
        {
            this.data.setId(to);
        }

        this.onDataRenamed(from, to);
    }

    public void renameFilmFolder(String fromPath, String name)
    {
        if (fromPath == null || name == null || name.trim().isEmpty())
        {
            return;
        }

        String oldPrefix = fromPath + "/";
        int slash = fromPath.lastIndexOf('/');
        String parentPath = slash >= 0 ? fromPath.substring(0, slash + 1) : "";
        String newPrefix = parentPath + name + "/";

        if (this.data != null)
        {
            String id = this.data.getId();

            if (id != null && id.startsWith(oldPrefix))
            {
                this.data.setId(newPrefix + id.substring(oldPrefix.length()));
            }
        }

        this.onDataFolderRenamed(fromPath, name);
    }

    public void deleteFilmIds(Set<String> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return;
        }

        for (String id : ids)
        {
            this.onDataRemoved(id);
        }

        this.updateTabVisibility();
    }

    public void deleteFilmFolders(Set<String> folderPaths)
    {
        if (folderPaths == null || folderPaths.isEmpty())
        {
            return;
        }

        for (String folder : folderPaths)
        {
            if (folder != null && !folder.isEmpty())
            {
                this.onDataFolderRemoved(folder);
            }
        }

        this.updateTabVisibility();
    }

    public void updateTabVisibility()
    {
        boolean hasFilm = this.hasFilmInCurrentTab();

        if (!hasFilm)
        {
            for (UIElement panel : this.panelById.values())
            {
                panel.setVisible(false);
            }
        }
        else
        {
            for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
            {
                boolean active = PANEL_MAIN_ID.equals(entry.getKey()) || this.isDockPanelActive(entry.getKey());

                entry.getValue().setVisible(active);
            }
        }

        this.updateMainEditorVisibility(hasFilm);

        for (Map.Entry<String, UIDraggable> entry : this.dragHandlesById.entrySet())
        {
            DockStackInfo stack = this.dockStackByPanelId.get(entry.getKey());
            boolean active = stack != null && entry.getKey().equals(stack.activePanelId);

            entry.getValue().setVisible(hasFilm && !this.layoutLocked && active);
        }

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.setVisible(hasFilm);
        }

        this.selectionPanel.setVisible(!hasFilm);
    }

    private boolean hasFilmInCurrentTab()
    {
        DataTab tab = this.getCurrentDataTab();

        return tab != null && tab.dataId != null;
    }

    private boolean isDockPanelActive(String panelId)
    {
        DockStackInfo stack = this.dockStackByPanelId.get(panelId);

        return stack != null && panelId.equals(stack.activePanelId);
    }

    private boolean isMainPanelActive()
    {
        DockStackInfo mainStack = this.dockStackByPanelId.get(PANEL_MAIN_ID);

        return mainStack == null || PANEL_MAIN_ID.equals(mainStack.activePanelId);
    }

    private boolean isEditAreaPanelActive()
    {
        DockStackInfo editAreaStack = this.dockStackByPanelId.get(PANEL_EDIT_AREA_ID);

        /* ⛔ Kein Stack heisst NICHT „aktiv". Seit editArea aus dem Standardlayout gefallen ist,
         * gibt es fuer sie keinen Stack — mit dem alten `== null || …` galten die Clip-Felder
         * damit als sichtbar und wurden ohne Flaeche gezeichnet: sie landeten oben links im
         * Bild, quer ueber Topbar und Vorschau. Genau die „alte Haut", die durchschien. */
        return editAreaStack != null && PANEL_EDIT_AREA_ID.equals(editAreaStack.activePanelId);
    }

    private void updateMainEditorVisibility(boolean hasFilm)
    {
        UIElement selected = this.selectedMainEditorPanel == null ? this.cameraEditor : this.selectedMainEditorPanel;
        boolean mainActive = this.isMainPanelActive();
        boolean editAreaActive = this.isEditAreaPanelActive();
        boolean visible = hasFilm && (mainActive || editAreaActive);
        boolean cameraVisible = visible && selected == this.cameraEditor;
        boolean replayVisible = visible && selected == this.replayEditor;

        this.cameraEditor.setVisible(cameraVisible);
        this.replayEditor.setVisible(replayVisible);

        this.cameraEditor.setTimelineVisible(mainActive && cameraVisible);
        this.replayEditor.setTimelineVisible(mainActive && replayVisible);
        ZeitleisteZweig.propertiesSchalten(this, mainActive, editAreaActive, cameraVisible, replayVisible);
    }

    private void toggleLayoutLock()
    {
        this.layoutLocked = !this.layoutLocked;
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.setupEditorFlex(true);
        this.refreshEditPanelOffsets();
    }

    private Icon getDockPanelIcon(String panelId)
    {
        switch (panelId)
        {
            case PANEL_PREVIEW_ID: return Icons.VIDEO_CAMERA;
            case PANEL_EDIT_AREA_ID: return Icons.EDITOR;
            case PANEL_REPLAYS_LIST_ID: return Icons.LIST;
            case PANEL_REPLAY_PROPS_ID: return Icons.PROPERTIES;
            case PANEL_UNDO_HISTORY_ID: return Icons.UNDO;
            case PANEL_MAIN_ID: return Icons.FILM;
            default: return Icons.FILE;
        }
    }

    private void refreshEditPanelOffsets()
    {
        this.cameraEditor.refreshEditPanelOffset();
        this.actionEditor.refreshEditPanelOffset();
        this.replayEditor.refreshEditPanelOffset();
    }

    private ValueEditorLayout.FilmEditor getCurrentFilmLayoutEditor()
    {
        if (this.selectedMainEditorPanel == this.replayEditor)
        {
            return ValueEditorLayout.FilmEditor.REPLAY;
        }

        return ValueEditorLayout.FilmEditor.CAMERA;
    }

    private ValueEditorLayout getFilmLayoutSettings()
    {
        return BBSSettings.editorLayoutSettings;
    }

    private EditorLayoutNode getCurrentFilmLayoutRoot()
    {
        return this.getFilmLayoutSettings().getFilmLayoutRoot(this.getCurrentFilmLayoutEditor());
    }

    private void setCurrentFilmLayoutRoot(EditorLayoutNode root)
    {
        this.getFilmLayoutSettings().setFilmLayoutRoot(this.getCurrentFilmLayoutEditor(), root);
    }

    private List<EditorLayoutNode.SplitterNode> getCurrentFilmSplitters()
    {
        return this.getFilmLayoutSettings().getFilmSplitters(this.getCurrentFilmLayoutEditor());
    }

    private List<EditorLayoutNode.SplitterNode> getCurrentFilmSplittersForWrite()
    {
        return this.getFilmLayoutSettings().getFilmSplittersForWrite(this.getCurrentFilmLayoutEditor());
    }

    private boolean isCurrentFilmLayoutBound()
    {
        return this.getFilmLayoutSettings().isFilmLayoutBound(this.getCurrentFilmLayoutEditor());
    }

    private void refreshCurrentFilmLayout()
    {
        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.setupEditorFlex(true);
    }

    private void toggleCurrentFilmLayoutBinding()
    {
        ValueEditorLayout layout = this.getFilmLayoutSettings();
        ValueEditorLayout.FilmEditor editor = this.getCurrentFilmLayoutEditor();
        boolean bound = layout.isFilmLayoutBound(editor);

        layout.setFilmLayoutBound(editor, !bound);
        this.refreshCurrentFilmLayout();
    }

    private void activateDockStackTab(String stackPanelId, String panelId)
    {
        if (stackPanelId == null || panelId == null)
        {
            return;
        }

        EditorLayoutNode root = this.getCurrentFilmLayoutRoot();
        EditorLayoutNode next = EditorLayoutNode.copyWithStackActivePanel(root, stackPanelId, panelId);

        if (next != root)
        {
            this.setCurrentFilmLayoutRoot(next);
            this.setupEditorFlex(true);
        }
    }

    private boolean cycleDockStackTab(int offset)
    {
        if (offset == 0)
        {
            return false;
        }

        DockStackInfo stack = this.resolveDockStackForKeyboardCycle();

        if (stack == null || !stack.isStacked() || stack.panelIds.isEmpty())
        {
            return false;
        }

        int currentIndex = stack.panelIds.indexOf(stack.activePanelId);

        if (currentIndex < 0)
        {
            currentIndex = 0;
        }

        int size = stack.panelIds.size();
        int nextIndex = (currentIndex + offset) % size;

        if (nextIndex < 0)
        {
            nextIndex += size;
        }

        this.activateDockStackTab(stack.getAnchorPanelId(), stack.panelIds.get(nextIndex));

        return true;
    }

    private DockStackInfo resolveDockStackForKeyboardCycle()
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return null;
        }

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            if (!tabs.isVisible() || !tabs.area.isInside(context.mouseX, context.mouseY))
            {
                continue;
            }

            DockStackInfo hoveredStack = this.dockStackByPanelId.get(tabs.anchorPanelId);

            if (hoveredStack != null && hoveredStack.isStacked())
            {
                return hoveredStack;
            }
        }

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            UIElement panel = entry.getValue();

            if (!panel.isVisible() || !panel.area.isInside(context.mouseX, context.mouseY))
            {
                continue;
            }

            DockStackInfo stack = this.dockStackByPanelId.get(entry.getKey());

            if (stack != null && stack.isStacked())
            {
                return stack;
            }
        }

        return null;
    }

    private MapType getFilmLayoutPresetData()
    {
        MapType data = new MapType();
        data.put("film_layout", this.getCurrentFilmLayoutRoot().toData());
        return data;
    }

    private void applyFilmLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        BaseType layoutData = data.get("film_layout");
        if (layoutData == null)
        {
            return;
        }
        EditorLayoutNode root = EditorLayoutNode.fromData(layoutData);
        if (root != null)
        {
            this.setCurrentFilmLayoutRoot(root);
            this.setupEditorFlex(true);
        }
    }

    private void resetFilmLayout()
    {
        this.setCurrentFilmLayoutRoot(EditorLayoutNode.defaultFilmLayout());
        this.refreshCurrentFilmLayout();
    }

    private void setupEditorFlex(boolean resize)
    {
        this.layoutAufNeuesBildZiehen();

        EditorLayoutNode originalRoot = this.getCurrentFilmLayoutRoot();
        EditorLayoutNode root = this.ensureFilmLayoutPanels(originalRoot);

        if (root != originalRoot)
        {
            this.setCurrentFilmLayoutRoot(root);
        }

        List<EditorLayoutNode.SplitterNode> splitters = this.getCurrentFilmSplitters();

        if (resize && splitters.size() == this.splitterHandles.size())
        {
            this.updateEditorFlexBoundsOnly(root);
            this.resize();
            this.resize();
            return;
        }

        this.clearSplitterDragState();

        List<DockStackInfo> stackInfos = new ArrayList<>();
        DockStackGeometry.collect(root, 0F, 0F, 1F, 1F, stackInfos);

        for (UIElement el : this.panelById.values())
        {
            el.resetFlex();
        }

        for (UIDraggable h : this.splitterHandles)
        {
            h.removeFromParent();
        }

        this.splitterHandles.clear();

        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.removeFromParent();
        }

        this.dockStackTabs.clear();
        this.dockStackByPanelId.clear();

        for (UIDraggable h : this.dragHandlesById.values())
        {
            h.resetFlex();
        }

        this.applyPanelBoundsFromStacks(stackInfos);
        this.rebuildDockStackTabs(stackInfos);

        this.splitterHandleInfos.clear();
        EditorLayoutNode.computeSplitterHandles(root, 0F, 0F, 1F, 1F, this.splitterHandleInfos);

        for (int i = 0; i < splitters.size(); i++)
        {
            UIDraggable handle = this.createSplitterHandle(i);
            this.splitterHandles.add(handle);
            IUIElement insertAfter = i == 0 ? this.main : this.splitterHandles.get(i - 1);
            this.editor.addAfter(insertAfter, handle);
        }

        if (this.layoutLocked)
        {
            for (UIDraggable h : this.dragHandlesById.values())
            {
                h.setVisible(false);
            }
        }
        else
        {
            this.applyDragHandleBoundsFromStacks(stackInfos);
        }

        this.updateTabVisibility();

        if (resize)
        {
            this.resize();
            this.resize();
        }
    }

    private EditorLayoutNode ensureFilmLayoutPanels(EditorLayoutNode root)
    {
        HashSet<String> ids = new HashSet<>();
        this.collectPanelIds(root, ids);

        boolean hasList = ids.contains(PANEL_REPLAYS_LIST_ID);
        boolean hasProps = ids.contains(PANEL_REPLAY_PROPS_ID);

        EditorLayoutNode out = root;

        if (!hasList)
        {
            out = EditorLayoutNode.copyWithInsertSplitAt(out, PANEL_EDIT_AREA_ID, PANEL_REPLAYS_LIST_ID, EditorLayoutNode.EDGE_BOTTOM);
        }

        if (!hasProps)
        {
            out = EditorLayoutNode.copyWithInsertSplitAt(out, PANEL_REPLAYS_LIST_ID, PANEL_REPLAY_PROPS_ID, EditorLayoutNode.EDGE_RIGHT);
        }

        /* Last, so the undo history's default anchor (the replay list) is guaranteed to exist by now. */
        return this.undoHistoryDock.reconcile(out);
    }

    private void collectPanelIds(EditorLayoutNode node, HashSet<String> out)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            out.add(((EditorLayoutNode.PanelNode) node).getPanelId());
        }
        else if (node instanceof EditorLayoutNode.StackNode)
        {
            out.addAll(((EditorLayoutNode.StackNode) node).getPanelIds());
        }
        else if (node instanceof EditorLayoutNode.SplitterNode)
        {
            EditorLayoutNode.SplitterNode s = (EditorLayoutNode.SplitterNode) node;
            this.collectPanelIds(s.getFirst(), out);
            this.collectPanelIds(s.getSecond(), out);
        }
    }

    private void applySplitterHandleBounds(UIDraggable handle, EditorLayoutNode.SplitterHandleInfo info)
    {
        int ew = this.editor.area.w;
        int eh = this.editor.area.h;
        if (ew < EDITOR_MIN_SIZE_FOR_PX_HANDLES || eh < EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            /* Editor not laid out yet (e.g. first open); use normalized bounds so handles are visible. */
            handle.relative(this.editor).x(info.hx).y(info.hy).w(info.hw).h(info.hh);
            return;
        }
        if (info.horizontal)
        {
            float centerY = info.hy + info.hh * 0.5F;
            float hyNew = centerY - (SPLITTER_HANDLE_PX / (2F * eh));
            handle.relative(this.editor).x(info.hx).y(hyNew).w(info.hw).h(SPLITTER_HANDLE_PX);
        }
        else
        {
            float centerX = info.hx + info.hw * 0.5F;
            float hxNew = centerX - (SPLITTER_HANDLE_PX / (2F * ew));
            handle.relative(this.editor).x(hxNew).y(info.hy).w(SPLITTER_HANDLE_PX).h(info.hh);
        }
    }

    private void syncSplitterHandleBounds()
    {
        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            this.applySplitterHandleBounds(this.splitterHandles.get(i), this.splitterHandleInfos.get(i));
        }
    }

    private UIDraggable createSplitterHandle(int index)
    {
        UIDraggable handle = new UIDraggable((context) -> this.applySplitterDrag(context.mouseX, context.mouseY))
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIFilmPanel.this.beginSplitterDrag(index, context.mouseX, context.mouseY);
                boolean handled = super.subMouseClicked(context);

                if (!handled)
                {
                    UIFilmPanel.this.clearSplitterDragState();
                }

                return handled;
            }
        };

        /* Disable the handle entirely (no click, no resize cursor) when panel resizing is turned off. */
        handle.enabled(() -> BBSSettings.editorResizablePanels.get());

        handle.dragEnd(() ->
        {
            this.clearSplitterDragState();
            this.applyPreviewSizeToBBS();
        });
        handle.reference(() -> this.getSplitterHandleReferencePosition(index))
            .referenceAxis(!this.splitterHandleInfos.get(index).horizontal, this.splitterHandleInfos.get(index).horizontal);
        handle.rendering((context) -> this.renderSplitter(context, index));
        this.applySplitterHandleBounds(handle, this.splitterHandleInfos.get(index));

        return handle;
    }

    private void beginSplitterDrag(int index, int mouseX, int mouseY)
    {
        if (!BBSSettings.editorResizablePanels.get() || index < 0 || index >= this.splitterHandleInfos.size())
        {
            this.clearSplitterDragState();
            return;
        }

        this.draggedSplitterIndices.clear();
        this.draggedSplitterIndices.add(index);
        boolean horizontal = this.splitterHandleInfos.get(index).horizontal;

        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            if (i == index || this.splitterHandleInfos.get(i).horizontal == horizontal)
            {
                continue;
            }

            UIDraggable handle = this.splitterHandles.get(i);

            if (this.isInsideSplitterIntersectionHitbox(handle, mouseX, mouseY))
            {
                this.draggedSplitterIndices.add(i);
            }
        }
    }

    private boolean isInsideSplitterIntersectionHitbox(UIDraggable handle, int mouseX, int mouseY)
    {
        int padding = SPLITTER_LINK_HITBOX_PADDING_PX;

        return mouseX >= handle.area.x - padding
            && mouseX < handle.area.ex() + padding
            && mouseY >= handle.area.y - padding
            && mouseY < handle.area.ey() + padding;
    }

    private void clearSplitterDragState()
    {
        this.draggedSplitterIndices.clear();
    }

    private void applySplitterDrag(int mouseX, int mouseY)
    {
        if (this.draggedSplitterIndices.isEmpty())
        {
            return;
        }

        BaseValue.edit(this.getFilmLayoutSettings(), (__) ->
        {
            List<EditorLayoutNode.SplitterNode> splitters = this.getCurrentFilmSplittersForWrite();

            for (int draggedIndex : this.draggedSplitterIndices)
            {
                this.applySplitterRatioFromMouse(splitters, draggedIndex, mouseX, mouseY);
            }
        });

        this.setupEditorFlex(true);

        /* ⛔ Vorschaugroesse SCHON WAEHREND des Ziehens nachfuehren, nicht erst beim Loslassen.
         * Vorher behielt die Vorschau ihre alte Aufloesung, bis der Nutzer den Splitter losliess
         * — beim Ziehen sah man das Bild dehnen statt mitwachsen. Blender fuehrt live nach.
         *
         * Der Framebuffer-Resize ist nicht gratis, aber setCustomSize steigt bei gleicher
         * Groesse selbst aus ("No-op when nothing actually changes"), und beim Ziehen aendert
         * sie sich ohnehin nur schrittweise. */
        this.applyPreviewSizeToBBS();
    }

    private void applySplitterRatioFromMouse(List<EditorLayoutNode.SplitterNode> splitters, int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= splitters.size())
        {
            return;
        }

        float ratio = this.getSplitterRatioFromMouse(index, mouseX, mouseY);

        if (ratio >= 0F)
        {
            splitters.get(index).setRatio(ratio);
        }
    }

    /** @return ratio in [0,1] or -1 if index invalid */
    private float getSplitterRatioFromMouse(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return -1F;
        }
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        int ex = this.editor.area.x;
        int ey = this.editor.area.y;
        int ew = Math.max(1, this.editor.area.w);
        int eh = Math.max(1, this.editor.area.h);
        float ratio = info.horizontal
            ? (mouseY - (ey + info.py * eh)) / (info.ph * eh)
            : (mouseX - (ex + info.px * ew)) / (info.pw * ew);
        return MathUtils.clamp(ratio, 0.05F, 0.95F);
    }

    private Vector2i getSplitterHandleReferencePosition(int index)
    {
        List<EditorLayoutNode.SplitterNode> splitters = this.getCurrentFilmSplitters();

        if (index < 0 || index >= this.splitterHandleInfos.size() || index >= splitters.size())
        {
            return new Vector2i(this.editor.area.x, this.editor.area.y);
        }
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        float r = splitters.get(index).getRatio();
        int ex = this.editor.area.x;
        int ey = this.editor.area.y;
        int ew = Math.max(1, this.editor.area.w);
        int eh = Math.max(1, this.editor.area.h);
        int hx = ex + (int) ((info.px + (info.horizontal ? info.pw * 0.5F : r * info.pw)) * ew);
        int hy = ey + (int) ((info.py + (info.horizontal ? r * info.ph : info.ph * 0.5F)) * eh);
        return new Vector2i(hx, hy);
    }

    /** Rect of the preview stack, or null — handed to the gutter maths, which treats it as an outer edge. */
    private float[] previewStackRect(List<DockStackInfo> stackInfos)
    {
        return DockStackGeometry.stackRect(stackInfos, PANEL_PREVIEW_ID);
    }

    private void applyPanelBoundsFromStacks(List<DockStackInfo> stackInfos)
    {
        this.dockStackByPanelId.clear();

        float[] preview = this.previewStackRect(stackInfos);

        for (DockStackInfo info : stackInfos)
        {
            int topOffset = info.isStacked() ? DOCK_STACK_TABS_HEIGHT_PX : 0;

            for (String panelId : info.panelIds)
            {
                UIElement panel = this.panelById.get(panelId);

                if (panel == null)
                {
                    continue;
                }

                int[] g = panel == this.preview ? new int[4] : DockStackGeometry.gutter(info, preview);

                panel.relative(this.editor)
                    .x(info.x, g[0])
                    .y(info.y, topOffset + g[1])
                    .w(info.w, -g[0] - g[2])
                    .h(info.h, -topOffset - g[1] - g[3]);
                this.dockStackByPanelId.put(panelId, info);
            }
        }
    }

    private void rebuildDockStackTabs(List<DockStackInfo> stackInfos)
    {
        for (UIDockStackTabs tabs : this.dockStackTabs)
        {
            tabs.removeFromParent();
        }

        this.dockStackTabs.clear();

        float[] preview = this.previewStackRect(stackInfos);

        for (DockStackInfo info : stackInfos)
        {
            if (!info.isStacked())
            {
                continue;
            }

            UIDockStackTabs tabs = new UIDockStackTabs(this);
            tabs.configure(info);
            int[] g = DockStackGeometry.gutter(info, preview);

            tabs.relative(this.editor).x(info.x, g[0]).y(info.y, g[1]).w(info.w, -g[0] - g[2]).h(DOCK_STACK_TABS_HEIGHT_PX);
            this.dockStackTabs.add(tabs);
            this.editor.add(tabs);
        }
    }

    /**
     * Fast path for layout bounds updates: avoid mutating editor children list
     * while rendering by only reconfiguring existing stack tabs.
     */
    private boolean updateDockStackTabsBoundsOnly(List<DockStackInfo> stackInfos)
    {
        List<DockStackInfo> stackedInfos = new ArrayList<>();

        for (DockStackInfo info : stackInfos)
        {
            if (info.isStacked())
            {
                stackedInfos.add(info);
            }
        }

        if (stackedInfos.size() != this.dockStackTabs.size())
        {
            return false;
        }

        for (int i = 0; i < stackedInfos.size(); i++)
        {
            UIDockStackTabs tabs = this.dockStackTabs.get(i);
            DockStackInfo info = stackedInfos.get(i);

            if (!tabs.matches(info))
            {
                return false;
            }
        }

        float[] preview = this.previewStackRect(stackInfos);

        for (int i = 0; i < stackedInfos.size(); i++)
        {
            UIDockStackTabs tabs = this.dockStackTabs.get(i);
            DockStackInfo info = stackedInfos.get(i);

            tabs.configure(info);
            int[] g = DockStackGeometry.gutter(info, preview);

            tabs.relative(this.editor).x(info.x, g[0]).y(info.y, g[1]).w(info.w, -g[0] - g[2]).h(DOCK_STACK_TABS_HEIGHT_PX);
        }

        return true;
    }

    private void applyDragHandleBoundsFromStacks(List<DockStackInfo> stackInfos)
    {
        for (UIDraggable handle : this.dragHandlesById.values())
        {
            handle.setVisible(false);
        }

        int editorHeight = Math.max(1, this.editor.area.h);
        float[] preview = this.previewStackRect(stackInfos);

        for (DockStackInfo info : stackInfos)
        {
            UIDraggable handle = this.dragHandlesById.get(info.activePanelId);

            if (handle == null)
            {
                continue;
            }

            float tabsOffset = info.isStacked() ? (float) DOCK_STACK_TABS_HEIGHT_PX / editorHeight : 0F;

            int[] g = PANEL_PREVIEW_ID.equals(info.activePanelId) ? new int[4] : DockStackGeometry.gutter(info, preview);

            handle.relative(this.editor)
                .x(info.x, g[0])
                .y(info.y + tabsOffset + DRAG_HANDLE_TOP_OFFSET_NORM, g[1])
                .w(info.w, -g[0] - g[2])
                .h(DRAG_HANDLE_HEIGHT_NORM);
            handle.setVisible(!this.layoutLocked);
        }
    }

    private void updateEditorFlexBoundsOnly(EditorLayoutNode root)
    {
        List<DockStackInfo> stackInfos = new ArrayList<>();

        DockStackGeometry.collect(root, 0F, 0F, 1F, 1F, stackInfos);
        this.applyPanelBoundsFromStacks(stackInfos);

        if (!this.updateDockStackTabsBoundsOnly(stackInfos))
        {
            this.rebuildDockStackTabs(stackInfos);
        }

        this.splitterHandleInfos.clear();
        EditorLayoutNode.computeSplitterHandles(root, 0F, 0F, 1F, 1F, this.splitterHandleInfos);
        this.syncSplitterHandleBounds();
        this.applyDragHandleBoundsFromStacks(stackInfos);
        this.updateTabVisibility();
    }

    private void clearPanelDragState()
    {
        this.draggingPanelId = null;
        this.dropTargetPanelId = null;
        this.dropTargetZone = DropZonen.CENTER;
    }

    private void applyPanelDropResult(String dragId, String targetId, int zone)
    {
        EditorLayoutNode root = this.getCurrentFilmLayoutRoot();
        EditorLayoutNode newRoot = zone == DropZonen.CENTER
            ? EditorLayoutNode.copyWithInsertStackAt(root, targetId, dragId)
            : EditorLayoutNode.copyWithInsertSplitAt(root, targetId, dragId, zone);

        if (newRoot != null && newRoot != root)
        {
            this.setCurrentFilmLayoutRoot(newRoot);
            this.setupEditorFlex(true);
        }
    }

    private UIDraggable createPanelDragHandle(String panelId)
    {
        UIDraggable handle = new UIDraggable((context) ->
        {
            if (this.draggingPanelId == null)
            {
                this.draggingPanelId = panelId;
            }
            this.dropTargetPanelId = null;
            this.dropTargetZone = DropZonen.CENTER;

            for (UIDockStackTabs tabs : this.dockStackTabs)
            {
                if (tabs.isVisible() && tabs.area.isInside(context.mouseX, context.mouseY))
                {
                    String targetPanelId = tabs.getPanelIdAt(context.mouseX);

                    if (targetPanelId != null)
                    {
                        this.dropTargetPanelId = targetPanelId;
                        this.dropTargetZone = DropZonen.CENTER;

                        return;
                    }

                    break;
                }
            }

            DropZonen.Ziel ziel = DropZonen.zielUnterPanels(this.panelById, context.mouseX, context.mouseY);

            if (ziel != null)
            {
                this.dropTargetPanelId = ziel.panelId;
                this.dropTargetZone = ziel.zone;
            }

            this.letzteZugMausX = context.mouseX;
            this.letzteZugMausY = context.mouseY;
        });
        handle.dragEnd(() ->
        {
            if (this.draggingPanelId == null || this.dropTargetPanelId == null || this.draggingPanelId.equals(this.dropTargetPanelId))
            {
                /* Undo-Panel ins Leere gezogen (ausserhalb jeder Panel-Flaeche, praktisch
                 * Icon-Leiste/Tab-Bar) = abdocken: zurueck zum schwebenden Fenster an der
                 * Maus. ADS-Muster; Kanten-Drops bleiben Splits. */
                boolean abdocken = PANEL_UNDO_HISTORY_ID.equals(this.draggingPanelId)
                    && this.dropTargetPanelId == null;

                this.clearPanelDragState();

                if (abdocken)
                {
                    this.undoHistoryDock.toggleDocked();
                    this.undoOverlayOeffnen(this.letzteZugMausX, this.letzteZugMausY);
                }

                return;
            }
            this.applyPanelDropResult(this.draggingPanelId, this.dropTargetPanelId, this.dropTargetZone);
            this.clearPanelDragState();
        });
        handle.hoverOnly().cursors(GLFW.GLFW_HAND_CURSOR, GLFW.GLFW_HAND_CURSOR).rendering((context) -> this.renderPanelDragHandle(context, handle));
        return handle;
    }

    private void renderPanelDragHandle(UIContext context, UIDraggable handle)
    {
        boolean active = handle.area.isInside(context) || handle.isDragging();
        int color = active ? Colors.WHITE : Colors.setA(Colors.WHITE, 0.6F);
        int cx = handle.area.mx();
        int cy = handle.area.y + handle.area.h / 2 + 4;
        context.batcher.icon(Icons.ALL_DIRECTIONS, color, cx, cy, 0.5F, 0.5F);
    }

    private void renderDropZoneHighlight(UIContext context)
    {
        if (this.layoutLocked || this.draggingPanelId == null || this.dropTargetPanelId == null)
        {
            return;
        }
        UIElement target = this.panelById.get(this.dropTargetPanelId);
        if (target == null)
        {
            return;
        }
        DropZonen.zeichnen(context, target.area, this.dropTargetZone);
    }

    private void renderSplitter(UIContext context, int index)
    {
        if (index < 0 || index >= this.splitterHandles.size() || index >= this.splitterHandleInfos.size())
        {
            return;
        }
        UIDraggable splitter = this.splitterHandles.get(index);
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        int lineColor = BBSSettings.primaryColor(Colors.A100);

        if ((splitter.isDragging() || splitter.area.isInside(context)) && BBSSettings.editorResizablePanels.get())
        {
            context.requestCursor(this.getSplitterCursor(index, context.mouseX, context.mouseY));
        }

        if (!splitter.isDragging() && !this.draggedSplitterIndices.contains(index))
        {
            return;
        }

        if (info.horizontal)
        {
            int cy = splitter.area.y + splitter.area.h / 2;
            int half = SPLITTER_HANDLE_LINE_PX / 2;
            context.batcher.box(splitter.area.x, cy - half, splitter.area.ex(), cy - half + SPLITTER_HANDLE_LINE_PX, lineColor);
        }
        else
        {
            int cx = splitter.area.x + splitter.area.w / 2;
            int half = SPLITTER_HANDLE_LINE_PX / 2;
            context.batcher.box(cx - half, splitter.area.y, cx - half + SPLITTER_HANDLE_LINE_PX, splitter.area.ey(), lineColor);
        }
    }

    private int getSplitterCursor(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return GLFW.GLFW_ARROW_CURSOR;
        }

        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);

        return this.isInsideSplitterIntersection(index, mouseX, mouseY)
            ? GLFW.GLFW_CROSSHAIR_CURSOR
            : info.horizontal
            ? GLFW.GLFW_VRESIZE_CURSOR
            : GLFW.GLFW_HRESIZE_CURSOR;
    }

    private boolean isInsideSplitterIntersection(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return false;
        }

        boolean horizontal = this.splitterHandleInfos.get(index).horizontal;

        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            if (i == index || this.splitterHandleInfos.get(i).horizontal == horizontal)
            {
                continue;
            }

            if (this.isInsideSplitterIntersectionHitbox(this.splitterHandles.get(i), mouseX, mouseY))
            {
                return true;
            }
        }

        return false;
    }

    private void fillFilmContextMenu(ContextMenuManager menu)
    {
        menu.action(Icons.FILM, UIKeys.FILM_TITLE, this::openFilmListOverlay);

        if (this.data == null)
        {
            return;
        }

        menu.action(Icons.SAVED, UIKeys.GENERAL_SAVE, this::save);
        menu.action(Icons.LAYOUT, UIKeys.FILM_LAYOUT_PRESETS, this::openLayoutPresetsMenu);
        menu.action(Icons.LINK, UIKeys.FILM_LAYOUT_BIND_TO_EDITOR, this.isCurrentFilmLayoutBound(), this::toggleCurrentFilmLayoutBinding);
        menu.action(Icons.REFRESH, UIKeys.FILM_LAYOUT_RESET, this::resetFilmLayout);
        menu.action(this.layoutLocked ? Icons.UNLOCKED : Icons.LOCKED, this.layoutLocked ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, this.layoutLocked, this::toggleLayoutLock);

        menu.action(Icons.UNDO, UIKeys.FILM_DOCK_HISTORY, this.undoHistoryDock.isDocked(), this.undoHistoryDock::toggleDocked);

        menu.action(Icons.LIST, UIKeys.FILM_OPEN_HISTORY, () ->
        {
            this.undoOverlayOeffnen(Integer.MIN_VALUE, Integer.MIN_VALUE);
        });

        menu.action(Icons.ARROW_RIGHT, UIKeys.FILM_MOVE_TITLE, () ->
        {
            UIFilmMoveOverlayPanel panel = new UIFilmMoveOverlayPanel((vector) ->
            {
                int topLayer = this.data.camera.getTopLayer() + 1;
                int duration = this.data.camera.calculateDuration();
                double dx = vector.x;
                double dy = vector.y;
                double dz = vector.z;

                BaseValue.edit(this.data, (__) ->
                {
                    TranslateClip clip = new TranslateClip();

                    clip.layer.set(topLayer);
                    clip.duration.set(duration);
                    clip.translate.get().set(dx, dy, dz);
                    __.camera.addClip(clip);

                    for (Replay replay : __.replays.getList())
                    {
                        for (Keyframe<Double> keyframe : replay.keyframes.x.getKeyframes()) keyframe.setValue(keyframe.getValue() + dx);
                        for (Keyframe<Double> keyframe : replay.keyframes.y.getKeyframes()) keyframe.setValue(keyframe.getValue() + dy);
                        for (Keyframe<Double> keyframe : replay.keyframes.z.getKeyframes()) keyframe.setValue(keyframe.getValue() + dz);

                        replay.actions.shift(dx, dy, dz);
                    }
                });
            });

            panel.difference(this::getMoveToPlayerOffset);

            UIOverlay.addOverlay(this.getContext(), panel, 240, 140);
        });

        menu.action(Icons.TIME, UIKeys.FILM_INSERT_SPACE_TITLE, () ->
        {
            UINumberOverlayPanel panel = new UINumberOverlayPanel(UIKeys.FILM_INSERT_SPACE_TITLE, UIKeys.FILM_INSERT_SPACE_DESCRIPTION, (d) ->
            {
                if (d.intValue() <= 0)
                {
                    return;
                }

                for (Replay replay : this.data.replays.getList())
                {
                    for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                    {
                        channel.insertSpace(this.getCursor(), d.intValue());
                    }

                    for (KeyframeChannel channel : replay.properties.properties.values())
                    {
                        channel.insertSpace(this.getCursor(), d.intValue());
                    }
                }
            });

            panel.value.limit(1).integer().setValue(1D);

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        menu.action(Icons.GEAR, UIKeys.FILM_PLAYER_SETTINGS, () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIFilmPlayerSettingsOverlayPanel(this.getData()), 280, 0.4F);
        });

        menu.action(Icons.HELP, L10n.lang("bbs.ui.film.details.button"), () ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIFilmDetailsOverlayPanel(this.getData()), 300, 260);
        });

        com.afrobama.bbstools.client.ui.optimize.OptimizeMenuEintrag.anhaengen(menu, this.getContext());
    }

    /**
     * Relative move: snaps the scene's first position keyframe onto the player's current
     * position ({@code round}: whole coordinates) — offered through the move overlay's menu.
     */
    private Vector3d getMoveToPlayerOffset(boolean round)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null)
        {
            return new Vector3d();
        }

        Vector3d first = this.getFirstReplayPosition();
        double px = round ? Math.round(player.getX()) : player.getX();
        double py = round ? Math.round(player.getY()) : player.getY();
        double pz = round ? Math.round(player.getZ()) : player.getZ();

        return new Vector3d(px - first.x, py - first.y, pz - first.z);
    }

    private Vector3d getFirstReplayPosition()
    {
        Replay selected = this.replayEditor.getReplay();

        if (selected != null && (!selected.keyframes.x.isEmpty() || !selected.keyframes.y.isEmpty() || !selected.keyframes.z.isEmpty()))
        {
            return new Vector3d(
                selected.keyframes.x.isEmpty() ? 0 : selected.keyframes.x.get(0).getValue(),
                selected.keyframes.y.isEmpty() ? 0 : selected.keyframes.y.get(0).getValue(),
                selected.keyframes.z.isEmpty() ? 0 : selected.keyframes.z.get(0).getValue()
            );
        }

        for (Replay replay : this.data.replays.getList())
        {
            if (!replay.keyframes.x.isEmpty() || !replay.keyframes.y.isEmpty() || !replay.keyframes.z.isEmpty())
            {
                return new Vector3d(
                    replay.keyframes.x.isEmpty() ? 0 : replay.keyframes.x.get(0).getValue(),
                    replay.keyframes.y.isEmpty() ? 0 : replay.keyframes.y.get(0).getValue(),
                    replay.keyframes.z.isEmpty() ? 0 : replay.keyframes.z.get(0).getValue()
                );
            }
        }

        return new Vector3d();
    }

    public void openFilmListOverlay()
    {
        UIOverlay.addOverlay(this.getContext(), this.overlay, 200, 0.9F);
    }

    public void openLayoutPresetsMenu()
    {
        UIContext context = this.getContext();

        this.layoutPresetsController.openPresets(context, context.mouseX, context.mouseY);
    }

    @Override
    protected boolean shouldAutoOpenListOnFirstResize()
    {
        return false;
    }

    @Override
    public void resize()
    {
        super.resize();
        this.updateTabVisibility();

        if (this.editor.area.w >= EDITOR_MIN_SIZE_FOR_PX_HANDLES && this.editor.area.h >= EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            if (this.splitterHandles.size() == this.splitterHandleInfos.size())
            {
                this.syncSplitterHandleBounds();
            }
            this.editor.resize();
        }

        boolean anySplitterDragging = this.splitterHandles.stream().anyMatch(UIDraggable::isDragging);
        if (!this.recorder.isExporting() && !anySplitterDragging
            && this.preview.area.w >= 2 && this.preview.area.h >= 2)
        {
            this.applyPreviewSizeToBBS();
        }
    }

    /**
     * Returns the currently-active queue exporter, or {@code null} when no
     * multi-film export is in progress.
     */
    public FilmQueueExporter getQueueExporter()
    {
        return this.queueExporter;
    }

    /**
     * Builds a queue exporter from all currently-open film tabs and starts it.
     * If a single-film export is already in progress, or no tabs hold a film,
     * the call is a no-op and notifies the user.
     */
    public void startQueueExportFromOpenTabs()
    {
        UIContext context = this.getContext();

        if (this.recorder.isExporting() || this.queueExporter != null)
        {
            return;
        }

        FilmQueueExporter exporter = FilmQueueExporter.fromOpenTabs(this);

        if (exporter == null)
        {
            if (context != null)
            {
                context.notifyError(UIKeys.FILM_RENDER_QUEUE_EMPTY);
            }

            return;
        }

        this.queueExporter = exporter;

        if (context != null)
        {
            context.notifyInfo(UIKeys.FILM_RENDER_QUEUE_STARTED.format(exporter.totalCount()));
        }

        exporter.start();
    }

    /**
     * Invoked by {@link FilmQueueExporter} once it has fully shut down.
     * Guarded so a stale exporter cannot clear a newer one.
     */
    public void clearQueueExporter(FilmQueueExporter exporter)
    {
        if (this.queueExporter == exporter)
        {
            this.queueExporter = null;
        }
    }

    /**
     * Sets BBS fake window size to export resolution (from video settings).
     * Use when starting record, or when entering F1 fullscreen in film panel.
     */
    public static void applyExportSizeToBBS()
    {
        int w = Math.max(2, BBSSettings.videoWidth.get());
        int h = Math.max(2, BBSSettings.videoHeight.get());
        if (w % 2 != 0) w++;
        if (h % 2 != 0) h++;
        BBSRendering.setCustomSize(true, w, h);
    }

    /**
     * Restores BBS fake window size to the preview block size. Call after recording
     * ends so the preview is no longer at export resolution.
     */
    public void restorePreviewSize()
    {
        this.applyPreviewSizeToBBS();
    }

    /**
     * Applies the preview or export size to BBSRendering. When the camera editor is
     * visible, uses export resolution so the preview matches export proportions.
     * Otherwise uses the UI preview area size. Called when the user finishes resizing
     * the preview, when the panel is laid out, and when switching to/from camera editor.
     */
    private void applyPreviewSizeToBBS()
    {
        if (this.recorder.isExporting() || mchorse.bbs_mod.client.ExportFrameCallbacks.hasPending())
        {
            return;
        }
        int w;
        int h;

        int previewMode = BBSSettings.editorPreviewSizeMode.get();

        if (previewMode == PREVIEW_MODE_EXPORT)
        {
            w = Math.max(2, BBSSettings.videoWidth.get());
            h = Math.max(2, BBSSettings.videoHeight.get());
        }
        else if (previewMode == PREVIEW_MODE_CUSTOM)
        {
            w = Math.max(2, BBSSettings.editorPreviewCustomWidth.get());
            h = Math.max(2, BBSSettings.editorPreviewCustomHeight.get());
        }
        else
        {
            float scale = BBSSettings.editorPreviewResolutionScale.get();

            /* ⛔ Blenders Unterscheidung, an BBS' eigene Modi gehaengt: in der KAMERA-Ansicht
             * (CAMERA_MODE_CAMERA) gilt das Bildformat des Exports — das ist Blenders
             * „Camera View" mit festem Rahmen. In jeder freien Ansicht (Frei, Orbit, POV)
             * fuellt die Vorschau die Flaeche und das Sichtfeld passt sich an — Blenders
             * „User Perspective". Vorher hing das an cameraEditor.isVisible(): ob ein EDITOR
             * offen ist, hat mit der Ansichtsart nichts zu tun. */
            int previewW = Math.max(2, this.preview.area.w);
            int previewH = Math.max(2, this.preview.area.h);
            float format;

            /* ⛔ AUCH die Kamera-Ansicht fuellt die Flaeche. Vorher erzwang sie das
             * Exportformat, wodurch das Bild als kleineres Rechteck mit Raendern in der
             * Flaeche sass — in jeder anderen Ansicht randlos, hier nicht (Nutzerbefund
             * 2026-08-20: „wenn ich den camera mode auf camera switche dann ist der viewport
             * nicht mehr durch den ganzen screen skaliert").
             *
             * ⚠️ Damit fehlt der Hinweis, WELCHER Ausschnitt spaeter im Video landet. Blender
             * loest das mit einem Kamerarahmen samt Passepartout ueber dem vollflaechigen
             * Bild — das ist der naechste Schritt, nicht der Sonderfall hier. */
            {
                /* Freie Ansicht fuellt die Flaeche randlos — Blenders „User Perspective".
                 * Damit das Bild dabei nicht breitgezogen wird, zieht die Weltprojektion mit:
                 * ProjectionPreviewAspectMixin setzt Minecrafts Bildformat auf genau diese
                 * Textur, statt es aus der Fenstergroesse zu nehmen. */
                format = 0F;
            }

            if (format <= 0F)
            {
                w = Math.max(2, (int) (previewW * scale));
                h = Math.max(2, (int) (previewH * scale));
            }
            else
            {
                Vector2i resized = Vectors.resize(format, previewW, previewH);

                w = Math.max(2, (int) (resized.x * scale));
                h = Math.max(2, (int) (resized.y * scale));
            }
        }

        if (w % 2 != 0) w++;
        if (h % 2 != 0) h++;

        boolean applied = w != BBSRendering.getVideoWidth() || h != BBSRendering.getVideoHeight();
        LOGGER.info("[BBS film] applyPreviewSizeToBBS mode={} cameraEditor={} -> w={} h={} applied={}",
            previewMode, this.cameraEditor.isVisible(), w, h, applied);

        if (applied)
        {
            BBSRendering.setCustomSize(true, w, h);
        }
    }

    /**
     * Vorschaugroesse neu rechnen — der Kameramodus bestimmt, ob sie die Flaeche fuellt.
     *
     * <p>⛔ Null-fest: {@code setPov} laeuft schon waehrend des Aufbaus, wenn der Controller
     * seinen gespeicherten Modus liest — da sind {@code recorder} und {@code preview} noch
     * nicht gesetzt. Ohne diese Pruefung stirbt der Editor beim Oeffnen.</p>
     */
    public void vorschauGroesseNachziehen()
    {
        if (this.recorder == null || this.preview == null || this.cameraEditor == null)
        {
            return;
        }

        this.applyPreviewSizeToBBS();
    }

    public void pickClip(Clip clip, UIClipsPanel panel)
    {
        if (panel == this.cameraEditor)
        {
            this.setFlight(false);
        }
    }

    public int getPanelIndex()
    {
        for (int i = 0; i < this.panels.size(); i++)
        {
            if (this.panels.get(i).isVisible())
            {
                return i;
            }
        }

        return -1;
    }

    public void showPanel(int index)
    {
        if (index >= 0 && index < this.panels.size())
        {
            this.showPanel(this.panels.get(index));
        }
    }

    public void showPanel(UIElement element)
    {
        this.cameraEditor.clips.embedView(null);

        EditorLayoutNode previousRoot = this.getCurrentFilmLayoutRoot();
        int index = this.getPanelIndex();

        if (index >= 0)
        {
            this.captureTimelineViewport(this.panels.get(index));
        }

        this.clearPanelDragState();
        this.clearSplitterDragState();
        this.selectedMainEditorPanel = element;

        if (previousRoot != this.getCurrentFilmLayoutRoot())
        {
            this.setupEditorFlex(true);
        }
        else
        {
            this.updateMainEditorVisibility(this.hasFilmInCurrentTab());
        }

        this.applyTimelineViewport(element);
        this.applyPreviewSizeToBBS();

        if (this.isFlying())
        {
            this.toggleFlight();
        }
    }

    private void captureTimelineViewport(UIElement panel)
    {
        if (panel == this.cameraEditor)
        {
            this.timelineXMin = this.cameraEditor.clips.scale.getMinValue();
            this.timelineXMax = this.cameraEditor.clips.scale.getMaxValue();
        }
        else if (panel == this.replayEditor && this.replayEditor.keyframeEditor != null)
        {
            this.timelineXMin = this.replayEditor.keyframeEditor.view.getXAxis().getMinValue();
            this.timelineXMax = this.replayEditor.keyframeEditor.view.getXAxis().getMaxValue();
        }
    }

    private void applyTimelineViewport(UIElement panel)
    {
        if (Double.isNaN(this.timelineXMin) || Double.isNaN(this.timelineXMax) || this.timelineXMin >= this.timelineXMax)
        {
            return;
        }

        if (panel == this.cameraEditor)
        {
            this.cameraEditor.clips.scale.view(this.timelineXMin, this.timelineXMax);
        }
        else if (panel == this.replayEditor && this.replayEditor.keyframeEditor != null)
        {
            this.replayEditor.keyframeEditor.view.getXAxis().view(this.timelineXMin, this.timelineXMax);
        }
    }

    private void captureTimelineScroll()
    {
        if (this.data == null || this.data.getId() == null)
        {
            return;
        }

        FilmTimelineScroll scroll = this.timelineScrollByFilm.computeIfAbsent(this.data.getId(), (id) -> new FilmTimelineScroll());

        scroll.camera = this.cameraEditor.clips.vertical.getScroll();
        scroll.action = this.actionEditor.clips.vertical.getScroll();

        if (this.replayEditor.keyframeEditor != null)
        {
            scroll.replay = this.replayEditor.keyframeEditor.view.getDopeSheet().getYAxis().getScroll();
        }
    }

    private void restoreTimelineScroll()
    {
        if (this.data == null || this.data.getId() == null)
        {
            return;
        }

        FilmTimelineScroll scroll = this.timelineScrollByFilm.get(this.data.getId());

        if (scroll == null)
        {
            return;
        }

        this.restoreClipsScroll(this.cameraEditor.clips, scroll.camera);
        this.restoreClipsScroll(this.actionEditor.clips, scroll.action);

        if (this.replayEditor.keyframeEditor != null)
        {
            this.replayEditor.keyframeEditor.view.getDopeSheet().getYAxis().setScroll(scroll.replay);
        }
    }

    private void restoreClipsScroll(UIClips clips, double scroll)
    {
        if (clips.getClips() == null)
        {
            return;
        }

        /* Scroll size depends on the freshly loaded clips, so recompute it before clamping the restored scroll. */
        clips.updateScrollSize();
        clips.vertical.setScroll(scroll);
    }

    private static class FilmTimelineScroll
    {
        public double camera;
        public double action;
        public double replay;
    }

    public UIFilmController getController()
    {
        return this.controller;
    }

    public UIFilmUndoHandler getUndoHandler()
    {
        return this.undoHandler;
    }

    public RunnerCameraController getRunner()
    {
        return this.runner;
    }

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        UIFilmOverlayPanel crudPanel = new UIFilmOverlayPanel(this.getTitle(), this, this::pickData);

        this.duplicateFilm = new UIIcon(Icons.SCENE, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> this.dupeData(crudPanel.namesList.getPath(str).toString())
            );

            panel.text.setText(crudPanel.namesList.getCurrentFirst().getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        crudPanel.icons.add(this.duplicateFilm);

        return crudPanel;
    }

    private void dupeData(String name)
    {
        if (this.getData() != null && !this.overlay.namesList.hasInHierarchy(name))
        {
            this.save();
            this.overlay.namesList.addFile(name);

            Film data = mchorse.bbs_mod.ui.film.utils.FilmDuplicator.duplicate(
                name, this.data, this.getCursor(), this.getCamera());

            this.fill(data);
            this.save();
        }
    }

    public void dupeCurrentFilmTo(String name)
    {
        this.dupeData(name);
    }

    public void dupeFilmTo(String sourceId, String name)
    {
        if (name == null || name.trim().isEmpty())
        {
            return;
        }

        Film current = this.getData();

        if (current != null && (sourceId == null || sourceId.equals(current.getId())))
        {
            this.dupeData(name);

            return;
        }

        if (sourceId == null || sourceId.trim().isEmpty() || this.overlay.namesList.hasInHierarchy(name))
        {
            return;
        }

        this.save();

        this.getType().getRepository().load(sourceId, (loaded) ->
        {
            Film source = (Film) loaded;

            if (source == null)
            {
                return;
            }

            Film duplicated = mchorse.bbs_mod.ui.film.utils.FilmDuplicator.duplicate(
                name, source, this.getCursor(), this.getCamera());

            this.fill(duplicated);
            this.save();
            this.requestNames();
        });
    }

    @Override
    public void open()
    {
        super.open();

        Recorder recorder = BBSModClient.getFilms().stopRecording();

        if (recorder == null || recorder.hasNotStarted())
        {
            this.notifyServer(ActionState.RESTART);

            return;
        }

        this.applyRecordedKeyframes(recorder, this.data);
    }

    public void receiveActions(String filmId, int replayId, int tick, BaseType clips)
    {
        Film film = this.data;

        if (film != null && film.getId().equals(filmId) && CollectionUtils.inRange(film.replays.getList(), replayId))
        {
            BaseValue.edit(film.replays.getList().get(replayId), IValueListener.FLAG_UNMERGEABLE, (replay) ->
            {
                Clips newClips = new Clips("", BBSMod.getFactoryActionClips());

                newClips.fromData(clips);
                replay.actions.copyOver(newClips, tick);
            });
        }

        this.save();
    }

    public void applyRecordedKeyframes(Recorder recorder, Film film)
    {
        int replayId = recorder.exception;
        Replay rp = CollectionUtils.getSafe(film.replays.getList(), replayId);

        if (rp != null)
        {
            BaseValue.edit(film, (f) ->
            {
                rp.keyframes.copyOver(recorder.keyframes, 0);

                Form form = rp.form.get();

                if (form != null)
                {
                    for (Map.Entry<String, KeyframeChannel> entry : recorder.properties.properties.entrySet())
                    {
                        KeyframeChannel channel = rp.properties.getOrCreate(form, entry.getKey());

                        if (channel != null && entry.getValue() != null)
                        {
                            channel.copyOver(entry.getValue(), 0);
                        }
                    }
                }

                f.inventory.fromData(recorder.inventory.toData());
                f.hp.set(recorder.hp);
                f.hunger.set(recorder.hunger);
                f.xpLevel.set(recorder.xpLevel);
                f.xpProgress.set(recorder.xpProgress);
            });
        }

        this.applyRecordedMobs(recorder, film);
    }

    /**
     * Turn every mob captured during recording into its own replay (with a mob form
     * and the recorded position/rotation keyframes), then refresh the replay list.
     */
    private void applyRecordedMobs(Recorder recorder, Film film)
    {
        if (recorder.mobs.isEmpty())
        {
            return;
        }

        BaseValue.edit(film, (f) ->
        {
            for (Recorder.RecordedMob mob : recorder.mobs)
            {
                Replay replay = f.replays.addReplay();

                replay.category.set("");
                replay.form.set(mob.form);
                replay.keyframes.copyOver(mob.keyframes, 0);
            }
        });

        this.replayEditor.replaysList.replays.refreshReplayList();
        this.controller.createEntities();
    }

    @Override
    public void appear()
    {
        super.appear();

        /* appear() also fires while the dashboard is being lazily constructed (the
         * teleport/record keybinds create it on first use), at which point there's no
         * context and the editor isn't actually shown. Running the side effects below
         * there leaks editor state into the plain world — most importantly it adds the
         * film camera controller (runner) to the GLOBAL camera controller, which then
         * hijacks the world view and is never removed (the screen is never closed),
         * freezing the screen until another BBS screen resets the camera controller.
         * So only do this once the panel is genuinely on screen. */
        if (this.getContext() == null)
        {
            return;
        }

        BBSRendering.setCustomSize(true);
        MorphRenderer.hidePlayer = true;
        /* Catches the dock setting being flipped in the settings screen while the film editor was away. */
        this.undoHistoryDock.syncWithSettings();

        CameraController cameraController = this.getCameraController();

        this.fillData();
        this.setFlight(false);
        cameraController.add(this.runner);

        this.getContext().menu.getRoot().add(this.secretPlay);
    }

    @Override
    public void close()
    {
        if (this.queueExporter != null)
        {
            this.queueExporter.cancel();
        }

        super.close();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        CameraController cameraController = this.getCameraController();

        this.cameraEditor.embedView(null);
        this.setFlight(false);
        cameraController.remove(this.runner);

        this.disableContext();
        this.replayEditor.close();

        this.notifyServer(ActionState.STOP);
    }

    @Override
    public void disappear()
    {
        super.disappear();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;

        this.setFlight(false);
        this.getCameraController().remove(this.runner);

        this.disableContext();
        this.secretPlay.removeFromParent();
    }

    private void disableContext()
    {
        this.runner.getContext().shutdown();
    }

    @Override
    public boolean needsBackground()
    {
        return true;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public boolean canRefresh()
    {
        return false;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.FILMS;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.FILM_TITLE;
    }

    @Override
    public void fillDefaultData(Film data)
    {
        super.fillDefaultData(data);

        IdleClip clip = new IdleClip();
        Camera camera = new Camera();
        Minecraft mc = Minecraft.getInstance();

        camera.set(mc.player, MathUtils.toRad(mc.options.fov().get()));

        clip.layer.set(0); /* nicht 8 — die Spurenzahl folgt der hoechsten belegten Ebene. */
        clip.duration.set(BBSSettings.getDefaultDuration());
        clip.fromCamera(camera);
        data.camera.addClip(clip);

        data.stampCreationTimeNow();

        this.newFilm = true;
    }

    @Override
    public void fill(Film data)
    {
        this.notifyServer(ActionState.STOP);
        this.captureTimelineScroll();
        super.fill(data);
        this.restoreTimelineScroll();
        this.notifyServer(ActionState.RESTART);

        /* Nach super.fill: dort legt fillData den Undo-Manager neu an. */
        if (data != null && this.undoHandler != null)
        {
            UndoPersistence.laden(data.getId(), this.undoHandler.getUndoManager());
        }
    }

    @Override
    public void forceSave()
    {
        super.forceSave();
        /* Journal-Hash und Kachelbild gehoeren zur gerade geschriebenen Film-Datei. */
        if (this.data != null && this.undoHandler != null)
        {
            UndoPersistence.speichern(this.data.getId(), this.undoHandler.getUndoManager());
            this.preview.standbildAblegen(BBSRendering.getTexture(), BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight());
        }
    }

    @Override
    protected void fillData(Film data)
    {
        if (this.data != null)
        {
            this.disableContext();
        }

        if (data != null)
        {
            this.undoHandler = new UIFilmUndoHandler(this);

            data.preCallback(this.undoHandler::handlePreValues);
        }
        else
        {
            this.undoHandler = null;
        }

        this.openFilmMenu.setEnabled(true);
        this.openCameraEditor.setEnabled(data != null);
        this.duplicateFilm.setEnabled(data != null);

        this.actionEditor.setClips(null);
        this.runner.setWork(data == null ? null : data.camera);
        this.cameraEditor.setClips(data == null ? null : data.camera);
        this.replayEditor.setFilm(data);
        this.cameraEditor.pickClip(null);

        this.fillData();
        this.controller.createEntities();

        if (this.newFilm)
        {
            Clip main = this.data.camera.get(0);

            this.cameraEditor.clips.setSelected(main);
            this.cameraEditor.pickClip(main);
        }

        this.entered = data != null;
        this.newFilm = false;

        if (data != null)
        {
            this.filmUserActivity.onFilmOpened();
        }
        else
        {
            this.filmUserActivity.reset();
        }

        this.updateTabVisibility();
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        if (this.selectionPanel != null)
        {
            this.selectionPanel.fillNames(names);
        }
    }

    public void undo()
    {
        if (this.data != null && this.undoHandler.getUndoManager().undo(this.data)) UIUtils.playClick();
    }

    public void redo()
    {
        if (this.data != null && this.undoHandler.getUndoManager().redo(this.data)) UIUtils.playClick();
    }

    public boolean isFlying()
    {
        return this.dashboard.orbitUI.canControl();
    }

    public void toggleFlight()
    {
        this.setFlight(!this.isFlying());
    }

    /**
     * Set flight mode
     */
    public void setFlight(boolean flight)
    {
        if (flight)
        {
            this.controller.stopGizmoInteraction();
        }

        if (!this.isRunning() || !flight)
        {
            if (!flight)
            {
                this.persistFlightFov();
                if (this.undoHandler != null)
                {
                    this.undoHandler.getUndoManager().markLastUndoNoMerging();
                }
                else
                {
                    this.flightRecorder.forgetLastPosition();
                }
            }
            else
            {
                this.flightRecorder.forgetLastPosition();
            }

            this.runner.setManual(flight ? this.position : null);
            this.dashboard.orbitUI.setControl(flight);
        }
    }

    private void persistFlightFov()
    {
        if (BBSSettings.fov != null)
        {
            BBSSettings.fov.set(this.position.angle.fov);
        }
    }

    public Vector2i getLoopingRange()
    {
        Clip clip = this.cameraEditor.getClip();

        int min = -1;
        int max = -1;

        if (clip != null)
        {
            min = clip.tick.get();
            max = min + clip.duration.get();
        }

        UIClips clips = this.cameraEditor.clips;

        if (clips.loopMin != clips.loopMax && clips.loopMin >= 0 && clips.loopMin < clips.loopMax)
        {
            min = clips.loopMin;
            max = clips.loopMax;
        }

        /* ⛔ OHNE DIESEN TEST STIRBT DER CLIENT, SOBALD DAS PANEL OHNE FILM ZEICHNET.
         *
         * updateLogic ruft diese Methode in JEDEM Bild, und render ruft updateLogic --
         * ein Film muss dafuer nicht geladen sein. Ist keiner da, ist this.data null und
         * der Zugriff auf .camera reisst die Instanz:
         *
         *     NullPointerException: Cannot read field "camera" because "this.data" is null
         *       at UIFilmPanel.getLoopingRange:2769 <- updateLogic:2973 <- render:2922
         *
         * Live passiert am 2026-08-01 in der 1.21.1-instanz, waehrend der Nutzer eine Szene
         * vorbereitete. KEIN Portschaden: der Code steht in 1.21.1 und 26.2 zeichengleich an
         * derselben Zeile; im Port fiel es nur nicht auf, weil dort zufaellig ein Film offen
         * war.
         *
         * Ohne Film gibt es keine Dauer, auf die man begrenzen koennte -- die Grenze bleibt
         * dann einfach ungesetzt, statt zu raten. */
        if (this.data != null && this.data.camera != null)
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("film.mit-daten");

            max = Math.min(max, this.data.camera.calculateDuration());
        }
        else
        {
            /* Genau der Zustand, der die 1.21.1-instanz gerissen hat -- der Zaehler belegt,
             * dass der Waechter hier WIRKLICH greift und nicht nur im Code steht. */
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("film.ohne-daten");
        }

        return new Vector2i(min, max);
    }

    @Override
    public void update()
    {
        if (this.getContext() != null && this.secretPlay.getParent() == null)
        {
            this.getContext().menu.getRoot().add(this.secretPlay);
        }

        this.playerToCamera = BBSSettings.editorPlayerFollowsCamera.get();
        this.controller.update();

        if (this.playerToCamera && this.data != null && !this.controller.isControlling())
        {
            this.teleportToCamera();
        }

        super.update();
    }

    /* Rendering code */

    @Override
    public void renderPanelBackground(UIContext context)
    {
        super.renderPanelBackground(context);

        Texture texture = BBSRendering.getTexture();

        if (texture != null)
        {
            context.batcher.box(0, 0, context.menu.width, context.menu.height, Colors.A100);

            int w = context.menu.width;
            int h = context.menu.height;
            Vector2i resize = Vectors.resize(texture.width / (float) texture.height, w, h);
            Area area = new Area();

            area.setSize(resize.x, resize.y);
            area.setPos((w - area.w) / 2, (h - area.h) / 2);

            context.batcher.texturedBox(texture, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        this.updateLogic(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);
    }

    private void renderTopBarActions(UIContext context)
    {
        if (this.topBarActions == null || !this.topBarActions.isVisible())
        {
            return;
        }

        this.renderTopBarButton(context, this.openCameraEditor, this.cameraEditor.isVisible());
        this.renderTopBarSeparator(context);
        this.renderTopBarButton(context, this.openFilmMenu, false);
    }

    private void renderTopBarButton(UIContext context, UIIcon button, boolean active)
    {
        if (button == null || !button.isVisible())
        {
            return;
        }

        Area area = button.area;
        boolean hover = area.isInside(context.mouseX, context.mouseY);

        if (active)
        {
            UIDashboardPanels.renderHighlight(context.batcher, area, Direction.BOTTOM);
        }
        else if (hover)
        {
            context.batcher.box(area.x, area.y, area.ex(), area.ey(), BBSSettings.color(BBSSettings.raisedSurface(), Colors.A25));
        }
    }

    private void renderTopBarSeparator(UIContext context)
    {
        if (this.topBarSeparator == null || !this.topBarSeparator.isVisible())
        {
            return;
        }

        Area area = this.topBarSeparator.area;
        int x = area.mx();

        context.batcher.box(x, area.y + 3, x + 1, area.ey() - 3, BBSSettings.dividerColor());
    }

    /** Das Undo-Fenster oeffnen; (x,y) = Zielposition des Titels, MIN_VALUE = zentriert. */
    private void undoOverlayOeffnen(int x, int y)
    {
        UndoOverlayAndocken.oeffnen(this.getContext(), this.getUndoHandler().getUndoManager(),
            this::getData, this::overlayDockZiel,
            (panelId) -> this.panelById.get(panelId).area,
            (ziel) -> this.undoAndockWunsch = ziel, x, y);
    }

    /** Dock-Ziel fuer die Overlay-Geste — null bei gesperrtem Layout (Standard) oder daneben. */
    private DropZonen.Ziel overlayDockZiel(int mouseX, int mouseY)
    {
        if (this.layoutLocked)
        {
            return null;
        }

        return DropZonen.zielUnterPanels(this.panelById, mouseX, mouseY);
    }

    /**
     * Draw everything on the screen
     */
    @Override
    public void render(UIContext context)
    {
        /* Ganz am Anfang, vor jeder Kinder-Iteration dieses Frames — derselbe sichere
         * Zeitpunkt wie UIOverlay.abschlussFaellig. Setting ZUERST, sonst wirft reconcile
         * (im setupEditorFlex des Drops) das frisch eingefuegte Panel wieder raus. */
        if (this.undoAndockWunsch != null)
        {
            DropZonen.Ziel wunsch = this.undoAndockWunsch;

            this.undoAndockWunsch = null;
            BBSSettings.editorUndoHistoryDocked.set(true);
            this.applyPanelDropResult(PANEL_UNDO_HISTORY_ID, wunsch.panelId, wunsch.zone);
        }

        if (this.lastTime == 0)
        {
            this.lastTime = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        long diff = now - this.lastTime;

        this.lastTime = now;

        if (this.getData() != null)
        {
            Minecraft mc = Minecraft.getInstance();

            if (this.filmUserActivity.shouldAccumulateActiveTime(mc, context, now))
            {
                this.timeSpentActiveAccumulator += diff;
            }

            /* Batch updates to once per second to avoid undo history pollution
             * and reduce set() overhead; display already refreshes every 1s */
            if (this.timeSpentActiveAccumulator >= 1000)
            {
                long ticks = (long) (this.timeSpentActiveAccumulator / 50);

                this.getData().timeSpentActive.set(this.getData().timeSpentActive.get() + ticks, mchorse.bbs_mod.settings.values.IValueListener.FLAG_SILENT);
                this.timeSpentActiveAccumulator -= ticks * 50;
            }
        }

        ControlMouse.pin(this, context);

        this.controller.orbit.update(context);

        if (this.undoHandler != null)
        {
            this.undoHandler.submitUndo();
        }

        if (this.queueExporter != null)
        {
            this.queueExporter.tick(context);
        }

        this.updateLogic(context);

        this.area.render(context.batcher, BBSSettings.baseSurface());

        if (this.editor.isVisible())
        {
            this.preview.area.render(context.batcher, Colors.A75);
        }

        if (this.getData() == null)
        {
            this.openOverlay.area.copy(this.openFilmMenu.area);
        }

        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = false;
        }

        if (this.entered)
        {
            LocalPlayer player = Minecraft.getInstance().player;
            Vec3 pos = player.position();
            Vector3d cameraPos = this.camera.position;
            double distance = cameraPos.distance(pos.x, pos.y, pos.z);
            int value = Minecraft.getInstance().options.renderDistance().get();
            if (distance > value * 12 && this.getContext().notifications.notifications.stream()
                .noneMatch(notification -> notification.message == UIKeys.FILM_TELEPORT_DESCRIPTION))
            {
                this.getContext().notifyError(UIKeys.FILM_TELEPORT_DESCRIPTION);
            }
            this.entered = false;
        }
    }

    /**
     * Update logic for such components as repeat fixture, minema recording,
     * sync mode, flight mode, etc.
     */
    private void updateLogic(UIContext context)
    {
        /* Loop fixture */
        if (BBSSettings.editorLoop.get() && this.isRunning())
        {
            Vector2i loop = this.getLoopingRange();
            int min = loop.x;
            int max = loop.y;
            int ticks = this.getCursor();

            if (!this.recorder.isRecording() && !this.controller.isRecording() && min >= 0 && max >= 0 && min < max && (ticks >= max - 1 || ticks < min))
            {
                this.setCursor(min);
            }
        }

        /* Animate flight mode, and record the camera into the clip during playback */
        this.flightRecorder.update();

        /* Rewind playback back to 0 */
        if (this.lastRunning && !this.isRunning())
        {
            this.lastRunning = this.runner.isRunning();

            if (BBSSettings.editorRewind.get())
            {
                this.setCursor(0);
                this.notifyServer(ActionState.RESTART);
            }
        }
    }

    /**
     * Paint the editor canvas behind the docked panels and give each panel a
     * deeper surface, so they read as recessed wells inset into the frame.
     */
    private void renderPanelSurfaces(UIContext context)
    {
        /* Naht in der Designfarbe (--seam #0D0D0D): der Grund zwischen den Flaechen soll
         * dunkler sein als die Flaechen selbst, nicht heller. */
        this.editor.area.render(context.batcher, 0xFF0D0D0D);

        for (UIElement panel : this.panelById.values())
        {
            if (panel.isVisible() && panel != this.preview)
            {
                panel.area.render(context.batcher, BBSSettings.deepSurface());
            }
        }
    }

    /**
     * Drawn on top of the panels so the inset shadow and border show even over
     * panels that paint their own opaque content (the timeline, lists, etc.).
     */
    private void renderPanelBorders(UIContext context)
    {
        if (!BBSSettings.interfaceShadows.get())
        {
            return;
        }

        int fade = Colors.setA(Colors.A100, 0F);

        for (UIElement panel : this.panelById.values())
        {
            if (!panel.isVisible() || panel == this.preview)
            {
                continue;
            }

            Area a = panel.area;

            context.batcher.gradientVBox(a.x, a.y, a.ex(), a.y + 4, Colors.A25, fade);
            context.batcher.gradientVBox(a.x, a.ey() - 4, a.ex(), a.ey(), fade, Colors.A25);
            context.batcher.gradientHBox(a.x, a.y, a.x + 4, a.ey(), Colors.A25, fade);
            context.batcher.gradientHBox(a.ex() - 4, a.y, a.ex(), a.ey(), fade, Colors.A25);
        }
    }

    @Override
    public void startRenderFrame(float tickDelta)
    {
        super.startRenderFrame(tickDelta);

        this.controller.startRenderFrame(tickDelta);
    }

    @Override
    public void renderInWorld(LevelRenderContext context)
    {
        super.renderInWorld(context);

        if (!BBSRendering.isIrisShadowPass())
        {
            /* 26.2: Projektion gibt es nur noch als GPU-Puffer, lesbar im Kamerazustand.
             * ⚠ Das ist NICHT die Zeichenmatrix (Sonde projektion.paar: gleich=false) — daher
             * der versetzte Auswahl-Umriss; Belege in BBSRendering#zeichenProjektion. */
            this.lastProjection.set(context.levelState().cameraRenderState.projectionMatrix);

            /* ⚗️ renderInWorld laeuft ZWEIMAL je Bild (lage.weltbild 400 vs stencil 200) —
             * getrennt zaehlen, sonst sieht Doppel-Zeichnen wie Textur-Wandern aus. */
            mchorse.bbs_mod.graphics.Zeichenlage.melden(
                mchorse.bbs_mod.client.BBSRendering.renderingWorld ? "weltbild-bbs" : "weltbild-vanilla");

            /* ⚗️ Rufer-Zaehler (feuert 1764x je 1552 Bilder — wer ruft zusaetzlich?).
             * ⛔ UNGEFILTERT sammeln: gewobene Handler stehen unter VANILLA-Namen im Stapel. */
            if (System.getProperty("bbs.mess") != null)
            {
                StackTraceElement[] stapel = Thread.currentThread().getStackTrace();
                StringBuilder ruf = new StringBuilder();

                for (int i = 2; i < Math.min(stapel.length, 9); i++)
                {
                    ruf.append(i > 2 ? " <- " : "").append(stapel[i].getClassName())
                       .append('.').append(stapel[i].getMethodName())
                       .append(':').append(stapel[i].getLineNumber());
                }

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("weltbild.rufer", ruf.toString());
            }
            /* 1.21.1 carries the camera view rotation in positionMatrix(); the context's
             * MatrixStack base is just an identity stack for entity-relative rendering, so
             * reading the view from it would drop the camera angle and misaim every raycast.
             *
             * 26.2: positionMatrix() gibt es an Fabrics LevelRenderContext nicht mehr --
             * uebrig sind submitNodeCollector, poseStack, gameRenderer, levelRenderer und
             * levelState. Derselbe Wert steht als viewRotationMatrix im eingefrorenen
             * Kamerazustand, also direkt neben der Projektion eine Zeile darueber. */
            this.lastView.set(context.levelState().cameraRenderState.viewRotationMatrix);

            /* ⚗️ HINKT DER EINGEFRORENE ZUSTAND WIRKLICH? (2026-08-05, #2.)
             *
             * Der Eintrag leitet aus den Matrixquellen ab, die Blickdrehung sei ein Bild alt --
             * abgeleitet, nicht gemessen. Hier stehen beide Werte nebeneinander: die
             * eingefrorene Drehung und die LEBENDE Kamera desselben Bildes. Beim Schwenken
             * muessen sie auseinanderlaufen, im Stillstand zusammenfallen. Faellt das aus,
             * ist die ganze Ursachenkette von #2 falsch. */
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                net.minecraft.client.Camera lebend =
                    net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera();
                /* ⛔ BEIDE IN DENSELBEN RAUM, SONST MISST MAN DIE HAENDIGKEIT (2026-08-05).
                 *
                 * Erster Anlauf verglich `viewRotation.transformDirection(0,0,-1)` mit dem
                 * gedrehten Blickvektor der lebenden Kamera. Die eine Matrix bildet WELT->SICHT
                 * ab, die Drehung SICHT->WELT -- die Ergebnisse waren systematisch
                 * Spiegelbilder (X-Vorzeichen gedreht) und der "Winkel" wuchs brav mit dem
                 * Schwenk. Das sah aus wie ein Versatz und war meine eigene Rechnung.
                 *
                 * Richtig ist die Probe gegen die IDENTITAET: der lebende Blickvektor, durch
                 * die eingefrorene Sichtmatrix geschickt, MUSS (0,0,-1) ergeben, wenn beide
                 * zum selben Bild gehoeren. Die Abweichung davon IST der Versatz -- in Grad,
                 * ohne Bezugsrahmen-Frage. */
                org.joml.Matrix4f eingefroren =
                    new org.joml.Matrix4f(context.levelState().cameraRenderState.viewRotationMatrix);
                org.joml.Vector3f hinLebend = new org.joml.Vector3f(0F, 0F, -1F)
                    .rotate(lebend.rotation());
                org.joml.Vector3f inSicht = eingefroren.transformDirection(
                    new org.joml.Vector3f(hinLebend));

                mchorse.bbs_mod.graphics.Messpunkt.zaehle("sicht.versatz",
                    String.format("lebend-in-sicht=%.4f/%.4f/%.4f (soll 0/0/-1) versatz=%.3f°",
                        inSicht.x, inSicht.y, inSicht.z,
                        Math.toDegrees(Math.acos(Math.max(-1F, Math.min(1F,
                            inSicht.normalize().dot(0F, 0F, -1F)))))));
            }
        }

        this.controller.renderFrame(context);
    }

    /* IUICameraWorkDelegate implementation */

    public void notifyServer(ActionState state)
    {
        if (this.data == null || !ClientNetwork.isIsBBSModOnServer())
        {
            return;
        }

        String id = this.data.getId();
        int tick = this.getCursor();

        ClientNetwork.sendActionState(id, state, tick);
    }

    public Camera getCamera()
    {
        return this.camera;
    }

    public Camera getWorldCamera()
    {
        return BBSModClient.getCameraController().camera;
    }

    public CameraController getCameraController()
    {
        return BBSModClient.getCameraController();
    }

    @Override
    public int getCursor()
    {
        return this.runner.ticks;
    }

    @Override
    public void setCursor(int value)
    {
        this.flightRecorder.rewind();

        this.runner.ticks = Math.max(0, value);

        this.notifyServer(ActionState.SEEK);
    }

    public boolean isRunning()
    {
        return this.runner.isRunning();
    }

    public void togglePlayback()
    {
        this.setFlight(false);

        this.runner.toggle(this.getCursor());
        this.lastRunning = this.runner.isRunning();

        if (this.runner.isRunning())
        {
            this.cameraEditor.clips.scale.shiftIntoMiddle(this.getCursor());

            if (this.replayEditor.keyframeEditor != null)
            {
                this.replayEditor.keyframeEditor.view.getXAxis().shiftIntoMiddle(this.getCursor());
            }
        }
    }

    public boolean canUseKeybinds()
    {
        return !this.isFlying();
    }

    /** Sichtbare Clip-Timeline mit Auswahl: Clip-Keybinds (M=duration) beanspruchen die
     *  Taste — geteilte Preview-Shortcuts treten zurueck (Clips-Editor sitzt im Dispatch davor). */
    public boolean hasSelectedClip()
    {
        return (this.cameraEditor != null && this.cameraEditor.isVisible() && this.cameraEditor.getClip() != null)
            || (this.actionEditor != null && this.actionEditor.isVisible() && this.actionEditor.getClip() != null);
    }

    public void fillData()
    {
        this.cameraEditor.fillData();
        this.actionEditor.fillData();

        if (this.replayEditor.keyframeEditor != null && this.replayEditor.keyframeEditor.editor != null)
        {
            this.replayEditor.keyframeEditor.editor.update();
        }
    }

    public void teleportToCamera()
    {
        Camera camera = this.getCamera();
        Vector3d cameraPos = camera.position;
        double x = cameraPos.x;
        double y = cameraPos.y;
        double z = cameraPos.z;

        PlayerUtils.teleport(x, y, z, MathUtils.toDeg(camera.rotation.y) - 180F, MathUtils.toDeg(camera.rotation.x));
    }

    public void setPlayerToCamera(boolean value)
    {
        this.playerToCamera = value;
        BBSSettings.editorPlayerFollowsCamera.set(value);
    }

    public boolean checkShowNoCamera()
    {
        boolean noCamera = this.getData().camera.calculateDuration() <= 0;

        if (noCamera)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(
                UIKeys.FILM_NO_CAMERA_TITLE,
                UIKeys.FILM_NO_CAMERA_DESCRIPTION
            ));
        }

        return noCamera;
    }

    public void updateActors(String filmId, Map<String, Integer> actors)
    {
        if (this.data != null && this.data.getId().equals(filmId))
        {
            this.controller.updateActors(actors);
        }
    }

    @Override
    public boolean handleKeyPressed(UIContext context)
    {
        return this.controller.orbit.keyPressed(context, this.preview.area);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.showPanel(data.getInt("panel"));
        this.setCursor(data.getInt("tick"));
        this.controller.createEntities();
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putInt("panel", this.getPanelIndex());
        data.putInt("tick", this.getCursor());
    }

    @Override
    protected boolean canSave(UIContext context)
    {
        return !this.recorder.isRecording();
    }
    /** Reiterleiste aufloesen — der Film sitzt im neuen Bild als Chip in der Topbar. */
    @Override
    protected int getTabsHeightPx()
    {
        return 0;
    }

    /** Filmmenue oeffnen — Einstieg fuer den Film-Chip der Topbar. */
    public void oeffneFilmMenue()
    {
        this.getContext().replaceContextMenu(this::fillFilmContextMenu);
    }

    /** Neuen Film anlegen — Einstieg fuer den Plus-Knopf der Topbar. */
    public void neuerFilm()
    {
        this.addTab();
    }

    /**
     * Oeffentlicher Einstieg auf das schon vorhandene {@link #resetFilmLayout()} — noetig, weil
     * das Zuruecksetzen sonst nur ueber das Filmmenue erreichbar ist und nicht aus einer Messung.
     */
    public void layoutZuruecksetzen()
    {
        this.resetFilmLayout();
    }

    /**
     * Eine Flaeche auf den ganzen Editor ziehen und wieder zurueck — Blenders Ctrl+Space.
     * Das vorherige Layout wird gemerkt, damit das Zurueck nichts verliert.
     */
    public void flaecheMaximieren(String panelId)
    {
        if (this.layoutVorMaximieren != null)
        {
            this.setCurrentFilmLayoutRoot(this.layoutVorMaximieren);
            this.layoutVorMaximieren = null;
        }
        else
        {
            this.layoutVorMaximieren = this.getCurrentFilmLayoutRoot();
            this.setCurrentFilmLayoutRoot(new EditorLayoutNode.PanelNode(panelId));
        }

        this.refreshCurrentFilmLayout();
    }

    /** Ist gerade eine Flaeche maximiert? Der Knopf im Flaechenkopf faerbt sich danach. */
    public boolean istMaximiert()
    {
        return this.layoutVorMaximieren != null;
    }

    /**
     * Einmalige Umstellung auf das neue Standardbild. Ein vor dem Umbau gespeichertes Layout
     * setzt den Dope Sheet als schmale Spalte neben die Vorschau; erkennbar daran, dass
     * {@code editArea} darin vorkommt. Solche Layouts werden einmal auf den neuen Standard
     * gezogen — ohne das sieht ein Bestandsnutzer nach dem Update weiter das alte Bild.
     */
    private void layoutAufNeuesBildZiehen()
    {
        /* Null-fest: nach einem Hotswap steht ein neu hinzugekommenes statisches Feld auf null,
         * weil statische Initialisierer beim Redefine nicht laufen. Dann einfach nichts tun. */
        if (BBSSettings.editorLayoutUmgestellt == null || BBSSettings.editorLayoutUmgestellt.get())
        {
            return;
        }

        BBSSettings.editorLayoutUmgestellt.set(true);

        for (ValueEditorLayout.FilmEditor editor : ValueEditorLayout.FilmEditor.values())
        {
            EditorLayoutNode root = this.getFilmLayoutSettings().getFilmLayoutRoot(editor);
            HashSet<String> ids = new HashSet<>();

            this.collectPanelIds(root, ids);

            if (ids.contains(PANEL_EDIT_AREA_ID))
            {
                this.getFilmLayoutSettings().setFilmLayoutRoot(editor, EditorLayoutNode.defaultFilmLayout());
            }
        }
    }
}
