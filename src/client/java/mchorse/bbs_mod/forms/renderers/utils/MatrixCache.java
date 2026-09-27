package mchorse.bbs_mod.forms.renderers.utils;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MatrixCache
{
    private static final MatrixCacheEntry EMPTY = new MatrixCacheEntry(null, null);

    /**
     * Stable storage for evaluated matrices. Clearing a render pass only advances the generation;
     * bone names normally stay identical, so their matrices and hash nodes are reused next pass.
     * This is the CPU-side equivalent of keeping Blender's evaluated data alive until it is dirty.
     */
    private final Map<String, Slot> slots = new HashMap<>();
    private final List<Map.Entry<String, MatrixCacheEntry>> activeEntries = new ArrayList<>();
    private final Set<String> activeKeys = new AbstractSet<>()
    {
        @Override
        public Iterator<String> iterator()
        {
            Iterator<Map.Entry<String, MatrixCacheEntry>> iterator = MatrixCache.this.activeEntries.iterator();

            return new Iterator<>()
            {
                @Override
                public boolean hasNext()
                {
                    return iterator.hasNext();
                }

                @Override
                public String next()
                {
                    return iterator.next().getKey();
                }
            };
        }

        @Override
        public int size()
        {
            return MatrixCache.this.activeEntries.size();
        }

        @Override
        public boolean contains(Object path)
        {
            return MatrixCache.this.has(String.valueOf(path));
        }
    };
    private int generation = 1;

    /** Je Anfragestelle das zuletzt angefragte Namensobjekt und sein Slot (siehe {@link #acquire(int, String)}). */
    private String[] stellenNamen = new String[0];
    private Slot[] stellenSlots = new Slot[0];

    public void clear()
    {
        this.activeEntries.clear();
        this.generation += 1;

        /* Practically unreachable, but never let an ancient slot become active after integer wrap. */
        if (this.generation == 0)
        {
            for (Slot slot : this.slots.values())
            {
                slot.generation = 0;
            }

            this.generation = 1;
        }
    }

    public boolean has(String path)
    {
        Slot slot = this.slots.get(path);

        return slot != null && slot.generation == this.generation;
    }

    public MatrixCacheEntry get(String path)
    {
        Slot slot = this.slots.get(path);

        return slot != null && slot.generation == this.generation ? slot.entry : EMPTY;
    }

    /** Activate and return a cache-owned pair of writable matrices for this pass. */
    public MatrixCacheEntry acquire(String path)
    {
        return this.aktivieren(this.slot(path));
    }

    /**
     * Wie {@link #acquire(String)}, fuer Aufrufer, die dieselben Namen stets in derselben Folge
     * anfragen (die Knochen eines Modells). Ist an dieser Stelle zuletzt genau dieses Namensobjekt
     * angefragt worden, entfaellt die Hash-Suche; sonst wird wie bisher gesucht und die Stelle neu
     * belegt. Slots werden nie entfernt, eine gemerkte Stelle bleibt also gueltig.
     *
     * <p>Die Suche je Knochen und Erfassung kostete im Rig-Film 1,5-2,4 % des Renderthreads,
     * danach 0,9-1,0 % (27.09.2026): {@code captureMatrices} laeuft mehrmals je Bild ueber alle Knochen.</p>
     */
    public MatrixCacheEntry acquire(int stelle, String path)
    {
        if (stelle < this.stellenNamen.length && this.stellenNamen[stelle] == path)
        {
            return this.aktivieren(this.stellenSlots[stelle]);
        }

        Slot slot = this.slot(path);

        if (stelle >= this.stellenNamen.length)
        {
            int laenge = Math.max(stelle + 1, this.stellenNamen.length * 2);

            this.stellenNamen = Arrays.copyOf(this.stellenNamen, laenge);
            this.stellenSlots = Arrays.copyOf(this.stellenSlots, laenge);
        }

        this.stellenNamen[stelle] = path;
        this.stellenSlots[stelle] = slot;

        return this.aktivieren(slot);
    }

    private Slot slot(String path)
    {
        Slot slot = this.slots.get(path);

        if (slot == null)
        {
            slot = new Slot(path);
            this.slots.put(path, slot);
        }

        return slot;
    }

    private MatrixCacheEntry aktivieren(Slot slot)
    {
        if (slot.generation != this.generation)
        {
            slot.generation = this.generation;
            slot.entry.clearEvaluatedRotation();
            this.activeEntries.add(slot);
        }

        return slot.entry;
    }

    public void put(String path, Matrix4f matrix, Matrix4f origin)
    {
        this.put(path, matrix, origin, null);
    }

    public void put(String path, Matrix4f matrix, Matrix4f origin, Vector3f evaluatedRotation)
    {
        MatrixCacheEntry entry = this.acquire(path);

        entry.matrix().set(matrix);
        entry.origin().set(origin);
        entry.setEvaluatedRotation(evaluatedRotation);
    }

    public Set<String> keySet()
    {
        return this.activeKeys;
    }

    public Iterable<Map.Entry<String, MatrixCacheEntry>> entrySet()
    {
        return this.activeEntries;
    }

    private static final class Slot implements Map.Entry<String, MatrixCacheEntry>
    {
        private final String path;
        private final MatrixCacheEntry entry = new MatrixCacheEntry(new Matrix4f(), new Matrix4f());
        private int generation;

        private Slot(String path)
        {
            this.path = path;
        }

        @Override
        public String getKey()
        {
            return this.path;
        }

        @Override
        public MatrixCacheEntry getValue()
        {
            return this.entry;
        }

        @Override
        public MatrixCacheEntry setValue(MatrixCacheEntry value)
        {
            throw new UnsupportedOperationException("Matrix cache slots are stable");
        }
    }
}
