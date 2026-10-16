package mchorse.bbs_mod;

import java.util.HashSet;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.settings.values.ui.ValuePhysicsDebug;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

public class BBSSettings {

	public static final String DEFAULT_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p %NAME%.mp4";
	public static final String DEFAULT_AUDIO_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -i %AUDIO_TRACK% -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest %NAME%.mp4";
	public static final String DEFAULT_MUX_FFMPEG_ARGUMENTS = "-y -i %VIDEO% -i %AUDIO_TRACK% -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -shortest %NAME%.mp4";

	public static ValueColors favoriteColors;
	public static ValueColors recentColors;
	/* MERGE 2026-07-27: aus dem CML-upstream — favoriten im form-picker (UIFormList). */
	public static ValueStringKeys favoriteModelForms;
	public static ValueStringKeys disabledSheets;
	public static ValueStringKeys disabledMorphFormCategories;
	public static ValueLanguage language;
	public static ValueInt primaryColor;
	public static ValueInt stencilHighlightColor;
	public static ValueBoolean enableTrackpadIncrements;
	public static ValueBoolean enableTrackpadScrolling;
	public static ValueFloat userIntefaceScale;
	public static ValueInt theme;
	public static ValueFloat fov;
	public static ValueBoolean hsvColorPicker;
	public static ValueBoolean forceQwerty;
	public static ValueBoolean freezeModels;
	public static ValueBoolean listModelPreview;
	public static ValueBoolean morphingFocusSearch;
	public static ValueFloat axesScale;
	public static ValueFloat axesThickness;
	public static ValueBoolean axesKeepScreenSize;
	public static ValueBoolean rotate3dSphere;
	public static ValueInt rotate3dSphereMode;
	public static ValueBoolean rotateHideRings;
	public static ValueBoolean hideInactiveHandles;
	public static ValueFloat snapTranslate;
	public static ValueFloat snapRotate;
	public static ValueFloat snapScale;
	public static ValueInt gizmoHoverTolerance;
	public static ValueFloat gizmoOpacity;
	public static ValueBoolean uniformScale;
	public static ValueBoolean clickSound;
	public static ValueBoolean gizmos;
	public static ValueBoolean defaultLocalTransform;
	public static ValueInt transformSpace;
	public static ValueBoolean transformHotkeys3dRay, simpleKeyboardTransform;
	public static ValueBoolean transformHotkeysWhileControlling;
	public static ValueBoolean transformMiddleClickAxis;
	/** Blenders Transform-Rueckmeldung (Akte 07): Cursor, Straehne, Achslinien, MMB-Voransicht. */
	public static ValueBoolean transformBlenderFeedback;
	/** #65: IK-Ketten aus der Modelldatei (arm_ik) fuellen leere form.ik-Configs. */
	public static ValueBoolean modelEmbeddedIk;
	/** P1 Vektorschrift · P2 weiche Flaechen · P3 Vektor-Icon-Atlanten. */
	public static ValueBoolean fontVector;
	public static ValueBoolean vectorUi;
	public static ValueBoolean vectorIcons;
	public static ValueInt texturePickerItemSize;
	public static ValueBoolean poseMirrorEdit;
	public static ValueBoolean poseAlternateInvert;
	public static ValueBoolean poseShowDisabledBones;
	public static ValueOrder translateHotkeyOrder;
	public static ValueOrder scaleHotkeyOrder;
	public static ValueOrder rotateHotkeyOrder;
	public static ValueFloat trackballSensitivity;

	public static ValueBoolean enableCursorRendering;
	public static ValueBoolean enableMouseButtonRendering;
	public static ValueBoolean enableKeystrokeRendering;
	public static ValueInt keystrokeOffset;
	public static ValueInt keystrokeMode;

	public static ValueLink backgroundImage;
	public static ValueInt backgroundColor;

	public static ValueBoolean chromaSkyEnabled;
	public static ValueInt chromaSkyColor;
	public static ValueBoolean chromaSkyTerrain;
	public static ValueFloat chromaSkyBillboard;

	public static ValueInt scrollbarWidth;
	public static ValueFloat scrollingSensitivity, scrollingSensitivityHorizontal, scrollingSmoothnessIntensity;
	public static ValueBoolean scrollingSmoothness;
	public static ValueBoolean scrollingDisableSmoothnessInEditors;

	public static ValueBoolean multiskinMultiThreaded;

	public static ValueString videoEncoderPath;
	public static ValueBoolean videoEncoderLog;
	public static ValueBoolean worldExportResizeWindow;
	public static ValueInt videoWidth;
	public static ValueInt videoHeight;
	public static ValueInt videoFrameRate;
	/** P7a: Export rendert mit Faktor N und ffmpeg skaliert per Lanczos zurueck. */
	public static ValueInt videoSupersampling;
	public static ValueInt videoMaxSide;
	public static ValueBoolean videoLimitFrameRate;
	public static ValueString videoExportPath;
	public static ValueString videoExportFilenameFormat;
	public static ValueBoolean videoExportAudio;
	public static ValueBoolean videoExportMinecraftSounds;
	public static ValueBoolean videoMuteAudioWhileRender;
	public static ValueInt videoMotionBlur;
	public static ValueInt videoHeldFrames;
	public static ValueFloat videoDelay;
	public static ValueBoolean videoOpenFolderAfterExport;
	public static ValueBoolean videoPlaySoundAfterExport;
	public static ValueString videoArguments;
	public static ValueString videoArgumentsAudio;
	public static ValueString videoArgumentsMux;

	public static ValueFloat editorCameraSpeed;
	public static ValueFloat editorCameraAngleSpeed;
	public static ValueInt duration;
	/* MERGE 2026-07-27 (CML): weiche UI-uebergaenge, abschaltbar — UIFormList/UIToggle/UIOverlay. */
	public static ValueBoolean editorSimplifyAnimations;
	/* MERGE 2026-07-27: felder aus dem CML-upstream, gebraucht von behaltenen CML-dateien. */
	public static ValueBoolean editorFisheyeWidenFov;
	public static ValueBoolean editorHandSprintFov;
	public static ValueString favoriteFormCategoriesData;
	public static ValueBoolean recordingAutoCaptureMobs;
	public static ValueBoolean recordingAutoCaptureProjectiles;
	public static ValueBoolean recordingMobCaptureOnAlt;
	public static ValueBoolean editorLoop;
	public static ValueInt editorJump;
	public static ValueInt editorGuidesColor;
	public static ValueBoolean editorRuleOfThirds, editorCenterLines, editorCrosshair;
	public static ValueBoolean editorSeconds, editorSnapToTicks, anchorKeepTransform;
	public static ValueBoolean editorTimelineGrid, editorSteppedPreview; /* stepped preview: KeyframeStufen */
	public static ValueInt editorPeriodicSave;
	/* Persistentes Undo (ueberlebt Neustart/Absturz) — gelesen von bbstools UndoPersistence. */
	public static ValueBoolean editorPersistentUndo;
	public static ValueInt editorPersistentUndoLimit;
	/* Bei gleicher haupttaste gewinnt die bindung mit den meisten modifiern (Keybind). */
	public static ValueBoolean editorKeybindSpecificWins;
	/* Jede aktion darf eine ZWEITE, alternative belegung haben (z.b. eine fuer die linke hand,
	 * eine fuer die maus). Gelesen von mchorse.bbs_mod.ui.utils.keys.Zweitbelegungen.
	 * ⛔ AUS loescht NICHTS: die zweiten belegungen bleiben auf der platte und in den presets,
	 * sie werden nur nicht ausgeloest und nicht angezeigt. */
	public static ValueBoolean editorKeybindSecondBinding;
	/* Flug schreibt in den fuehrenden clip, auch wenn keiner ausgewaehlt ist.
	 * Gelesen von mchorse.bbs_mod.ui.film.UIClipsPanel.pickActiveClip. */
	public static ValueBoolean editorFlightEditsActiveClip;
	/* Bei mehreren gewaehlten clips: ein ZUG an einem zahlenfeld verschiebt alle um
	 * denselben betrag (abstaende bleiben), eine GETIPPTE zahl setzt alle gleich.
	 * Aus = wie vorher, alle bekommen denselben wert.
	 * Gelesen von mchorse.bbs_mod.ui.film.mehrfach.MehrfachWert. */
	public static ValueBoolean editorMultiClipDelta;
	/* Waehrend der wiedergabe wird die live-kamera fortlaufend in den fuehrenden clip
	 * geschrieben. Gelesen von mchorse.bbs_mod.ui.film.FlightClipRecorder. */
	public static ValueBoolean editorRecordCameraDuringPlayback;
	public static ValueBoolean editorHorizontalFlight;
	public static ValueBoolean editorOrbitMovementRequiresFlight;
	public static ValueBoolean editorOrbitCenterMarker;
	public static ValueBoolean editorOrbitGizmo;
	public static ValueFloat editorOrbitGizmoScale;
	public static ValueBoolean editorOrbitAxisOrtho;
	public static ValueMotionPath editorMotionPath;
	public static ValueBoolean editorOrbitTeleportOnSwitch;
	public static ValueFloat editorOrbitFrameMargin;
	public static ValueFloat editorCameraSmoothness;
	public static ValueInt editorCameraMode;
	public static ValueInt renderEngine;
	public static ValueBoolean editorPlayerFollowsCamera;
	public static ValueEditorLayout editorLayoutSettings;
	/** Merker der einmaligen Umstellung auf das neue Standardlayout des Film-Editors. */
	public static ValueBoolean editorLayoutUmgestellt;
	public static ValueOnionSkin editorOnionSkin;
	public static ValueIKDebug ikDebug;
	public static ValuePhysicsDebug physicsDebug;
	public static ValueBoolean editorSnapToMarkers;
	/* Quick Search: neuer replay landet auf dem anvisierten block statt fuenf bloecke
	 * geradeaus. Gelesen von UIQuickReplayMenu. */
	public static ValueBoolean editorQuickReplaySnapToBlock;
	public static ValueBoolean editorClipPreview;
	public static ValueBoolean editorRewind;
	public static ValueBoolean editorHorizontalClipEditor;
	public static ValueBoolean editorMinutesBackup;
	public static ValueBoolean editorResizablePanels;
	/** Whether the film editor's undo history is a docked panel instead of a floating window. */
	public static ValueBoolean editorUndoHistoryDocked;
	public static ValueInt editorTrackWidth;
	public static ValueInt keyframeDefaultShape;
	public static ValueString keyframeDefaultInterpolation;
	public static ValueInt editorPreviewSizeMode;
	public static ValueInt editorPreviewCustomWidth;
	public static ValueInt editorPreviewCustomHeight;
	public static ValueFloat editorPreviewResolutionScale;
	public static ValueBoolean editorClipAutoName;
	public static ValueBoolean editorGroupHandHudTracks;
	public static ValueBoolean editorCameraClipsInReplays;
	public static ValueBoolean editorReplayOutline;
	public static ValueInt editorReplayOutlineColor;
	public static ValueInt editorReplayOutlineWidth;

	public static ValueFloat recordingCountdown;
	public static ValueBoolean recordingSwipeDamage;
	public static ValueBoolean recordingOverlays;
	public static ValueInt recordingPoseOverlays, recordingTransformOverlays;
	public static ValueBoolean recordingCameraPreview;

	public static ValueBoolean renderAllModelBlocks;
	public static ValueBoolean clickModelBlocks;

	public static ValueString entitySelectorsPropertyWhitelist;

	public static ValueBoolean damageControl;

	public static ValueFloat backgroundBrightness;
	public static ValueBoolean interfaceShadows;

	public static ValueBoolean shaderCurvesEnabled;

	public static ValueBoolean audioWaveformVisibleInPreview;
	public static ValueBoolean audioWaveformVisibleInKeyframes, audioWaveformDetailed;
	public static ValueInt audioWaveformDensity;
	public static ValueFloat audioWaveformWidth;
	public static ValueInt audioWaveformHeight;
	public static ValueBoolean audioWaveformFilename;
	public static ValueBoolean audioWaveformTime;
	public static ValueBoolean audioWaveformPreviewCombined;
	/* Audio-export schreibt eine WAV PRO CLIP statt einer mischung.
	 * Gelesen von mchorse.bbs_mod.ui.film.UIFilmPreview / AudioStemExporter. */
	public static ValueBoolean audioExportSeparateFiles;

	/* AUTO AUDIO (#68): ein vanilla-partikel-effekt bringt seinen Minecraft-ton mit.
	 * Standard AUS — das feature schreibt echte audio-clips in den film, und die landen
	 * im export. Ein vorzug, den man einschaltet, kein arbeitsmodus, der von selbst laeuft.
	 * Gelesen von mchorse.bbs_mod.film.autoaudio.AutoAudio / UIReplayList. */
	public static ValueBoolean autoAudio;
	public static ValueFloat autoAudioVolume;
	public static ValueBoolean autoAudioSpatial;

	/* AUTONOMIE 2026-07-27: schalter fuer den crowd/bake-schnellpfad in com/afrobama
	 * (bbstools/client/bbs/fastpath). Eigene kategorie "bbstools" statt sie wie im CML-fork
	 * in eine fremde einzuhaengen — dort landeten sie unter "cdn" und bekamen deshalb
	 * unlesbare namen in der settings-UI. */
	public static ValueBoolean bakeFastPath;
	public static ValueBoolean bakeFastPathRender;
	public static ValueBoolean bakeCrowdMode;
	public static ValueBoolean bakeLod;
	public static ValueBoolean bakeDistanceCulling;
	public static ValueBoolean bakeArmyBake;

	/** Solid-Vorschau: Modelle in der 3D-Ansicht einfarbig + normalenbeleuchtet zeichnen
	 *  statt texturiert (Blenders Solid-Modus). Vorgabe AUS, weil es das Aussehen JEDES
	 *  Modells in der Ansicht aendert. Gelesen von
	 *  com.afrobama.bbstools.client.solid.SolidVorschau — das prueft zusaetzlich, ob
	 *  Optimize laeuft und ob gerade exportiert wird. */
	public static ValueBoolean solidPreview;

	/** Beim replay-posieren nur die markierten knochen aufzeichnen statt des ganzen skeletts. */
	public static ValueBoolean replayMarkedBonesOnly;

	/* START-ZUSTAND, drei schalter (2026-07-28). Alle drei bewusst sparsam vorbelegt:
	 * der bisherige zustand — alles an, JFR laeuft, fenster reisst den fokus — kostete
	 * gemessen einen voll-GC pro minute, den grossteil der log-zeilen, und die faehigkeit,
	 * neue methoden ohne neustart einzuspielen. Gelesen von JfrAutostart,
	 * StartPositivliste und WindowNoFocusStealMixin. */
	public static ValueBoolean jfrAutoRecord;
	public static ValueBoolean debuggerStartAllesAn;
	public static ValueBoolean startupKeepFocus;

	public static ValueString cdnUrl;
	public static ValueString cdnToken;

	private static final int LIGHT_THEME = 0;
	private static final int DARK_THEME = 1;
	private static final int DEFAULT_THEME = DARK_THEME;
	private static final float DEFAULT_BACKGROUND_BRIGHTNESS = 1F;
	private static final float MIN_BACKGROUND_BRIGHTNESS = 0.5F;
	private static final float MAX_BACKGROUND_BRIGHTNESS = 1.5F;
	private static final float IDENTITY_BRIGHTNESS = 1F;
	private static final float BRIGHTNESS_EPSILON = 0.001F;
	private static final int DEFAULT_PRIMARY_COLOR = 0xff3242;
	private static final int LIGHT_CHROME_SURFACE = 0xffe6e9ef;
	/* Design-Palette statt Blaustich: chrome = editor #1D1D1D. Die alten Werte (0xff111316
	 * u. a.) waren blaeulich; das Blender-Design ist neutralgrau. Ueber diese vier Konstanten
	 * bekommt JEDE alte Flaeche den neuen Ton, ohne dass ihr Zeichencode angefasst wird. */
	private static final int DARK_CHROME_SURFACE = 0xff1D1D1D;
	private static final int LIGHT_BASE_SURFACE = 0xfff1f4f8;
	private static final int DARK_BASE_SURFACE = 0xff242424;
	private static final int LIGHT_RAISED_SURFACE = 0xfff8fafd;
	private static final int DARK_RAISED_SURFACE = 0xff2F2F2F;
	private static final int LIGHT_DEEP_SURFACE = 0xffdee4ed;
	private static final int DARK_DEEP_SURFACE = 0xff161616;
	private static final int LIGHT_DIVIDER_COLOR = 0xffc2cbd8;
	private static final int DARK_DIVIDER_COLOR = 0xff303030;

	public static int primaryColor()
	{
		return primaryColor(Colors.A50);
	}

	public static int primaryColor(int alpha)
	{
		return withAlpha(primaryColor.get(), alpha);
	}

	public static boolean isLightTheme()
	{
		return theme != null && theme.get() == LIGHT_THEME;
	}

	private static int withAlpha(int color, int alpha)
	{
		return (color & Colors.RGB) | alpha;
	}

	private static int getThemeColor(int lightColor, int darkColor)
	{
		return isLightTheme() ? lightColor : darkColor;
	}

	private static float getBackgroundBrightnessFactor()
	{
		return backgroundBrightness == null ? DEFAULT_BACKGROUND_BRIGHTNESS : backgroundBrightness.get();
	}

	private static int applyBackgroundBrightness(int color)
	{
		float brightness = MathUtils.clamp(getBackgroundBrightnessFactor(), MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS);

		if (Math.abs(brightness - IDENTITY_BRIGHTNESS) < BRIGHTNESS_EPSILON)
		{
			return color;
		}

		int a = color & 0xff000000;
		int r = (color >> 16) & 0xff;
		int g = (color >> 8) & 0xff;
		int b = color & 0xff;

		if (brightness < 1F)
		{
			r = Math.round(r * brightness);
			g = Math.round(g * brightness);
			b = Math.round(b * brightness);
		}
		else
		{
			float factor = brightness - 1F;

			r += Math.round((255 - r) * factor);
			g += Math.round((255 - g) * factor);
			b += Math.round((255 - b) * factor);
		}

		r = MathUtils.clamp(r, 0, 255);
		g = MathUtils.clamp(g, 0, 255);
		b = MathUtils.clamp(b, 0, 255);

		return a | (r << 16) | (g << 8) | b;
	}

	private static int getThemeSurface(int lightColor, int darkColor)
	{
		return applyBackgroundBrightness(getThemeColor(lightColor, darkColor));
	}

	public static int chromeSurface()
	{
		return getThemeSurface(LIGHT_CHROME_SURFACE, DARK_CHROME_SURFACE);
	}

	public static int baseSurface()
	{
		return getThemeSurface(LIGHT_BASE_SURFACE, DARK_BASE_SURFACE);
	}

	public static int raisedSurface()
	{
		return getThemeSurface(LIGHT_RAISED_SURFACE, DARK_RAISED_SURFACE);
	}

	public static int deepSurface()
	{
		return getThemeSurface(LIGHT_DEEP_SURFACE, DARK_DEEP_SURFACE);
	}

	public static int dividerColor()
	{
		return getThemeColor(LIGHT_DIVIDER_COLOR, DARK_DIVIDER_COLOR);
	}

	public static int color(int color, int alpha)
	{
		return withAlpha(color, alpha);
	}

	public static int accentOverlay(int alpha)
	{
		return primaryColor(alpha);
	}

	/**
	 * Render-scoped: the film editor sets this so its inputs stay light on its dark panels.
	 */
	public static boolean lightInputs = false;

	public static int inputSurface()
	{
		return lightInputs ? raisedSurface() : deepSurface();
	}

	public static int panelShadowOpaqueColor()
	{
		return Colors.A50; /* schwarz, nicht akzentfarben: sonst blauer Hof um jedes Menue */
	}

	public static int panelShadowTransparentColor()
	{
		return Colors.setA(0, 0F);
	}

	public static int getDefaultDuration()
	{
		return duration == null ? 30 : duration.get();
	}

	public static float getFov()
	{
		return BBSSettings.fov == null ? MathUtils.toRad(50) : MathUtils.toRad(BBSSettings.fov.get());
	}

	public static float getAxesDistanceScale(float distance)
	{
		return getAxesDistanceScale(distance, getFov());
	}

	public static float getAxesDistanceScale(float distance, float fov)
	{
		if (axesKeepScreenSize != null && axesKeepScreenSize.get())
		{
			float tanFov = (float) Math.tan(fov / 2.0);
			// 0.4663F is roughly tan(50 degrees / 2)
			float scale = (distance / 5F) * (tanFov / 0.4663F);

			return Math.max(scale, 0.0001F);
		}

		return 1F;
	}

	public static boolean isHorizontalClipEditorEffective()
	{
		return editorHorizontalClipEditor.get();
	}

	/** Default-Form neuer Keyframes: Raute wie im Entwurf; Fallback DIAMOND. */
	public static KeyframeShape getDefaultKeyframeShape()
	{
		if (keyframeDefaultShape == null)
		{
			return KeyframeShape.DIAMOND;
		}

		int index = keyframeDefaultShape.get();
		KeyframeShape[] values = KeyframeShape.values();

		return index >= 0 && index < values.length ? values[index] : KeyframeShape.DIAMOND;
	}

	/** Interpolation fuer hand-erzeugte Keyframes OHNE Nachbarn (IUIKeyframeGraph#addKeyframeManually);
	 *  mit Nachbar erbt der Keyframe, recorded/baked fragen nie. Fallback linear (vor Registrierung
	 *  oder bei unbekanntem Key). */
	public static IInterp getDefaultKeyframeInterpolation()
	{
		if (keyframeDefaultInterpolation == null)
		{
			return Interpolations.LINEAR;
		}

		IInterp interp = Interpolations.MAP.get(keyframeDefaultInterpolation.get());

		return interp == null ? Interpolations.LINEAR : interp;
	}

	public static void register(SettingsBuilder builder)
	{
		HashSet<String> defaultFilters = new HashSet<>();

		defaultFilters.add("item_head");
		defaultFilters.add("item_chest");
		defaultFilters.add("item_legs");
		defaultFilters.add("item_feet");
		defaultFilters.add("vX");
		defaultFilters.add("vY");
		defaultFilters.add("vZ");
		defaultFilters.add("grounded");
		defaultFilters.add("stick_rx");
		defaultFilters.add("stick_ry");
		defaultFilters.add("trigger_l");
		defaultFilters.add("trigger_r");
		defaultFilters.add("extra1_x");
		defaultFilters.add("extra1_y");
		defaultFilters.add("extra2_x");
		defaultFilters.add("extra2_y");

		builder.category("appearance", Icons.LAYOUT);
		builder.register(language = new ValueLanguage("language"));
		enableTrackpadIncrements = builder.getBoolean("trackpad_increments", false);
		enableTrackpadScrolling = builder.getBoolean("trackpad_scrolling", false);
		userIntefaceScale = builder.getFloat("ui_scale", 2F, 0F, 4F).slider().live();
		fov = builder.getFloat("fov", 40, 0, 180).slider();
		hsvColorPicker = builder.getBoolean("hsv_color_picker", true);
		forceQwerty = builder.getBoolean("force_qwerty", false);
		freezeModels = builder.getBoolean("freeze_models", false);
		listModelPreview = builder.getBoolean("list_model_preview", true);
		morphingFocusSearch = builder.getBoolean("morphing_focus_search", false);
		uniformScale = builder.getBoolean("uniform_scale", false);
		clickSound = builder.getBoolean("click_sound", false);
		/* invisible(): gespeicherte ZUSTAENDE, keine einstellungen — die UI schreibt sie
		 * selbst, sichtbar erzeugten sie nur rohe schluessel in der settings-liste (kein
		 * sprach-eintrag noetig; anzeigename kaeme aus "bbs.config.<kategorie>.<name>"). */
		favoriteColors = new ValueColors("favorite_colors");
		recentColors = new ValueColors("recent_colors").limit(33);
		disabledSheets = new ValueStringKeys("disabled_sheets");
		disabledSheets.set(defaultFilters);
		favoriteColors.invisible();
		recentColors.invisible();
		disabledSheets.invisible();
		builder.register(favoriteColors);
		builder.register(recentColors);
		builder.register(disabledSheets);
		favoriteModelForms = new ValueStringKeys("favorite_model_forms");
		favoriteModelForms.invisible();
		builder.register(favoriteModelForms);
		disabledMorphFormCategories = new ValueStringKeys("disabled_morph_form_categories");
		disabledMorphFormCategories.invisible();
		builder.register(disabledMorphFormCategories);
		mchorse.bbs_mod.settings.EditorZustaende.register(builder);
		editorClipAutoName = builder.getBoolean("clip_auto_name", true);
		editorGroupHandHudTracks = builder.getBoolean("group_hand_hud_tracks", true);
		editorCameraClipsInReplays = builder.getBoolean("camera_clips_in_replays", true);
		editorReplayOutline = builder.getBoolean("replay_outline", true);
		/* Blenders Vorgabe, in der Instanz des Users abgelesen: object_active = #FFA028. */
		editorReplayOutlineColor = builder.getInt("replay_outline_color", 0xFFFFA028).colorAlpha();
		editorReplayOutlineWidth = builder.getInt("replay_outline_width", 1, 1, 5).slider();

		builder.category("personalization", Icons.COLOR);
		backgroundBrightness = builder.getFloat("background_brightness", DEFAULT_BACKGROUND_BRIGHTNESS, MIN_BACKGROUND_BRIGHTNESS, MAX_BACKGROUND_BRIGHTNESS).slider();
		interfaceShadows = builder.getBoolean("interface_shadows", true);
		primaryColor = builder.getInt("primary_color", DEFAULT_PRIMARY_COLOR).color();
		stencilHighlightColor = builder.getInt("stencil_highlight_color", 0x2EFFFFFF).colorAlpha();
		theme = builder.getInt("theme", DEFAULT_THEME);
		editorTrackWidth = builder.getInt("track_width", 2, 1, 10).slider();
		keyframeDefaultShape = builder.getInt("keyframe_default_shape", KeyframeShape.DIAMOND.ordinal(), 0, KeyframeShape.values().length - 1);

		builder.category("transformation", Icons.SCALE);
		gizmos = builder.getBoolean("gizmos", true);
		axesScale = builder.getFloat("axes_scale", 2F, 0F, 10F).slider();
		axesThickness = builder.getFloat("axes_thickness", 0.35F, 0.25F, 3F).slider();
		axesKeepScreenSize = builder.getBoolean("axes_keep_screen_size", true);
		rotate3dSphere = builder.getBoolean("rotate_3d_sphere", true);
		rotate3dSphereMode = builder.getInt("rotate_3d_sphere_mode", 0);
		rotateHideRings = builder.getBoolean("rotate_hide_rings", false);
		hideInactiveHandles = builder.getBoolean("hide_inactive_handles", true);
		snapTranslate = builder.getFloat("snap_translate", 1F, 0.001F, 100F);
		snapRotate = builder.getFloat("snap_rotate", 5F, 0.001F, 90F);
		snapScale = builder.getFloat("snap_scale", 0.1F, 0.001F, 10F);
		gizmoHoverTolerance = builder.getInt("gizmo_hover_tolerance", 8, 0, 40).slider();
		gizmoOpacity = builder.getFloat("gizmo_opacity", 1F, 0.05F, 1F).slider();
		defaultLocalTransform = builder.getBoolean("default_local", false);
		transformSpace = builder.getInt("transform_space", defaultLocalTransform.get() ? 0 : 3);
		transformSpace.invisible();
		transformHotkeys3dRay = builder.getBoolean("hotkeys_3d_ray", true); simpleKeyboardTransform = builder.getBoolean("simple_keyboard_transform", false); /* BBS FS b89d70e0b */
		transformHotkeysWhileControlling = builder.getBoolean("hotkeys_while_controlling", true);
		transformMiddleClickAxis = builder.getBoolean("middle_click_axis", true);
		transformBlenderFeedback = builder.getBoolean("blender_feedback", true);
		modelEmbeddedIk = builder.getBoolean("model_embedded_ik", true);
		fontVector = builder.getBoolean("vector_font", true);
		vectorUi = builder.getBoolean("vector_ui", true);
		vectorIcons = builder.getBoolean("vector_icons", true);
		texturePickerItemSize = builder.getInt("texture_picker_item_size", 16, 16, 220);
		poseMirrorEdit = builder.getBoolean("pose_mirror_edit", false);
		poseMirrorEdit.invisible();
		poseAlternateInvert = builder.getBoolean("pose_alternate_invert", false);
		poseAlternateInvert.invisible();
		poseShowDisabledBones = builder.getBoolean("pose_show_disabled_bones", false);
		/* BBS' own cycle again (owner 2026-10-14): R = view -> trackball -> X -> Y -> Z,
		 * G = screen -> X -> Y -> Z, S = all -> X -> Y -> Z, and X/Y/Z keys on top. The
		 * Blender-only cut of 2026-08-15 (bb0a8c0fd) dropped the axis steps. */
		translateHotkeyOrder = new ValueOrder("translate_hotkey_order", "screen", "x", "y", "z");
		builder.register(translateHotkeyOrder);
		scaleHotkeyOrder = new ValueOrder("scale_hotkey_order", "all", "x", "y", "z");
		builder.register(scaleHotkeyOrder);
		rotateHotkeyOrder = new ValueOrder("rotate_hotkey_order", "view", "sphere", "x", "y", "z");
		builder.register(rotateHotkeyOrder);
		trackballSensitivity = builder.getFloat("trackball_sensitivity", 1F, 0.05F, 2F).slider();

		builder.category("tutorials", Icons.HELP);
		enableCursorRendering = builder.getBoolean("cursor", false);
		enableMouseButtonRendering = builder.getBoolean("mouse_buttons", false);
		enableKeystrokeRendering = builder.getBoolean("keystrokes", false);
		keystrokeOffset = builder.getInt("keystrokes_offset", 10, 0, 20).slider();
		keystrokeMode = builder.getInt("keystrokes_position", 1);

		builder.category("background", Icons.IMAGE);
		backgroundImage = builder.getRL("image", null);
		backgroundColor = builder.getInt("color", 0x7b000000).colorAlpha();

		builder.category("chroma_sky", Icons.GLOBE);
		chromaSkyEnabled = builder.getBoolean("enabled", false);
		chromaSkyColor = builder.getInt("color", Colors.A75).color();
		chromaSkyTerrain = builder.getBoolean("terrain", true);
		chromaSkyBillboard = builder.getFloat("billboard", 0F, 0F, 256F);

		builder.category("scrollbars", Icons.VERTICAL);
		scrollbarWidth = builder.getInt("width", 4, 2, 10).slider();
		scrollingSensitivity = builder.getFloat("sensitivity", 3F, 0F, 10F).slider();
		scrollingSensitivityHorizontal = builder.getFloat("sensitivity_horizontal", 3F, 0F, 10F).slider();
		scrollingSmoothness = builder.getBoolean("smoothness", true);
		scrollingSmoothnessIntensity = builder.getFloat("smoothness_intensity", 0.75F, 0F, 2F).slider();
		scrollingDisableSmoothnessInEditors = builder.getBoolean("disable_smoothness_in_editors", false);

		builder.category("multiskin", Icons.USER);
		multiskinMultiThreaded = builder.getBoolean("multithreaded", true);

		builder.category("video", Icons.VIDEO_CAMERA);
		videoEncoderPath = builder.getString("encoder_path", "ffmpeg");
		videoEncoderLog = builder.getBoolean("log", true);
		worldExportResizeWindow = builder.getBoolean("world_export_resize_window", false);
		videoWidth = builder.getInt("width", 1280, 2, 8096);
		videoHeight = builder.getInt("height", 720, 2, 8096);
		videoFrameRate = builder.getInt("frame_rate", 60, 10, 1000);
		videoLimitFrameRate = builder.getBoolean("limit_frame_rate", false);
		videoExportPath = builder.getString("export_path", "");
		videoExportFilenameFormat = builder.getString("filename_format", "{datetime}");
		videoExportAudio = builder.getBoolean("audio", false);
		videoExportMinecraftSounds = builder.getBoolean("minecraft_sounds", false);
		videoMuteAudioWhileRender = builder.getBoolean("mute_audio_while_render", false);
		videoMotionBlur = builder.getInt("motion_blur", 0, 0, 6);
		videoSupersampling = builder.getInt("supersampling", 1, 1, 4).slider();
		videoHeldFrames = builder.getInt("held_frames", 1, 1, 1000);
		videoDelay = builder.getFloat("delay", 0.5F, 0F, 30F);
		videoOpenFolderAfterExport = builder.getBoolean("open_folder_after_export", false);
		videoPlaySoundAfterExport = builder.getBoolean("play_sound_after_export", true);
		videoArguments = builder.getString("arguments", DEFAULT_FFMPEG_ARGUMENTS);
		videoArgumentsAudio = builder.getString("arguments_audio", DEFAULT_AUDIO_FFMPEG_ARGUMENTS);
		videoArgumentsMux = builder.getString("arguments_mux", DEFAULT_MUX_FFMPEG_ARGUMENTS);
		videoMaxSide = builder.getInt("video_form_max_side", 1280, 64, 4096);
		mchorse.bbs_mod.settings.VideoSettings.register(builder);

		/* Camera editor */
		builder.category("editor", Icons.EDITOR);
		editorCameraSpeed = builder.getFloat("speed", 1F, 0.1F, 100F);
		editorCameraAngleSpeed = builder.getFloat("angle_speed", 1F, 0.1F, 100F);
		duration = builder.getInt("duration", 30, 1, 1000);
		editorJump = builder.getInt("jump", 5, 1, 1000);
		editorSimplifyAnimations = builder.getBoolean("simplify_animations", false);
		editorFisheyeWidenFov = builder.getBoolean("fisheye_widen_fov", true);
		editorHandSprintFov = builder.getBoolean("hand_sprint_fov", false);
		favoriteFormCategoriesData = builder.getString("favorite_form_categories_data", "");
		favoriteFormCategoriesData.invisible();  /* serialisierter zustand, kein einstellbarer wert */
		recordingAutoCaptureMobs = builder.getBoolean("auto_capture_mobs", true);
		recordingAutoCaptureProjectiles = builder.getBoolean("auto_capture_projectiles", true);
		recordingMobCaptureOnAlt = builder.getBoolean("mob_capture_on_alt", false);
		editorLoop = builder.getBoolean("loop", false);
		editorGuidesColor = builder.getInt("guides_color", 0xcccc0000).colorAlpha();
		editorRuleOfThirds = builder.getBoolean("rule_of_thirds", false);
		editorCenterLines = builder.getBoolean("center_lines", false);
		editorCrosshair = builder.getBoolean("crosshair", false);
		editorSeconds = builder.getBoolean("seconds", false);
		editorSnapToTicks = builder.getBoolean("snap_to_ticks", true); anchorKeepTransform = builder.getBoolean("anchor_keep_transform", true); /* BBS FS a220e30c5 */
		editorTimelineGrid = builder.getBoolean("timeline_grid", false); editorSteppedPreview = builder.getBoolean("stepped_preview", false);
		keyframeDefaultInterpolation = builder.getString("keyframe_default_interpolation", Interpolations.LINEAR.getKey());
		editorPeriodicSave = builder.getInt("periodic_save", 60, 0, 3600);
		editorPersistentUndo = builder.getBoolean("persistent_undo", true);
		/* Obergrenze 100, weil der UndoManager selbst bei 100 deckelt (UIFormUndoHandler.reset) —
		 * ein hoeherer Wert waere ein Versprechen, das die Sitzung nicht halten kann. */
		editorPersistentUndoLimit = builder.getInt("persistent_undo_limit", 100, 1, 100);
		editorKeybindSpecificWins = builder.getBoolean("keybind_specific_wins", true);
		editorKeybindSecondBinding = builder.getBoolean("keybind_second_binding", true);
		editorFlightEditsActiveClip = builder.getBoolean("flight_edits_active_clip", true);
		editorMultiClipDelta = builder.getBoolean("multi_clip_delta", true);
		editorRecordCameraDuringPlayback = builder.getBoolean("record_camera_during_playback", false);
		editorHorizontalFlight = builder.getBoolean("horizontal_flight", false);
		editorOrbitMovementRequiresFlight = builder.getBoolean("orbit_movement_requires_flight", true);
		editorOrbitCenterMarker = builder.getBoolean("orbit_center_marker", false);
		editorOrbitGizmo = builder.getBoolean("orbit_gizmo", true);
		editorOrbitGizmoScale = builder.getFloat("orbit_gizmo_scale", 1F, 0.5F, 2F).slider();
		editorOrbitAxisOrtho = builder.getBoolean("orbit_axis_ortho", true);
		editorOrbitTeleportOnSwitch = builder.getBoolean("orbit_teleport_on_switch", true);
		/* Blenders VIEW3D_MARGIN (view3d_view.cc, wert 1.4): rand um die huellbox beim
		 * rahmen — einstellbar statt formel-edit (OrbitFilmCameraController.rahmenAbstand). */
		editorOrbitFrameMargin = builder.getFloat("orbit_frame_margin", 1.4F, 0.5F, 4F).slider();
		editorCameraSmoothness = builder.getFloat("camera_smoothness", 0.1F, 0F, 0.95F).slider();
		editorCameraMode = builder.getInt("camera_mode", 0, 0, 5);
		editorCameraMode.invisible();
		/* P7 Render-Reiter: 0=BBS, 1=Eevee, 2=Cycles — bedient im Seitenpanel, nicht hier. */
		(renderEngine = builder.getInt("render_engine", 0, 0, 2)).invisible();
		editorPlayerFollowsCamera = builder.getBoolean("player_follows_camera", false);
		/* Dock-layout: durch panel-verschieben geschrieben, nicht in den settings eingestellt. */
		builder.register((editorLayoutSettings = new ValueEditorLayout("layout")).invisible());
		builder.register((editorLayoutUmgestellt = new ValueBoolean("layout_umgestellt", false)).invisible());
		builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
		builder.register(editorMotionPath = new ValueMotionPath("motion_path"));
		builder.register(ikDebug = new ValueIKDebug("ik_debug"));
		builder.register(physicsDebug = new ValuePhysicsDebug("physics_debug"));
		editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
		editorQuickReplaySnapToBlock = builder.getBoolean("quick_replay_snap_to_block", true);
		editorClipPreview = builder.getBoolean("clip_preview", true);
		editorRewind = builder.getBoolean("rewind", true);
		editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", true);
		editorMinutesBackup = builder.getBoolean("minutes_backup", true);
		editorResizablePanels = builder.getBoolean("resizable_panels", true);
		/* Off by default: the history was a floating window before, so an existing layout must not
		 * suddenly grow a sixth panel. Where it sits once enabled lives in the layout tree.
		 *
		 * invisible: der Nutzer dockt per GESTE (Overlay ziehen), nicht per Schalter — der
		 * Wert bleibt nur als Persistenz des OB-Zustands, wie editorLayoutSettings darueber. */
		editorUndoHistoryDocked = builder.getBoolean("undo_history_docked", false);
		editorUndoHistoryDocked.invisible();
		/* Werkstandard AUTO (2): die Vorschau folgt der Flaeche, und der Kameramodus entscheidet,
		 * ob sie fuellt (freie Ansicht) oder im Exportformat bleibt (Kamera-Ansicht) — Blenders
		 * Verhalten. Mit 0 (Export) stand sie immer auf 1920x1080 und schrumpfte beim
		 * Verkleinern der Flaeche, statt den Ausschnitt zu aendern. */
		editorPreviewSizeMode = builder.getInt("preview_size_mode", 2, 0, 2);
		editorPreviewCustomWidth = builder.getInt("preview_custom_width", 1280, 2, 16384);
		editorPreviewCustomHeight = builder.getInt("preview_custom_height", 720, 2, 16384);
		editorPreviewResolutionScale = builder.getFloat("preview_resolution_scale", 2F, 1F, 3F).slider();

		builder.category("recording", Icons.FILM);
		recordingCountdown = builder.getFloat("countdown", 1.5F, 0F, 30F);
		recordingSwipeDamage = builder.getBoolean("swipe_damage", false);
		recordingOverlays = builder.getBoolean("overlays", true);
		recordingPoseOverlays = builder.getInt("pose_overlays", 0, 0, 42);
		recordingTransformOverlays = builder.getInt("transform_overlays", 0, 0, 42);
		recordingCameraPreview = builder.getBoolean("camera_preview", true);

		builder.category("model_blocks", Icons.BLOCK);
		renderAllModelBlocks = builder.getBoolean("render_all", true);
		clickModelBlocks = builder.getBoolean("click", true);

		builder.category("entity_selectors", Icons.POINTER);
		entitySelectorsPropertyWhitelist = builder.getString("whitelist", "CustomName,Name");

		builder.category("dc", Icons.EXCLAMATION);
		damageControl = builder.getBoolean("enabled", true);

		builder.category("shader_curves", Icons.CURVES);
		shaderCurvesEnabled = builder.getBoolean("enabled", true);

		builder.category("audio", Icons.SOUND);
		audioWaveformVisibleInPreview = builder.getBoolean("waveform_visible_preview", true);
		audioWaveformVisibleInKeyframes = builder.getBoolean("waveform_visible_keyframes", true); audioWaveformDetailed = builder.getBoolean("waveform_detailed", true); /* BBS FS 07159d44d */
		audioWaveformDensity = builder.getInt("waveform_density", 20, 10, 100).slider();
		audioWaveformWidth = builder.getFloat("waveform_width", 0.8F, 0F, 1F).slider();
		audioWaveformHeight = builder.getInt("waveform_height", 24, 10, 40).slider();
		audioWaveformFilename = builder.getBoolean("waveform_filename", false);
		audioWaveformTime = builder.getBoolean("waveform_time", false);
		audioWaveformPreviewCombined = builder.getBoolean("waveform_preview_combined", false);
		audioExportSeparateFiles = builder.getBoolean("export_separate_files", false);
		autoAudio = builder.getBoolean("auto_audio", false);
		autoAudioVolume = builder.getFloat("auto_audio_volume", 1F, 0F, 1F).slider();
		autoAudioSpatial = builder.getBoolean("auto_audio_spatial", true);

		builder.category("bbstools", Icons.GEAR);
		bakeFastPath = builder.getBoolean("bake_fast_path", true);
		bakeFastPathRender = builder.getBoolean("bake_fast_path_render", true);
		bakeCrowdMode = builder.getBoolean("bake_crowd_mode", false);
		bakeLod = builder.getBoolean("bake_lod", true);
		bakeDistanceCulling = builder.getBoolean("bake_distance_culling", true);
		bakeArmyBake = builder.getBoolean("bake_army_bake", false);
		solidPreview = builder.getBoolean("solid_preview", false);
		replayMarkedBonesOnly = builder.getBoolean("replay_marked_bones_only", false);
		jfrAutoRecord = builder.getBoolean("jfr_auto_record", false);
		debuggerStartAllesAn = builder.getBoolean("debugger_start_alles_an", false);
		startupKeepFocus = builder.getBoolean("startup_keep_focus", true);

		builder.category("cdn", Icons.SERVER);
		cdnUrl = builder.getString("url", "");
		cdnToken = builder.getString("token", "");
	}
}
