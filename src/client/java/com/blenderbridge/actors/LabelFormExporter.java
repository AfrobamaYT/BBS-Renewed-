package com.blenderbridge.actors;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.utils.colors.Color;

/**
 * PHASE V3 — LabelForm. Serialises a {@link LabelForm} actor's text-render settings for the Blender
 * bridge.
 *
 * <h2>WHY A SEPARATE CLASS (deploy-window-free, {@code LightFormExporter}/{@code PaintExporter}-muster)</h2>
 * <p>{@link ActorPropertiesExporter} is already loaded — a new field/method there would need a boot.
 * The label channel lives in THIS new lazy class; the delegation is one body-call in
 * {@code ActorPropertiesExporter.serialize} (leader's job, HOTSPOT-regel).</p>
 *
 * <h2>NOT A KEYFRAME CHANNEL — FORM-level static values</h2>
 * <p>Like {@link LightFormExporter}, LabelForm's data lives as {@code Value*} fields on the FORM
 * object ({@code replay.form.get()}), not as animated {@code replay.properties} curves. So the wire
 * shape is flat scalar/string values. The Blender side (actor_label.py) builds a text object
 * (bpy FONT curve) with a coloured material, optional outline, and a billboard track-to constraint.</p>
 *
 * <h2>WIRE SHAPE</h2>
 * <pre>
 * {
 *   "label": {
 *     "text":       "Hello, World!",
 *     "r": 1.0, "g": 1.0, "b": 1.0, "a": 1.0,   // color
 *     "fontSize":   1.0,
 *     "billboard":  false,
 *     "opacity":    1.0,
 *     "outline":    false,
 *     "outlineR": 0.0, "outlineG": 0.0, "outlineB": 0.0,
 *     "outlineWidth": 1.0,                       // nische 2026-07-18: outline-breite (text-pixel)
 *     "bgR": 0.0, "bgG": 0.0, "bgB": 0.0, "bgA": 0.0,   // background-farbe (bgA 0 = transparent/aus)
 *     "gradient": false,                         // nische: text-farbverlauf color(unten)→gradient(oben)
 *     "gradientR": 1.0, "gradientG": 1.0, "gradientB": 1.0,
 *     "gradientOffset": 0.5,
 *     "shadowX": 1.0, "shadowY": 1.0,            // nische 2026-07-18: drop-shadow versatz (text-pixel)
 *     "shadowR": 0.0, "shadowG": 0.0, "shadowB": 0.0, "shadowA": 0.0,   // shadowColor (shadowA 0 = kein schatten)
 *     "shadowBlur": 0.0                          // nische 2026-07-18: weichzeichnung (text-pixel, spread)
 *   }
 * }
 * </pre>
 *
 * <h2>SHADOW + BLUR — WIE BBS ES RENDERT (LabelFormRenderer.renderTextShadow, gemessen)</h2>
 * <p>BBS' drop-shadow ist KEIN gaussian-compositor-blur, sondern reine GEOMETRIE: der text wird
 * hinter dem haupt-text (z −0.05) am versatz {@code (shadowX, shadowY)} in {@code shadowColor}
 * gezeichnet. Ist {@code shadowBlur > 0}, zeichnet BBS statt EINER schatten-kopie VIER kopien —
 * bei {@code (sx±blur, sy)} und {@code (sx, sy±blur)} —, jede mit VIERTEL-alpha
 * ({@code blurAlpha = max(1, alpha/4)}). Das ist ein billiger "spread"-blur (4 gedimmte geister die
 * sich vom kern-versatz um {@code blur} pixel wegspreizen). Der addon (actor_label.py) bildet GENAU
 * das nach: ein schatten-text-objekt (kern) + bei blur>0 vier viertel-alpha-schatten-kopien — rein
 * text-objekte + material-alpha, KEIN compositor (damit keine kollision mit fp_hands/hud_overlays
 * compositor-gruppe). Der schatten liegt hinter outline/text, vor dem background.</p>
 *
 * <h2>GRENZEN (Regel 0 — ehrlich geflaggt, nicht falsch gemappt)</h2>
 * <p>text/color/fontSize/billboard/opacity/outline(+width)/background/gradient/shadow(+blur) sync-bar
 * (text-objekt + material + hintergrund-plane + color-ramp + schatten-kopien). NICHT transportiert /
 * addon-seitig als ECHTE grenze dokumentiert: {@code font} (custom-font-datei müsste in Blender
 * existieren), {@code nametag}/{@code anchorLines}/{@code letterSpacing}/{@code lineHeight}/
 * {@code textAlign}/{@code underline}/{@code strikethrough}/{@code fontWeight}/{@code fontStyle}
 * (feineres text-layout, v1-grenze). Diese felder werden bewusst NICHT gesendet — der user weiss
 * dass sie v1-grenzen sind.</p>
 *
 * <h2>THREADING</h2>
 * <p>MAIN THREAD ONLY. Never throws: a non-LabelForm replay returns an empty result.</p>
 */
public final class LabelFormExporter
{
    /** Top-level key of the label object inside {@code properties}. */
    public static final String LABEL = "label";
    public static final String TEXT = "text";
    public static final String FONT_SIZE = "fontSize";
    public static final String BILLBOARD = "billboard";
    public static final String OPACITY = "opacity";
    public static final String OUTLINE = "outline";
    /** nische 2026-07-18: outline-breite (text-pixel), sowie background- + gradient-felder. */
    public static final String OUTLINE_WIDTH = "outlineWidth";
    public static final String GRADIENT = "gradient";
    public static final String GRADIENT_OFFSET = "gradientOffset";
    /** nische 2026-07-18: drop-shadow versatz (text-pixel) + weichzeichnung (spread, text-pixel). */
    public static final String SHADOW_X = "shadowX";
    public static final String SHADOW_Y = "shadowY";
    public static final String SHADOW_BLUR = "shadowBlur";

    private LabelFormExporter() {}

    /**
     * Serialise the LabelForm settings of a replay.
     *
     * @return an object carrying {@code "label"} when the replay's form is a {@link LabelForm}; empty
     *         (size 0) otherwise. Never null, never throws.
     */
    public static JsonObject serializeLabel(Replay replay)
    {
        JsonObject out = new JsonObject();

        if (replay == null || replay.form == null)
        {
            return out;
        }

        Form form = replay.form.get();

        if (!(form instanceof LabelForm label))
        {
            return out;
        }

        JsonObject data = new JsonObject();

        data.addProperty(TEXT, label.text.get());
        data.addProperty(FONT_SIZE, label.fontSize.get());
        data.addProperty(BILLBOARD, label.billboard.get());
        data.addProperty(OPACITY, label.opacity.get());
        data.addProperty(OUTLINE, label.outline.get());
        data.addProperty(OUTLINE_WIDTH, label.outlineWidth.get());
        data.addProperty(GRADIENT, label.gradient.get());
        data.addProperty(GRADIENT_OFFSET, label.gradientOffset.get());

        /* nische 2026-07-18: drop-shadow versatz + blur (text-pixel-einheit, wie shadowX/Y in BBS).
         * shadowColor unten separat (kann null sein). shadowBlur 0 = harter schatten (eine kopie),
         * >0 = spread-blur (vier viertel-alpha kopien, addon bildet die BBS-mimik nach). */
        data.addProperty(SHADOW_X, label.shadowX.get());
        data.addProperty(SHADOW_Y, label.shadowY.get());
        data.addProperty(SHADOW_BLUR, label.shadowBlur.get());

        Color color = label.color.get();
        if (color != null)
        {
            data.addProperty(ActorProtocol.PROP_R, color.r);
            data.addProperty(ActorProtocol.PROP_G, color.g);
            data.addProperty(ActorProtocol.PROP_B, color.b);
            data.addProperty(ActorProtocol.PROP_A, color.a);
        }

        Color outlineColor = label.outlineColor.get();
        if (outlineColor != null)
        {
            data.addProperty("outlineR", outlineColor.r);
            data.addProperty("outlineG", outlineColor.g);
            data.addProperty("outlineB", outlineColor.b);
        }

        /* nische 2026-07-18: background-farbe (bgA 0 = transparent = addon baut keine plane) +
         * gradient-endfarbe (nur relevant wenn gradient==true, aber immer gesendet: der addon liest
         * sie nur im gradient-fall). */
        Color background = label.background.get();
        if (background != null)
        {
            data.addProperty("bgR", background.r);
            data.addProperty("bgG", background.g);
            data.addProperty("bgB", background.b);
            data.addProperty("bgA", background.a);
        }

        Color gradientColor = label.gradientEndColor.get();
        if (gradientColor != null)
        {
            data.addProperty("gradientR", gradientColor.r);
            data.addProperty("gradientG", gradientColor.g);
            data.addProperty("gradientB", gradientColor.b);
        }

        /* shadowColor: shadowA 0 (BBS-default) = kein schatten → addon baut kein schatten-objekt.
         * Immer gesendet wenn nicht-null; der addon gatet auf shadowA>0 wie background auf bgA. */
        Color shadowColor = label.shadowColor.get();
        if (shadowColor != null)
        {
            data.addProperty("shadowR", shadowColor.r);
            data.addProperty("shadowG", shadowColor.g);
            data.addProperty("shadowB", shadowColor.b);
            data.addProperty("shadowA", shadowColor.a);
        }

        out.add(LABEL, data);

        return out;
    }
}
