package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.mixin.client.BlockModelRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * Zeichnet EINEN Blockzustand fuer sich allein -- der Ersatz fuer {@code renderSingleBlock}.
 *
 * <h2>Was 26.2 daraus gemacht hat</h2>
 *
 * <p>{@code Minecraft.getBlockRenderer()} und der {@code BlockRenderDispatcher} sind weg. Einen
 * Block anzuzeigen ist jetzt zweistufig:
 *
 * <ol>
 *   <li>{@code BlockModelResolver.update(zustand, block, kontext)} loest den Blockzustand in
 *       Modellteile auf und legt Zeichenebene, Anzeigetransformation, Toenungsschichten und die
 *       Eigenleuchtkraft daneben,</li>
 *   <li>{@code zustand.submit(...)} reicht das beim Zeichenweg ein; gezeichnet wird spaeter.</li>
 * </ol>
 *
 * <p>Diese Klasse macht Schritt 1 mit Vanillas eigenem Aufloeser und Schritt 2 SELBST. Der Grund
 * steht unten unter "Warum nicht einfach submit()".
 *
 * <h2>⛔ Warum nicht einfach {@code zustand.submit(...)}</h2>
 *
 * <p>Weil BBS-Formen eine eigene Farbe tragen (Farbwaehler je Form, dazu die additive Mischung)
 * und {@code submit} sie nicht durchlaesst: es ruft {@code submitBlockModel}, und diese Methode
 * setzt die Toenungsfarbe fest auf {@code -1} -- weiss, im Bytecode nachgelesen. Der Datensatz
 * dahinter HAT ein Farbfeld, die oeffentliche Methode fuellt es nur nie.
 *
 * <p>Also reicht BBS die Vierecke selbst ein, ueber {@code submitCustomGeometry}. Das ist der
 * einzige Weg, an dem der Zeichenweg einem den {@code VertexConsumer} in die Hand gibt -- genau
 * die Stelle, an der {@link CustomVertexConsumerProvider#umfaerben(VertexConsumer)} greift.
 * Waeren wir den bequemen Weg gegangen, waere die Form-Farbe LAUTLOS verschwunden: der Block
 * erschiene, nur eben immer weiss.
 *
 * <h2>Was hier Vanilla-Logik ist und darum nicht "verbessert" werden darf</h2>
 *
 * <p>{@link #vierecke} ist Zeile fuer Zeile {@code BlockModelFeatureRenderer.putPartQuads} +
 * {@code putQuad}: sechs Richtungseimer, Toenungsschicht nur wenn der Index passt, sonst die
 * blanke Farbe. Wer hier etwas glaettet, aendert die Farbe von Laub und Gras -- und das faellt
 * erst im fertigen Film auf.
 *
 * <h2>⚠️ Fallen</h2>
 *
 * <ul>
 *   <li><b>Nur im Renderthread.</b> Zustand und Aufloeser sind geteilt und wiederverwendet.
 *       Ein zweiter Thread wuerde dem ersten den Block unter den Fuessen wegtauschen.
 *       {@link #einreichen} prueft das und wirft, statt Unsinn zu zeichnen.</li>
 *   <li><b>Die Zeichenebene waehlt Vanilla</b>, nicht der Block: {@code setupModel} nimmt eine
 *       der beiden Item-Ebenen (durchsichtig oder ausgeschnitten). Das ist derselbe Weg, den
 *       das Spiel fuer einen Block im Inventar oder in einem Rahmen nimmt.</li>
 *   <li><b>Sonderzeichner (Truhe, Schild, Bett, Kopf) zeichnen sich selbst</b> und nehmen die
 *       Form-Farbe NICHT an -- sie gehen nicht durch unseren Verbraucher. Ohne sie waeren diese
 *       Bloecke unsichtbar statt ungefaerbt, also ist Zeichnen die bessere Haelfte.</li>
 * </ul>
 */
public final class Blockmodell
{
    /**
     * Reine Marke ohne Felder (javap -p geprueft): sie sagt dem Modell nur, dass es fuer eine
     * Anzeige und nicht fuer einen Weltblock aufgeloest wird. Vanilla haelt sie genauso als
     * eine geteilte Instanz je Zeichner ({@code ItemFrameRenderer.BLOCK_DISPLAY_CONTEXT}).
     */
    private static final BlockDisplayContext KONTEXT = BlockDisplayContext.create();

    /**
     * Sechs Eimer, kein Null-Eimer -- exakt {@code BlockModelFeatureRenderer.DIRECTIONS}. Vanillas
     * Anzeige-Zeichner fragt nur diese sechs ab; wer einen siebten erfindet, weicht davon ab.
     */
    private static final Direction[] RICHTUNGEN = Direction.values();

    private static final BlockModelRenderState ZUSTAND = new BlockModelRenderState();

    /**
     * ⛔ EIGENER ZUSTAND FUER DIE VORSCHAU (2026-08-04 gemessen).
     *
     * <p>{@link #ZUSTAND} gehoert dem Weltdurchgang. Loest die Oberflaeche DENSELBEN Block noch
     * einmal darin auf, kommt nichts zurueck: gemessen {@code teile=null ebene=item_cutout} --
     * die Ebene steht, die Modellteile fehlen, weil der eingereichte Auftrag des Weltdurchgangs
     * die Liste laengst uebernommen hat. Der Klassenkopf warnt genau davor ("Zustand und
     * Aufloeser sind geteilt und wiederverwendet"); die Vorschau ist der zweite Leser.</p>
     */
    private static BlockModelRenderState zustandVorschau;

    private static final QuadInstance VIERECK = new QuadInstance();

    private static BlockModelResolver aufloeser;

    /**
     * Der Modellverwalter, zu dem {@link #aufloeser} gebaut wurde. Nach einem Ressourcen-Neuladen
     * kann ein anderer dastehen; dann wird der Aufloeser neu gebaut statt auf tote Modelle zu
     * zeigen.
     */
    private static ModelManager fuer;

    private Blockmodell()
    {}

    /**
     * Reicht den Block ohne Umfaerbung ein -- fuer alles, was keine BBS-Form ist.
     */
    public static void einreichen(BlockState block, PoseStack stapel, SubmitNodeCollector sammler, int licht, int overlay)
    {
        einreichen(block, stapel, sammler, null, licht, overlay);
    }

    /**
     * Reicht den Block mit der eingestellten Umfaerbung des Anbieters ein.
     *
     * <p>Das ist der Weg fuer BBS-Formen: der Anbieter traegt sowohl den Sammler als auch die
     * Farbe, die {@code setSubstitute} vorher gesetzt hat.
     */
    public static void einreichen(BlockState block, PoseStack stapel, CustomVertexConsumerProvider anbieter, int licht, int overlay)
    {
        einreichen(block, stapel, anbieter.sammler(), anbieter, licht, overlay);
    }

    private static void einreichen(BlockState block, PoseStack stapel, SubmitNodeCollector sammler, CustomVertexConsumerProvider anbieter, int licht, int overlay)
    {
        if (!RenderSystem.isOnRenderThread())
        {
            throw new IllegalStateException(
                "Blockmodell.einreichen laeuft nur im Renderthread -- Zustand und Aufloeser sind "
                + "geteilt und wiederverwendet. Wer einen Block auf einem Arbeitsthread braucht, "
                + "geht ueber ModelManager.getBlockStateModelSet().get(zustand).collectParts(...) "
                + "und haelt seine eigene Liste (so macht es der Welt-Exporter).");
        }

        ModelManager verwalter = Minecraft.getInstance().getModelManager();

        if (aufloeser == null || fuer != verwalter)
        {
            aufloeser = new BlockModelResolver(verwalter);
            fuer = verwalter;
        }

        /* Fuellt Modellteile, Zeichenebene, Anzeigetransformation, Toenungsschichten und
         * blockLightCoords. Der Wuerfel fuer Modellvarianten sitzt dabei INNEN und ist auf 42
         * festgenagelt (Bytecode: BlockModel.update(..., 42L)) -- eine stehende Anzeige soll bei
         * jedem Bild dieselbe Variante zeigen. Genau darum darf hier NICHT eigenhaendig gewuerfelt
         * werden: ein frischer Zufall je Bild laesst Gras und Steinziegel flackern. */
        aufloeser.update(ZUSTAND, block, KONTEXT);

        BlockModelRenderStateAccessor zugriff = (BlockModelRenderStateAccessor) ZUSTAND;
        List<BlockStateModelPart> teile = zugriff.bbs$getModelParts();
        RenderType ebene = zugriff.bbs$getRenderType();

        /* Vergleichssonde zur Vorschau (2026-08-04): bekommt DER WELTWEG Modellteile?
         * Dazu das Feld `mesh`, das 26.2 neben modelParts fuehrt -- die Frage ist, ob die
         * Geometrie dort landet statt in modelParts. */
        String maschen;

        try
        {
            var mf = BlockModelRenderState.class.getDeclaredField("mesh");

            mf.setAccessible(true);

            Object mm = mf.get(ZUSTAND);

            maschen = mm == null ? "null" : mm.getClass().getSimpleName() + "=" + mm;
        }
        catch (Throwable t)
        {
            maschen = "unlesbar " + t.getClass().getSimpleName();
        }

        Messpunkt.zaehle("blockmodell.welt", "teile="
            + (teile == null ? "null" : String.valueOf(teile.size()))
            + " ebene=" + (ebene == null ? "null" : "da")
            + " mesh=" + maschen);

        /* Eigenleuchtende Bloecke (Fackel, Glowstone, Lava) ziehen ihr eigenes Licht hoch --
         * dieselbe Rechnung wie in BlockModelRenderState.submitModel. */
        int hell = LightCoordsUtil.max(licht, ZUSTAND.blockLightCoords);

        /* ⭐ FABRIC-WEG ZUERST PRUEFEN (Bugliste #15): mit Indigo bleibt modelParts leer und
         * die Vierecke liegen in der Masche. Ohne diesen Zweig zeichnet eine Block-Form
         * ueberhaupt nichts -- gemessen in Welt UND Vorschau. */
        if ((teile == null || teile.isEmpty()) && ebene != null)
        {
            int[] toenungM = toenung(zugriff);
            int hellM = LightCoordsUtil.max(licht, ZUSTAND.blockLightCoords);
            Matrix4fc anzeigeM = zugriff.bbs$getTransformation();
            java.util.function.Function<VertexConsumer, VertexConsumer> umfaerbungM =
                anbieter == null ? (v) -> v : anbieter.umfaerbungEinfrieren();

            if (anzeigeM != null)
            {
                stapel.pushPose();
                stapel.mulPose(anzeigeM);
            }

            net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh eingefroren =
                ((net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh) masche(ZUSTAND)).immutableCopy();
            sammler.submitCustomGeometry(stapel, ebene, (pose, verbraucher) ->
                viereckeAusMasche(pose, umfaerbungM.apply(verbraucher), toenungM, hellM, overlay, eingefroren));

            if (anzeigeM != null)
            {
                stapel.popPose();
            }

            sonderzeichner(zugriff, stapel, sammler, hellM, overlay);

            return;
        }

        if (teile != null && !teile.isEmpty() && ebene != null)
        {
            int[] toenung = toenung(zugriff);
            Matrix4fc anzeige = zugriff.bbs$getTransformation();

            if (anzeige != null)
            {
                stapel.pushPose();
                stapel.mulPose(anzeige);
            }

            /* Die Liste wird beim naechsten update() geleert, nicht neu gebaut -- der Auftrag
             * haelt sie aber, bis der Bildgraph ihn zeichnet. Darum eine eigene Kopie; sonst
             * zeichnet der Auftrag den Block, der zufaellig als naechstes aufgeloest wurde. */
            List<BlockStateModelPart> kopie = List.copyOf(teile);

            /* ⛔ DIE UMFAERBUNG MUSS HIER EINGEFROREN WERDEN, nicht im Rumpf abgefragt.
             * Bis 2026-07-31 stand hier anbieter.umfaerben(verbraucher) IM Rumpf -- und der
             * Rumpf laeuft VERZOEGERT: submitCustomGeometry legt ihn als Feld in einem
             * Auftrags-Record ab und fuehrt ihn erst beim Bauen des Bildes aus. Da hat die
             * Aufrufstelle (BlockFormRenderer) ihr setSubstitute(null) laengst gesetzt, der
             * Rumpf bekam den rohen Verbraucher, und die Form-Farbe war STILL WEG.
             * Kein Compiler meldet das; gefunden wurde es beim Bau des Item-Gegenstuecks. */
            java.util.function.Function<com.mojang.blaze3d.vertex.VertexConsumer,
                com.mojang.blaze3d.vertex.VertexConsumer> umfaerbung =
                    anbieter == null ? (v) -> v : anbieter.umfaerbungEinfrieren();

            sammler.submitCustomGeometry(stapel, ebene, (pose, verbraucher) ->
                vierecke(kopie, pose, umfaerbung.apply(verbraucher), toenung, hell, overlay));

            if (anzeige != null)
            {
                stapel.popPose();
            }
        }

        sonderzeichner(zugriff, stapel, sammler, hell, overlay);
    }

    /**
     * Vierecke aller Modellteile in den Verbraucher schreiben.
     *
     * <p>1:1 {@code BlockModelFeatureRenderer.putPartQuads} + {@code putQuad}: die Toenungsschicht
     * greift nur, wenn das Viereck einen Toenungsindex traegt UND dieser in der Schichtliste
     * liegt. Genau daran haengt, ob Laub gruen oder grau ist.
     */
    private static void vierecke(List<BlockStateModelPart> teile, PoseStack.Pose pose, VertexConsumer verbraucher, int[] toenung, int licht, int overlay)
    {
        VIERECK.setLightCoords(licht);
        VIERECK.setOverlayCoords(overlay);

        for (int t = 0; t < teile.size(); t++)
        {
            BlockStateModelPart teil = teile.get(t);

            for (Direction richtung : RICHTUNGEN)
            {
                List<BakedQuad> vierecke = teil.getQuads(richtung);

                for (int v = 0; v < vierecke.size(); v++)
                {
                    BakedQuad viereck = vierecke.get(v);
                    int index = viereck.materialInfo().tintIndex();
                    boolean getoent = index != -1 && index < toenung.length;

                    /* -1 ist weiss: die Form-Farbe kommt NICHT hierher, sondern aus dem
                     * umgefaerbten Verbraucher. So multipliziert sie sich ueber die Toenung,
                     * genau wie vor dem Port. */
                    VIERECK.setColor(getoent ? ARGB.multiply(-1, toenung[index]) : -1);

                    verbraucher.putBakedQuad(pose, viereck, VIERECK);
                }
            }
        }
    }

    /**
     * Die Zeichenebene eines Blocks, ohne ihn einzureihen.
     *
     * <p>Gebraucht von {@link Blockvorschau}: wer die Vierecke selbst in einen Puffer schreibt,
     * braucht vorher Format und Topologie, und beides haengt an der Ebene.</p>
     *
     * @return {@code null}, wenn der Block kein Backmodell hat (Truhe, Schild, Kopf)
     */
    public static RenderType ebeneFuer(BlockState block)
    {
        if (!aufloesenVorschau(block))
        {
            return null;
        }

        BlockModelRenderStateAccessor zugriff = (BlockModelRenderStateAccessor) zustandVorschau();
        List<BlockStateModelPart> teile = zugriff.bbs$getModelParts();

        Object m = masche(zustandVorschau());

        Messpunkt.zaehle("blockmodell.aufloesung", "teile="
            + (teile == null ? "null" : String.valueOf(teile.size()))
            + " ebene=" + (zugriff.bbs$getRenderType() == null ? "null" : "da")
            + " masche=" + (m == null ? "null"
                : (m instanceof net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh mm
                    ? mm.size() + " vierecke" : m.getClass().getSimpleName())));

        return zugriff.bbs$getRenderType();
    }

    /**
     * Schreibt die Vierecke eines Blocks direkt in einen Verbraucher.
     *
     * <p>Der Unterschied zu {@link #einreichen}: dort geht ein AUFTRAG an den Bildgraphen des
     * Weltdurchgangs, hier werden die Ecken sofort geschrieben. Nur so kommt ein Block in die
     * Oberflaeche -- siehe {@link Blockvorschau} fuer den gemessenen Befund dahinter.</p>
     *
     * @return {@code false}, wenn es nichts zu zeichnen gab
     */
    public static boolean vierecke(BlockState block, PoseStack.Pose pose, VertexConsumer verbraucher,
        int licht, int overlay)
    {
        if (!aufloesen(block))
        {
            return false;
        }

        return viereckeAusZustand(pose, verbraucher, licht, overlay);
    }

    /**
     * Wie {@link #vierecke}, aber OHNE neu aufzuloesen -- fuer Aufrufer, die schon
     * {@link #ebeneFuer} gerufen haben.
     *
     * <p>⛔ WARUM ES DAS BRAUCHT (2026-08-04 gemessen): zweimal hintereinander aufzuloesen
     * liefert beim ZWEITEN Mal keine Modellteile mehr. Der Zaehler sagte
     * {@code blockvorschau = 56 | keine vierecke}, obwohl derselbe Block im Weltdurchgang
     * einwandfrei zeichnet. {@code BlockModelRenderState} ist geteilt und wiederverwendet;
     * wer ihn zweimal fuellen laesst, bekommt beim zweiten Mal eine leere Liste.</p>
     */
    public static boolean viereckeAusZustand(PoseStack.Pose pose, VertexConsumer verbraucher,
        int licht, int overlay)
    {
        BlockModelRenderStateAccessor zugriff = (BlockModelRenderStateAccessor) zustandVorschau();
        List<BlockStateModelPart> teile = zugriff.bbs$getModelParts();

        if (teile == null || teile.isEmpty())
        {
            /* Siehe viereckeAusMasche: mit Fabric Indigo liegt die Geometrie dort. */
            return viereckeAusMasche(pose, verbraucher, toenung(zugriff),
                LightCoordsUtil.max(licht, zustandVorschau().blockLightCoords), overlay,
                zustandVorschau());
        }

        vierecke(List.copyOf(teile), pose, verbraucher, toenung(zugriff),
            LightCoordsUtil.max(licht, zustandVorschau().blockLightCoords), overlay);

        return true;
    }

    /** Wie {@link #aufloesen}, aber in den Vorschau-Zustand -- siehe {@link #zustandVorschau()}. */
    private static boolean aufloesenVorschau(BlockState block)
    {
        if (block == null || !RenderSystem.isOnRenderThread())
        {
            return false;
        }

        ModelManager verwalter = Minecraft.getInstance().getModelManager();

        if (aufloeser == null || fuer != verwalter)
        {
            aufloeser = new BlockModelResolver(verwalter);
            fuer = verwalter;
        }

        aufloeser.update(zustandVorschau(), block, KONTEXT);

        return true;
    }

    /**
     * Der Vorschau-Zustand, traege angelegt.
     *
     * <p>Traege, weil ein nachtraeglich eingefuegtes statisches Feld nach einem Hotswap
     * {@code null} ist und dieser Pfad in jedem Bild laeuft.</p>
     */
    private static BlockModelRenderState zustandVorschau()
    {
        if (zustandVorschau == null)
        {
            zustandVorschau = new BlockModelRenderState();
        }

        return zustandVorschau;
    }

    /** Der gemeinsame Auflöseschritt von {@link #einreichen}, {@link #ebeneFuer} und {@link #vierecke}. */
    private static boolean aufloesen(BlockState block)
    {
        if (block == null || !RenderSystem.isOnRenderThread())
        {
            return false;
        }

        ModelManager verwalter = Minecraft.getInstance().getModelManager();

        if (aufloeser == null || fuer != verwalter)
        {
            aufloeser = new BlockModelResolver(verwalter);
            fuer = verwalter;
        }

        aufloeser.update(ZUSTAND, block, KONTEXT);

        return true;
    }

    /**
     * Die Vierecke aus Fabrics Masche schreiben -- der Weg, den 26.2 mit Fabric WIRKLICH nimmt.
     *
     * <h2>⛔ Warum es das gibt (2026-08-04, Bugliste #15)</h2>
     *
     * <p>{@code BlockModelResolver.update} fuellt hier NIE {@code modelParts}. Gemessen in
     * beiden Wegen -- Weltdurchgang und Oberflaechen-Vorschau -- immer {@code teile=null},
     * waehrend die Zeichenebene steht. Die Sonde auf das Nachbarfeld zeigt, wohin die
     * Geometrie geht:</p>
     *
     * <pre>
     *   mesh = net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableMeshImpl
     * </pre>
     *
     * <p>Fabrics Indigo-Renderer faengt die Aufloesung ab und legt die Vierecke in seine eigene
     * Masche. Wer {@code modelParts} liest, liest ein Feld, das in dieser Umgebung niemand
     * fuellt -- und zeichnet folgerichtig nichts. Genau daran hingen die unsichtbaren
     * Block-Formen.</p>
     *
     * <p>Ueber Reflexion statt ueber einen Accessor, weil ein neuer {@code @Accessor} eine
     * Mixin-Aenderung waere und damit einen Boot kostet; das Feld ist stabil benannt und der
     * Zugriff laeuft einmal je Block, nicht je Viereck.</p>
     *
     * @return {@code false}, wenn keine Masche da ist oder sie leer ist
     */
    private static boolean viereckeAusMasche(PoseStack.Pose pose, VertexConsumer verbraucher,
        int[] toenung, int licht, int overlay, Object zustand)
    {
        Object masche = zustand instanceof BlockModelRenderState modell ? masche(modell) : zustand;

        if (!(masche instanceof net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView mm)
            || mm.size() == 0)
        {
            return false;
        }

        VIERECK.setLightCoords(licht);
        VIERECK.setOverlayCoords(overlay);

        mm.forEach((viereck) ->
        {
            BakedQuad gebacken = viereck.toBakedQuad(null);
            int index = gebacken.materialInfo().tintIndex();
            boolean getoent = index != -1 && index < toenung.length;

            /* Dieselbe Toenungsregel wie im Vanilla-Weg -- siehe vierecke(). */
            VIERECK.setColor(getoent ? ARGB.multiply(-1, toenung[index]) : -1);

            verbraucher.putBakedQuad(pose, gebacken, VIERECK);
        });

        return true;
    }

    /** Das Maschenfeld des Zustands, per Reflexion (siehe {@link #viereckeAusMasche}). */
    private static Object masche(BlockModelRenderState zustand)
    {
        try
        {
            if (maschenFeld == null)
            {
                maschenFeld = BlockModelRenderState.class.getDeclaredField("mesh");

                maschenFeld.setAccessible(true);
            }

            return maschenFeld.get(zustand);
        }
        catch (Throwable t)
        {
            Messpunkt.stoerung("blockmodell.masche", t.getClass().getSimpleName() + ": " + t.getMessage());

            return null;
        }
    }

    private static java.lang.reflect.Field maschenFeld;

    private static int[] toenung(BlockModelRenderStateAccessor zugriff)
    {
        it.unimi.dsi.fastutil.ints.IntList schichten = zugriff.bbs$getTintLayers();

        return schichten == null ? BlockModelRenderState.EMPTY_TINTS : schichten.toArray(BlockModelRenderState.EMPTY_TINTS);
    }

    /**
     * Truhe, Schild, Bett, Kopf: Bloecke, deren Aussehen kein Backmodell hat.
     *
     * <p>Ohne diesen Zweig blieben sie unsichtbar -- ihr Backmodell ist leer, das ganze Aussehen
     * steckt im Sonderzeichner. Sie nehmen die Form-Farbe nicht an, weil sie ihre Ecken selbst
     * schreiben und nicht durch unseren Verbraucher gehen.
     */
    private static void sonderzeichner(BlockModelRenderStateAccessor zugriff, PoseStack stapel, SubmitNodeCollector sammler, int licht, int overlay)
    {
        SpecialModelRenderer<?> zeichner = zugriff.bbs$getSpecialRenderer();

        if (zeichner == null)
        {
            return;
        }

        Matrix4fc anzeige = zugriff.bbs$getSpecialRendererTransformation();

        if (anzeige != null)
        {
            stapel.pushPose();
            stapel.mulPose(anzeige);
        }

        /* Erstes Argument ist das Zusatzargument des Zeichners (z.B. das Muster einer Flagge).
         * Vanilla reicht hier ebenfalls null: aus einem blossen Blockzustand ist es nicht
         * abzuleiten, das gibt es nur aus einem ItemStack. */
        zeichner.submit(null, stapel, sammler, licht, overlay, false, 0);

        if (anzeige != null)
        {
            stapel.popPose();
        }
    }
}
