package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
//? if >=26.3 {
//?} else {
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
//?}
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.MatrixUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
//? if >=26.3 {
//?} else {
import net.minecraft.client.renderer.rendertype.OutputTarget;
//?}
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
//? if >=26.3 {
/*import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.client.renderer.texture.UvMapping;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Zeichnet EINEN Gegenstand fuer sich allein -- der Ersatz fuer {@code renderStatic}.
 *
 * <p>Das Gegenstueck zu {@link Blockmodell}, mit demselben Problem und derselben Antwort.
 *
 * <h2>Was 26.2 daraus gemacht hat</h2>
 *
 * <p>{@code Minecraft.getItemRenderer()} und {@code ItemRenderer.renderStatic} sind weg. Einen
 * Gegenstand anzuzeigen ist jetzt zweistufig:
 *
 * <ol>
 *   <li>{@code ItemModelResolver.updateForTopItem(zustand, stapel, kontext, welt, besitzer,
 *       wuerfel)} loest den Stapel in Ebenen auf (Vierecke, Anzeigetransformation,
 *       Toenungsschichten, Glanzart, Sonderzeichner),</li>
 *   <li>{@code zustand.submit(pose, sammler, licht, overlay, umriss)} reicht ihn beim
 *       Zeichenweg ein; gezeichnet wird spaeter.</li>
 * </ol>
 *
 * <h2>⛔ Warum nicht einfach {@code zustand.submit(...)} mit dem echten Sammler</h2>
 *
 * <p>Weil BBS-Formen eine eigene Farbe tragen und {@code submit} keinen
 * {@code VertexConsumer} herausgibt. Ein gehaltener Gegenstand wuerde erscheinen, seine
 * Faerbung waere LAUTLOS weg: es uebersetzt, es zeichnet, und es ist falsch.
 *
 * <p>{@code submitCustomGeometry} ist die einzige Stelle, an der der Zeichenweg einem den
 * Verbraucher reicht. Um dorthin zu kommen, braucht es die Vierecke der einzelnen Ebenen --
 * und die sind in {@code ItemStackRenderState} privat, ohne Zugriffsweg.
 *
 * <h2>Der Weg dorthin: den Sammler abfangen, nicht den Zustand aufbrechen</h2>
 *
 * <p>{@code zustand.submit(...)} ruft je Ebene {@code sammler.submitItem(...)} und reicht dabei
 * genau das heraus, was sonst privat bleibt: Vierecke, Toenungsschichten, Glanzart. Also
 * bekommt {@code submit} hier einen UMGELENKTEN Sammler ({@link Umlenkung}), der
 * {@code submitItem} abfaengt und stattdessen {@code submitCustomGeometry} benutzt -- mit der
 * Umfaerbung dazwischen. Alles andere reicht er unveraendert weiter.
 *
 * <p>Das ist der Grund, warum hier kein Mixin-Zugriff noetig ist und warum die
 * Anzeigetransformationen stimmen: Vanilla wendet sie selbst an, bevor es {@code submitItem}
 * ruft.
 *
 * <h2>Was hier Vanilla-Logik ist und darum nicht "verbessert" werden darf</h2>
 *
 * <p>{@link #schreiben} und {@link #farbe} sind Zeile fuer Zeile
 * {@code ItemFeatureRenderer.prepareMainSubmit} + {@code getLayerColorSafe}, der Glanz ist
 * {@code prepareFoilSubmit} + {@code getFoilBuffer} + {@code computeFoilDecalPose}. Wer hier
 * etwas glaettet, aendert die Farbe getoenter Gegenstaende (Leder, Traenke, Karten) oder
 * verliert den Verzauberungsglanz -- und beides faellt erst im fertigen Film auf.
 *
 * <h2>⚠️ Fallen</h2>
 *
 * <ul>
 *   <li><b>Nur im Renderthread.</b> Zustand, Viereck-Vorlage und Aufloeser sind geteilt und
 *       wiederverwendet. {@link #einreichen} prueft das und wirft, statt Unsinn zu zeichnen.</li>
 *   <li><b>Die Umfaerbung wird BEIM EINREICHEN uebergeben, nicht beim Zeichnen geholt.</b>
 *       Siehe {@link #einreichen} -- daran haengt, ob die Faerbung ueberhaupt ankommt.</li>
 *   <li><b>Sonderzeichner (Schild, Trident, Kopf, Truhe) zeichnen sich selbst</b> und nehmen
 *       die Form-Farbe NICHT an -- sie gehen nicht durch unseren Verbraucher. Ohne sie waeren
 *       diese Gegenstaende unsichtbar statt ungefaerbt, also ist Zeichnen die bessere
 *       Haelfte. Dieselbe Grenze wie bei {@link Blockmodell}.</li>
 * </ul>
 */
public final class Gegenstandsmodell
{
    /**
     * Der geteilte Aufloesungs-Zustand. Vanilla haelt je Zeichner einen; BBS zeichnet
     * Gegenstaende nur im Renderthread und nur nacheinander, darum reicht einer -- aber
     * genau deshalb muessen die Vierecklisten kopiert werden, bevor sie in einen Auftrag
     * wandern (siehe {@link Umlenkung#nachEbene}).
     */
    private static final ItemStackRenderState ZUSTAND = new ItemStackRenderState();

    private static final QuadInstance VIERECK = new QuadInstance();

    /** Leere Toenungsschichten: {@link #farbe} liefert damit immer Weiss. */
//? if >=26.3 {

//?} else {
    private static final int[] KEINE_TOENUNG = new int[0];
//?}

    /**
     * Der Abstand, mit dem der Glanz vor die Oberflaeche gelegt wird -- 1/128, wortgleich aus
     * {@code ItemFeatureRenderer.getFoilBuffer}. Kleiner und er verschwindet im Z-Kampf,
     * groesser und er schwebt sichtbar ab.
     */
//? if >=26.3 {

//?} else {
    private static final float GLANZ_ABSTAND = 0.0078125F;
//?}

    private Gegenstandsmodell()
    {}

    /**
     * Reicht den Gegenstand mit Umfaerbung ein.
     *
     * <p>⚠️ DIE UMFAERBUNG KOMMT ALS PARAMETER, nicht aus
     * {@code CustomVertexConsumerProvider.setSubstitute} -- und das ist kein Geschmack.
     * {@code submitCustomGeometry} legt den Rumpf in einen Auftrag ({@code CustomFeatureRenderer.Submit},
     * ein record mit dem Rueckruf darin) und ruft ihn erst beim Bauen des Bildes auf. Zu
     * diesem Zeitpunkt hat die Aufrufstelle ihr {@code setSubstitute(null)} laengst gesetzt:
     * eine dort abgeholte Umfaerbung waere null, und die Faerbung waere lautlos weg. Als
     * Parameter ist sie beim Einreichen festgehalten und ueberlebt bis zum Zeichnen.
     *
     * @param umfaerbung darf {@code null} sein -- dann wird ungefaerbt gezeichnet.
     * @param welt       die Welt des Traegers; darf {@code null} sein, wie beim alten
     *                   {@code renderStatic}.
     */
    public static void einreichen(ItemStack stapel, ItemDisplayContext kontext, PoseStack pose,
        SubmitNodeCollector sammler, Function<VertexConsumer, VertexConsumer> umfaerbung,
        Level welt, int licht, int overlay)
    {
        if (!RenderSystem.isOnRenderThread())
        {
            throw new IllegalStateException(
                "Gegenstandsmodell.einreichen laeuft nur im Renderthread -- Zustand und "
                + "Viereck-Vorlage sind geteilt und wiederverwendet.");
        }

        if (stapel == null || stapel.isEmpty())
        {
            return;
        }

        /* Der Besitzer (frueher der LivingEntity-Parameter von renderStatic) bleibt null: BBS
         * zeichnet den Gegenstand an einem Modellknochen, nicht an einer Entity, und gab dort
         * schon vor dem Port null. Er steuert nur besitzerabhaengige Modellwahlen (Kompass,
         * Uhr) -- die fallen damit auf ihren Vorgabezustand zurueck, genau wie vorher.
         *
         * Der letzte Parameter ist der Wuerfel-Startwert fuer Modellvarianten; 0 wie vorher. */
        Minecraft.getInstance().getItemModelResolver()
            .updateForTopItem(ZUSTAND, stapel, kontext, welt, null, 0);

        /* ⚠️ Der Linkshaender-Schalter des alten renderStatic entfaellt: 26.2 leitet ihn aus
         * dem Anzeigekontext ab (ItemDisplayContext.leftHand()). Fuer die beiden Kontexte, die
         * BBS benutzt -- THIRD_PERSON_LEFT_HAND und THIRD_PERSON_RIGHT_HAND -- ergibt das
         * denselben Wert wie der frueher von Hand gereichte Vergleich. */
        ZUSTAND.submit(pose, new Umlenkung(sammler, umfaerbung), licht, overlay, 0);
    }

    /**
     * Vierecke einer Zeichenebene in den Verbraucher schreiben.
     *
     * <p>1:1 {@code ItemFeatureRenderer.prepareMainSubmit}: Licht und Overlay einmal, Farbe je
     * Viereck aus der Toenungsschicht.
     */
    //? if >=26.3 {
    /*private static void schreiben(List<BakedQuad> vierecke, PoseStack.Pose pose, VertexConsumer verbraucher, int[] toenung, int licht, int overlay, PoseStack.Pose glanzPose)
    *///?} else {
    private static void schreiben(List<BakedQuad> vierecke, PoseStack.Pose pose, VertexConsumer verbraucher, int[] toenung, int licht, int overlay)
    //?}
    {
        VIERECK.setLightCoords(licht);
        VIERECK.setOverlayCoords(overlay);

        for (int i = 0; i < vierecke.size(); i++)
        {
            BakedQuad viereck = vierecke.get(i);

            /* -1 ist weiss: die Form-Farbe kommt NICHT hierher, sondern aus dem umgefaerbten
             * Verbraucher. So multipliziert sie sich ueber die Toenung, genau wie vor dem
             * Port. */
            VIERECK.setColor(farbe(toenung, viereck.materialInfo()));

            //? if >=26.3 {
            /*if (glanzPose == null)
            {
                verbraucher.putBakedQuad(pose, viereck, VIERECK);
            }
            else
            {
                verbraucher.putBakedQuadWithGlint(pose, viereck, VIERECK, glanzPose);
            }
            *///?} else {
            verbraucher.putBakedQuad(pose, viereck, VIERECK);
            //?}
        }
    }

    /** 1:1 {@code ItemFeatureRenderer.getLayerColorSafe}. -1 heisst ungetoent, also weiss. */
    private static int farbe(int[] toenung, mchorse.bbs_mod.compat.alt.MaterialInfo material)
    {
        if (!material.isTinted())
        {
            return -1;
        }

        int index = material.tintIndex();

        return index >= 0 && index < toenung.length ? toenung[index] : -1;
    }

    /**
     * Der abgefangene Sammler: leitet alles weiter, nur {@code submitItem} nicht.
     *
     * <p>Die Sammler-Schnittstelle ist breit, aber nur eine Methode ist hier interessant.
     * Alles andere durchzureichen ist Pflicht und kein Beiwerk: der Sonderzeichner eines
     * Schildes oder Kopfes reicht seine Modellteile ueber {@code submitModel} ein, und wer
     * die verschluckt, macht den Gegenstand unsichtbar.
     */
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
            if (this.umfaerbung == null)
            {
                return verbraucher;
            }

            VertexConsumer umgefaerbt = this.umfaerbung.apply(verbraucher);

            return umgefaerbt == null ? verbraucher : umgefaerbt;
        }

        @Override
        //? if >=26.3 {
        /*public void submitItem(PoseStack pose, ItemDisplayContext kontext, int licht, int overlay, int umriss, int[] toenung, ItemQuads vierecke, ItemStackRenderState.FoilType folie)
        *///?} else {
        public void submitItem(PoseStack pose, ItemDisplayContext kontext, int licht, int overlay, int umriss, int[] toenung, List<BakedQuad> vierecke, ItemStackRenderState.FoilType folie)
        //?}
        {
            /* Umriss-Fall unveraendert an Vanilla: dort zeichnet ItemFeatureRenderer NUR den
             * Umriss und keine Hauptgeometrie (prepareSubmit), und der traegt seine eigene
             * Farbe -- eine Umfaerbung waere dort falsch. BBS reicht heute 0 herein, der Zweig
             * ist also Vorsorge, kein laufender Weg. */
            if (umriss != 0 || vierecke.isEmpty())
            {
                this.sammler.submitItem(pose, kontext, licht, overlay, umriss, toenung, vierecke, folie);

                return;
            }

            //? if >=26.3 {
            /*for (Map.Entry<RenderType, List<BakedQuad>> eintrag : nachEbene(vierecke.all(), folie).entrySet())
            *///?} else {
            for (Map.Entry<RenderType, List<BakedQuad>> eintrag : nachEbene(vierecke, null).entrySet())
            //?}
            {
                List<BakedQuad> gruppe = eintrag.getValue();

                this.sammler.submitCustomGeometry(pose, eintrag.getKey(), (p, verbraucher) ->
                    //? if >=26.3 {
                    /*schreiben(gruppe, p, this.umfaerben(verbraucher), toenung, licht, overlay,
                        folie == ItemStackRenderState.FoilType.SPECIAL ? glanzPose(kontext, p) : null));
                    *///?} else {
                    schreiben(gruppe, p, this.umfaerben(verbraucher), toenung, licht, overlay));
                    //?}
            }

            //? if >=26.3 {
            //?} else {
            this.glanz(pose, kontext, licht, overlay, vierecke, folie);
        }

        /***
         * Der Verzauberungsglanz -- 1:1 {@code ItemFeatureRenderer.prepareFoilSubmit}.
         *
         * <p>⚠️ Er laeuft ABSICHTLICH ohne Umfaerbung: der Glanz ist eine eigene Zeichenebene
         * mit eigener Farbe, und Vanilla faerbt ihn ebenfalls nicht mit der Toenung des
         * Gegenstands.
         *
         * <p>⚠️ Abweichung von Vanilla, die eine Verbesserung ist und darum hier steht:
         * Vanilla setzt fuer den Glanzdurchgang weder Licht noch Overlay noch Farbe, sondern
         * erbt sie vom zuletzt gezeichneten Haupt-Gegenstand des Bildes (die
         * {@code QuadInstance} des Zeichners bleibt zwischen den beiden Durchgaengen stehen).
         * Diese Reihenfolge gibt es hier nicht, also werden sie ausdruecklich gesetzt -- Licht
         * und Overlay des eigenen Gegenstands, Farbe weiss.
         **/
        private void glanz(PoseStack pose, ItemDisplayContext kontext, int licht, int overlay, List<BakedQuad> vierecke, ItemStackRenderState.FoilType folie)
        {
            if (folie == ItemStackRenderState.FoilType.NONE)
            {
                return;
            }

            /** SPECIAL (Enderauge, Netherstern) legt den Glanz als Abziehbild mit eigener,
             * verkleinerter Pose auf; STANDARD nimmt die Pose des Gegenstands. **/
            boolean abziehbild = folie == ItemStackRenderState.FoilType.SPECIAL;

            for (Map.Entry<RenderType, List<BakedQuad>> eintrag : nachEbene(vierecke, Gegenstandsmodell::glanzEbene).entrySet())
            {
                List<BakedQuad> gruppe = eintrag.getValue();

                this.sammler.submitCustomGeometry(pose, eintrag.getKey(), (p, verbraucher) ->
                {
                    VertexConsumer ziel = abziehbild
                        ? new SheetedDecalTextureGenerator(verbraucher, glanzPose(kontext, p), GLANZ_ABSTAND)
                        : verbraucher;

                    schreiben(gruppe, p, ziel, KEINE_TOENUNG, licht, overlay);
                });
            }
            //?}
        }

        /**
         * Gruppiert die Vierecke nach Zeichenebene.
         *
         * <p>Vanilla erreicht dasselbe ueber {@code getVertexBuilder(RenderType)}, das je Ebene
         * denselben Puffer zurueckgibt. Ueber {@code submitCustomGeometry} gibt es keinen
         * geteilten Puffer, also braucht es je Ebene EINEN Auftrag statt je Viereck einen.
         *
         * <p>⚠️ Die Gruppenlisten sind eigene Listen, und das ist zwingend: die uebergebene
         * Liste gehoert dem {@code ItemStackRenderState}, der beim naechsten
         * {@code updateForTopItem} geleert und neu gefuellt wird -- der Auftrag haelt sie aber,
         * bis der Bildgraph ihn zeichnet. Ohne Kopie zeichnete der Auftrag den Gegenstand, der
         * zufaellig als naechstes aufgeloest wurde.
         *
         * @param ebene bildet die Zeichenebene des Vierecks auf die tatsaechlich benutzte ab
         *              (Glanz), oder {@code null} fuer die Ebene des Vierecks selbst.
         */
        //? if >=26.3 {
        /*private static Map<RenderType, List<BakedQuad>> nachEbene(List<BakedQuad> vierecke, ItemStackRenderState.FoilType folie)
        *///?} else {
        private static Map<RenderType, List<BakedQuad>> nachEbene(List<BakedQuad> vierecke, Function<RenderType, RenderType> ebene)
        //?}
        {
            /* Reihenfolge erhalten: bei zwei Ebenen im selben Modell entscheidet sie, welche
             * zuerst eingereicht wird. */
            Map<RenderType, List<BakedQuad>> gruppen = new LinkedHashMap<>();

            for (int i = 0; i < vierecke.size(); i++)
            {
                BakedQuad viereck = vierecke.get(i);
                //? if >=26.3 {
                /*mchorse.bbs_mod.compat.alt.MaterialInfo material = viereck.materialInfo();
                RenderType eigen = switch (folie)
                {
                    case NONE -> material.itemRenderType();
                    case STANDARD -> material.itemGlintRenderType();
                    case SPECIAL -> material.itemGlintSpecialRenderType();
                };
                *///?} else {
                RenderType eigen = viereck.materialInfo().itemRenderType();
                //?}

                //? if >=26.3 {
                /*gruppen.computeIfAbsent(eigen,
                *///?} else {
                gruppen.computeIfAbsent(ebene == null ? eigen : ebene.apply(eigen),
                //?}
                    (e) -> new ArrayList<>()).add(viereck);
            }

            return gruppen;
        }

        /* ------------------------------------------------------------------
         * Ab hier nur noch Durchreichen. Keine Entscheidung, kein Zustand.
         * ------------------------------------------------------------------ */

        /**
         * ⚠️ Gibt den ECHTEN geordneten Sammler zurueck, nicht noch einmal umgelenkt.
         *
         * <p>{@code ItemStackRenderState.submit} ruft {@code order(...)} nicht; nur
         * Sonderzeichner tun das, und deren Geometrie geht ohnehin nicht durch unseren
         * Verbraucher (siehe Klassendoku). Eine zweite Umlenkung waere hier also Aufwand ohne
         * Wirkung -- aber wer den Sonderzeichnern einmal die Form-Farbe geben will, faengt an
         * dieser Zeile an.
         */
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
        public void submitNameTag(PoseStack pose, Vec3 ort, int licht, Component text, boolean sichtbarDurchWaende, int hintergrund, CameraRenderState kamera)
        {
            this.sammler.submitNameTag(pose, ort, licht, text, sichtbarDurchWaende, hintergrund, kamera);
        }

        @Override
        public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean schatten, Font.DisplayMode modus, int licht, int farbe, int hintergrund, int umriss)
        {
            this.sammler.submitText(pose, x, y, text, schatten, modus, licht, farbe, hintergrund, umriss);
        }

        @Override
        //? if >=26.3 {
        /*public void submitTextBackground(PoseStack pose, float x, float y, float breite, float hoehe,
            int farbe, Font.DisplayMode modus, int licht)
        {
            this.sammler.submitTextBackground(pose, x, y, breite, hoehe, farbe, modus, licht);
        }

        @Override
        *///?}
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
        //? if >=26.3 {
        /*public <S> void submitModel(Model<? super S> modell, S zustand, PoseStack pose, RenderType ebene, int licht, int overlay, int umriss, UvMapping sprite, int farbe)
        *///?} else {
        public <S> void submitModel(Model<? super S> modell, S zustand, PoseStack pose, RenderType ebene, int licht, int overlay, int umriss, TextureAtlasSprite sprite, int farbe, ModelFeatureRenderer.CrumblingOverlay bruch)
        //?}
        {
            //? if >=26.3 {
            /*this.sammler.submitModel(modell, zustand, pose, ebene, licht, overlay, umriss, sprite, farbe);
        }

        @Override
        public <S> void submitCrumblingOverlay(Model<? super S> modell, S zustand, PoseStack pose,
            RenderType ebene, int licht, int overlay, int umriss, ModelFeatureRenderer.CrumblingOverlay bruch)
        {
            this.sammler.submitCrumblingOverlay(modell, zustand, pose, ebene, licht, overlay, umriss, bruch);
            *///?} else {
            this.sammler.submitModel(modell, zustand, pose, ebene, licht, overlay, umriss, sprite, farbe, bruch);
            //?}
        }

        @Override
        public void submitMovingBlock(PoseStack pose, MovingBlockRenderState zustand, int licht)
        {
            this.sammler.submitMovingBlock(pose, zustand, licht);
        }

        @Override
        public void submitBlockModel(PoseStack pose, RenderType ebene, List<net.minecraft.client.renderer.block.model.BlockModelPart> teile, int[] toenung, int licht, int overlay, int umriss)
        {
            this.sammler.submitBlockModel(pose, ebene, teile, toenung, licht, overlay, umriss);
        }

        @Override
        //? if >=26.3 {
        /*public void submitBreakingBlockModel(PoseStack pose, List<net.minecraft.client.renderer.block.model.BlockModelPart> teile, int stufe, boolean multiTexture)
        *///?} else {
        public void submitBreakingBlockModel(PoseStack pose, List<net.minecraft.client.renderer.block.model.BlockModelPart> teile, int stufe)
        //?}
        {
            //? if >=26.3 {
            /*this.sammler.submitBreakingBlockModel(pose, teile, stufe, multiTexture);
            *///?} else {
            this.sammler.submitBreakingBlockModel(pose, teile, stufe);
            //?}
        }

        @Override
        public void submitShapeOutline(PoseStack pose, VoxelShape form, RenderType ebene, int farbe, float breite, boolean durchWaende)
        {
            this.sammler.submitShapeOutline(pose, form, ebene, farbe, breite, durchWaende);
        }

        @Override
        public void submitCustomGeometry(PoseStack pose, RenderType ebene, SubmitNodeCollector.CustomGeometryRenderer zeichner)
        {
            this.sammler.submitCustomGeometry(pose, ebene, zeichner);
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState zustand)
        {
            this.sammler.submitQuadParticleGroup(zustand);
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group gruppe, CameraRenderState kamera, boolean durchWaende)
        {
            this.sammler.submitGizmoPrimitives(gruppe, kamera, durchWaende);
        }
    }

    //? if >=26.3 {
    //?} else {
    /*** Welche Glanzebene ein Gegenstand bekommt -- 1:1
     * {@code ItemFeatureRenderer.useTransparentGlint}.
     *
     * <p>Nur wenn der Shader-Durchsichtigkeitspfad laeuft UND die Ebene des Gegenstands ins
     * Item-Entity-Ziel schreibt, kommt der durchsichtige Glanz; sonst der normale.
     **/
    private static RenderType glanzEbene(RenderType gegenstandsEbene)
    {
        boolean durchsichtig = Minecraft.getInstance().gameRenderer.gameRenderState().useShaderTransparency()
            && gegenstandsEbene.outputTarget() == OutputTarget.ITEM_ENTITY_TARGET;

        return durchsichtig ? RenderTypes.glintTranslucent() : RenderTypes.glint();
    }

    //?}
    /**
     * Die verkleinerte Pose des Abziehbild-Glanzes -- 1:1
     * {@code ItemFeatureRenderer.computeFoilDecalPose}.
     *
     * <p>Die komponentenweise Multiplikation staucht das Glanzmuster, damit es im Inventar
     * bzw. in der Hand nicht ueber den Gegenstand hinauslaeuft.
     */
    private static PoseStack.Pose glanzPose(ItemDisplayContext kontext, PoseStack.Pose pose)
    {
        PoseStack.Pose kopie = pose.copy();

        if (kontext == ItemDisplayContext.GUI)
        {
            MatrixUtil.mulComponentWise(kopie.pose(), 0.5F);
        }
        else if (kontext.firstPerson())
        {
            MatrixUtil.mulComponentWise(kopie.pose(), 0.75F);
        }

        return kopie;
    }
}
