package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.mixin.client.LevelRendererAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
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
 * <li><b>Setzen</b> braucht einen Umweg ueber {@link ProjectionMatrixBuffer}, der eine
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
    private static ProjectionMatrixBuffer puffer;

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
            puffer = new ProjectionMatrixBuffer("bbs-projektion");
        }

        return puffer.getBuffer(matrix);
    }

    /** Setzt die Projektion aus einer Matrix — der Ersatz fuer das alte setProjectionMatrix. */
    public static void setzen(Matrix4f matrix, ProjectionType art)
    {
        RenderSystem.setProjectionMatrix(hochladen(matrix), art);
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
     * letzten Bildes. Wer einen LevelRenderContext hat, nimmt dessen levelState() direkt --
     * das ist derselbe Wert ohne den Umweg ueber den Mixin.
     */
    public static Matrix4f lesen()
    {
        LevelRenderer zeichner = Minecraft.getInstance().levelRenderer;

        return ((LevelRendererAccessor) zeichner).bbs$getLevelRenderState().cameraRenderState.projectionMatrix;
    }

    private Projektion()
    {}
}
