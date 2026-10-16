package mchorse.bbs_mod.audio;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import mchorse.bbs_mod.client.FormPipelines;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;

import java.util.Arrays;

/**
 * Many flat rectangles handed to the GUI as ONE element.
 *
 * <p>The detailed waveform draws two to four boxes per screen column. Submitted one by one, every
 * box became its own GuiElementRenderState, and GuiRenderState sorts each new element against the
 * ones already there: measured 2026-10-15 on the owner's instance (jstack, 6 of 8 render-thread
 * samples in GuiRenderState.hasIntersection under Waveform.render) the film editor fell to 13 fps
 * with 26 audio clips under the cursor. Collected here, a clip's whole waveform is one mesh.</p>
 */
public final class SpaltenNetz
{
    private float[] lagen = new float[64];
    private int[] farben = new int[32];
    private int anzahl;

    /** Queue a rectangle with a top and a bottom colour. */
    public void box(float x1, float y1, float x2, float y2, int oben, int unten)
    {
        if (x2 <= x1 || y2 <= y1)
        {
            return;
        }

        if ((this.anzahl + 1) * 4 > this.lagen.length)
        {
            this.lagen = Arrays.copyOf(this.lagen, this.lagen.length * 2);
            this.farben = Arrays.copyOf(this.farben, this.farben.length * 2);
        }

        int i = this.anzahl * 4;
        int c = this.anzahl * 2;

        this.lagen[i] = x1;
        this.lagen[i + 1] = y1;
        this.lagen[i + 2] = x2;
        this.lagen[i + 3] = y2;
        this.farben[c] = oben;
        this.farben[c + 1] = unten;
        this.anzahl += 1;
    }

    public void box(float x1, float y1, float x2, float y2, int farbe)
    {
        this.box(x1, y1, x2, y2, farbe, farbe);
    }

    /** Submit everything queued as one GUI element covering x, y, w, h, then start over. */
    public void einreichen(Batcher2D batcher, int x, int y, int w, int h)
    {
        if (this.anzahl == 0)
        {
            return;
        }

        Matrix3x2f pose = new Matrix3x2f(batcher.getContext().pose());
        float[] lagen = Arrays.copyOf(this.lagen, this.anzahl * 4);
        int[] farben = Arrays.copyOf(this.farben, this.anzahl * 2);
        int n = this.anzahl;

        this.anzahl = 0;

        /* Vertex order and pose as Batcher2D.UIQuad (TL, BL, BR, TR) */
        batcher.netz(FormPipelines.holen(PrimitiveTopology.QUADS, true, false, false), (ecken) ->
        {
            for (int k = 0; k < n; k++)
            {
                float x1 = lagen[k * 4], y1 = lagen[k * 4 + 1], x2 = lagen[k * 4 + 2], y2 = lagen[k * 4 + 3];
                int oben = farben[k * 2], unten = farben[k * 2 + 1];

                ecken.addVertexWith2DPose(pose, x1, y1).setColor(oben);
                ecken.addVertexWith2DPose(pose, x1, y2).setColor(unten);
                ecken.addVertexWith2DPose(pose, x2, y2).setColor(unten);
                ecken.addVertexWith2DPose(pose, x2, y1).setColor(oben);
            }
        }, new ScreenRectangle(x, y, Math.max(1, w), Math.max(1, h)).transformMaxBounds(pose));
    }
}
