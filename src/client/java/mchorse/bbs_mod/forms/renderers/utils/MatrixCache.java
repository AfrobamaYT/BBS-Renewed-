package mchorse.bbs_mod.forms.renderers.utils;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.ArrayList;
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
        Slot slot = this.slots.get(path);

        if (slot == null)
        {
            slot = new Slot(path);
            this.slots.put(path, slot);
        }

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
