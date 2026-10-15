package mchorse.bbs_mod.client;

//? if >=26.3 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.compat.alt.ColorTargetState;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?}

//? if >=26.3 {
/*import java.util.Map;
import java.util.WeakHashMap;
*///?} else {
import org.lwjgl.opengl.GL11;
//?}

//? if >=26.3 {
/** Terrain wireframe for planning shots; actors and editor UI keep their own fill mode. */
//?} else {
/***
 * Die Welt als Drahtgitter zeichnen, damit man durch sie hindurchsieht.
 *
 * <p>USER-WUNSCH 2026-07-29: „welt wire frame/xray / ich will das man die welt
 * xrayen/wireframen kann sodass man shots besser machen kann wo ein replay vom boden zur
 * oberflaeche kommt". Heute steckt ein Darsteller, der unter der Erde startet, unsichtbar
 * im Boden — man kann die Aufnahme nicht planen.</p>
 *
 * <p>⛔ KEIN EINTRAG IN DEN EINSTELLUNGEN (User in derselben Nachricht): das ist ein
 * Arbeitsmodus, den man im Editor an- und ausknipst, kein dauerhafter Vorzug. Deshalb ein
 * Tastenkürzel und ein Schalter im Editor, nichts in {@code BBSSettings}.</p>
 *
 * <h2>Warum der Füllmodus und nicht der Chunk-Renderer</h2>
 *
 * <p>Die verbreiteten X-Ray-Mods setzen am Chunk-Renderer an: sie entscheiden je Block, ob
 * er überhaupt gebaut wird, und lösen dafür einen Neuaufbau der Chunks aus. Das ist der
 * richtige Weg, wenn man einzelne Blockarten sucht (Erz), aber teuer, an Sodiums Interna
 * gebunden und für unseren Zweck zu viel: hier soll die Welt nur durchsichtig werden.</p>
 *
 * <p>{@code glPolygonMode(GL_FRONT_AND_BACK, GL_LINE)} ist Zustand der Rasterisierung und
 * gilt für jeden Zeichenaufruf danach — unabhängig davon, wer die Geometrie hochgeladen hat.
 * Deshalb hat er die Chance, auch mit Sodiums eigenem Chunk-Backend zu greifen. Ob das in
 * der Instanz des Users mit Sodium und Iris wirklich hält, ist GEMESSEN zu belegen und nicht
 * anzunehmen — die Recherche gab dazu nichts Belastbares her.</p>
 *
 * <p>⚠ Der Zustand ist GLOBAL. Er wird deshalb erst nach dem Aufbau der Welt gesetzt und vor
 * dem Ende des Welt-Durchlaufs zurückgenommen, damit Oberfläche und Aufnahme unberührt
 * bleiben. Ein vergessenes Zurücksetzen zeichnet das ganze Spiel als Drahtgitter.</p>
 **/
//?}
public final class WeltDrahtgitter
{
    private static boolean an;
    //? if <26.3 {
    private static boolean angemeldet;
    //?}
    //? if >=26.3 {
    /*private static final Map<RenderPipeline, RenderPipeline> variants = new WeakHashMap<>();
    *///?}

    //? if >=26.3 {
    /*private WeltDrahtgitter() {}
    *///?} else {
    private WeltDrahtgitter()
    {
    }
    //?}

    //? if >=26.3 {
    /*public static boolean istAn() { return an; }
    *///?} else {
    public static boolean istAn()
    {
        return an;
    }
    //?}

    public static void umschalten()
    {
        //? if >=26.3 {
        /*RenderSystem.assertOnRenderThread();
        if (!an && !mchorse.bbs_mod.compat.Pipelines.isWireframeAvailable())
            throw new IllegalStateException("The current graphics device does not support terrain wireframe");
        *///?} else {
        /* Vor 26.3 meldet sich das Drahtgitter beim ersten Umschalten an; vorher ist es aus. */
        if (!angemeldet)
        {
            anmelden();
            angemeldet = true;
        }
        //?}
        an = !an;
    }

    //? if >=26.3 {
    /*/^* Called only by vanilla and Sodium terrain submissions, never by GUI/model draws. ^/
    public static RenderPipeline pipelineFor(RenderPipeline pipeline)
    *///?} else {
    /***
     * Hooks anmelden. Wird einmal beim Start des Clients gerufen.
     *
     * <p>Das Fenster ist eng gewählt: der Modus gilt NUR für das Terrain.</p>
     *
     * <pre>
     *   AFTER_SETUP      -&gt; Drahtgitter an   (die Blöcke werden danach gezeichnet)
     *   BEFORE_ENTITIES  -&gt; Drahtgitter aus  (Darsteller, Formen und Gizmos bleiben massiv)
     *   END              -&gt; Drahtgitter aus  (Sicherheitsnetz)
     * </pre>
     *
     * <p>⛔ WARUM NICHT EINFACH BIS END (erster Entwurf, vom User sofort bemerkt): der
     * Füllmodus ist GLOBALER Zustand und erwischt alles, was danach gezeichnet wird — auch
     * das Replay. „unser replay wird einfach auch davon betroffen". Damit war das Feature
     * gegen seinen eigenen Zweck gerichtet: die Welt soll durchsichtig werden, DAMIT man den
     * Darsteller sieht. Ein Darsteller aus Drahtgitter hilft niemandem.</p>
     *
     * <p>⚠ Das Zurücksetzen läuft BEDINGUNGSLOS, nicht nur wenn {@link #an} gilt: schaltet
     * der Nutzer mitten im Bild um, bliebe der Modus sonst für alles Weitere stehen.</p>
     **/
    public static void anmelden()
    //?}
    {
        //? if >=26.3 {
        /*if (pipeline == null || !an || pipeline.getPolygonMode() == PolygonMode.WIREFRAME) return pipeline;
        ResourceLocation location = pipeline.getLocation();
        String namespace = location.getNamespace();
        String path = location.getPath();
        boolean terrain = (namespace.equals("minecraft") || namespace.equals("sodium"))
            && path.startsWith("pipeline/") && path.contains("terrain");
        if (!terrain) return pipeline;
        if (!mchorse.bbs_mod.compat.Pipelines.isWireframeAvailable())
            throw new IllegalStateException("Terrain wireframe requires an enabled device wireframe feature");
        RenderPipeline existing = variants.get(pipeline);
        if (existing != null) return existing;
        RenderPipeline variant = new WireVariant(pipeline);
        variants.put(pipeline, variant);
        return variant;
    }

    private static final class WireVariant extends RenderPipeline
    {
        WireVariant(RenderPipeline original)
        *///?} else {
        /** ⚠️ PHASE NICHT BELEGT GLEICHWERTIG. Alt war WorldRenderEvents.AFTER_SETUP -- nach dem
         * Aufbau, vor dem Zeichnen. 26.2 kennt diese Zwischenstufe nicht; START_MAIN ist
         * die frueheste verfuegbare. Fuer reines Uniform-Setzen genuegt das, fuer alles was
         * den aufgebauten Zustand LIEST nicht. **/
        WorldRenderEvents.START.register(context ->
        //?}
        {
            //? if >=26.3 {
            /*super(new ResourceLocation("bbs", "wireframe/"
                    + original.getLocation().getNamespace() + "/" + original.getLocation().getPath()),
                original.getVertexShader(), original.getFragmentShader(), original.getShaderDefines(), original.getBindGroupLayouts(),
                original.bbsColorTargetStates().toArray(ColorTargetState[]::new),
                original.getDepthStencilState(), PolygonMode.WIREFRAME, original.isCull(),
                original.bbsVertexFormatBindings().toArray(VertexFormat[]::new),
                original.getPrimitiveTopology(), original.getSortKey());
        }
            *///?} else {
            if (an)
            {
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            }
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context ->
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL));

        /** ⚠️ PHASE NICHT BELEGT GLEICHWERTIG. Alt war WorldRenderEvents.END. END_MAIN ist die
         * naechstliegende Phase; die alte Reihenfolge zwischen END und LAST geht dabei
         * verloren, weil beide jetzt dieselbe Phase treffen. Am Bild zu pruefen. **/
        WorldRenderEvents.LAST.register(context ->
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL));
            //?}
    }
}
