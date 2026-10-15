package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import io.netty.util.collection.IntObjectMap;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A rig's panel furniture next to its bone widgets: the fields, axis lines and labels Blender
 * shows around Moggity's face controls ("BRAUE L", "KIEFER"). tools/rig-extrakt-blender.py
 * lets Blender turn those curves and texts into triangles (bevel and glyphs included), so they
 * are filled here in their material colour, bone-local to the bone they hang on.
 */
public final class RigDeko
{
    /** Either filled triangles (labels) or polylines drawn like the widgets (fields, axes). */
    /** Labels also carry their centre and the plate behind them (4 corners), bone-local. */
    public record Teil(String anker, int farbe, float[] dreiecke, float[][] linien, float breite,
        float[] mitte, float[] platte) {}

    /** Smallest on-screen height of a label plate, in pixels; Blender's 0.046 text was
     *  a few pixels tall in the film viewport and unreadable (owner 2026-10-14). */
    private static final float PLATTE_MIN_PX = 26F;
    private static final int PLATTE_FARBE = 0xA0101418;
    private static final float PLATTE_MAX_SKALA = 2F;

    /** Film viewport height in window pixels, set each frame by ModelIKDebug. */
    private static float feldHoehe = 700F;

    public static void setFeldHoehe(float hoehe)
    {
        feldHoehe = hoehe > 0F ? hoehe : 700F;
    }

    /* Lazy (hotswap: new statics are null). Empty list = the model's rig.json has no panel. */
    private static Map<String, List<Teil>> proModell;

    private RigDeko()
    {}

    public static void leeren()
    {
        proModell = null;
    }

    public static List<Teil> fuer(String modellId)
    {
        if (modellId == null || modellId.isEmpty())
        {
            return Collections.emptyList();
        }

        if (proModell == null)
        {
            proModell = new HashMap<>();
        }

        return proModell.computeIfAbsent(modellId, id ->
        {
            try (InputStream rein = mchorse.bbs_mod.BBSMod.getProvider()
                .getAsset(mchorse.bbs_mod.resources.Link.assets("models/" + id + "/rig.json")))
            {
                return parse(new String(rein.readAllBytes(), StandardCharsets.UTF_8));
            }
            catch (Exception e)
            {
                return Collections.emptyList();
            }
        });
    }

    private static List<Teil> parse(String json)
    {
        MapType wurzel = (MapType) DataToString.fromString(json);
        ListType deko = wurzel.getList("deko");
        List<Teil> teile = new ArrayList<>();

        for (int i = 0; deko != null && i < deko.size(); i++)
        {
            MapType t = deko.getMap(i);
            ListType linien = t.getList("linien");
            float[][] zuege = new float[linien == null ? 0 : linien.size()][];

            for (int l = 0; l < zuege.length; l++)
            {
                zuege[l] = zahlen((ListType) linien.get(l));
            }

            teile.add(new Teil(t.getString("anker"), Integer.parseInt(t.getString("farbe"), 16),
                zahlen(t.getList("dreiecke")), zuege, t.getFloat("breite", 1F),
                t.has("mitte") ? zahlen(t.getList("mitte")) : null,
                t.has("platte") ? zahlen(t.getList("platte")) : null));
        }

        return Collections.unmodifiableList(teile);
    }

    private static float[] zahlen(ListType liste)
    {
        float[] p = new float[liste == null ? 0 : liste.size()];

        for (int v = 0; v < p.length; v++)
        {
            p[v] = liste.getFloat(v);
        }

        return p;
    }

    /**
     * A label scaled about its centre to at least {@link #PLATTE_MIN_PX} on screen, on a dark
     * plate — the Minecraft name-tag answer to text over a bright, busy world.
     */
    private static void beschriftung(BufferBuilder wire, Matrix4f kombi, Teil t, int farbe, Vector3f p)
    {
        float[] d = t.dreiecke();
        Vector3f mitte = null;
        float s = 1F;

        if (t.mitte() != null && t.platte() != null)
        {
            mitte = kombi.transformPosition(t.mitte()[0], t.mitte()[1], t.mitte()[2], new Vector3f());

            Vector3f unten = kombi.transformPosition(t.platte()[0], t.platte()[1], t.platte()[2], new Vector3f());
            Vector3f oben = kombi.transformPosition(t.platte()[9], t.platte()[10], t.platte()[11], new Vector3f());
            /* On-screen height through the projection: the line-width helper (halb) carries the
             * video-to-viewport scale for strokes and gave 5 px where the picture had 24. */
            Matrix4f proj = mchorse.bbs_mod.graphics.Projektion.lesen();
            org.joml.Vector4f u = proj.transform(new org.joml.Vector4f(unten, 1F));
            org.joml.Vector4f o = proj.transform(new org.joml.Vector4f(oben, 1F));

            if (u.w <= 1E-4F || o.w <= 1E-4F)
            {
                return;
            }

            float px = (float) Math.hypot(u.x / u.w - o.x / o.w, u.y / u.w - o.y / o.w) * 0.5F * feldHoehe;

            /* Grow at most twice Blender's size (at 4x 'MUNDWINKEL L' and 'BRAUE L' overlapped
             * eight blocks out); beyond that the label shrinks with the panel. Never left out:
             * hiding far labels read as "der Text verschwindet" (owner 2026-10-14). */
            s = Math.min(PLATTE_MAX_SKALA, Math.max(1F, PLATTE_MIN_PX / Math.max(px, 1E-3F)));

            Vector3f[] e = new Vector3f[4];

            for (int i = 0; i < 4; i++)
            {
                e[i] = groesser(kombi.transformPosition(t.platte()[i * 3], t.platte()[i * 3 + 1], t.platte()[i * 3 + 2], new Vector3f()), mitte, s);
            }

            for (int i : new int[] {0, 1, 2, 0, 2, 3})
            {
                wire.addVertex(e[i].x, e[i].y, e[i].z).setColor(PLATTE_FARBE);
            }
        }

        for (int i = 0; i + 2 < d.length; i += 3)
        {
            kombi.transformPosition(d[i], d[i + 1], d[i + 2], p);

            if (mitte != null)
            {
                groesser(p, mitte, s);
            }

            wire.addVertex(p.x, p.y, p.z).setColor(farbe);
        }
    }

    private static Vector3f groesser(Vector3f p, Vector3f mitte, float s)
    {
        return p.sub(mitte).mul(s).add(mitte);
    }

    /** Into the same camera-relative world pass as the widgets (RigCtrlZeichner#weltPass). */
    public static void weltPass(BufferBuilder wire, Matrix4f sicht, Vec3 kamera,
        IntObjectMap<IEntity> entities, IEntity entity, Replay replay, String modellId,
        float a, float transition)
    {
        List<Teil> teile = fuer(modellId);

        if (teile.isEmpty())
        {
            return;
        }

        Map<String, Matrix4f> anker = new HashMap<>();
        Vector3f p = new Vector3f();

        mchorse.bbs_mod.forms.renderers.utils.BildFrameCache.anlegen();

        try
        {
            for (Teil t : teile)
            {
                Matrix4f kombi = anker.computeIfAbsent(t.anker(), name ->
                {
                    Matrix4f welt = BaseFilmController.getBoneCompositeMatrix(entities, entity, replay,
                        0D, 0D, 0D, transition, name, true);

                    return welt == null ? null : new Matrix4f(sicht).mul(new Matrix4f(welt).setTranslation(
                        (float) (welt.m30() - kamera.x),
                        (float) (welt.m31() - kamera.y),
                        (float) (welt.m32() - kamera.z)));
                });

                if (kombi == null)
                {
                    continue;
                }

                /* Opaque like the solid objects they are in Blender; the control opacity
                 * setting (0.5 for the owner) made the labels unreadable. */
                int farbe = (a > 0F ? 0xFF000000 : 0) | (t.farbe() & 0xFFFFFF);
                float[] col = {(t.farbe() >> 16 & 0xFF) / 255F, (t.farbe() >> 8 & 0xFF) / 255F, (t.farbe() & 0xFF) / 255F};

                float nah = RigCtrlZeichner.nahFaktor(kombi);
                float breite = t.breite() * Math.max(1F, RigCtrlZeichner.BREITE * nah);
                float halo = RigCtrlZeichner.HALO_PX * nah;

                for (float[] linie : t.linien())
                {
                    if (halo >= 1F)
                    {
                        RigWidgets.kette(wire, kombi, linie, RigCtrlZeichner.halo(), RigCtrlZeichner.HALO_ALPHA, breite + halo);
                    }

                    RigWidgets.kette(wire, kombi, linie, col, 1F, breite);
                }

                beschriftung(wire, kombi, t, farbe, p);
            }
        }
        finally
        {
            mchorse.bbs_mod.forms.renderers.utils.BildFrameCache.frei();
        }
    }
}
