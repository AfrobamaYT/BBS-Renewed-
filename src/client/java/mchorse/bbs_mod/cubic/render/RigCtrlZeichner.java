package mchorse.bbs_mod.cubic.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.cubic.ik.RigCtrl;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import io.netty.util.collection.IntObjectMap;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet das {@link RigCtrl}-Control-Rig wie Blender: die Original-Polylinien
 * als weiche Baender im Editor-Weltpass, und dieselben Linien als schmale
 * Kreuz-Quads in den Auswahlpuffer — geklickt wird am Strich, nicht in der
 * Ringflaeche, mit der Toleranz des Pick-Radius.
 */
public class RigCtrlZeichner
{
    /** Klickziel-Halbbreite bei 5m Abstand — skaliert screen-konstant mit der Distanz. */
    private static final float PICK_HALB = 0.011F;

    public static void weltPass(BufferBuilder wire, Matrix4f sicht, Vec3 kamera,
        IntObjectMap<IEntity> entities, IEntity entity, Replay replay,
        Iterable<RigCtrl.Eintrag> rig, String selectedBone, float a)
    {
        /* ⛔ EIN Cache fuers ganze Bild, nicht einer je Control. Ohne ihn legt
         * getBoneCompositeMatrix je Aufruf einen frischen an und baut die Armature-Matrizen
         * komplett neu — bei 220 Controls also 220 volle Aufbauten pro Bild. Gemessen:
         * 23 von 30 Render-Stapelabzuegen unter diesem Pfad, 3 FPS im Editor. */
        mchorse.bbs_mod.forms.renderers.utils.BildFrameCache.anlegen();

        try
        {
        for (RigCtrl.Eintrag e : rig)
        {
            if (!RigCtrl.sichtbar(e))
            {
                continue;
            }

            Matrix4f welt = e.anker().isEmpty()
                ? BaseFilmController.getGizmoAnchorCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F)
                : BaseFilmController.getBoneCompositeMatrix(entities, entity, replay, 0D, 0D, 0D, 0F, e.anker(), false);

            if (welt == null)
            {
                continue;
            }

            Matrix4f kombi = new Matrix4f(sicht).mul(new Matrix4f(welt).setTranslation(
                (float) (welt.m30() - kamera.x),
                (float) (welt.m31() - kamera.y),
                (float) (welt.m32() - kamera.z)));

            if (e.name().equals("Ctrl_Head") && mchorse.bbs_mod.graphics.Messpunkt.istAn())
            {
                /* Rufer im SCHLUESSEL: zwei Aufrufpfade je Frame trennen sich in zwei Zaehler. */
                mchorse.bbs_mod.graphics.Messpunkt.zaehle(
                    "rigctrl.von." + mchorse.bbs_mod.graphics.Messpunkt.rufer(3),
                    "welt=" + welt.m30() + "/" + welt.m31() + "/" + welt.m32() + " kamera=" + kamera);
            }
            float[] col = rgb(e.pick().equals(selectedBone) ? e.farbeA() : e.farbeN());

            /* `breite` IST Blenders `custom_shape_wire_width` — in Pixeln, so wie Blender
             * es meint. Der frueher hier stehende Faktor 0.11 machte daraus 0.19 px
             * (nachgerechnet), deshalb waren die Controls kaum zu sehen. */
            for (float[] linie : e.linien())
            {
                RigWidgets.kette(wire, kombi, linie, col, a, e.breite());
            }
        }
        }
        finally
        {
            /* ⛔ Ein liegengebliebener Cache liefert im naechsten Bild veraltete Matrizen. */
            mchorse.bbs_mod.forms.renderers.utils.BildFrameCache.frei();
        }
    }

    /** Modellraum-Pass in den Auswahlpuffer; vergibt je Control eine eigene Kennung. */
    public static void stencilPass(BufferBuilder builder, PoseStack stack, StencilMap stencilMap,
        Form form, BOBJArmature armature, Iterable<RigCtrl.Eintrag> rig)
    {
        Matrix4f pose = stack.last().pose();
        Vector3f p1 = new Vector3f();
        Vector3f p2 = new Vector3f();

        for (RigCtrl.Eintrag e : rig)
        {
            if (!RigCtrl.sichtbar(e))
            {
                continue;
            }

            Matrix4f mat = pose;

            if (!e.anker().isEmpty())
            {
                BOBJBone bone = armature.bones.get(e.anker());

                if (bone == null)
                {
                    continue;
                }

                mat = new Matrix4f(pose).mul(bone.mat);
            }

            int id = stencilMap.objectIndex;
            float[] col = {(id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F};

            /* Der Master liegt FLACH am Boden — seine duennen Linien-Quads kommen dort
             * nicht zuverlaessig im Puffer an (gemessen: deckung=0 trotz Builder-Lauf).
             * Ein knoechelhohes Kreuz durchs Zentrum macht ihn unverfehlbar klickbar. */
            if (e.anker().isEmpty())
            {
                p1.set(-0.55F, 0.10F, 0F);
                p2.set(0.55F, 0.10F, 0F);
                mat.transformPosition(p1);
                mat.transformPosition(p2);
                kreuzQuad(builder, p1, p2, col);
                p1.set(0F, 0.10F, -0.55F);
                p2.set(0F, 0.10F, 0.55F);
                mat.transformPosition(p1);
                mat.transformPosition(p2);
                kreuzQuad(builder, p1, p2, col);
            }

            for (float[] linie : e.linien())
            {
                for (int i = 0; i + 5 < linie.length; i += 3)
                {
                    mat.transformPosition(linie[i], linie[i + 1], linie[i + 2], p1);
                    mat.transformPosition(linie[i + 3], linie[i + 4], linie[i + 5], p2);
                    kreuzQuad(builder, p1, p2, col);
                }
            }

            stencilMap.addPicking(form, e.pick());
        }
    }

    /** Zwei senkrecht gekreuzte Quads laengs p1->p2 — aus jeder Blickrichtung Flaeche. */
    private static void kreuzQuad(BufferBuilder builder, Vector3f p1, Vector3f p2, float[] col)
    {
        Vector3f d = new Vector3f(p2).sub(p1);

        if (d.lengthSquared() < 1.0E-10F)
        {
            return;
        }

        /* Screen-konstant wie die sichtbaren Baender — ferne/kleine Ringe waren sonst
         * im Puffer unter einem Texel breit und unklickbar (User-Abnahme R2). Der
         * Stencil-Raum ist kamera-relativ: |mitte| IST die Distanz. */
        float halb = PICK_HALB * RigWidgets.screenSkala(new Vector3f(p1).add(p2).mul(0.5F).length());
        Vector3f hilf = Math.abs(d.y) < 0.9F * d.length() ? new Vector3f(0F, 1F, 0F) : new Vector3f(1F, 0F, 0F);
        Vector3f s1 = new Vector3f(d).cross(hilf).normalize().mul(halb);
        Vector3f s2 = new Vector3f(d).cross(s1).normalize().mul(halb);

        quad(builder, p1, p2, s1, col);
        quad(builder, p1, p2, s2, col);
    }

    private static void quad(BufferBuilder builder, Vector3f p1, Vector3f p2, Vector3f s, float[] col)
    {
        builder.addVertex(p1.x - s.x, p1.y - s.y, p1.z - s.z).setColor(col[0], col[1], col[2], 1F);
        builder.addVertex(p2.x - s.x, p2.y - s.y, p2.z - s.z).setColor(col[0], col[1], col[2], 1F);
        builder.addVertex(p2.x + s.x, p2.y + s.y, p2.z + s.z).setColor(col[0], col[1], col[2], 1F);
        builder.addVertex(p1.x - s.x, p1.y - s.y, p1.z - s.z).setColor(col[0], col[1], col[2], 1F);
        builder.addVertex(p2.x + s.x, p2.y + s.y, p2.z + s.z).setColor(col[0], col[1], col[2], 1F);
        builder.addVertex(p1.x + s.x, p1.y + s.y, p1.z + s.z).setColor(col[0], col[1], col[2], 1F);
    }

    /**
     * Die Control-Farbe so, wie Blender sie ZEICHNET — nicht wie sie gespeichert ist.
     *
     * <p>⛔ Gemessen an zwei Controls im Blender-Viewport: {@code hand_ik.L} ist als
     * (204,0,0) hinterlegt und wird als **(152,0,0)** gezeichnet, {@code upper_arm_fk.L}
     * als (96,199,53) hinterlegt und als **(46,147,0)** gezeichnet. Beide folgen der
     * sRGB→linear-Umrechnung (Vorhersage 154 bzw. 146 im Hauptkanal). BBS malte bisher
     * den GESPEICHERTEN Wert und war damit sichtbar greller als Blender.
     *
     * <p>Ausgeschlossen wurde vorher: das Farbmanagement (AgX gegen Standard liefert
     * BITGLEICHE Bilder) und ein globaler Anzeige-Transform (der Viewport-Hintergrund
     * kommt mit 63 an, das Theme sagt 61).
     */
    private static float[] rgb(int farbe)
    {
        return new float[] {linear((farbe >> 16 & 0xFF) / 255F),
            linear((farbe >> 8 & 0xFF) / 255F), linear((farbe & 0xFF) / 255F)};
    }

    private static float linear(float c)
    {
        return c <= 0.04045F ? c / 12.92F : (float) Math.pow((c + 0.055F) / 1.055F, 2.4);
    }
}
