package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.List;
import java.util.function.Function;

/**
 * Before 26.1 (replaces the shared Gegenstandsmodell, build.gradle nurAb261): an item submitted
 * the vanilla way, but with its quads written by BBS so a form's colour reaches them. As in the
 * shared version, the collector is wrapped and only submitItem is diverted; the quads are
 * written like 1.21.11's ItemRenderer.renderQuadList (tint by layer), glint like its
 * getFoilBuffer/getSpecialFoilBuffer. Everything else goes to vanilla unchanged.
 */
public final class Gegenstandsmodell
{
    private static final ItemStackRenderState ZUSTAND = new ItemStackRenderState();
    private static final int[] KEINE_TOENUNG = new int[0];
    private static final float GLANZ_ABSTAND = 0.0078125F;

    private Gegenstandsmodell()
    {}

    public static void einreichen(ItemStack stapel, ItemDisplayContext kontext, PoseStack pose,
        SubmitNodeCollector sammler, Function<VertexConsumer, VertexConsumer> umfaerbung,
        Level welt, int licht, int overlay)
    {
        if (!RenderSystem.isOnRenderThread())
        {
            throw new IllegalStateException("Gegenstandsmodell.einreichen laeuft nur im Renderthread");
        }

        if (stapel == null || stapel.isEmpty())
        {
            return;
        }

        Minecraft.getInstance().getItemModelResolver().updateForTopItem(ZUSTAND, stapel, kontext, welt, null, 0);
        ZUSTAND.submit(pose, new Umlenkung(sammler, umfaerbung), licht, overlay, 0);
    }

    private static void schreiben(List<BakedQuad> vierecke, PoseStack.Pose pose, VertexConsumer verbraucher, int[] toenung, int licht, int overlay)
    {
        for (BakedQuad viereck : vierecke)
        {
            int farbe = viereck.isTinted() && viereck.tintIndex() >= 0 && viereck.tintIndex() < toenung.length ? toenung[viereck.tintIndex()] : -1;

            verbraucher.putBulkData(pose, viereck, ARGB.red(farbe) / 255F, ARGB.green(farbe) / 255F, ARGB.blue(farbe) / 255F,
                ARGB.alpha(farbe) / 255F, licht, overlay);
        }
    }

    private static RenderType glanzEbene(RenderType ebene)
    {
        boolean durchsichtig = Minecraft.useShaderTransparency()
            && (ebene == Sheets.translucentItemSheet() || ebene == Sheets.translucentBlockItemSheet());

        return durchsichtig ? RenderTypes.glintTranslucent() : RenderTypes.glint();
    }

    private static PoseStack.Pose glanzPose(ItemDisplayContext kontext, PoseStack.Pose pose)
    {
        PoseStack.Pose kopie = pose.copy();

        if (kontext == ItemDisplayContext.GUI) MatrixUtil.mulComponentWise(kopie.pose(), 0.5F);
        else if (kontext.firstPerson()) MatrixUtil.mulComponentWise(kopie.pose(), 0.75F);

        return kopie;
    }

    private static final class Umlenkung implements SubmitNodeCollector
    {
        private final SubmitNodeCollector sammler;
        private final Function<VertexConsumer, VertexConsumer> umfaerbung;

        private Umlenkung(SubmitNodeCollector sammler, Function<VertexConsumer, VertexConsumer> umfaerbung)
        {
            this.sammler = sammler;
            this.umfaerbung = umfaerbung;
        }

        private VertexConsumer umfaerben(VertexConsumer verbraucher)
        {
            VertexConsumer umgefaerbt = this.umfaerbung == null ? null : this.umfaerbung.apply(verbraucher);

            return umgefaerbt == null ? verbraucher : umgefaerbt;
        }

        @Override
        public void submitItem(PoseStack pose, ItemDisplayContext kontext, int licht, int overlay, int umriss, int[] toenung,
            List<BakedQuad> vierecke, RenderType ebene, ItemStackRenderState.FoilType folie)
        {
            /* Umriss-Fall wie Vanilla: dort zeichnet der Item-Zeichner nur den Umriss. */
            if (umriss != 0 || vierecke.isEmpty())
            {
                this.sammler.submitItem(pose, kontext, licht, overlay, umriss, toenung, vierecke, ebene, folie);

                return;
            }

            this.sammler.submitCustomGeometry(pose, ebene, (p, verbraucher) -> schreiben(vierecke, p, this.umfaerben(verbraucher), toenung, licht, overlay));

            if (folie != ItemStackRenderState.FoilType.NONE)
            {
                boolean abziehbild = folie == ItemStackRenderState.FoilType.SPECIAL;

                this.sammler.submitCustomGeometry(pose, glanzEbene(ebene), (p, verbraucher) ->
                    schreiben(vierecke, p, abziehbild ? new SheetedDecalTextureGenerator(verbraucher, glanzPose(kontext, p), GLANZ_ABSTAND) : verbraucher,
                        KEINE_TOENUNG, licht, overlay));
            }
        }

        @Override
        public OrderedSubmitNodeCollector order(int reihenfolge)
        {
            return this.sammler.order(reihenfolge);
        }

        @Override
        public void submitShadow(PoseStack pose, float f, List<EntityRenderState.ShadowPiece> stuecke)
        {
            this.sammler.submitShadow(pose, f, stuecke);
        }

        @Override
        public void submitNameTag(PoseStack pose, Vec3 ort, int licht, Component text, boolean sichtbar, int hintergrund, double abstand, CameraRenderState kamera)
        {
            this.sammler.submitNameTag(pose, ort, licht, text, sichtbar, hintergrund, abstand, kamera);
        }

        @Override
        public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean schatten, Font.DisplayMode modus, int licht, int farbe, int hintergrund, int umriss)
        {
            this.sammler.submitText(pose, x, y, text, schatten, modus, licht, farbe, hintergrund, umriss);
        }

        @Override
        public void submitFlame(PoseStack pose, EntityRenderState zustand, Quaternionf drehung)
        {
            this.sammler.submitFlame(pose, zustand, drehung);
        }

        @Override
        public void submitLeash(PoseStack pose, EntityRenderState.LeashState leine)
        {
            this.sammler.submitLeash(pose, leine);
        }

        @Override
        public <S> void submitModel(Model<? super S> modell, S zustand, PoseStack pose, RenderType ebene, int licht, int overlay, int umriss,
            TextureAtlasSprite sprite, int farbe, ModelFeatureRenderer.CrumblingOverlay bruch)
        {
            this.sammler.submitModel(modell, zustand, pose, ebene, licht, overlay, umriss, sprite, farbe, bruch);
        }

        @Override
        public void submitModelPart(ModelPart teil, PoseStack pose, RenderType ebene, int licht, int overlay, TextureAtlasSprite sprite,
            boolean a, boolean b, int farbe, ModelFeatureRenderer.CrumblingOverlay bruch, int umriss)
        {
            this.sammler.submitModelPart(teil, pose, ebene, licht, overlay, sprite, a, b, farbe, bruch, umriss);
        }

        @Override
        public void submitBlock(PoseStack pose, BlockState block, int licht, int overlay, int umriss)
        {
            this.sammler.submitBlock(pose, block, licht, overlay, umriss);
        }

        @Override
        public void submitMovingBlock(PoseStack pose, MovingBlockRenderState zustand)
        {
            this.sammler.submitMovingBlock(pose, zustand);
        }

        @Override
        public void submitBlockModel(PoseStack pose, RenderType ebene, BlockStateModel modell, float r, float g, float b, int licht, int overlay, int umriss)
        {
            this.sammler.submitBlockModel(pose, ebene, modell, r, g, b, licht, overlay, umriss);
        }

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType ebene, SubmitNodeCollector.CustomGeometryRenderer zeichner)
        {
            this.sammler.submitCustomGeometry(pose, ebene, zeichner);
        }

        @Override
        public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer zeichner)
        {
            this.sammler.submitParticleGroup(zeichner);
        }
    }
}
