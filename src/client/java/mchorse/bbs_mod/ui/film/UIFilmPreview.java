package mchorse.bbs_mod.ui.film;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.audio.AudioStemExporter;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.controller.UIMotionPathContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIOnionSkinContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fStack;
import org.joml.Vector2i;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UIFilmPreview extends UIElement
{
    private List<AudioClip> clips = new ArrayList<>();
    private UIFilmPanel panel;

    public UIElement icons;
    /** Werkzeugleiste oben im Viewport (Designsystem). */
    public UIWerkzeugleisteNano werkzeuge;
    public UIViewportHudNano hud;
    private final com.mcstudio.nano.NanoFlaeche eckenFlaeche;
    private String eckenStand = "";

    public UIIcon onionSkin;
    public UIIcon motionPath;
    public UIIcon plause;
    public UIIcon teleport;
    public UIIcon flight;
    public UIIcon control;
    public UIIcon perspective;
    public UIIcon recordReplay;
    public UIIcon recordVideo;

    public UIFilmPreview(UIFilmPanel filmPanel)
    {
        this.panel = filmPanel;

        /* Blenders N-Panel am rechten Rand. Meldet seinen ersten Reiter selbst an;
         * weitere kommen ueber UISeitenleiste.anmelden(name, bauer) dazu. */
        UISeitenleiste.anmelden("Rig", mchorse.bbs_mod.ui.film.replays.UIRigReiter::neu);
        UISeitenleiste.anmelden("Render", UIRenderReiterNano::neu);

        UISeitenleiste leiste = new UISeitenleiste();

        /* ⛔ Anteilig, nicht fest: mit fester Breite 268 verschluckte die Leiste in einem
         * 854x480-Fenster fast die ganze Vorschau. Blenders N-Panel nimmt auch einen
         * Anteil, keine feste Pixelzahl. Gedeckelt, damit sie auf grossen Schirmen nicht
         * ausufert. */
        /* ⛔ Anteilig UND von links gerechnet: mit x(1F,-1)+anchor sass die Leiste bei
         * x=8, also links (gemessen). x(0.66F)+w(0.34F) legt sie eindeutig ans rechte
         * Drittel, unabhaengig von der Fenstergroesse. */
        leiste.relative(this).x(0.66F).y(30).w(0.34F, -4).h(1F, -34);
        this.add(leiste);

        this.icons = UI.row(0, 0);
        this.icons.row().resize();
        this.icons.relative(this).x(0.5F).y(1F).anchor(0.5F, 1F);

        /* Preview buttons */
        this.onionSkin = new UIIcon(Icons.ONION_SKIN, (b) -> this.openOnionSkin());
        this.onionSkin.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);
        this.motionPath = new UIIcon(Icons.CURVES, (b) -> this.openMotionPath());
        this.motionPath.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE);
        this.plause = new UIIcon(() -> this.panel.isRunning() ? Icons.PAUSE : Icons.PLAY, (b) -> this.panel.togglePlayback());
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE);
        this.plause.context((menu) ->
        {
            menu.action(Icons.PLAY, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAY_FILM, () ->
            {
                if (!this.panel.checkShowNoCamera())
                {
                    this.panel.dashboard.closeThisMenu();

                    Films.playFilm(this.panel.getData().getId(), true);
                }
            });

            menu.action(Icons.PAUSE, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FREEZE_PAUSED, !this.panel.getController().isPaused(), () ->
            {
                this.panel.getController().setPaused(!this.panel.getController().isPaused());
            });
        });
        this.teleport = new UIIcon(Icons.MOVE_TO, (b) -> this.panel.teleportToCamera());
        this.teleport.tooltip(UIKeys.FILM_TELEPORT_TITLE);
        this.teleport.context((menu) ->
        {
            menu.action(Icons.MOVE_TO, UIKeys.FILM_TELEPORT_CONTEXT_PLAYER, this.panel.playerToCamera, () -> this.panel.setPlayerToCamera(!this.panel.playerToCamera));
            menu.action(Icons.COPY, UIKeys.CAMERA_PANELS_CONTEXT_COPY_POSITION, () ->
            {
                Position current = new Position(this.panel.getCamera());

                Map<String, Double> map = new LinkedHashMap<>();

                UICameraUtils.copyPoint(map, current.point);
                UICameraUtils.copyAngle(map, current.angle);

                Window.setClipboard(UICameraUtils.mapToString(map));
            });

            Map<String, Double> map = UICameraUtils.stringToMap(Window.getClipboard());

            if (!map.isEmpty())
            {
                menu.action(Icons.PASTE, UIKeys.CAMERA_PANELS_CONTEXT_PASTE_POSITION, () ->
                {
                    Position position = new Position();
                    Point point = UICameraUtils.createPoint(map);
                    Angle angle = UICameraUtils.createAngle(map);

                    if (point != null && angle != null)
                    {
                        position.point.set(point);
                        position.angle.set(angle);
                    }

                    this.panel.cameraEditor.editClip(position);
                });
            }
        });
        this.flight = new UIIcon(Icons.PLANE, (b) -> this.panel.toggleFlight());
        this.flight.tooltip(UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT);
        this.control = new UIIcon(Icons.POSE, (b) -> this.panel.getController().toggleControl());
        this.control.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL);
        this.perspective = new UIIcon(this.panel.getController()::getOrbitModeIcon, (b) -> this.panel.getController().toggleOrbitMode());
        this.perspective.tooltip(UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE);
        this.perspective.context((menu) ->
        {
            UIFilmController controller = this.panel.getController();

            if (controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_ORBIT_TELEPORT_TO_RECORDING, controller::teleportOrbitPivotToReplay);
                menu.action(Icons.LINK, UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, controller.orbit.isAttached(), controller::toggleOrbitAttachment);
                menu.action(Icons.FRUSTUM, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, controller.orbit.isOrtho(), controller.orbit::toggleOrtho);
            }
        });
        this.recordReplay = new UIIcon(Icons.SPHERE, (b) -> this.panel.getController().pickRecording());
        this.recordReplay.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordReplay.context((menu) ->
        {
            menu.action(Icons.DOWNLOAD, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_INSTANT_KEYFRAMES, this.panel.getController().isInstantKeyframes(), () ->
            {
                this.panel.getController().toggleInstantKeyframes();
            });

            menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_TELEPORT_TO_PLAYER, () -> this.panel.getController().insertPlayerFrame());
        });
        this.recordVideo = new UIIcon(Icons.VIDEO_CAMERA, (b) ->
        {
            if (this.panel.checkShowNoCamera())
            {
                return;
            }

            if (!FFMpegUtils.checkFFMPEG())
            {
                UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
                UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

                guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
                panel.icons.add(guide);

                UIOverlay.addOverlay(this.getContext(), panel);

                return;
            }

            int duration = this.panel.getData().camera.calculateDuration();
            UIFilmPanel.applyExportSizeToBBS();
            BBSRendering.scheduleAfterNextExportFrame(() ->
            {
                this.panel.recorder.startRecording(duration, BBSRendering.getTexture().id, BBSRendering.getVideoWidth(), BBSRendering.getVideoHeight());
            });
        });
        this.recordVideo.tooltip(UIKeys.CAMERA_TOOLTIPS_RECORD);
        this.recordVideo.context((menu) ->
        {
            menu.action(Icons.CAMERA, UIKeys.FILM_SCREENSHOT, () ->
            {
                ScreenshotRecorder recorder = BBSModClient.getScreenshotRecorder();
                File output = Window.isAltPressed() ? null : recorder.getScreenshotFile();

                UIFilmPanel.applyExportSizeToBBS();
                BBSRendering.scheduleAfterNextExportFrame(() ->
                {
                    Texture texture = BBSRendering.getTexture();
                    int w = BBSRendering.getVideoWidth();
                    int h = BBSRendering.getVideoHeight();
                    recorder.takeScreenshot(output, texture.id, w, h);

                    /* Derselbe Zug legt auch das Standbild des Films an — genau der Ausschnitt,
                     * den der Nutzer gerade gewaehlt hat, wird sein Kachelbild. Kein zweiter
                     * Renderdurchgang, nur ein zweiter Schreibort. */
                    this.standbildAblegen(texture, w, h);

                    this.panel.restorePreviewSize();

                    UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
                    if (currentMenu != null)
                    {
                        UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                            UIKeys.FILM_SCREENSHOT_TITLE,
                            UIKeys.FILM_SCREENSHOT_DESCRIPTION,
                            recorder.getScreenshots()
                        );
                        UIOverlay.addOverlay(currentMenu.context, overlayPanel);
                    }
                });
            });

            menu.action(Icons.FILM, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> this.panel.recorder.openMovies());
            menu.action(Icons.GEAR, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEO_SETTINGS, () ->
            {
                UISettingsOverlayPanel panel = new UISettingsOverlayPanel();

                panel.showCategory("bbs", "video");
                UIOverlay.addOverlay(this.getContext(), panel, 430, 380);
            });

            menu.action(Icons.VIDEO_CAMERA, UIKeys.FILM_RENDER_QUEUE, this::exportQueueFromTabs);
            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
            menu.action(Icons.REFRESH, UIKeys.FILM_RESET_REPLAYS, this.panel.recorder.resetReplays, () ->
            {
                this.panel.recorder.resetReplays = !this.panel.recorder.resetReplays;
            });
        });

        this.icons.add(this.onionSkin, this.motionPath, this.plause, this.teleport, this.flight, this.control, this.perspective, this.recordReplay, this.recordVideo);

        /* Die alte Reihe bleibt als OBJEKT geladen — die UIIcons tragen Kontextmenues,
         * Tooltips und Zustaende, und die Werkzeugleiste loest sie per clickItself() aus.
         *
         * ⛔ setVisible(false) reichte NICHT: die Reihe zeichnete weiter einen dunklen Balken
         * an den unteren Bildrand (gemessen 2026-08-19: area 499,257 180x20, Viewport endet
         * bei 277 — genau der Streifen, den der Nutzer meldete). Deshalb wird sie unten gar
         * nicht mehr als Kind eingehaengt; ohne Elternteil rendert sie nie. */
        this.icons.setVisible(false);

        /* ⛔ KEINE Kopfleiste mehr ueber dem Bild. Gemessen am 2026-08-19: von acht Elementen
         * hatten sechs keine Klickzone — Editor-Typ, Modus-Waehler und die drei Menues zeichneten
         * nur ein Hover-Rechteck. Was lebte (Perspektive, Replay-Aufnahme, Vollbild) sitzt jetzt
         * in der schwebenden Leiste am Bild; „Raster" hatte gar kein Ziel und faellt ersatzlos.
         * Gewinn: 26 px Bildhoehe und ein Bedienort statt zwei. */

        /* Schwebende Werkzeugleiste links am Bild — Blenders Toolbar. Drei Gruppen:
         * Werkzeuge · Ansicht · Aufnahme. */
        this.werkzeuge = new UIWerkzeugleisteNano(this);
        this.werkzeuge.relative(this).x(7).y(7)
            .w(UIWerkzeugleisteNano.breite()).h(UIWerkzeugleisteNano.hoehe(6, 1));

        /* Kontextzeile am Bild wie im Entwurf: Ansichtsart, Film, gewaehltes Replay. */
        this.hud = new UIViewportHudNano(this);
        this.hud.relative(this).x(46).y(7).w(1F, -86).h(28);

        /* ⛔ ECKEN MIT NANOVG, NICHT MIT RECHTECKEN. Uebermalen bleibt eine Treppe, auch mit
         * Teildeckung je Bildschirmzeile (2026-09-11: 4 -> 8 Stufen, sichtbar blieb sie).
         * NanoVG glaettet Kurven selbst. Zuerst hinzugefuegt: ueber dem Bild, unter Leiste
         * und HUD. */
        this.eckenFlaeche = new com.mcstudio.nano.NanoFlaeche(this::eckenZeichnen);
        this.eckenFlaeche.relative(this).x(0).y(0).w(1F).h(1F);
        this.eckenFlaeche.setEnabled(false);

        this.add(this.eckenFlaeche, this.werkzeuge, this.hud);
        mchorse.bbs_mod.ui.film.markers.UITimerPlacement.install(this, this.panel);
    }

    public void openOnionSkin()
    {
        this.getContext().replaceContextMenu(new UIOnionSkinContextMenu(this.panel, this.panel.getController().getOnionSkin()));
    }

    public void openMotionPath()
    {
        this.getContext().replaceContextMenu(new UIMotionPathContextMenu(this.panel, this.panel.getController().getMotionPath()));
    }

    private void exportQueueFromTabs()
    {
        if (this.panel.checkShowNoCamera())
        {
            return;
        }

        if (!FFMpegUtils.checkFFMPEG())
        {
            UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
            UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

            guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
            panel.icons.add(guide);

            UIOverlay.addOverlay(this.getContext(), panel);

            return;
        }

        this.panel.startQueueExportFromOpenTabs();
    }

    private void renderAudio()
    {
        Clips camera = this.panel.getData().camera;
        List<AudioClip> audioClips = camera.getClips(AudioClip.class);

        String timestamp = StringUtils.createTimestampFilename();
        File videos = BBSRendering.getVideoFolder();
        UIContext context = this.getContext();
        Vector2i range = BBSSettings.editorLoop.get() ? this.panel.getLoopingRange() : new Vector2i();

        if (BBSSettings.audioExportSeparateFiles.get())
        {
            /* Own subfolder: one clip = one file means a busy film drops dozens of wavs, and
             * dumping those next to the rendered videos would bury them. */
            File stems = new File(videos, timestamp + "_stems");
            int written = AudioStemExporter.exportStems(stems, audioClips, 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y));

            if (written > 0)
            {
                UIOverlay.addOverlay(context, new UIMessageFolderOverlayPanel(UIKeys.GENERAL_SUCCESS, UIKeys.FILM_RENDER_AUDIO_SUCCESS, stems));
            }
            else
            {
                UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.FILM_RENDER_AUDIO_ERROR));
            }

            return;
        }

        if (AudioRenderer.renderAudio(new File(videos, timestamp + ".wav"), audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
        {
            UIOverlay.addOverlay(context, new UIMessageFolderOverlayPanel(UIKeys.GENERAL_SUCCESS, UIKeys.FILM_RENDER_AUDIO_SUCCESS, videos));
        }
        else
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.FILM_RENDER_AUDIO_ERROR));
        }
    }

    public Area getViewport()
    {
        int width = BBSRendering.getVideoWidth();
        int height = BBSRendering.getVideoHeight();
        int w = this.area.w;
        int h = this.area.h;

        Camera camera = new Camera();

        camera.copy(this.panel.getWorldCamera());
        camera.updatePerspectiveProjection(width, height);

        Vector2i size = Vectors.resize(width / (float) height, w, h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(this.area.mx() - area.w / 2, this.area.my() - area.h / 2);

        return area;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
            if (this.panel.getController().orbitGizmo.mouseClicked(context, area))
            {
                return true;
            }

            return this.panel.replayEditor.clickViewport(context, area);
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context) && !this.panel.isFlying() && this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
        {
            return this.panel.getController().zoomOrbit(context.mouseWheel);
        }

        return super.subMouseScrolled(context);
    }

    /**
     * ⛔ R/G/S IM KAMERA-BLICK, OHNE DEN ALTEN POSE-EDITOR (Umzug 2026-08-24, derselbe
     * Schnitt wie an der Zeitleiste: Logik behalten, Zuhause tauschen). Die Transform-
     * Kuerzel sind am {@code UIPoseTransforms} des Keyframe-Editors registriert — verborgen
     * laeuft dessen Keybind-Pruefung nie. Hier werden dieselben Keybinds gefeuert; ihre
     * {@code active}-Bedingungen (enabled/editing) werden RESPEKTIERT, nur die Sichtbarkeit
     * nicht — sie sind der einzige Grund, warum das Panel frueher aufgeschaltet wurde.
     * Muster: KameraSpurTasten.weiterreichen.
     */
    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        var transform = mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils
            .getEditableTransform(this.panel.replayEditor == null ? null : this.panel.replayEditor.keyframeEditor);

        if (transform != null
            && !mchorse.bbs_mod.ui.framework.elements.input.UITransformHandler.aufSchirm(transform))
        {
            for (mchorse.bbs_mod.ui.utils.keys.Keybind keybind : transform.keys().keybinds)
            {
                /* inside-Kuerzel messen gegen eine Flaeche, die verborgen veraltet ist. */
                if (keybind.inside || (keybind.active != null && !keybind.active.get()))
                {
                    continue;
                }

                if (keybind.feuern(context.getKeyCode(), context.getKeyAction(), true))
                {
                    return true;
                }
            }
        }

        return super.subKeyPressed(context);
    }

    private void eckenZeichnen(long vg, int b, int h, float mausX, float mausY,
        com.mcstudio.nano.NanoFlaeche f)
    {
        Area v = this.getViewport();
        float k = Math.max(1F, f.skala());

        /* 6 = NanoDesign.R_PANEL, damit die Rundung neben der der Nachbarflaechen sitzt. */
        com.mcstudio.nano.NanoStifte.eckenSaum(vg, (v.x - this.area.x) * k, (v.y - this.area.y) * k,
            v.w * k, v.h * k, 6F * k, 0xFF0D0D0D);
    }

    @Override
    public void render(UIContext context)
    {
        Texture texture = BBSRendering.getTexture();
        Area area = this.getViewport();
        String eckenJetzt = area.x + ":" + area.y + ":" + area.w + ":" + area.h;

        /* Nur neu malen, wenn das Rechteck sich bewegt hat — sonst kostet es jeden Frame. */
        if (!eckenJetzt.equals(this.eckenStand))
        {
            this.eckenStand = eckenJetzt;
            this.eckenFlaeche.neuMalen();
        }

        Camera camera = this.panel.getCamera();

        camera.copy(this.panel.getWorldCamera());
        camera.view.set(this.panel.lastView);
        camera.projection.set(this.panel.lastProjection);
        context.batcher.flush();

        if (texture != null)
        {
            if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                mchorse.bbs_mod.graphics.Messpunkt.zaehle("preview.tex",
                    "id=" + texture.id + " " + texture.width + "x" + texture.height);
            }

            context.batcher.texturedBox(texture, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);

            this.renderViewportHud(context, area);

            /* Runde Ecken wie an Zeitleiste und Panels. texturedBox kann nur ein hartes
             * Rechteck, also werden die vier Ecken danach in der Nahtfarbe uebermalt —
             * eine Treppe aus Pixelzeilen entlang der Kreisgleichung. Bei 6 px Radius sind
             * das 24 winzige Rechtecke je Bild, gegenueber dem Weltrender nichts. */
        }

        /* The navigation ball replaces the axes crosshair in the corner */
        if (!this.panel.getController().orbitGizmo.isActive())
        {
            this.renderCursor(context);
        }

        boolean needGuides = BBSSettings.editorRuleOfThirds.get()
            || BBSSettings.editorCenterLines.get()
            || BBSSettings.editorCrosshair.get();
        if (needGuides)
        {
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
        }

        /* Unten rechts steht die EXPORT-Aufloesung, nicht die der Vorschau-Textur.
         * Im flaechenfuellenden Modus sind das zwei verschiedene Zahlen — dort rendert die
         * Vorschau in Flaechengroesse (gemessen 896x340), waehrend das Video 3840x2160 wird.
         * Die Zahl, die den Nutzer interessiert, ist die des Exports. */
        int resW = BBSSettings.videoWidth.get();
        int resH = BBSSettings.videoHeight.get();
        String resLabel = resW + " × " + resH;
        int resLabelW = context.batcher.getFont().getWidth(resLabel);
        int resLabelH = context.batcher.getFont().getHeight();
        int resX = area.ex() - 8;
        int resY = area.ey() - resLabelH - 6;

        /* Wie die Kontextzeile oben links: Schattenkante statt Karte. Der weisse Kasten war
         * der letzte Rest alter Optik im Bild. */
        context.batcher.text(resLabel, resX - resLabelW + 1, resY + 1, 0xFF0A0A0A);
        context.batcher.text(resLabel, resX - resLabelW, resY, 0xFFE0E0E0);

        this.panel.getController().renderHUD(context, area);

        KameraRahmen.zeichnen(this.panel, context, area);

        ViewportWellenform.zeichnen(this.panel, this.clips, context, area);

        Area a = this.icons.area;

        /* Render icon bar */
        int barShade = BBSSettings.isLightTheme() ? (Colors.A50 | 0xFFFFFF) : Colors.A50;
        context.batcher.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0, barShade);

        /* ⛔ Nur zeichnen, wenn die alte Icon-Reihe ueberhaupt sichtbar ist. Sie ist seit dem
         * UI-Umbau ausgeblendet, ihre area bleibt aber gesetzt — die Hervorhebungen landeten
         * weiter im Bild, ohne dass ein Knopf darunter lag. */
        if (this.icons.isVisible())
        {
        if (this.panel.isFlying()) UIDashboardPanels.renderHighlight(context.batcher, this.flight.area, Direction.BOTTOM);
        if (this.panel.getController().isControlling()) UIDashboardPanels.renderHighlight(context.batcher, this.control.area, Direction.BOTTOM);
        if (this.panel.getController().isRecording()) UIDashboardPanels.renderHighlight(context.batcher, this.recordReplay.area, Direction.BOTTOM);
        if (this.panel.recorder.isRecording()) UIDashboardPanels.renderHighlight(context.batcher, this.recordVideo.area, Direction.BOTTOM);
        if (this.panel.getController().getOnionSkin().enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.onionSkin.area, Direction.BOTTOM);
        if (this.panel.getController().getMotionPath().enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.motionPath.area, Direction.BOTTOM);
        }

        if (this.panel.getController().isControlling())
        {
            String s = UIKeys.FILM_CONTROLLER_CONTROL_MODE_TOOLTIP.format(KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey())).get();
            int w = context.batcher.getFont().getWidth(s);
            int height = context.batcher.getFont().getHeight();

            context.batcher.textCard(s, a.mx(w), a.y - height - 5);
        }

        context.batcher.clip(this.area, context);
        super.render(context);
        context.batcher.unclip(context);
    }

    /**
     * Draws Minecraft's HUD inside the viewport when a tracker clip asks for it.
     *
     * Extract only the HUD into the existing screen state. Calling Gui.extractRenderState
     * here would reset that state and recursively extract the dashboard again.
     */
    private void renderViewportHud(UIContext context, Area area)
    {
        if (!mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.shouldRenderHud())
        {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.gui == null || mc.player == null || area.w <= 0 || area.h <= 0)
        {
            return;
        }

        context.batcher.flush();

        com.mojang.blaze3d.platform.Window window = mc.getWindow();
        int gameScale = window.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
        int guiWidth = (int) Math.ceil(window.getWidth() / (double) gameScale);
        float scale = area.w / (float) guiWidth;
        int guiHeight = Math.max(1, (int) Math.ceil(area.h / scale));
        net.minecraft.client.gui.GuiGraphicsExtractor draw = new ViewportHudGraphics(
            mc, ((mchorse.bbs_mod.mixin.client.GuiRenderStateAccessor) mc.gui).bbs$getGuiRenderState(), guiWidth, guiHeight);

        /* ⚠️ 26.2: der Zeichen-Stapel der Oberflaeche ist ZWEIDIMENSIONAL geworden
         * (Matrix3x2fStack statt PoseStack) — die Oberflaeche kennt kein Z mehr, die Tiefe
         * regelt der Zeichengraph ueber die Reihenfolge. Deshalb heissen die Aufrufe
         * pushMatrix/popMatrix und translate/scale nehmen nur noch x und y; das alte
         * "0F" fuer Z faellt ersatzlos weg (es war hier ohnehin immer 0). */
        org.joml.Matrix3x2fStack matrices = draw.pose();

        /* Scissor auf das viewport-rechteck: ohne das zeichnet das HUD ueber das ganze
         * dashboard (hotbar neben der replay-liste, siehe screenshot des nutzers). */
        draw.enableScissor(area.x, area.y, area.ex(), area.ey());

        boolean hidden = mchorse.bbs_mod.client.HudSichtbarkeit.istVersteckt();

        /* Unser debug-overlay haengt am HudRenderCallback und wuerde den viewport
         * zupflastern — es gehoert auf den bildschirm, nicht ins kamerabild. */
        boolean debugOverlay = mchorse.bbs_mod.sonden.Sonden.ueberlagerung.ausschalten();

        mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(false);

        try
        {
            mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.beginHudRendering(draw);
            /* Tracker's slide uses the real window height; this fresh HUD-only stack
             * maps that offset into the virtual canvas before the viewport transform. */
            matrices.translate(0F, matrices.m21() * (guiHeight / Math.max(1F, window.getGuiScaledHeight()) - 1F));
            matrices.pushMatrix();

            try
            {
                /* Hud reads the extractor's current window dimensions. Pre-multiply the
                 * viewport mapping so the tracker's slide is scaled with the HUD too. */
                matrices.scaleLocal(scale, scale);
                matrices.translateLocal(area.x, area.y);
                mc.gui.hud.extractRenderState(draw, mc.getDeltaTracker());
            }
            finally
            {
                matrices.popMatrix();
            }
        }
        finally
        {
            try
            {
                mchorse.bbs_mod.camera.clips.misc.TrackerClientClip.endHudRendering(draw);
            }
            finally
            {
                mchorse.bbs_mod.sonden.Sonden.ueberlagerung.wiederherstellen(debugOverlay);
                mchorse.bbs_mod.client.HudSichtbarkeit.verstecken(hidden);

                context.batcher.flush();
                draw.disableScissor();
            }
        }
    }

    private void renderCursor(UIContext context)
    {
        net.minecraft.client.Camera mcCamera = Minecraft.getInstance().gameRenderer.mainCamera();

        AchsenKreuz.zeichne(context.batcher, area.x + 16, area.ey() - 12,
            mcCamera.xRot(), mcCamera.yRot(), 10F);
    }
    /**
     * Legt den eben aufgenommenen Ausschnitt als Standbild des offenen Films ab.
     *
     * <p>⛔ Unter der Assets-Wurzel, nicht neben der Filmdatei: der Textur-Weg kennt nur die
     * Schemata {@code assets:} und {@code bbs:}, und Filme liegen im Welt-Ordner. Das
     * Standbild ist ohnehin ein Zwischenspeicher — es laesst sich jederzeit neu aufnehmen.</p>
     */
    void standbildAblegen(mchorse.bbs_mod.graphics.texture.Texture texture, int w, int h)
    {
        try
        {
            mchorse.bbs_mod.film.Film film = this.panel.getData();

            if (film == null || film.getId() == null || film.getId().isEmpty())
            {
                return;
            }

            java.io.File ziel = new java.io.File(mchorse.bbs_mod.BBSMod.getAssetsFolder(),
                mchorse.bbs_mod.ui.film.UIFilmSelectionPanel.standbildPfad(film.getId()));

            ziel.getParentFile().mkdirs();
            BBSModClient.getScreenshotRecorder().takeScreenshot(ziel, texture.id, w, h);
        }
        catch (Exception fehler)
        {
            /* ⛔ Nicht still: ein fehlendes Standbild faellt in der Kachelliste auf die
             * Form-Vorschau zurueck, aber der Grund gehoert ins Log. */
            org.apache.logging.log4j.LogManager.getLogger("bbs/standbild").warn("Standbild des Films konnte nicht abgelegt werden", fehler);
        }
    }


}

/** HUD-only canvas, kept package-private beside its sole renderer. */
final class ViewportHudGraphics extends net.minecraft.client.gui.GuiGraphicsExtractor
{
    private final int width;
    private final int height;

    ViewportHudGraphics(Minecraft minecraft, net.minecraft.client.renderer.state.gui.GuiRenderState state, int width, int height)
    {
        super(minecraft, state, 0, 0);
        this.width = width;
        this.height = height;
    }

    @Override
    public int guiWidth()
    {
        /* Base construction needs the real screen for its root scissor. */
        return this.width > 0 ? this.width : super.guiWidth();
    }

    @Override
    public int guiHeight()
    {
        return this.height > 0 ? this.height : super.guiHeight();
    }
}
