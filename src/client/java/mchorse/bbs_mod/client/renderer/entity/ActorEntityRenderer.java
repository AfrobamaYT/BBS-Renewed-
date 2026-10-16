package mchorse.bbs_mod.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mchorse.bbs_mod.cubic.render.vanilla.ArmorRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;

/**
 * ⚠️ 26.2 — ZWEI PHASEN STATT EINER. Ein EntityRenderer bekommt die Entity nicht mehr beim
 * Zeichnen, sondern EINMAL PRO BILD beim Einsammeln: {@code extractRenderState} zieht alles,
 * was das Bild braucht, aus der Entity in einen Zustand; {@code submit} sieht nur noch diesen
 * Zustand und reicht daraus Zeichenauftraege ein. Deshalb hat {@code EntityRenderer} jetzt
 * ZWEI Typargumente (Entity, Zustand).
 *
 * <p>Wer im submit-Zweig doch wieder auf die Entity zugreift, liest sie zu einem anderen
 * Zeitpunkt als der Rest des Bildes — genau das soll die Umstellung verhindern. Alles, was
 * hier gebraucht wird, steht darum in {@link ActorRenderState}.
 */
public class ActorEntityRenderer extends EntityRenderer<ActorEntity, ActorEntityRenderer.ActorRenderState>
{
    public static ArmorRenderer armorRenderer;

    public ActorEntityRenderer(EntityRendererProvider.Context ctx)
    {
        super(ctx);

        /* ⚠️ HumanoidArmorModel ist ersatzlos weg — die Ruestungsmodelle SIND jetzt normale
         * HumanoidModel, und ihre Ebenen liegen als Vierersatz je Ausruestungsplatz in
         * ModelLayers.PLAYER_ARMOR (head/chest/legs/feet) statt als PLAYER_INNER_ARMOR /
         * PLAYER_OUTER_ARMOR.
         *
         * ⚠️ VERHALTENSAENDERUNG: BBS' ArmorRenderer kennt weiterhin nur ZWEI Modelle
         * (innen/aussen, Auswahl ueber usesInnerModel(slot) = nur LEGS ist innen). Vanilla
         * hat jetzt VIER. Wir bilden ab: innen = legs(), aussen = chest(). Damit zeichnen
         * Helm und Stiefel ueber das BRUSTMODELL statt ueber ihr eigenes — bis 1.21.1 taten
         * sie das ebenfalls (beide hingen am selben OUTER-Modell), der Unterschied entsteht
         * nur, falls Vanilla head/feet inzwischen anders aufblaest. Sichtpruefung offen. */
        /* ⚠️ Dritter Parameter ist jetzt der KONTEXT selbst, nicht mehr ein ModelManager:
         * ctx.getModelManager() gibt es nicht mehr, und der EquipmentAssetManager, den
         * ArmorRenderer inzwischen zusaetzlich braucht, haengt ausschliesslich am Kontext. */
        armorRenderer = new ArmorRenderer(
            new HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER_ARMOR.legs())),
            new HumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER_ARMOR.chest())),
            ctx
        );

        this.shadowRadius = 0.5F;
    }

    /**
     * Sammelt den Zustand fuer EINEN Schattenwurf eines Modellblocks — mit abweichendem
     * Radius und abweichender Staerke.
     *
     * <p>⚠️ In 26.2 ist der Schatten kein Zeichenaufruf mehr, sondern ein Auftrag: die
     * Bodenstuecke darunter werden beim EINSAMMELN ermittelt ({@code
     * EntityRenderer.extractShadow}, am Bytecode geprueft), und zwar aus {@link
     * #getShadowRadius} und {@link #getShadowStrength} — also aus den Renderer-Feldern.
     * Radius und Deckkraft sind damit Sammel-Eingaben, keine Zeichen-Eingaben.
     * {@code ModelBlockEntityRenderer.renderShadow} waehlt beide aber pro Aufruf
     * (BaseFilmController gibt eigene mit), deshalb werden sie hier fuer die Dauer EINER
     * Sammlung gesetzt und danach zurueckgestellt.
     *
     * <p>⚠️ JEDER Aufruf bekommt einen EIGENEN Zustand, absichtlich. Der eingereichte
     * Auftrag haelt die Stueckliste als Referenz und zeichnet sie SPAETER; ein
     * wiederverwendeter Zustand wuerde jeden zuvor eingereichten Schatten nachtraeglich auf
     * die Bodenstuecke des letzten umbiegen.
     */
    public ActorRenderState zustandFuerSchatten(ActorEntity entity, float teilTick, float radius, float staerke)
    {
        float altRadius = this.shadowRadius;
        float altStaerke = this.shadowStrength;

        this.shadowRadius = radius;
        this.shadowStrength = staerke;

        try
        {
            return this.createRenderState(entity, teilTick);
        }
        finally
        {
            this.shadowRadius = altRadius;
            this.shadowStrength = altStaerke;
        }
    }

    @Override
    public ActorRenderState createRenderState()
    {
        return new ActorRenderState();
    }

    @Override
    public void extractRenderState(ActorEntity entity, ActorRenderState zustand, float teilTick)
    {
        super.extractRenderState(entity, zustand, teilTick);

        zustand.form = entity.getForm();
        zustand.bbsEntity = entity.getEntity();
        zustand.koerperDrehung = Mth.rotLerp(teilTick, entity.yBodyRotO, entity.yBodyRot);
        zustand.teilTick = teilTick;
        mchorse.bbs_mod.film.replays.ReplayScaleKeyframes.sample(zustand.bbsEntity, teilTick, zustand.rootScale);
        zustand.schlaeft = entity.hasPose(Pose.SLEEPING);
        zustand.todesZeit = entity.deathTime;

        /* ⚠️ LivingEntityRenderer.getOverlayCoords nimmt in 26.2 einen LivingEntityRenderState
         * (nicht mehr die Entity) und liest daraus GENAU EIN Feld: hasRedOverlay. Unser
         * Zustand ist keiner — statt einen kuenstlich danebenzustellen, packen wir dasselbe
         * Ergebnis direkt. Das ist Zeile fuer Zeile das, was getOverlayCoords(entity, 0F) bis
         * 1.21.1 tat: kein Weiss-Anteil, roter Anteil bei Schaden oder Tod. */
        zustand.ueberlagerung = OverlayTexture.pack(0F, entity.hurtTime > 0 || entity.deathTime > 0);
    }

    @Override
    public void submit(ActorRenderState zustand, PoseStack matrices, SubmitNodeCollector sammler, CameraRenderState kamera)
    {
        matrices.pushPose();

        try
        {
            this.setupTransforms(zustand, matrices);

            /* 26.2: das Paar enableBlend()/enableDepthTest() und seine Gegenstuecke hinter dem
             * Draw sind ersatzlos entfallen. Sie richteten nur den globalen Vorgabezustand fuer
             * das ein, was FormUtilsClient.render() zeichnet - und das bringt seinen Zustand
             * selbst mit: Item-, Block- und Mob-Formen ueber die Pipeline ihres RenderType
             * (RenderType#pipeline()), BBS' eigene Modelle ueber ModelVAOPipeline (dort Blend,
             * Tiefe und Cull fest verbaut, siehe ModelVAORenderer#render). Einen globalen
             * Schalter, den man setzen oder zuruecksetzen koennte, gibt es nicht mehr. */

            /* ⛔ OHNE DAS mitSammler() STIRBT DAS SPIEL (2026-08-10, gemessen, halbe nacht).
             * Diese Methode laeuft in VANILLAS entity-weg (LevelRenderer.submitEntities), nicht
             * im film-weg von BBS. Der FormSammler wird aber nur in den LevelRenderEvents
             * angemeldet (BBSModClient) — hier war also keiner aktiv. ModelFormRenderer.
             * renderArmor greift per FormSammler.pflicht() danach und warf
             *
             *     IllegalStateException: Kein Sammler angemeldet.
             *
             * Der uebergebene `sammler` ist genau der richtige; er war nur nie angemeldet.
             * Betroffen waren ausschliesslich Actor-Replays, weil nur die Ruestung zeichnen. */
            FormSammler.mitSammler(sammler, () ->
                FormUtilsClient.render(zustand.form, new FormRenderingContext()
                    .set(FormRenderType.ENTITY, zustand.bbsEntity, matrices, zustand.lightCoords, zustand.ueberlagerung, zustand.teilTick)
                    .camera(Minecraft.getInstance().gameRenderer.mainCamera())));
        }
        finally
        {
            /* ⛔ DAS finally IST DER ZWEITE HALBE FEHLER. Vorher stand popPose() nackt hier
             * unten: wirft irgendetwas dazwischen, wird es uebersprungen, der push aus Z132
             * bleibt offen — und vanilla stirbt ein BILD SPAETER an checkPoseStack, mit einem
             * stapelabzug, in dem weder bbs noch bbstools vorkommt. Genau so gemessen:
             * lastIndex=1, und die matrix darin zeigte exakt auf die position des actors. */
            matrices.popPose();
        }

        /* Zeichnet den Namensschild-Auftrag; hiess bis 1.21.1 super.render(...). */
        super.submit(zustand, matrices, sammler, kamera);
    }

    protected boolean isVisible(ActorEntity entity)
    {
        return !entity.isInvisible();
    }

    /**
     * ⚠️ Nimmt den Zustand statt der Entity — sonst liefe die Drehung aus einem anderen
     * Zeitpunkt als der Rest des Bildes.
     */
    protected void setupTransforms(ActorRenderState zustand, PoseStack matrices)
    {
        if (!zustand.schlaeft)
        {
            matrices.rotate(Axis.YP.rotationDegrees(-zustand.koerperDrehung));
        }

        if (zustand.todesZeit > 0)
        {
            float deathAngle = (zustand.todesZeit + zustand.teilTick - 1F) / 20F * 1.6F;

            matrices.rotate(Axis.ZP.rotationDegrees(Math.min(Mth.sqrt(deathAngle), 1F) * 90F));
        }
        matrices.scale(zustand.rootScale.x, zustand.rootScale.y, zustand.rootScale.z);
    }

    /* ⚠️ getTextureLocation(ActorEntity) ist ersatzlos entfallen: EntityRenderer hat in 26.2
     * ueberhaupt keine Textur mehr, nur LivingEntityRenderer kennt eine — und dort nimmt sie
     * den Zustand, nicht die Entity. Die Methode war hier ohnehin tot: dieser Renderer
     * zeichnet ausschliesslich BBS-Formen, die ihre Texturen selbst mitbringen, und niemand
     * im Baum ruft sie auf (geprueft). */

    /**
     * Alles, was {@link #submit} braucht, EINMAL pro Bild aus der Entity gezogen.
     *
     * <p>⚠️ Diese Felder fuellt NIEMAND ausser {@link #extractRenderState} — die Basisklasse
     * kennt sie nicht. Der Bildgraph verwendet Zustandsobjekte wieder, ein hier nicht
     * gesetztes Feld traegt also den Wert des VORHERIGEN Bildes.
     */
    public static class ActorRenderState extends EntityRenderState
    {
        public final org.joml.Vector3f rootScale = new org.joml.Vector3f(1F);
        /** Die BBS-Form, die statt eines Vanilla-Modells gezeichnet wird. */
        public Form form;

        /** BBS' eigene Entity-Sicht, die der Form-Zeichenweg als Kontext braucht. */
        public IEntity bbsEntity;

        /** Interpolierte Koerperdrehung (Grad), frueher direkt in render() gerechnet. */
        public float koerperDrehung;

        /** Teil-Tick des Bildes; die Form-Animation braucht ihn. */
        public float teilTick;

        /** Gepackte Ueberlagerung (Schaden/Tod), siehe Kommentar in extractRenderState. */
        public int ueberlagerung;

        /** Ob die Entity liegt — dann faellt die Koerperdrehung aus. */
        public boolean schlaeft;

        /** Todes-Tickzaehler fuer das Umkippen. */
        public float todesZeit;
    }
}
