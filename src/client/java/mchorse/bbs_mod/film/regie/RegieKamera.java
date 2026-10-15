package mchorse.bbs_mod.film.regie;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Die Kamera EINES Bildes: Projektion, Blickdrehung (ohne Verschiebung) und Augpunkt.
 *
 * <p>Wird je Bild aus dem Kamerazustand der Weltzeichnung gefuellt und erst danach benutzt —
 * mit einer Matrix aus dem Vorbild schwimmen die Karten beim Drehen hinterher.</p>
 */
public final class RegieKamera
{
    public final Matrix4f projektion = new Matrix4f();
    public final Matrix4f blick = new Matrix4f();
    public double x;
    public double y;
    public double z;
    public boolean gesetzt;

    private final Vector4f v = new Vector4f();

    public void setzen(Matrix4fc projektion, Matrix4fc blick, double x, double y, double z)
    {
        this.projektion.set(projektion);
        this.blick.set(blick);
        this.x = x;
        this.y = y;
        this.z = z;
        this.gesetzt = true;
    }

    /**
     * Weltpunkt auf den Bildschirm. aus[0]/aus[1] = Pixel (oben links), aus[2] = Tiefe in
     * Bloecken vor der Kamera.
     *
     * @return true, wenn der Punkt VOR der Kamera liegt. Hinter der Kamera stehen x/y
     *         gespiegelt — der Randpfeil dreht die Richtung dann selbst um.
     */
    public boolean projizieren(double wx, double wy, double wz, float breite, float hoehe, float[] aus)
    {
        this.v.set((float) (wx - this.x), (float) (wy - this.y), (float) (wz - this.z), 1F);
        this.blick.transform(this.v);

        float tiefe = -this.v.z;

        this.projektion.transform(this.v);

        float w = Math.abs(this.v.w) < 1.0E-6F ? 1.0E-6F : this.v.w;

        aus[0] = (this.v.x / w + 1F) * 0.5F * breite;
        aus[1] = (1F - this.v.y / w) * 0.5F * hoehe;
        aus[2] = tiefe;

        return tiefe > 0.05F;
    }

    /** Brennweite in Pixeln (fuer Groessen, die mit der Entfernung schrumpfen). */
    public float brennweite(float hoehe)
    {
        return hoehe * 0.5F * Math.abs(this.projektion.m11());
    }

    /** Blickrichtung in Weltkoordinaten (Einheitsvektor). */
    public Vector3f richtung(Vector3f aus)
    {
        return new Matrix4f(this.blick).invert().transformDirection(aus.set(0F, 0F, -1F)).normalize();
    }
}
