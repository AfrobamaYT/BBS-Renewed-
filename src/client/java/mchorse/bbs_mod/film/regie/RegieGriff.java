package mchorse.bbs_mod.film.regie;

/**
 * Welchen Marker trifft ein Mausstrahl im Editor? Ort-Marker und Startpunkt werden an ihrer
 * Bodenflaeche gefasst (Strahl schneidet die Ebene innerhalb der Platte bzw. des Rings),
 * Blick-Marker als Punkt in der Luft. Bei mehreren Treffern gewinnt der vorderste. Der
 * Richtungspfeil des Startrings ist ein eigener Griff: er dreht statt zu verschieben.
 */
public final class RegieGriff
{
    /** Kennung des Startpunkts (Marker liefern ihre UUID). */
    public static final String START = "start";
    /** Kennung des Richtungspfeils am Startring. */
    public static final String START_RICHTUNG = "start-richtung";
    /** Blick-Marker: so nah muss der Strahl am Punkt vorbei. */
    public static final double BLICK_RADIUS = 0.6D;

    private RegieGriff()
    {
    }

    /**
     * @param ox Strahlursprung, {@code dx} Richtung (muss nicht normiert sein)
     * @return UUID des Markers, {@link #START} oder null
     */
    public static String fassen(Regie regie, double ox, double oy, double oz, double dx, double dy, double dz)
    {
        double laenge = Math.sqrt(dx * dx + dy * dy + dz * dz);
        String bester = null;
        double vorn = Double.MAX_VALUE;

        if (laenge < 1E-9D)
        {
            return null;
        }

        dx /= laenge;
        dy /= laenge;
        dz /= laenge;

        for (RegieMarker m : regie.marker.getList())
        {
            double t = m.istOrt()
                ? ebene(ox, oy, oz, dx, dy, dz, m.x.get(), m.y.get(), m.z.get(), RegieTreffer.ORT_RADIUS)
                : punkt(ox, oy, oz, dx, dy, dz, m.x.get(), m.y.get(), m.z.get());

            if (t < vorn)
            {
                vorn = t;
                bester = m.uuid.get();
            }
        }

        if (regie.startGesetzt.get())
        {
            double t = ebene(ox, oy, oz, dx, dy, dz, regie.startX.get(), regie.startY.get(), regie.startZ.get(),
                RegieTreffer.ORT_RADIUS * 1.6D);

            /* Der Startring liegt oft unter einem Marker: nur fassen, wenn er klar vorn ist. */
            if (t < vorn - 0.5D)
            {
                bester = pfeil(regie, ox + dx * t, oz + dz * t) ? START_RICHTUNG : START;
            }
        }

        return bester;
    }

    /**
     * Liegt der Bodenpunkt auf dem Richtungspfeil? Der Pfeil sitzt 1,2-1,8 m vor der Mitte in
     * Blickrichtung (regie_start.fsh: 0,98-1,48 R), der Griff ist etwas grosszuegiger.
     */
    static boolean pfeil(Regie regie, double x, double z)
    {
        double gier = Math.toRadians(regie.startYaw.get());
        double rx = -Math.sin(gier);
        double rz = Math.cos(gier);
        double vx = x - regie.startX.get();
        double vz = z - regie.startZ.get();
        double laengs = vx * rx + vz * rz;

        return laengs >= 1.1D && laengs <= 1.9D && Math.abs(vx * rz - vz * rx) <= 0.45D;
    }

    /** Gier in Grad (wie Minecraft: 0 = +z, -90 = +x), mit der man vom Startpunkt auf (x, z) blickt. */
    public static float gier(Regie regie, double x, double z)
    {
        return (float) Math.toDegrees(Math.atan2(-(x - regie.startX.get()), z - regie.startZ.get()));
    }

    /** Abstand bis zur waagerechten Ebene durch den Punkt, wenn der Schnitt im Radius liegt. */
    private static double ebene(double ox, double oy, double oz, double dx, double dy, double dz,
        double x, double y, double z, double radius)
    {
        if (Math.abs(dy) < 1E-6D)
        {
            return Double.MAX_VALUE;
        }

        double t = (y - oy) / dy;

        return t > 0D && Math.hypot(ox + dx * t - x, oz + dz * t - z) <= radius ? t : Double.MAX_VALUE;
    }

    /** Abstand bis zum Lotfusspunkt, wenn der Strahl nah genug am Punkt vorbeigeht. */
    private static double punkt(double ox, double oy, double oz, double dx, double dy, double dz, double x, double y, double z)
    {
        double t = (x - ox) * dx + (y - oy) * dy + (z - oz) * dz;
        double ax = ox + dx * t - x;
        double ay = oy + dy * t - y;
        double az = oz + dz * t - z;

        return t > 0D && ax * ax + ay * ay + az * az <= BLICK_RADIUS * BLICK_RADIUS ? t : Double.MAX_VALUE;
    }
}
