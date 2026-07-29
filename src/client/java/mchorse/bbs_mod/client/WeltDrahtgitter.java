package mchorse.bbs_mod.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import org.lwjgl.opengl.GL11;

/**
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
 */
public final class WeltDrahtgitter
{
    private static boolean an;

    private WeltDrahtgitter()
    {
    }

    public static boolean istAn()
    {
        return an;
    }

    public static void umschalten()
    {
        an = !an;
    }

    /**
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
     */
    public static void anmelden()
    {
        WorldRenderEvents.AFTER_SETUP.register(context ->
        {
            if (an)
            {
                GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
            }
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(context ->
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL));

        WorldRenderEvents.END.register(context ->
            GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL));
    }
}
