package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Zeichnet ein {@link IModelVAO} in das laufende Weltbild.
 *
 * <h2>26.2: vom Shader-Objekt zum Zeichendurchgang</h2>
 *
 * <p>Frueher setzte diese Klasse ein Dutzend Uniforms von Hand an einem
 * {@code RenderPipeline}, rief {@code shader.apply()} und liess den VAO zeichnen. In 26.2
 * gibt es weder {@code RenderPipeline} noch einzelne Uniforms: gezeichnet wird in einen
 * {@link RenderPass}, der eine {@link RenderPipeline} bekommt und seine Werte aus
 * Uniform-Bloecken liest.
 *
 * <p>Die Reihenfolge ist von Vanillas eigenen Ad-hoc-Zeichnern abgeschaut
 * ({@code SkyRenderer}, {@code CloudRenderer}, {@code WorldBorderRenderer} — sie machen
 * genau dasselbe fuer Himmel, Wolken und Weltgrenze):
 * Geraet holen, Kommandoschreiber anlegen, Durchgang gegen das Hauptziel oeffnen,
 * Standard-Uniforms binden, eigene Werte daruebersetzen, zeichnen, schliessen.
 *
 * <h2>⚠️ Ein Durchgang je Modell — das ist der bekannte Preis</h2>
 *
 * <p>Vanilla sammelt gleichartige Geometrie und zeichnet sie gebuendelt. Dieser Weg
 * oeffnet fuer JEDES Modell einen eigenen Durchgang, weil BBS' Modelle mitten im
 * Weltrendering an beliebiger Stelle auftauchen. Fuer eine Handvoll Schauspieler ist das
 * richtig; wenn eine Szene hunderte Modelle zeigt, ist genau hier der Hebel fuer
 * Sammeln — nicht in den Modellen selbst. Erst messen, dann buendeln.
 */
public class ModelVAORenderer
{
    /**
     * Der eigene Uniform-Block, absichtlich nur aus 16-Byte-Feldern gebaut.
     *
     * <p>std140 richtet Felder unterschiedlich aus, je nach Typ — ein {@code ivec2} nach
     * einer {@code mat3} landet nicht dort, wo man es naiv erwartet, und die Folge waere
     * ein falsches Bild ohne Fehlermeldung. Darum steht hier alles als volle 16-Byte-Zeile:
     * die Normalenmatrix als drei Zeilen, Licht und Overlay zusammen in einer vierten,
     * die Durchgangsart in einer fuenften und die Materialparameter in einer sechsten.
     * Kein Feld braucht Fuellbytes, kein Offset ist geraten.
     */
    private static final int BLOCK_GROESSE = 6 * 16;

    /**
     * ⛔ RINGPUFFER STATT EINEM BLOCK (2026-08-04 gemessen).
     *
     * <p>Hier stand EIN Puffer, der je Zeichenaufruf neu beschrieben wurde — mit dem
     * ausdruecklichen Hinweis darunter, dass das nur traegt, solange zwischen Schreiben und
     * Zeichnen kein zweiter Aufruf liegt, und dass "mehrere Modelle in EINEM Durchgang"
     * einen Ringpuffer brauchen. Genau dieser Fall ist im Editor eingetreten: haelt man Alt,
     * zeichnet der Auswahl-Durchgang ALLE Replays nacheinander in denselben Durchgang.</p>
     *
     * <p>Folge, am laufenden Spiel gemessen: jedes Modell trug die Kennung des ZULETZT
     * geschriebenen Blocks. Bei den Entity-Schluesseln 0,1,3,4 ueberall 24, nach dem
     * Entfernen der 4 ueberall 25. Sichtbar als der vom Nutzer gemeldete Sprung — mit Alt
     * waren beide Modelle orange umrandet statt nur das gewaehlte.</p>
     *
     * <p>{@link #RING} Bloecke reichen weit ueber die Zahl der Modelle je Durchgang; der
     * Zeiger laeuft um und ueberschreibt erst wieder, wenn der Abstand groesser ist als
     * alles, was gleichzeitig in der Warteschlange steht. Kosten: RING × BLOCK_GROESSE
     * Bytes, hier unter 30 KB.</p>
     */
    private static final int RING = 256;

    private static GpuBuffer[] bloecke;
    private static int blockZeiger;

    /**
     * The full model-view a draw issued right now would use. Deferred translucent commands
     * capture it at enqueue time, because the global model-view is different by the time
     * the queue flushes at the end of the frame.
     *
     * <p>26.2: {@code getModelViewMatrix()} heisst jetzt {@code getModelViewMatrixCopy()}
     * und liefert eine Kopie — hier ist das richtig, es wird ohnehin nur gelesen.
     */
    public static Matrix4f captureModelView(PoseStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrixCopy()).mul(stack.last().pose());
    }

    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, PoseStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        render(pipeline, modelVAO, stack, r, g, b, a, light, overlay, null);
    }

    /** Wie oben, aber mit der Modelltextur -- siehe die lange Fassung, warum sie noetig ist. */
    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, PoseStack stack, float r, float g, float b, float a, int light, int overlay, mchorse.bbs_mod.graphics.texture.Texture textur)
    {
        render(pipeline, modelVAO, stack, r, g, b, a, light, overlay, textur, 0);
    }

    /**
     * Wie oben, aber mit dem Grundindex der Form fuer den Auswahl-Durchgang.
     *
     * <p>⛔ DIESE Fassung ist die des Auswahl-Durchgangs, nicht die mit {@code passMode}
     * (2026-08-02 gemessen). {@link FormTranslucentQueue#needsSplit} und
     * {@link FormTranslucentQueue#needsWholeDefer} verlangen beide {@code stencilMap == null};
     * beim Picken IST die Karte gesetzt, also faellt der Zug immer in den schlichten
     * {@code else}-Zweig der Aufrufer -- und der reichte die Kennung als 0 herein. Wer den
     * Grundindex nur in den Aufschub-Zweig legt, aendert eine Stelle, die im Auswahl-Durchgang
     * nie laeuft, und misst danach unveraendert {@code kennung=0}.
     */
    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, PoseStack stack, float r, float g, float b, float a, int light, int overlay, mchorse.bbs_mod.graphics.texture.Texture textur, int kennung)
    {
        render(pipeline, modelVAO, stack, r, g, b, a, light, overlay, textur, kennung, 0F);
    }

    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, PoseStack stack, float r, float g, float b, float a, int light, int overlay, mchorse.bbs_mod.graphics.texture.Texture textur, int kennung, float emission)
    {
        render(pipeline, modelVAO, captureModelView(stack), stack.last().normal(), r, g, b, a, light, overlay, 0, kennung, textur, emission);
    }

    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, int passMode)
    {
        render(pipeline, modelVAO, modelView, normalMat, r, g, b, a, light, overlay, passMode, 0);
    }

    /**
     * Wie oben, aber mit Kennung fuer den Auswahl-Durchgang.
     *
     * <p>Die Picker-Shader schreiben statt einer Farbe eine 24-Bit-Zahl, aus der der Klick
     * das getroffene Objekt zurueckliest. Frueher stand sie im Uniform "Target"; jetzt ist
     * sie Modus.y im gemeinsamen Block.
     */
    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, int passMode, int kennung)
    {
        render(pipeline, modelVAO, modelView, normalMat, r, g, b, a, light, overlay, passMode, kennung, null);
    }

    /**
     * Wie oben, aber mit der MODELLTEXTUR.
     *
     * <h2>⛔ OHNE SIE ZEICHNET DAS MODELL WEISS (2026-07-31 gemessen)</h2>
     *
     * <p>Bis 1.21.1 band der Aufrufer die Textur global ({@code getTextures().bindTexture}),
     * und der Zeichenaufruf las sie von dort. In 26.2 gehoert eine Textur zum ZUG: die
     * Pipeline deklariert {@code Sampler0}, und wer ihn nicht bindet, bekommt keine Textur --
     * ohne Fehler, ohne Log, einfach weiss.
     *
     * <p>Gemessen im Film-Editor: ein ModelForm-Replay fuellte 48 Prozent des Vorschaufeldes
     * mit reinem Weiss (256691 von 534100 Bildpunkten ueber Helligkeit 200). Geometrie da,
     * Textur fehlt.
     *
     * <p>⚠️ Sampler1 (Overlay) und Sampler2 (Lichtkarte) deklariert die Pipeline ebenfalls.
     * Sie werden hier mitgebunden, weil ein Durchgang mit unvollstaendiger Bindegruppe
     * scheitert -- nicht, weil das Modell sie unbedingt braeuchte.
     */
    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, int passMode, int kennung, mchorse.bbs_mod.graphics.texture.Texture textur)
    {
        render(pipeline, modelVAO, modelView, normalMat, r, g, b, a, light, overlay, passMode, kennung, textur, 0F);
    }

    public static void render(RenderPipeline pipeline, IModelVAO modelVAO, Matrix4f modelView, Matrix3f normalMat, float r, float g, float b, float a, int light, int overlay, int passMode, int kennung, mchorse.bbs_mod.graphics.texture.Texture textur, float emission)
    {
        if (modelVAO.eckenZahl() <= 0)
        {
            return;
        }

        GpuBuffer skinMatrices = modelVAO instanceof BOBJModelVAO bobj ? bobj.skinMatrices() : null;

        if (skinMatrices != null)
        {
            RenderPipeline gpuPipeline = ModelVAOPipeline.gpuVariante(pipeline);

            if (gpuPipeline == null)
            {
                throw new IllegalStateException("GPU-skinned BOBJ has no matching pipeline: " + pipeline);
            }

            pipeline = gpuPipeline;
        }

        /* ⚠️ RUECKFALL AUF DIE ZULETZT GLOBAL GEBUNDENE TEXTUR -- eine BRUECKE, kein Entwurf.
         *
         * Nicht jeder Aufrufer reicht die Textur schon als Parameter durch. CubicVAORenderer
         * #renderGroup -- der Weg JEDES cubic-Modells, also auch der ModelForms im Film --
         * ruft hier ohne sie herein, bindet sie unmittelbar davor aber global; es ist derselbe
         * Wert, den es auch in seinen aufgeschobenen Befehl legt (dort steht er schon als Feld).
         * Ohne diesen Rueckfall bleibt Sampler0 ungebunden, und das Modell zeichnet WEISS --
         * ohne Fehler, ohne Log (gemessen 2026-07-31: 118515 von 534100 Bildpunkten der
         * Vorschauflaeche ueber Helligkeit 200, Aufgabe 99).
         *
         * ⛔ WER NEU HIER HEREINZEICHNET, GIBT DIE TEXTUR ALS PARAMETER MIT. Der Rueckfall
         * liest einen globalen Zustand und ist nur so richtig wie die Bindung unmittelbar
         * davor -- fuer einen AUFGESCHOBENEN Zug waere er falsch, weil zwischen Einreihen und
         * Zeichnen beliebig viel anderes bindet. Darum reichen ModelVAOCommand und BOBJCommand
         * ihre Textur ausdruecklich durch, statt sich hierauf zu verlassen. */
        boolean durchgereicht = textur != null;


        if (textur == null)
        {
            textur = mchorse.bbs_mod.BBSModClient.getTextures().getLastBound();
        }

        /* Messfuehler Aufgabe 99 (Figur erscheint magenta = Fehltextur): kommt eine Textur
         * hier an, und ueber welchen Weg -- als Parameter oder ueber den globalen Rueckfall,
         * vor dem der Kommentar oben warnt? */
        /* ⛔ GETRENNTE SCHLUESSEL, NICHT EINE ~-PROBE (2026-08-05). Vorher stand beides unter
         * EINEM Schluessel, und die ~-Probe zeigt nur den LETZTEN Aufruf. In der Formpalette
         * laufen 22 Kacheln verschiedener Typen je Bild -- der letzte Wert gehoerte dort
         * einer fremden Kachel, und "rueckfall" sah nach einem Modell-Befund aus. Getrennt
         * gezaehlt sagen die ZAHLEN, welcher Weg wie oft geht. */
        /* Einzelheiten nur bei laufender Messung bauen (siehe Messpunkt.istAn): ungeschuetzt
         * kosteten die drei Einzelheiten dieses Zugs 0,8-1,0 % des Renderthreads (27.09.2026). */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle(
                "modell.textur." + (durchgereicht ? "parameter" : "rueckfall"),
                textur == null
                    ? "NULL"
                    : "id=" + textur.id + " " + textur.width + "x" + textur.height);
        }


        RenderTarget ziel = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        CommandEncoder schreiber = RenderSystem.getDevice().createCommandEncoder();


        /* ⭐ WOHIN gezeichnet wird, entscheidet nicht mehr diese Zeile allein (Aufgabe 96).
         * In der Welt bleibt es das Hauptbild; in einer Oberflaechen-Vorschau muss dasselbe
         * Modell in die Textur des Bild-im-Bild-Zeichners, sonst malt es ins Hauptbild und
         * verschwindet unter dem Panel-Hintergrund. Siehe Zeichenziel.
         *
         * ⛔ DER AUSWAHL-PUFFER GEHT VOR -- SEIN FEHLEN WAR DER LEERE STENCIL.
         *
         * Der Kennungs-Durchgang (StencilFormFramebuffer.apply() ... unbind()) meldete sein
         * Ziel ueber PickZiel an, aber NUR der Gizmo-Weg fragte es ab (Gizmo, GizmoRinge,
         * GizmoAuswahl). Dieser Weg hier -- der Weg JEDER Form, also des orangen
         * Replay-Umrisses und der Bone-Auswahl -- las nur Zeichenziel und zeichnete seine
         * Kennungen deshalb ins Hauptbild. Der Auswahl-Puffer bekam kein einziges Pixel:
         * gemessen direkt hinter der Fuellung, 2560x1440, RGB!=0 = 0 und alpha!=0 = 0.
         *
         * Reihenfolge, nicht Geschmack: PickZiel ist nur zwischen apply() und unbind()
         * gesetzt, und in diesem Fenster gehoert ALLES in den Kennungspuffer -- auch dann,
         * wenn der Durchgang aus einer Vorschau heraus laeuft und Zeichenziel gleichzeitig
         * auf deren Textur zeigt (UIPickableFormRenderer). Kennungsfarben in der Vorschau
         * waeren sichtbarer Farbmuell UND ein leerer Auswahl-Puffer. */
        com.mojang.blaze3d.textures.GpuTextureView farbZiel;
        com.mojang.blaze3d.textures.GpuTextureView tiefenZiel;

        if (mchorse.bbs_mod.graphics.PickZiel.aktiv() != null)
        {
            farbZiel = mchorse.bbs_mod.graphics.PickZiel.aktiv();
            tiefenZiel = mchorse.bbs_mod.graphics.PickZiel.tiefe();
        }
        else if (mchorse.bbs_mod.graphics.Zeichenziel.eigenes())
        {
            farbZiel = mchorse.bbs_mod.graphics.Zeichenziel.farbe();
            tiefenZiel = mchorse.bbs_mod.graphics.Zeichenziel.tiefe();
        }
        else
        {
            farbZiel = ziel.getColorTextureView();
            tiefenZiel = ziel.getDepthTextureView();
        }

        /* ⚗️ WELCHER ZWEIG GEWINNT? (2026-08-05, #13.) Die drei Ziele stehen hier in fester
         * Rangfolge, und welches greift, ist von aussen nicht zu sehen -- der Zug laeuft
         * durch, egal wohin er schreibt. Getrennte Schluessel, damit ZAHLEN antworten. */
        if (mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("modell.ziel."
                + (mchorse.bbs_mod.graphics.PickZiel.aktiv() != null ? "pick"
                    : mchorse.bbs_mod.graphics.Zeichenziel.eigenes() ? "zeichenziel" : "hauptbild"),
                /* Der AUSSCHNITT dazu: ein Zug ins richtige Ziel hinterlaesst trotzdem nichts,
                 * wenn der Ausschnitt ihn wegschneidet. Die Sammeltextur ist in Bildpunkten,
                 * ihr Zustand nennt aber GUI-Einheiten -- genau hier wuerde sich das zeigen. */
                "ausschnitt=" + mchorse.bbs_mod.graphics.Zeichenbereich.aktiv()
                    + " zielgroesse=" + (farbZiel == null ? "null"
                        : farbZiel.getWidth(0) + "x" + farbZiel.getHeight(0))
                    /* ⛔ HIER STAND DIE PROJEKTION -- UND SIE WAR EINE LUEGE (2026-08-05).
                     *
                     * `Projektion.lesen()` liefert NICHT die gebundene Abbildung, sondern den
                     * eingefrorenen Kamerazustand des WELTdurchgangs; sein eigener Kommentar sagt
                     * "nur waehrend eines Weltdurchgangs sinnvoll, ausserhalb steht dort der Wert
                     * des letzten Bildes". Dieser Zug hier laeuft in der GUI-Phase. Gemeldet
                     * wurde also auf JEDEM Pfad derselbe Weltwert (m33=0, Perspektive) -- was wie
                     * ein Befund aussah ("Perspektive auf Pixelgeometrie") und keiner war.
                     *
                     * Eine lesbare Fassung der gebundenen Projektion gibt es nicht: sie liegt als
                     * GPU-Puffer vor. Wer sie braucht, muss sie an der Setzstelle mitschreiben.
                     * Bis dahin steht hier NICHTS statt einer Zahl, der man glaubt.
                     *
                     * Die Modellansicht darunter ist dagegen echt -- sie ist genau das Argument,
                     * das writeTransform bekommt. */
                    + " mv[m30=" + String.format("%.3f", modelView.m30())
                    + " m31=" + String.format("%.3f", modelView.m31())
                    + " m32=" + String.format("%.3f", modelView.m32()) + "]");
        }

        /* Modellansicht und Farbe gehen in Vanillas DynamicTransforms-Block. BBS setzt eine
         * EIGENE Modellansicht (die eingefangene, siehe captureModelView) — writeTransform
         * fuellt genau diesen Block fuer einen Zeichenaufruf und gibt den Ausschnitt
         * zurueck, den der Durchgang binden muss. */
        GpuBufferSlice transformationen = RenderSystem.getDynamicUniforms()
            .writeTransform(modelView, new Vector4f(r, g, b, a));

        /* Den ZURUECKGEGEBENEN Block binden, nicht ein globales Feld: seit dem Ringpuffer
         * ist es je Zeichenaufruf ein anderer. */
        ModelDrawBatch.beforeUniformWrite();
        GpuBuffer block = blockFuer(normalMat, light, overlay, passMode, kennung, emission);

        /* Messfuehler 2026-08-02: der Auswahlpuffer traegt Alpha (100928 Punkte gemessen),
         * aber RGB = 0 — die Kennung kommt also nicht an, obwohl der Durchgang laeuft.
         * Diese Zeile sagt, WELCHE Kennung und WELCHER Modus hier ueberhaupt eingereicht
         * werden. Nur im Kennungs-Fenster, sonst waere es Rauschen aus dem Weltdurchgang. */
        if (mchorse.bbs_mod.graphics.PickZiel.aktiv() != null && mchorse.bbs_mod.graphics.Messpunkt.istAn())
        {
            mchorse.bbs_mod.graphics.Messpunkt.zaehle("pick.einreichung",
                "kennung=" + kennung + " passMode=" + passMode + " farbe=" + r + "," + g + "," + b + "," + a);
        }

        /* Leeres Optional und leeres OptionalDouble heissen: Farb- und Tiefenpuffer NICHT
         * loeschen. Das Modell wird in das bestehende Weltbild gezeichnet — mit einem
         * Loeschwert waere die Welt weg. */
        /* Messfuehler Aufgabe 96: dieser Durchgang zeichnet SOFORT ins Hauptziel. In der Welt
         * richtig; in der Oberflaeche von 26.2 landet er VOR dem GUI und wird vom
         * Panel-Hintergrund uebermalt. Ob er beim schwarzen Vorschaufeld ueberhaupt laeuft,
         * sagt dieser Zaehler. Standardmaessig aus, siehe Messpunkt. */
        mchorse.bbs_mod.graphics.Messpunkt.zaehle("modell.vao-durchgang");

        /* ⛔ DER AUSSCHNITT MUSS AN DEN DURCHGANG, NICHT NUR IN DEN ZAEHLER (2026-08-05).
         *
         * Diese Stelle MELDETE {@code Zeichenbereich.aktiv()} oben im Messpunkt und UEBERGAB
         * ihn dann nicht -- der Zug lief immer ueber das ganze Ziel. Im Frame nachgemessen,
         * nachdem die Miniaturen ihre Zelle korrekt setzten:
         *
         *     ~modell.ziel.zeichenziel = ausschnitt=RenderArea[x=600...]   <- gesetzt
         *     RenderDoc, alle 17 Kacheln:  scissor = 0,0 854x480           <- nicht benutzt
         *
         * Genau der Fall aus Regel 1j: eine Sonde las einen Wert, den der Zeichenaufruf gar
         * nicht verwendet. {@code GehaltenesNetz} macht es seit jeher richtig; der VAO-Weg
         * (Model, Block, Extruded, Trail) war der, der es nicht tat.
         *
         * {@code null} heisst ganzes Ziel und ist der Normalfall -- im Weltdurchgang steht
         * dort nichts, der bleibt also unberuehrt. Zurechtgeschnitten aus demselben Grund wie
         * dort: ein zu grosser Ausschnitt bricht in 26.2 ab, statt zu beschneiden. */
        RenderPass.RenderArea bereich = mchorse.bbs_mod.graphics.Zeichenbereich.begrenztAuf(
            mchorse.bbs_mod.graphics.Zeichenbereich.aktiv(),
            farbZiel.getWidth(0), farbZiel.getHeight(0));

        /* ⛔ EIN Lambda, nicht zwei. Zwei Zweige mit je einem {@code () -> "bbs-modell"} sind
         * zwei synthetische Methoden -- und eine geaenderte Lambda-MENGE bricht den Hotswap
         * ("class redefinition failed: attempted to add a method"), womit diese Aenderung nur
         * noch mit Boot pruefbar waere. Einmal hier passiert, darum steht es dabei. */
        java.util.function.Supplier<String> name = () -> "bbs-modell";

        if (ModelDrawBatch.collect(name, farbZiel, tiefenZiel, bereich, pipeline, modelVAO,
            transformationen, block, skinMatrices, textur))
        {
            return;
        }

        try (RenderPass durchgang = bereich == null
            ? schreiber.createRenderPass(
                name, farbZiel, Optional.empty(), tiefenZiel, OptionalDouble.empty())
            : schreiber.createRenderPass(
                name, farbZiel, Optional.empty(), tiefenZiel, OptionalDouble.empty(), bereich))
        {
            durchgang.setPipeline(pipeline);

            RenderSystem.bindDefaultUniforms(durchgang);

            /* NACH bindDefaultUniforms, nicht davor: die Standardbindung setzt die globale
             * Modellansicht, und genau die soll hier ueberschrieben werden. */
            durchgang.setUniform("DynamicTransforms", transformationen);
            durchgang.setUniform(ModelVAOPipeline.MODELL_BLOCK, block);

            if (skinMatrices != null)
            {
                durchgang.setUniform("SkinMatrices", skinMatrices);
            }

            /* ⛔ AM DURCHGANG, NICHT GLOBAL. Zwei Anlaeufe ueber RenderSystem.setShaderLights
             * blieben wirkungslos (gemessen 2026-08-10, Helligkeit 0,415 unveraendert): der
             * globale Stand ist bis hierher wieder ueberschrieben. Nur die Bindung am
             * Durchgang selbst haelt. Siehe UiLicht. */
            if (!mchorse.bbs_mod.client.BBSRendering.isRenderingWorld())
            {
                durchgang.setUniform("Lighting", mchorse.bbs_mod.graphics.UiLicht.scheibe());
            }

            /* ⛔ OHNE DIESE BINDUNG ZEICHNET DAS MODELL WEISS. Die Pipeline deklariert
             * Sampler0/1/2; in 26.2 gehoert die Textur zum Zug, nicht in einen globalen
             * Aufruf davor. */
            if (textur != null)
            {
                durchgang.bindTexture("Sampler0", textur.view(), textur.sampler());

                /* ⚠️ ALLE DREI SAMPLER, die die Pipeline deklariert. Sampler1 (Overlay) und
                 * Sampler2 (Lichtkarte) liest der Shader per texelFetch; eine Bindegruppe,
                 * der ein deklarierter Sampler fehlt, ist unvollstaendig -- und ob ein
                 * Durchgang dann zeichnet oder still verwirft, wollen wir nicht ausprobieren
                 * muessen. Die Quellen sind Vanillas eigene (GameRenderer.lightmap /
                 * overlayTexture), abgelesen an TextureSetup.singleTextureWithLightmap. */
                net.minecraft.client.renderer.GameRenderer spielZeichner =
                    Minecraft.getInstance().gameRenderer;
                com.mojang.blaze3d.textures.GpuSampler glatt = RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);

                durchgang.bindTexture("Sampler1", spielZeichner.overlayTexture().getTextureView(), glatt);
                durchgang.bindTexture("Sampler2", spielZeichner.lightmap(), glatt);
            }

            modelVAO.zeichnen(durchgang);
        }
    }

    /**
     * Fuellt den eigenen Block fuer den naechsten Zeichenaufruf und gibt ihn zurueck.
     *
     * <p>Oeffentlich, weil nicht nur der VAO-Weg ihn braucht: die aufgeschobenen Zuege der
     * durchsichtigen Warteschlange zeichnen CPU-Geometrie mit demselben Modell-Shader und
     * damit mit demselben Block.
     *
     * <p>Der Puffer wird einmal angelegt und wiederbeschrieben. Das ist erlaubt, weil
     * zwischen dem Schreiben und dem Zeichnen kein zweiter Aufruf liegt — wer das aendert
     * (etwa um mehrere Modelle in EINEM Durchgang zu zeichnen), braucht einen Ringpuffer,
     * sonst ueberschreibt das zweite Modell die Werte des ersten, bevor die Karte sie
     * gelesen hat.
     */
    public static GpuBuffer blockFuer(Matrix3f normalMat, int light, int overlay, int passMode, int kennung)
    {
        return blockFuer(normalMat, light, overlay, passMode, kennung, 0F);
    }

    public static GpuBuffer blockFuer(Matrix3f normalMat, int light, int overlay, int passMode, int kennung, float emission)
    {
        if (bloecke == null)
        {
            bloecke = new GpuBuffer[RING];
        }

        blockZeiger = (blockZeiger + 1) % RING;

        GpuBuffer block = bloecke[blockZeiger];

        if (block == null)
        {
            /* ⚠️ USAGE_COPY_DST ist PFLICHT: der Block wird pro Zeichenaufruf neu beschrieben.
             * Ohne das Flag lehnt 26.2 das Schreiben ab ("Buffer needs USAGE_COPY_DST to be a
             * destination for a copy"). Ein Puffer, der nur mit USAGE_UNIFORM angelegt wird,
             * ist einer, den man EINMAL beim Anlegen fuellt -- das trifft hier nicht zu, denn
             * angelegt wird nur mit einer Groesse. */
            block = RenderSystem.getDevice().createBuffer(() -> "bbs-modell-block",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLOCK_GROESSE);

            bloecke[blockZeiger] = block;
        }

        ByteBuffer roh = MemoryUtil.memAlloc(BLOCK_GROESSE).order(ByteOrder.nativeOrder());

        try
        {
            /* Die Normalenmatrix als drei volle Zeilen; die vierte Spalte jeder Zeile ist
             * Fuellung, die der Shader ignoriert (er liest mat3). */
            zeile(roh, normalMat.m00(), normalMat.m01(), normalMat.m02());
            zeile(roh, normalMat.m10(), normalMat.m11(), normalMat.m12());
            zeile(roh, normalMat.m20(), normalMat.m21(), normalMat.m22());

            /* Licht und Overlay sind gepackte Koordinatenpaare; der alte Shader las sie als
             * zwei ivec2 und holte damit aus den Karten. Sie liegen hier zusammen in einer
             * Zeile: x/y = Licht, z/w = Overlay. */
            roh.putInt(light & 0xFFFF).putInt(light >> 16 & 0xFFFF);
            roh.putInt(overlay & 0xFFFF).putInt(overlay >> 16 & 0xFFFF);

            /* Modus.x: die Durchgangsart der Zwei-Pass-Durchsichtigkeit -- 0 = ein
             * Durchgang, 1 = nur undurchsichtige Texel (schreiben Tiefe), 2 = nur
             * halbdurchsichtige (sortiert, ohne Tiefenschreiben).
             *
             * Modus.y: die Kennung des Auswahl-Durchgangs (frueher das Uniform "Target").
             * Beim normalen Zeichnen ist sie 0 und wird nicht gelesen; die Picker-Shader
             * bauen daraus die Zahl, die der Klick zurueckliest. */
            roh.putInt(passMode).putInt(kennung).putInt(0).putInt(0);

            /* Blender material parity: x is the Mix Shader share of the Emission branch.
             * The remaining values stay reserved so the std140 layout remains explicit. */
            roh.putFloat(MathUtils.clamp(emission, 0F, 1F)).putFloat(0F).putFloat(0F).putFloat(0F);

            /* ⚠️ IViewRotMat steht bewusst NICHT im Block. Der alte Shader deklarierte es
             * ({@code uniform mat3 IViewRotMat}), benutzte es in main() aber nirgends —
             * nachgelesen in model.vsh, nicht angenommen. Ein toter Wert kostet drei volle
             * Zeilen. Wer ihn braucht, erweitert den Block UND zieht BLOCK_GROESSE mit. */

            roh.flip();

            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(block.slice(), roh);
        }
        finally
        {
            MemoryUtil.memFree(roh);
        }

        return block;
    }

    /** Eine 16-Byte-Zeile: drei Werte plus eine Fuellstelle. */
    private static void zeile(ByteBuffer puffer, float x, float y, float z)
    {
        puffer.putFloat(x).putFloat(y).putFloat(z).putFloat(0F);
    }
}
