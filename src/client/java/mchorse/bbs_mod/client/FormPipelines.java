package mchorse.bbs_mod.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import mchorse.bbs_mod.compat.alt.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.compat.alt.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.compat.alt.BindGroupLayouts;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Die Pipeline-Familie fuer BBS' einfache Hilfsgeometrie: Gizmos, Pfade, Drahtgitter,
 * Kill-Ebenen, Dicklinien, Keyframe-Formen.
 *
 * <h2>Warum eine Familie statt einzelner Konstanten</h2>
 *
 * <p>Diese Klasse ist aus einer Messung entstanden, nicht aus einem Entwurf: eine Welle
 * von zwoelf Pruefungen des Zustands-Blocks meldete unabhaengig voneinander immer
 * dieselbe Luecke — {@code POSITION_COLOR}, gemischt, mal als Dreiecke, mal als Linien,
 * mal als Streifen, mit oder ohne Tiefenpruefung. Im Baum stehen 44 Stellen mit
 * {@code POSITION_COLOR} und 38 mit Dreiecks-Topologie.
 *
 * <p>Bis 1.21.1 war das kein Thema: man setzte Blend, Tiefe und Cull als globalen Zustand
 * und zeichnete mit irgendeinem Shader. In 26.2 ist jede Kombination eine eigene
 * Pipeline. Statt zwanzig fast gleiche Konstanten von Hand zu pflegen — oder schlimmer:
 * jede Aufrufstelle baut sich ihre eigene — gibt es hier EINE Fabrik mit Zwischenspeicher.
 *
 * <h2>⛔ Tiefenpruefung AUS heisst hier auch Tiefenschreiben AUS</h2>
 *
 * <p>Das ist kein Schoenheitsfehler, sondern die Uebersetzung von rohem GL: schaltet man
 * dort {@code GL_DEPTH_TEST} ab, schreibt die Karte auch keine Tiefe mehr. Wer nur den
 * Test abschaltet und weiter schreiben laesst, legt mit einem Gizmo eine unsichtbare Wand
 * vor alles, was danach kommt — genau der Fall, den das Alt-Kamera-Gitter und der
 * Bewegungspfad melden.
 *
 * <p>⚠️ REVERSED-Z: bei eingeschalteter Pruefung ist der Vergleich
 * {@code GREATER_THAN_OR_EQUAL}, nicht {@code LESS_THAN_OR_EQUAL}. 26.2 rechnet die Tiefe
 * umgekehrt (Loeschwert 0.0, {@code DepthStencilState.DEFAULT} traegt GEQUAL). Die
 * mechanische Uebersetzung von {@code GL_LEQUAL} waere hier der teuerste Fehler: sie
 * dreht den Test um, und alles verschwindet hinter allem.
 */
public final class FormPipelines
{
    private static final Map<String, RenderPipeline> ZWISCHENSPEICHER = new HashMap<>();

    /**
     * Liefert die Pipeline fuer eine Zustandskombination und legt sie beim ersten Mal an.
     *
     * @param topologie   wie die Ecken zu Flaechen werden — {@code TRIANGLES},
     *                    {@code DEBUG_LINES}, {@code TRIANGLE_STRIP}, {@code QUADS}
     * @param mischen     Alphamischung (frueher {@code enableBlend} + {@code defaultBlendFunc})
     * @param tiefePruefen Tiefentest UND Tiefenschreiben (frueher {@code enableDepthTest})
     * @param culling     Rueckseiten verwerfen (frueher {@code enableCull})
     */
    public static RenderPipeline holen(VertexFormat.Mode topologie, boolean mischen, boolean tiefePruefen, boolean culling)
    {
        return holen(DefaultVertexFormat.POSITION_COLOR, "position_color", topologie, mischen, tiefePruefen, culling);
    }

    /**
     * Linien MIT einstellbarer Breite. Fuer alles, was Striche zeichnet.
     *
     * <p>⛔ WARUM ES DIESE METHODE BRAUCHT (2026-08-03, am Frame gemessen). Wer Linien ueber
     * {@link #holen(VertexFormat.Mode, boolean, boolean, boolean)} zeichnet, bekommt den
     * Shader {@code core/position_color} — und dessen Eingangssignatur ist genau
     * {@code Color, Position}. Der Vanilla-Linienshader rechnet aber
     * {@code lineOffset = richtung * LineWidth / ScreenSize}; ohne das Attribut
     * {@code LineWidth} im Format kommt dort eine Null an und JEDE Linie kollabiert auf
     * einen Pixel. Das ist kein vergessener Aufruf, den man nachtragen koennte: ein Format
     * ohne LineWidth KANN keine Breite tragen. Belegt im RenderDoc-Capture — der einzige
     * Linienaufruf des Bildes hatte stride 16, zwei Attribute, Shader-Eingaenge
     * "Color, Position".
     *
     * <p>Gebaut aus {@code RenderPipelines.LINES_SNIPPET}: von dort kommen Shader,
     * Vertexformat ({@code Position, Color, Normal, LineWidth}) und alle drei
     * Bindegruppen fertig mit. Selbst zusammengesetzt fehlt sonst die Gruppe mit
     * {@code ScreenSize}, und der Shader rechnet wieder ins Leere.
     *
     * <p><b>Die Normale ist bei Linien die Linienrichtung</b>, nicht die Flaechennormale —
     * der Shader spannt die Linie senkrecht dazu auf. Mit Nullvektor faellt sie zusammen.
     *
     * @param tiefePruefen ob die Linie hinter Geometrie verschwindet. {@code false} =
     *                     immer sichtbar (frueher {@code disableDepthTest}).
     *                     Tiefe wird NIE geschrieben — sonst legt ein grosses Gitter eine
     *                     Tiefenwand in den Puffer und verdeckt den folgenden Durchgang.
     */
    public static RenderPipeline linien(boolean tiefePruefen)
    {
        return ZWISCHENSPEICHER.computeIfAbsent("vanilla_lines|" + tiefePruefen, (s) ->
            RenderPipeline.builder(net.minecraft.client.renderer.RenderPipelines.LINES_SNIPPET)
                .withLocation(new ResourceLocation(BBSMod.MOD_ID,
                    "pipeline/lines" + (tiefePruefen ? "" : "_no_depth")))
                .withDepthStencilState(new DepthStencilState(
                    tiefePruefen ? CompareOp.GREATER_THAN_OR_EQUAL : CompareOp.ALWAYS_PASS,
                    false))
                .build());
    }

    /**
     * Wie {@link #holen(VertexFormat.Mode, boolean, boolean, boolean)}, aber mit frei
     * waehlbarem Vertexformat und Vanilla-Shader — etwa {@code POSITION_TEX} fuer die
     * texturierte Spur des Trail-Renderers.
     *
     * <p>Der Shadername ist der Vanilla-Kern ohne Pfad und Endung: {@code position_color},
     * {@code position_tex}, {@code position_tex_color}. Sie liegen im minecraft-Namensraum
     * und passen zu den gleichnamigen {@code DefaultVertexFormat}-Konstanten.
     */
    public static RenderPipeline holen(VertexFormat format, String shader, VertexFormat.Mode topologie, boolean mischen, boolean tiefePruefen, boolean culling)
    {
        String schluessel = shader + "|" + topologie + "|" + mischen + "|" + tiefePruefen + "|" + culling;

        return ZWISCHENSPEICHER.computeIfAbsent(schluessel,
            (s) -> bauen(format, shader, topologie, mischen, tiefePruefen, culling));
    }

    private static RenderPipeline bauen(VertexFormat format, String shader, VertexFormat.Mode topologie, boolean mischen, boolean tiefePruefen, boolean culling)
    {
        String name = shader + "_" + topologie.name().toLowerCase()
            + (mischen ? "_blend" : "")
            + (tiefePruefen ? "" : "_no_depth")
            + (culling ? "" : "_no_cull");

        ResourceLocation datei = new ResourceLocation("core/" + shader);

        RenderPipeline.Builder bauer = RenderPipeline.builder()
            .withLocation(new ResourceLocation(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexShader(datei)
            .withFragmentShader(datei)
            .withVertexBinding(0, format)
            .withPrimitiveTopology(topologie)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(culling);

        /* Texturierte Formate brauchen ihren Sampler; die reinen Farbformate nicht. Wer
         * hier eine Bindegruppe ergaenzt, muss den Vanilla-Shader dazu pruefen — eine
         * Gruppe, die der Shader nicht kennt, ist ein Fehler beim Uebersetzen, kein
         * stiller Verlust. */
        if (shader.contains("tex"))
        {
            bauer.withBindGroupLayout(BindGroupLayouts.SAMPLER0);
        }

        /* Test aus = Schreiben aus. Siehe Klassendoku: alles andere baut eine unsichtbare
         * Tiefenwand vor alles Nachfolgende. */
        bauer.withDepthStencilState(tiefePruefen
            ? new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)
            : new DepthStencilState(CompareOp.ALWAYS_PASS, false));

        return bauer.withColorTargetState(mischen
            ? new ColorTargetState(BlendFunction.TRANSLUCENT)
            : ColorTargetState.DEFAULT).build();
    }

    /**
     * NUR die VERDECKTEN Fragmente — der Dimm-Pass des Control-Rig-Overlays.
     *
     * <p>26.2 rechnet reversed-Z (Klassendoku oben): sichtbar ist {@code GREATER_THAN_OR_
     * EQUAL}, also ist verdeckt {@code LESS_THAN} — bewusst OHNE equal, sonst kaempfen
     * beide Paesse auf exakt der Silhouettenkante um dasselbe Fragment. Tiefe wird nie
     * geschrieben (Overlay), Blend an, Cull aus wie bei den Baendern.
     *
     * <p>Zusammen mit dem normalen Tiefen-Pass ergibt das den Editor-Standard, den Unity
     * (occluded gizmo color) und Unreals Control Rig fahren: sichtbare Teile voll,
     * verdeckte gedimmt — auffindbar, ohne das Modell zu ueberdecken.
     */
    public static RenderPipeline hinterGeometrie(VertexFormat.Mode topologie)
    {
        return ZWISCHENSPEICHER.computeIfAbsent("hinter|" + topologie, (s) -> RenderPipeline.builder()
            .withLocation(new ResourceLocation(BBSMod.MOD_ID,
                "pipeline/position_color_" + topologie.name().toLowerCase() + "_hinter"))
            .withVertexShader(new ResourceLocation("core/position_color"))
            .withFragmentShader(new ResourceLocation("core/position_color"))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(topologie)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, false))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build());
    }

    private FormPipelines()
    {}
}
