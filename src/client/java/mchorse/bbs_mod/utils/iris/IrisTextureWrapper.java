package mchorse.bbs_mod.utils.iris;

import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;

/**
 * Reicht eine BBS-Textur als Vanilla-{@link AbstractTexture} an Iris durch, damit Shader
 * BBS-Texturen (auch animierte) benutzen koennen.
 *
 * <p>⛔ AUF 26.2 PORTIERT (2026-08-10). Bis 1.21.1 hing hier ein {@code getId()}, das eine
 * GL-Nummer zurueckgab. In 26.2 gibt es das nicht mehr: {@code AbstractTexture} fuehrt nur
 * noch {@code getTexture()}, {@code getSampler()} und {@code getTextureView()}, alle auf
 * {@code Gpu*}-Typen. Gemessen im Iris-1.11.2-Bytecode ruft Iris davon {@code getTexture()}
 * und {@code getSampler()} — {@code getTextureView()} im PBR-Pfad gar nicht. Ueberschrieben
 * werden trotzdem alle drei, damit kein anderer Aufrufer auf das leere Feld der Oberklasse
 * faellt.</p>
 *
 * <p>⛔ DER WITZ IST, DASS ES PRO AUFRUF NEU AUFLOEST. Diese Klasse laedt nie etwas und
 * fuellt die geerbten Felder {@code texture}/{@code sampler}/{@code textureView} NICHT — sie
 * schlaegt bei jedem Zugriff in BBS' Texturverwaltung nach. Nur so folgt der Shader einer
 * animierten Textur; wer hier einmalig zwischenspeichert, friert das erste Bild ein.</p>
 *
 * <p>⚠️ {@code null} heisst "keine Textur". Vorher war das {@code -1}, eine ungueltige
 * GL-Nummer — dieselbe Aussage, andere Form. Ein Aufrufer, der nicht damit rechnet, sah
 * vorher einen GL-Fehler und sieht jetzt eine NPE; beides ist ein Fehler beim Aufrufer,
 * nicht hier.</p>
 */
public class IrisTextureWrapper extends AbstractTexture
{
    public final Link texture;
    public final AbstractTexture fallback;
    public final int index;

    public IrisTextureWrapper(Link texture, int index)
    {
        this(texture, null, index);
    }

    public IrisTextureWrapper(Link texture, AbstractTexture fallback, int index)
    {
        this.texture = texture;
        this.fallback = fallback;
        this.index = index;
    }

    //? if <1.21.2 {
    /* Vor 1.21.2 ist load abstrakt; die Huelle laedt nichts selbst, sie loest beim Abfragen auf. */
    @Override
    public void load(net.minecraft.server.packs.resources.ResourceManager manager)
    {}
    //?}
    @Override
    public GpuTexture getTexture()
    {
        Texture gefunden = this.aufloesen();

        if (gefunden != null)
        {
            return gefunden.gpu();
        }

        return this.fallback == null ? null : this.fallback.getTexture();
    }

    @Override
    public GpuTextureView getTextureView()
    {
        Texture gefunden = this.aufloesen();

        if (gefunden != null)
        {
            return gefunden.view();
        }

        return this.fallback == null ? null : this.fallback.getTextureView();
    }

    @Override
    public GpuSampler getSampler()
    {
        Texture gefunden = this.aufloesen();

        if (gefunden != null)
        {
            return gefunden.sampler();
        }

        return this.fallback == null ? null : this.fallback.getSampler();
    }

    /**
     * Die BBS-Textur hinter diesem Wrapper — oder {@code null}, wenn es keine gibt.
     *
     * <p>⚠️ {@code getError()} ist KEIN Fehlerfall zum Melden, sondern BBS' Platzhalter fuer
     * "nicht gefunden". Ihn durchzureichen wuerde dem Shader die magentafarbene Ersatztextur
     * unterschieben, statt auf den Fallback zu gehen.</p>
     */
    private Texture aufloesen()
    {
        Texture gefunden = BBSModClient.getTextures().getTexture(this.texture, GL11.GL_NEAREST, true);

        if (gefunden == null || gefunden == BBSModClient.getTextures().getError())
        {
            return null;
        }

        if (this.index >= 0 && gefunden.getParent() != null)
        {
            Texture bild = CollectionUtils.getSafe(gefunden.getParent().textures, this.index);

            if (bild != null)
            {
                return bild;
            }
        }

        return gefunden;
    }

    @Override
    public void close()
    {
        BBSModClient.getTextures().delete(this.texture);
    }
}
