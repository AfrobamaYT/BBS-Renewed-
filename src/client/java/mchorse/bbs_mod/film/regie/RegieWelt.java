package mchorse.bbs_mod.film.regie;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Weltschicht der Regiehilfe: der Trail. Beim Aufnehmen die Route vom Spieler zum naechsten
 * Ort-Marker, im Editor die Abschnitte vom Startpunkt durch die Ort-Marker (gedimmt).
 * Regeln wie im Design: Route alle 0,15 s neu, Laufgeschwindigkeit der Chevrons nach
 * Zeitpolster — ruhig („Losgehen in …"), zuegig („Jetzt los"), schnell („Rennen", „zu spaet").
 */
public final class RegieWelt
{
    private static final RegiePfadsuche SUCHE = new RegiePfadsuche();

    /** Wann das Vorschaufeld des Editors zuletzt gezeichnet wurde (die UI wird vor der Welt gesammelt). */
    private static long editorZeit;
    private static UIFilmPanel editorPanel;
    private static long letzteZeit;

    private static String routeZiel;
    private static float routeAlter = 1F;
    private static List<double[]> routePunkte;
    private static float routePhase;
    private static float hell = 1F;
    private static float dimm;


    private RegieWelt()
    {
    }

    /** Aus der Editor-Vorschau: dieses Bild zeigt den Film im Planen-Modus. */
    static void editorMelden(UIFilmPanel panel)
    {
        editorPanel = panel;
        editorZeit = System.nanoTime();
    }

    /**
     * Aus {@code GameRenderer.render3dHud} @HEAD (ueber SpielRendererLogik): nach der Welt und
     * nach dem Composite eines Shaderpacks. ⛔ Nicht END_MAIN: Vitrails Composite laeuft danach
     * und ueberschreibt das ganze Bild — der Trail war unter Complementary unsichtbar (gemessen
     * 2026-10-11). ⛔ Nicht AFTER_TRANSLUCENT_TERRAIN: dort ist Vanillas Durchgang noch offen
     * („Close the existing render pass before creating a new one", Absturz beim ersten Bild).
     */
    public static void zeichnen(CameraRenderState kamera)
    {
        Minecraft mc = Minecraft.getInstance();
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0L ? 0F : Math.min(0.1F, (jetzt - letzteZeit) / 1.0E9F);

        letzteZeit = jetzt;

        if (mc.level == null || kamera == null)
        {
            return;
        }

        Recorder aufnahme = BBSModClient.getFilms().getRecorder();

        if (aufnahme != null && BBSSettings.recordingOverlays.get())
        {
            boolean laeuft = !aufnahme.hasNotStarted();
            float tick = aufnahme.getTick() + (laeuft ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0F);

            String ziel = route(mc.level, aufnahme.film.regie, tick, laeuft, dt, kamera);
            net.minecraft.world.entity.player.Player spieler = mc.player;

            RegieWeltMarken.zeichnen(mc.level, aufnahme.film.regie, tick, true, !laeuft, ziel,
                spieler == null ? 0D : spieler.getX(), spieler == null ? 0D : spieler.getZ(), kamera);
        }
        /* Waehrend einer Weltbild-Fahrt (Hybrid-Render) oder eines Video-Exports keine Editor-Hilfen ins Bild zeichnen:
         * Marker-Platten und Strahlen landeten sonst im gerenderten Film (gemessen 2026-10-14). */
        else if (editorPanel != null && editorPanel.getData() != null && jetzt - editorZeit < 250_000_000L
            && !com.mcstudio.see.SubTick.aktiv() && !mchorse.bbs_mod.film.VideoExportSession.laeuftExport())
        {
            float tick = editorPanel.getCursor() + (editorPanel.isRunning() ? mc.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 0F);

            RegieWeltWege.zeichnen(mc.level, editorPanel.getData().regie, dt, kamera);
            RegieWeltMarken.zeichnen(mc.level, editorPanel.getData().regie, tick, false, false, null, 0D, 0D, kamera);
        }
    }

    /**
     * Beim Aufnehmen: vom Spieler zum naechsten Ort-Marker, solange er nicht schon dort steht.
     *
     * @return UUID des Ziels, zu dem die Route gerade fuehrt, oder null
     */
    private static String route(Level level, Regie regie, float tick, boolean laeuft, float dt, CameraRenderState kamera)
    {
        net.minecraft.world.entity.player.Player spieler = Minecraft.getInstance().player;
        RegieMarker ziel = null;

        if (spieler == null)
        {
            return null;
        }

        for (RegieMarker m : RegieAnsicht.sortiert(regie))
        {
            if (m.istOrt() && m.ende.get() > tick)
            {
                boolean drin = Math.hypot(spieler.getX() - m.x.get(), spieler.getZ() - m.z.get()) < RegieTreffer.ORT_RADIUS * 0.95F;

                ziel = tick >= m.start.get() && drin ? null : m;
                break;
            }
        }

        if (ziel == null)
        {
            routePunkte = null;
            return null;
        }

        routeAlter += dt;

        if (!ziel.uuid.get().equals(routeZiel) || routeAlter > 0.15F || routePunkte == null)
        {
            routeZiel = ziel.uuid.get();
            routeAlter = 0F;
            /* Vom Boden unter dem Spieler aus suchen: mitten im Sprung (oder Flug) findet die
             * Mob-Navigation keinen Start, und die Route liefe gerade durch Waende (gemessen). */
            double fuesse = RegiePfadsuche.boden(level, spieler.getX(), spieler.getZ(), spieler.getY());

            routePunkte = SUCHE.weg(level, spieler.getX(), fuesse, spieler.getZ(), ziel.x.get(), ziel.y.get(), ziel.z.get());

            if (routePunkte == null)
            {
                routePunkte = List.of(new double[] {spieler.getX(), spieler.getY(), spieler.getZ()}, new double[] {ziel.x.get(), ziel.y.get(), ziel.z.get()});
            }
        }

        List<double[]> punkte = new ArrayList<>(routePunkte);

        punkte.set(0, new double[] {spieler.getX(), spieler.getY(), spieler.getZ()});

        if (punkte.size() > 2 && Math.hypot(punkte.get(1)[0] - spieler.getX(), punkte.get(1)[2] - spieler.getZ()) < 0.6D)
        {
            punkte.remove(1);
        }

        RegieBand band = RegieBand.bauen(punkte, 0.95D, (x, z, bezug) -> RegiePfadsuche.boden(level, x, z, bezug));
        float vorrat = (ziel.start.get() - tick) / 20F;
        float polster = vorrat - band.laenge / RegieWeg.GEHEN - 0.35F;
        /* Laufen, Helligkeit, Dimmen je Modus (Design: zu spaet / warten / jetzt los / rennen) */
        float[] modus = vorrat <= 0F ? new float[] {6F, 1.1F, 0F} : polster > 0.2F ? new float[] {0.7F, 0.7F, 1F}
            : polster > -1.2F ? new float[] {3.4F, 1F, 0F} : new float[] {5.5F, 1.1F, 0F};
        float lauf = laeuft ? modus[0] : Math.min(modus[0], 1.2F);
        float weich = 1F - (float) Math.exp(-6F * dt);

        hell += (modus[1] - hell) * weich;
        dimm += (modus[2] - dimm) * weich;
        routePhase += dt * lauf;
        RegieWeltZug.band(band, ziel.farbe.get(), new float[] {band.laenge, routePhase, hell, dimm}, 1.2F, kamera);

        return routeZiel;
    }
}
