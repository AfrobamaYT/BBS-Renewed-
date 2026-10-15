package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
//? if >=1.21.6 {
/*import mchorse.bbs_mod.mixin.client.LevelRendererAccessor;
*///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import org.joml.Matrix4f;

/**
 * BBS' Zugang zur Projektionsmatrix in 26.2.
 *
 * <h2>Warum es diese Klasse gibt</h2>
 *
 * <p>Bis 1.21.1 war die Projektion eine {@link Matrix4f}, die man setzen und zurueckfragen
 * konnte. In 26.2 ist sie ein <b>Uniform-Puffer auf der Grafikkarte</b>:
 * {@code RenderSystem.setProjectionMatrix} nimmt einen {@link GpuBufferSlice} entgegen,
 * und {@code getProjectionMatrixBuffer()} gibt einen zurueck. Eine Matrix kommt dort nicht
 * mehr heraus — der Weg fuehrt nur noch hin, nicht zurueck.
 *
 * <p>Fuer BBS heisst das zweierlei:
 *
 * <ol>
 * <li><b>Setzen</b> braucht einen Umweg ueber {@link PerspectiveProjectionMatrixBuffer}, der eine
 *     Matrix in den Puffer schreibt und den Ausschnitt zurueckgibt. BBS haelt dafuer einen
 *     eigenen — Vanilla macht es genauso, GameRenderer und GuiRenderer haben je einen.</li>
 * <li><b>Sichern und Wiederherstellen</b> geht NICHT mehr ueber "Matrix merken, spaeter
 *     wieder setzen". Der Wert liegt auf der Grafikkarte, nicht im Java-Speicher. Dafuer
 *     gibt es {@link #sichern()} / {@link #zurueck()}, die Vanillas eigene Sicherung
 *     benutzen.</li>
 * </ol>
 *
 * <h2>⚠️ Zwei Fallen, beide still</h2>
 *
 * <p><b>Der Ausschnitt lebt nur bis zum naechsten Hochladen.</b> {@link #hochladen} schreibt
 * immer in DENSELBEN Puffer. Wer sich einen Ausschnitt merkt und danach ein zweites Mal
 * hochlaedt, hat den ersten ueberschrieben — ohne Fehlermeldung, mit falschem Bild.
 *
 * <p><b>Vanillas Sicherung hat GENAU EINEN Platz, keinen Stapel.</b> Zwei verschachtelte
 * {@code sichern()} verlieren den aeusseren Wert lautlos. Diese Klasse zaehlt darum mit und
 * wirft, statt still das Falsche wiederherzustellen.
 */
public final class Projektion
{
    /**
     * BBS' eigener Puffer. Ein zweiter Nutzer im selben Bild wuerde sich mit uns
     * ueberschreiben — genau darum hat Vanilla auch mehrere.
     */
    private static PerspectiveProjectionMatrixBuffer puffer;

    /** Tiefe der Sicherung. Vanilla haelt nur eine; alles ueber 1 ist ein Fehler. */
    private static int gesichert;

    /**
     * Laedt eine Matrix in BBS' Projektionspuffer und gibt den Ausschnitt zurueck.
     *
     * <p>⚠️ Nur bis zum naechsten Aufruf gueltig, siehe Klassenkommentar.
     */
    public static GpuBufferSlice hochladen(Matrix4f matrix)
    {
        if (puffer == null)
        {
            puffer = new PerspectiveProjectionMatrixBuffer("bbs-projektion");
        }

        return puffer.getBuffer(matrix);
    }

    /**
     * Setzt die Projektion aus einer Matrix — der Ersatz fuer das alte setProjectionMatrix.
     *
     * <p>⛔ NUR fuer Matrizen, die schon in 26.2s Konvention stehen — praktisch heisst das:
     * aus {@link #lesen()}. Alles, was BBS selbst mit JOML baut
     * ({@code perspective}, {@code setOrtho}), gehoert in {@link #setzenUndUmkehren}.
     */
    public static void setzen(Matrix4f matrix, ProjectionType art)
    {
        //? if >=1.21.6 {
        /*RenderSystem.setProjectionMatrix(hochladen(matrix), art);
        *///?} else {
        RenderSystem.setProjectionMatrix(matrix, art.vertexSorting());
        //?}
    }

    /**
     * Setzt eine von BBS gebaute Matrix und dreht sie dabei auf 26.2s umgekehrte Tiefe.
     *
     * <p>⛔ WOFUER (2026-08-11, aus einem RenderDoc-Mitschnitt der Modellvorschau). JOMLs
     * {@code perspective}/{@code setOrtho} bauen die alte OpenGL-Form: nah &rarr; -1,
     * fern &rarr; +1, im Fenster also nah 0, fern 1. 26.2 prueft aber mit
     * {@code GREATER_THAN_OR_EQUAL} — gemessen im Pipeline-Zustand von Zug 1117. Groesser
     * heisst dort „weiter weg", und behalten wird der groessere: <b>die entferntere Flaeche
     * ueberschreibt die naehere.</b> Das ist das winkelabhaengige Durchscheinen am Modell.
     *
     * <p>Die Pixel-Historie an einem Rumpfpunkt zeigt es unmittelbar — sechs Flaechen aus
     * demselben Zug setzen sich nacheinander mit <i>steigender</i> Tiefe durch
     * (0,9947874 &rarr; 0,9947893), waehrend die naeheren Zuege danach
     * (0,9944611 / 0,9944738) am Tiefentest scheitern.
     *
     * <p>Gegenprobe, warum es die Matrix und nicht die Pipeline ist: Mojangs eigene
     * Weltprojektion trug live {@code Zeile2 = (0, 0, 2,441E-5, 5,000E-2)} — nah 1, fern 0.
     * BBS' Kamera trug {@code (0, 0, -1,0000667, -0,0200007)} — nah -1, fern +1. Mit BBS'
     * {@code near=0,01 / far=300} liegt ein Modell bei z=1,9 rechnerisch auf 0,9948, und
     * genau das stand im Puffer.
     */
    public static void setzenUndUmkehren(Matrix4f matrix, ProjectionType art)
    {
        setzen(umgekehrt(matrix), art);
    }

    /**
     * Dieselbe Umrechnung wie {@link #setzenUndUmkehren}, aber als Matrix — fuer Stellen, die
     * eine BBS-Matrix WEITERREICHEN statt sie selbst zu setzen (etwa
     * {@code GizmoViewport#getGizmoProjection}).
     *
     * <p>⚠️ x, y und w bleiben unberuehrt; nur die z-Zeile dreht sich. Bildschirmrechnungen
     * ueber {@code x/w} und {@code y/w} — Auswahlkreis, Griffmitte — liefern danach exakt
     * dieselben Werte.
     */
    public static Matrix4f umgekehrt(Matrix4f matrix)
    {
        /* Identitaet, in der z' = (w - z) / 2 wird. Absichtlich hier gebaut und nicht als
         * statische Konstante: ein neu hinzugekommenes statisches Feld ist nach einem
         * Hotswap null, weil der statische Initialisierer nicht noch einmal laeuft. */
        return new Matrix4f().m22(-0.5F).m32(0.5F).mul(matrix);
    }

    /** Wie {@link #setzen}, fuer die haeufigste BBS-Variante: flache Projektion. */
    public static void setzenFlach(Matrix4f matrix)
    {
        setzen(matrix, ProjectionType.ORTHOGRAPHIC);
    }

    /**
     * Sichert die aktuelle Projektion, damit sie nach einem eigenen Durchgang
     * wiederhergestellt werden kann.
     */
    public static void sichern()
    {
        if (gesichert > 0)
        {
            throw new IllegalStateException(
                "Projektion.sichern() zweimal ineinander: Vanilla hat nur EINEN Platz, der "
                + "aeussere Wert waere weg. Die innere Sicherung gehoert entfernt oder die "
                + "beiden Durchgaenge gehoeren nacheinander statt ineinander.");
        }

        gesichert++;
        RenderSystem.backupProjectionMatrix();
    }

    /** Stellt die mit {@link #sichern()} gesicherte Projektion wieder her. */
    public static void zurueck()
    {
        if (gesichert == 0)
        {
            throw new IllegalStateException(
                "Projektion.zurueck() ohne vorheriges sichern() -- es wuerde eine fremde oder "
                + "veraltete Projektion wiederherstellen.");
        }

        gesichert--;
        RenderSystem.restoreProjectionMatrix();
    }

    /**
     * Die aktuelle Projektion als LESBARE Matrix.
     *
     * <p>Aus dem GPU-Puffer kommen keine Zahlen zurueck. Die einzige lesbare Fassung steht
     * im eingefrorenen Kamerazustand des Weltdurchgangs, und dorthin fuehrt der Accessor.
     *
     * <p>⚠️ Nur WAEHREND eines Weltdurchgangs sinnvoll. Ausserhalb steht dort der Wert des
     * letzten Bildes. Wer einen WorldRenderContext hat, nimmt dessen levelState() direkt --
     * das ist derselbe Wert ohne den Umweg ueber den Mixin.
     */
    public static Matrix4f lesen()
    {
        //? if >=1.21.6 {
        /*LevelRenderer zeichner = Minecraft.getInstance().levelRenderer;

        return ((LevelRendererAccessor) zeichner).bbs$getLevelRenderState().cameraRenderState.bbsProjectionMatrix();
        *///?} else {
        return new Matrix4f(RenderSystem.getProjectionMatrix());
        //?}
    }

    private Projektion()
    {}
}
