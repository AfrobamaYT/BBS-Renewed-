package mchorse.bbs_mod.film.regie;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Begehbarer Weg zwischen zwei Punkten — mit Minecrafts eigener Mob-Wegfindung statt eines
 * Nachbaus: {@link GroundPathNavigation} kennt Stufen, Platten, Tueren, Zaeune, Fallhoehe,
 * Wasser und Lava. Sie braucht einen Mob; der Hilfs-Zombie ist ungefaehr spielergross und
 * wird nie in die Welt eingefuegt (keine Ticks, keine Netzwerknachricht).
 *
 * <p>Danach wird der Knotenweg wie im Design vereinfacht: von jedem Punkt springt der Weg zum
 * fernsten Punkt (hoechstens 28 weiter), dessen gerade Verbindung begehbar ist.</p>
 */
final class RegiePfadsuche
{
    private static final double REICHWEITE = 128D;

    private Level welt;
    private Zombie helfer;
    private GroundPathNavigation navigation;

    /** @return Punkte x, y (Fuesse), z; null, wenn Minecraft keinen Weg findet */
    List<double[]> weg(Level level, double ax, double ay, double az, double bx, double by, double bz)
    {
        if (this.welt != level || this.helfer == null)
        {
            this.welt = level;
            this.helfer = new Zombie(level);
            this.helfer.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(REICHWEITE);
            this.navigation = new GroundPathNavigation(this.helfer, level);
        }

        this.helfer.setPos(ax, ay, az);
        this.helfer.setOnGround(true);

        Path pfad = this.navigation.createPath(BlockPos.containing(bx, by + 0.5D, bz), 1);

        if (pfad == null || pfad.getNodeCount() == 0)
        {
            return null;
        }

        List<double[]> punkte = new ArrayList<>();

        for (int i = 0; i < pfad.getNodeCount(); i++)
        {
            Node knoten = pfad.getNode(i);

            punkte.add(new double[] {knoten.x + 0.5D, knoten.y, knoten.z + 0.5D});
        }

        punkte.set(0, new double[] {ax, ay, az});

        if (pfad.canReach())
        {
            punkte.set(punkte.size() - 1, new double[] {bx, by, bz});
        }
        else
        {
            punkte.add(new double[] {bx, by, bz});
        }

        return vereinfachen(level, punkte);
    }

    /**
     * Hoehe des begehbaren Bodens an (x, z) unter der Bezugshoehe (Platten und Stufen genau).
     * Gesucht wird bis 48 Bloecke tief — wie surfaceBelow im Design; mit 3 Bloecken begann der
     * Trail eines fliegenden Spielers in der Luft (gemessen 2026-10-11).
     */
    static double boden(Level level, double x, double z, double bezug)
    {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = (int) Math.floor(bezug + 1.2D); y >= (int) Math.floor(bezug - 48D); y--)
        {
            pos.set(bx, y, bz);

            VoxelShape form = level.getBlockState(pos).getCollisionShape(level, pos);

            if (!form.isEmpty())
            {
                return y + form.max(Direction.Axis.Y);
            }
        }

        return bezug;
    }

    private static List<double[]> vereinfachen(Level level, List<double[]> punkte)
    {
        if (punkte.size() < 3)
        {
            return punkte;
        }

        List<double[]> aus = new ArrayList<>();
        int i = 0;

        aus.add(punkte.get(0));

        while (i < punkte.size() - 1)
        {
            int weiter = i + 1;

            for (int j = Math.min(punkte.size() - 1, i + 28); j > weiter; j -= 2)
            {
                if (frei(level, punkte.get(i), punkte.get(j)))
                {
                    weiter = j;
                    break;
                }
            }

            aus.add(punkte.get(weiter));
            i = weiter;
        }

        return aus;
    }

    /** Ist die gerade Verbindung begehbar? Alle 0,3 m drei Spuren quer (±0,34 m), Stufe hoechstens 1. */
    private static boolean frei(Level level, double[] a, double[] b)
    {
        double dx = b[0] - a[0];
        double dz = b[2] - a[2];
        double laenge = Math.hypot(dx, dz);
        int schritte = (int) Math.max(2, Math.ceil(laenge / 0.3D));
        double nx = -dz / Math.max(1.0E-9D, laenge);
        double nz = dx / Math.max(1.0E-9D, laenge);
        double vorher = boden(level, a[0], a[2], a[1]);

        for (int h = 1; h <= schritte; h++)
        {
            double t = h / (double) schritte;
            double x = a[0] + dx * t;
            double z = a[2] + dz * t;
            double bezug = a[1] + (b[1] - a[1]) * t;

            for (double quer : new double[] {-0.34D, 0D, 0.34D})
            {
                double y = boden(level, x + nx * quer, z + nz * quer, bezug);

                if (Math.abs(y - vorher) > 1.01D || !luft(level, x + nx * quer, y, z + nz * quer))
                {
                    return false;
                }
            }

            vorher = boden(level, x, z, bezug);
        }

        return true;
    }

    /** Zwei Bloecke Kopffreiheit ueber dem Boden. */
    private static boolean luft(Level level, double x, double y, double z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int d = 0; d < 2; d++)
        {
            pos.set(Math.floor(x), Math.floor(y + 0.01D) + d, Math.floor(z));

            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || level.getFluidState(pos).is(FluidTags.LAVA))
            {
                return false;
            }
        }

        return true;
    }
}
