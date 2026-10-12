package mchorse.bbs_mod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import mchorse.bbs_mod.forms.FormSammler;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeichnet Morph-Gestalten ein zweites Mal — zur ZEICHEN-Zeit statt zur Sammel-Zeit.
 *
 * <p>⛔ WARUM ES DAS BRAUCHT (RenderDoc-belegt 2026-08-11): {@code MorphRenderer} laeuft im
 * Entity-Einsammeln ({@code LevelRenderer#submitFeatures}). BBS' Modell-Zeichenweg
 * (ModelVAORenderer) zeichnet dort SOFORT — und der Hauptpass raeumt danach das Ziel und
 * malt Himmel und Terrain darueber. Modell-Morphs (Emoticon/Player) waren deshalb in der
 * Welt unsichtbar, waehrend Block/Item-Morphs sichtbar blieben: die reichen ihre Zuege beim
 * {@code SubmitNodeCollector} ein und der Bildgraph zeichnet sie zur richtigen Zeit.
 *
 * <p>Der Schnitt folgt dem Film-Weg (BBSModClient): dort laufen die Modell-Sofortzuege in
 * {@code AFTER_SOLID_FEATURES} — im Hauptpass, nach {@code executeSolid}, vor dem
 * durchsichtigen Terrain. Derselbe Punkt traegt hier den Zweitrender.
 *
 * <p>⚠️ ZWEI RENDER PRO BILD, MIT ABSICHT. Der Sammel-Render in {@code MorphRenderer}
 * bleibt unveraendert: nur er reicht die Collector-Anteile (Ruestung, Item-/Block-Teile)
 * mit den Matrizen DIESES Bildes ein — zur Zeichen-Zeit eingereicht landeten sie in schon
 * geleerten Sammlungen und kaemen ein Bild zu spaet (siehe den Schatten-Fall in
 * BBSModClient). Sein Sofortzug ist unsichtbar (wird uebermalt), sein Einreichen ist der
 * korrekte Teil. Der Zweitrender hier liefert nur den sichtbaren Sofortzug nach; seine
 * Collector-Anteile fallen in einen WEGWERF-Speicher, sonst staenden Ruestung und
 * Item-Teile doppelt im Bild.
 *
 * <p>⛔ KEIN {@code renderAllFeatures} fuer den Wegwerf-Speicher: {@code
 * FeatureRenderDispatcher} haelt EIN wiederverwendetes {@code PreparedFrame}; ein
 * verschachtelter Aufruf im Hauptpass wuerde den aeusseren Frame zuruecksetzen und die
 * restlichen Phasen (translucent, alwaysOnTop) verlieren. Am Bytecode geprueft
 * (prepareFrameWithContext -> this.preparedFrame.begin).
 */
public final class MorphAufschub
{
    /** Pose-Schnappschuss + alles, was {@code FormRenderingContext.set} braucht. */
    private record Auftrag(Form form, IEntity entity, FormRenderType typ, Matrix4f pose, Matrix3f normale,
        int licht, int ueberlagerung, float teilTick) {}

    private static final List<Auftrag> AUFTRAEGE = new ArrayList<>();

    /**
     * Notbremse gegen unbegrenztes Wachsen: feuert {@code AFTER_SOLID_FEATURES} nie
     * (abgebrochener Weltdurchgang, fremde Renderpfade wie Iris), wuerde die Liste sonst
     * jedes Bild weiterwachsen. Ein Ueberlauf verwirft alles Alte — sichtbar ist dann
     * hoechstens ein Bild ohne Zweitrender, kein Leck.
     */
    private static final int MAX_AUFTRAEGE = 256;

    private MorphAufschub() {}

    /* Registrierung beim Lazy-Classload — der erste Morph-Render laedt die Klasse waehrend
     * der Sammelphase, das Event feuert im selben Bild. Kein Aufruf in BBSModClient noetig
     * (die Datei ist ueber der 800-Zeilen-Grenze und darf nur schrumpfen), und ein
     * Hotswap registriert nicht doppelt: Redefinition laesst den static-Init kalt. */
    static
    {
        LevelRenderEvents.AFTER_SOLID_FEATURES.register((context) -> zeichnen());
    }

    /**
     * Merkt eine Gestalt fuer den Zweitrender vor. Aufzurufen an der Stelle des
     * Sammel-Renders, mit demselben Stapelzustand — die oberste Pose wird KOPIERT, denn
     * der Stapel ist beim Zeichnen laengst wieder abgebaut.
     */
    public static void einreichen(Form form, IEntity entity, PoseStack stapel,
        int licht, int ueberlagerung, float teilTick)
    {
        einreichen(form, entity, FormRenderType.ENTITY, stapel, licht, ueberlagerung, teilTick);
    }

    /** Dasselbe fuer Modellbloecke und Actors: auch ihre Renderer laufen in Vanillas
     * Sammelphase ({@code submitBlockEntities}/{@code submitEntities}) und waren deshalb
     * unsichtbar (2026-10-12 gemessen, beide leer, nur der Schatten stand da). */
    public static void einreichen(Form form, IEntity entity, FormRenderType typ, PoseStack stapel,
        int licht, int ueberlagerung, float teilTick)
    {
        if (AUFTRAEGE.size() >= MAX_AUFTRAEGE)
        {
            AUFTRAEGE.clear();
        }

        AUFTRAEGE.add(new Auftrag(form, entity, typ,
            new Matrix4f(stapel.last().pose()), new Matrix3f(stapel.last().normal()),
            licht, ueberlagerung, teilTick));
    }

    private static void zeichnen()
    {
        if (AUFTRAEGE.isEmpty())
        {
            return;
        }

        try
        {
            /* EIN Wegwerf-Speicher fuer alle Auftraege des Bildes; er wird nie gezeichnet
             * und mit dem Verlassen dieser Methode fallengelassen. */
            SubmitNodeStorage wegwerf = new SubmitNodeStorage();

            for (Auftrag auftrag : AUFTRAEGE)
            {
                PoseStack stapel = new PoseStack();

                stapel.last().pose().set(auftrag.pose());
                stapel.last().normal().set(auftrag.normale());

                /* Ausnahmen faengt FormUtilsClient.render selbst (gedrosselt geloggt);
                 * was dort durchschluepft, soll laut scheitern statt still. */
                FormSammler.mitSammler(wegwerf, () ->
                    FormUtilsClient.render(auftrag.form(), new FormRenderingContext()
                        .set(auftrag.typ(), auftrag.entity(), stapel,
                            auftrag.licht(), auftrag.ueberlagerung(), auftrag.teilTick())
                        .camera(Minecraft.getInstance().gameRenderer.mainCamera())));
            }
        }
        finally
        {
            AUFTRAEGE.clear();
        }
    }
}
