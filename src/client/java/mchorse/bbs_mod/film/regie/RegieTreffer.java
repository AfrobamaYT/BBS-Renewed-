package mchorse.bbs_mod.film.regie;

import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Treffer beim Aufnehmen: steht der Spieler am Ort, sitzt der Blick? Und wie lange schon —
 * daraus wird nach dem Zeitfenster „erledigt" oder „vorbei" (Design: mehr als 0,3 s).
 */
public final class RegieTreffer
{
    /** Radius der Bodenplatte; „drin" gilt ab 95 % davon. */
    public static final float ORT_RADIUS = 1.2F;
    /** Blick sitzt, wenn die Richtung hoechstens so weit danebenliegt (und naeher als 60 m). */
    public static final float BLICK_WINKEL = 8.5F;

    /** Spielerfuesse beim Aufnehmen. */
    public boolean spielerDa;
    public double spielerX;
    public double spielerZ;

    /** Je Marker-UUID: Sekunden drin (Ort) bzw. Blick gesessen. */
    private final Map<String, float[]> konto = new HashMap<>();
    private float letzteZeit = Float.NaN;
    private float dt;

    /** Neue Aufnahme: Treffer vergessen. */
    public void leeren()
    {
        this.konto.clear();
        this.letzteZeit = Float.NaN;
    }

    /** Je Bild einmal: Zeitschritt seit dem letzten Bild (nur, solange die Aufnahme zaehlt). */
    void bild(float sekunden, boolean zaehlt)
    {
        this.dt = zaehlt && !Float.isNaN(this.letzteZeit) ? Math.max(0F, sekunden - this.letzteZeit) : 0F;
        this.letzteZeit = zaehlt ? sekunden : Float.NaN;
    }

    /** Steht der Spieler am Ort? Zaehlt die Zeit, solange das Fenster laeuft. */
    boolean drin(RegieMarker marker, boolean aktiv)
    {
        boolean drin = this.spielerDa
            && Math.hypot(this.spielerX - marker.x.get(), this.spielerZ - marker.z.get()) < ORT_RADIUS * 0.95F;

        if (drin && aktiv)
        {
            this.konto(marker)[0] += this.dt;
        }

        return drin;
    }

    /** Sitzt der Blick auf dem Punkt? Nur im laufenden Fenster gefragt. */
    boolean fang(RegieMarker marker, RegieKamera kamera, Vector3f richtung)
    {
        float dx = (float) (marker.x.get() - kamera.x);
        float dy = (float) (marker.y.get() - kamera.y);
        float dz = (float) (marker.z.get() - kamera.z);
        float abstand = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean fang = abstand < 60F && abstand > 1.0E-4F
            && (dx * richtung.x + dy * richtung.y + dz * richtung.z) / abstand > Math.cos(Math.toRadians(BLICK_WINKEL));

        if (fang)
        {
            this.konto(marker)[1] += this.dt;
        }

        return fang;
    }

    /** Lange genug drin bzw. hingesehen? */
    boolean erledigt(RegieMarker marker)
    {
        float[] zeit = this.konto(marker);

        return zeit[0] > 0.3F || zeit[1] > 0.3F;
    }

    private float[] konto(RegieMarker marker)
    {
        return this.konto.computeIfAbsent(marker.uuid.get(), (uuid) -> new float[2]);
    }
}
