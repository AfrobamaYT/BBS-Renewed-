/* Coded by Afrobama/Claude */
package mchorse.bbs_mod.crowd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * On-disk record of a MobArmy walk simulation. Each frame stores
 * {@code (x, y, z, yaw)} for every army mob at that tick.
 * Playback re-applies these positions verbatim (no AI / pathfinding /
 * physics) so a 5000-mob crowd recorded at /tick rate 1 plays back
 * cheaply at /tick rate 20 — the frames are pre-computed.
 *
 * <h2>v3 compressed binary format (big-endian via DataOutputStream)</h2>
 * <p>Legacy v1/v2 stored {@code 16 bytes/mob/frame} of raw floats. At
 * 100k mobs × 3 min (3600 frames) that is ~5.7 GB — an OOM / disk bomb.
 * v3 fixes this with three stacked techniques:</p>
 * <ol>
 *   <li><b>Quantization.</b> Position is stored as a world-RELATIVE
 *       {@code int16} per axis: {@code q = round((world - origin) / scale)}.
 *       Origin = the bake bounding-box MIDPOINT (so the relative offset is
 *       symmetric {@code [-extent/2, +extent/2]} and fits the SIGNED int16
 *       range {@code [-32767, +32767]} — a min-based origin would push the
 *       far edge to step 65535 and overflow/clamp). Scale is chosen per
 *       bake so the {@code int16} range spans the full army extent, with a
 *       floor of {@link #MIN_POS_SCALE} = 1 cm. For any army smaller than
 *       ~655 m on an axis the scale stays at the 1 cm floor (round-trip
 *       error ≤ 5 mm, visually lossless); a multi-km army auto-widens the
 *       scale (e.g. a 5 km axis → ~7.6 cm/step, ≤ 3.8 cm error), still
 *       visually lossless for crowd positions.
 *       Yaw is stored as {@code int16} via {@code round(angle * 65536/360)}
 *       (~0.0055° precision) — wrapping arithmetic, no origin needed.
 *       This drops 16 bytes/mob/frame to 8 bytes/mob/frame before any
 *       further compression.</li>
 *   <li><b>Delta encoding.</b> Each frame's quantized int16 values are
 *       stored as the difference against the PREVIOUS frame's quantized
 *       values. Most mobs barely move per tick, so the deltas are tiny
 *       (frequently zero), which DEFLATE then crushes.</li>
 *   <li><b>DEFLATE.</b> The whole delta-int16 stream is run through
 *       {@link Deflater} at {@link #DEFLATE_LEVEL}. Pure JDK, zero new
 *       dependency, no native zstd/lz4 binary to ship on Windows.</li>
 * </ol>
 *
 * <pre>
 *  magic         : int32  = 0x43525744 ('CRWD')
 *  version       : int32  = 3
 *  mob_count N   : int32
 *  uuid_table    : N × {long msb, long lsb, utf entityType}
 *  origin        : 3 × double (ox, oy, oz)   — world-space quantization origin
 *  pos_scale     : 3 × double (sx, sy, sz)   — metres per int16 step, per axis
 *  frame_count F : int32
 *  raw_len       : int32   — length of the uncompressed delta stream
 *  comp_len      : int32   — length of the DEFLATE payload
 *  payload       : comp_len bytes (DEFLATE of the delta-int16 stream)
 *
 *  delta stream (uncompressed) = F × N × {int16 dqx, int16 dqy, int16 dqz, int16 dyaw}
 *      where for frame 0 the "previous" quantized values are all 0,
 *      so frame 0 carries the absolute quantized values.
 * </pre>
 *
 * <p>v1 and v2 (raw-float) caches still load for backward compatibility.</p>
 *
 * <h2>In-RAM columnar store (audit #3, Coded by Afrobama/Claude, 2026-05-30)</h2>
 * <p>The legacy in-memory representation was a {@code List<Frame>} where each
 * {@link Frame} held a {@code MobFrame[]} of four-float objects. At 100k mobs ×
 * 3600 frames that is 360M heap objects (~8.6 GB with object headers) which
 * OOMs and GC-death-spirals. This is now a flat <b>columnar</b> store:</p>
 * <ul>
 *   <li><b>Recording (bake) caches</b> hold raw <b>float columns</b>
 *       ({@code recX/recY/recZ/recYaw}), 16 B/mob/frame with no per-object
 *       header. The full-bake bounding box (needed for the quantization
 *       origin/scale) is only known when the bake ends, so we keep floats
 *       live and quantize once at {@link #save(File)} — producing byte-identical
 *       v3 output to the legacy code.</li>
 *   <li><b>Loaded caches</b> hold quantized <b>int16 columns</b>
 *       ({@code qx/qy/qz/qyaw}), 8 B/mob/frame — the playback-resident form that
 *       actually OOMed. Accessors dequantize on read (cost is noise vs the
 *       per-mob {@code getEntity(uuid)} lookup that dominates playback).</li>
 * </ul>
 * <p>Both stores are frame-major: {@code idx = frame * columnMobCount + i}.
 * External callers read positions through {@link #getX(int, int)} /
 * {@link #getY(int, int)} / {@link #getZ(int, int)} / {@link #getYaw(int, int)}.
 * An {@link #guardAllocation(long, long)} OOM-guard refuses any columnar
 * allocation whose transient peak exceeds {@link #HEAP_BUDGET_FRACTION} of the
 * max heap (logging first, since the {@code :7777} HTTP debugger is dead while
 * the render thread is blocked), and {@link #MAX_SAVABLE_CELLS} caps a bake at
 * the largest cell count the v3 {@code int rawLen} can serialize so a bake can
 * never grow into an unsaveable state.</p>
 */
public final class CrowdCache
{
    public static final int MAGIC = 0x43525744; /* 'CRWD' */
    public static final int VERSION = 3;
    public static final int VERSION_V2 = 2;
    public static final int VERSION_LEGACY = 1;

    /** Metres represented by one int16 step at the finest (floored) scale.
     *  0.01 m = 1 cm. int16 spans 65535 steps → ±327.67 m around origin at
     *  this floor (655.35 m total per axis). Larger armies auto-widen. */
    public static final double MIN_POS_SCALE = 0.01D;

    /** int16 range used for quantization sizing (65535 representable steps). */
    private static final double INT16_SPAN = 65535.0D;

    /** Yaw → int16 conversion: angle * (65536 / 360). Wraps cleanly. */
    private static final double YAW_TO_Q = 65536.0D / 360.0D;
    private static final double YAW_FROM_Q = 360.0D / 65536.0D;

    /** DEFLATE compression level. 6 = JDK default; good ratio/speed balance.
     *  The data is already delta-int16 so the extra CPU of level 9 buys
     *  little; 6 keeps big-bake saves fast. */
    private static final int DEFLATE_LEVEL = 6;

    /** {@code rawLen = cells * 8} must stay a positive int (disk format v3
     *  limit). Caps total (frames × mobs) cells so a bake can never grow past
     *  what {@link #save(File)} can serialize — a clean, logged refusal instead
     *  of a {@link NegativeArraySizeException} at save time. 100k mobs → 2684
     *  frames (~2.2 min @ 20 TPS). Lifting this needs a chunked v4 disk format. */
    public static final long MAX_SAVABLE_CELLS = (long) (Integer.MAX_VALUE / 8) - 1L;

    /** Refuse a columnar allocation whose transient peak exceeds this fraction
     *  of {@code -Xmx} rather than triggering a GC death-spiral / OOM. */
    private static final double HEAP_BUDGET_FRACTION = 0.6D;

    private static final Logger LOGGER = LoggerFactory.getLogger("bbs/crowdcache");

    /* ---- static probe fields (read live via :7777 /eval as
     *  mchorse.bbs_mod.crowd.CrowdCache.lastBake{Bytes,Mobs,Frames}) ---- */

    public static volatile long lastBakeBytes = 0L;
    public static volatile long lastBakeMobs = 0L;
    public static volatile long lastBakeFrames = 0L;

    public final List<UUID> uuids = new ArrayList<>();
    /** Entity type id (e.g. "minecraft:villager") parallel to {@link #uuids}.
     *  Lets {@code /mobarmy bake play} auto-respawn missing entities. */
    public final List<String> entityTypes = new ArrayList<>();

    /** UUID → index into the columns. Built lazily. */
    private Map<UUID, Integer> uuidIndex;

    /** Locked at first {@link #recordFrame} / at load. -1 = not yet fixed. */
    private int columnMobCount = -1;
    private int columnFrameCount = 0;

    /* RECORDING store (bake): raw floats appended live, frame-major,
     *  idx = frame * columnMobCount + i. Null on a loaded cache. */
    private float[] recX;
    private float[] recY;
    private float[] recZ;
    private float[] recYaw;
    private int recCapacityFrames;

    /* LOADED store: quantized int16 columns, frame-major,
     *  idx = frame * columnMobCount + i. Null on a recording cache. */
    private short[] qx;
    private short[] qy;
    private short[] qz;
    private short[] qyaw;

    /* Quantization params for the LOADED int16 store (from the file header). */
    private double ox;
    private double oy;
    private double oz;
    private double sx;
    private double sy;
    private double sz;

    /**
     * Quantize a yaw angle (degrees) into a wrapping int16 step.
     * Mirrors vanilla angle packing; round-trips within ~0.0055°.
     */
    private static short quantizeYaw(float yaw)
    {
        /* (short) cast wraps modulo 65536, which is exactly the angle wrap
         *  we want — a yaw of 370° and 10° map to the same step. */
        return (short) Math.round(yaw * YAW_TO_Q);
    }

    /** Dequantize an int16 yaw step back into degrees in [0, 360). */
    private static float dequantizeYaw(short q)
    {
        /* (q & 0xFFFF) treats the step as unsigned so we get [0, 360). */
        return (float) ((q & 0xFFFF) * YAW_FROM_Q);
    }

    /**
     * Quantize a world coordinate to an int16 step relative to origin.
     * Clamped to the int16 range so a stray out-of-bounds mob can never
     * corrupt neighbouring values (it just pins to the army edge).
     */
    private static short quantizePos(double world, double origin, double scale)
    {
        long q = Math.round((world - origin) / scale);
        if (q < Short.MIN_VALUE)
        {
            q = Short.MIN_VALUE;
        }
        else if (q > Short.MAX_VALUE)
        {
            q = Short.MAX_VALUE;
        }
        return (short) q;
    }

    /** Dequantize an int16 position step back into a world coordinate. */
    private static double dequantizePos(short q, double origin, double scale)
    {
        return origin + (q * scale);
    }

    /**
     * Choose a position scale (metres per int16 step) for a given axis
     * extent. The int16 range holds {@link #INT16_SPAN} steps; scale grows
     * just enough to cover the extent, never finer than {@link #MIN_POS_SCALE}
     * (1 cm). Small armies therefore keep 1 cm precision; huge ones widen.
     */
    private static double scaleFor(double extent)
    {
        if (!(extent > 0D))
        {
            return MIN_POS_SCALE;
        }
        double needed = extent / INT16_SPAN;
        return Math.max(MIN_POS_SCALE, needed);
    }

    public int mobCount()
    {
        return this.uuids.size();
    }

    public int frameCount()
    {
        return this.columnFrameCount;
    }

    public Map<UUID, Integer> getUuidIndex()
    {
        if (this.uuidIndex == null)
        {
            this.uuidIndex = new HashMap<>();
            for (int i = 0; i < this.uuids.size(); i++)
            {
                this.uuidIndex.put(this.uuids.get(i), i);
            }
        }
        return this.uuidIndex;
    }

    /* ---- columnar read accessors (replace the legacy frame.mobs[i].x) ----
     *  A recording cache reads float columns; a loaded cache dequantizes the
     *  int16 columns on the fly. Both index frame-major. */

    public float getX(int frame, int i)
    {
        int idx = frame * this.columnMobCount + i;
        return this.recX != null ? this.recX[idx]
            : (float) dequantizePos(this.qx[idx], this.ox, this.sx);
    }

    public float getY(int frame, int i)
    {
        int idx = frame * this.columnMobCount + i;
        return this.recY != null ? this.recY[idx]
            : (float) dequantizePos(this.qy[idx], this.oy, this.sy);
    }

    public float getZ(int frame, int i)
    {
        int idx = frame * this.columnMobCount + i;
        return this.recZ != null ? this.recZ[idx]
            : (float) dequantizePos(this.qz[idx], this.oz, this.sz);
    }

    public float getYaw(int frame, int i)
    {
        int idx = frame * this.columnMobCount + i;
        return this.recYaw != null ? this.recYaw[idx]
            : dequantizeYaw(this.qyaw[idx]);
    }

    /**
     * Optional pre-size hint from the bake command (known fixed duration) so
     * the recording columns allocate once at full size, avoiding the doubling
     * copy-peak. Call BEFORE the first {@link #recordFrame}.
     */
    public void presizeRecording(int expectedFrames)
    {
        if (this.columnMobCount < 0 && expectedFrames > 0)
        {
            this.recCapacityFrames = expectedFrames;
        }
    }

    /**
     * Append a freshly-recorded tick. Caller passes positions in mob-list
     * order (must match {@link #uuids}). The mob count is locked on the first
     * call; a mob-count change mid-bake throws (the MobArmy roster is frozen on
     * the first bake tick, so this never legitimately happens). Refuses to grow
     * past {@link #MAX_SAVABLE_CELLS} so a bake can never become unsaveable.
     */
    public void recordFrame(double[] xs, double[] ys, double[] zs, float[] yaws)
    {
        int n = this.uuids.size();

        if (this.columnMobCount < 0)
        {
            this.columnMobCount = n;
            this.recCapacityFrames = Math.max(256, this.recCapacityFrames);
            int cap = this.recCapacityFrames * n;
            long bytes = (long) cap * 4L * 4L;
            this.guardAllocation(bytes, bytes);
            this.recX = new float[cap];
            this.recY = new float[cap];
            this.recZ = new float[cap];
            this.recYaw = new float[cap];
        }

        if (n != this.columnMobCount)
        {
            throw new IllegalStateException("CrowdCache.recordFrame mob count changed mid-bake: "
                + n + " vs locked " + this.columnMobCount);
        }

        long cells = (long) (this.columnFrameCount + 1) * (long) n;
        if (cells > MAX_SAVABLE_CELLS)
        {
            CrowdCache.lastBakeMobs = n;
            CrowdCache.lastBakeFrames = this.columnFrameCount;
            LOGGER.error("[CrowdCache] BAKE CAPPED at {} frames x {} mobs ({} cells): next frame would exceed "
                    + "MAX_SAVABLE_CELLS {} (disk v3 rawLen int limit). Stop and save now; longer bakes need a "
                    + "chunked v4 format.", this.columnFrameCount, n, cells, MAX_SAVABLE_CELLS);
            throw new IllegalStateException("CrowdCache bake exceeded MAX_SAVABLE_CELLS " + MAX_SAVABLE_CELLS
                + " (mobs=" + n + ", frames=" + this.columnFrameCount + ")");
        }

        if (this.columnFrameCount >= this.recCapacityFrames)
        {
            this.growRecording();
        }

        int base = this.columnFrameCount * n;
        for (int i = 0; i < n; i++)
        {
            this.recX[base + i] = (float) xs[i];
            this.recY[base + i] = (float) ys[i];
            this.recZ[base + i] = (float) zs[i];
            this.recYaw[base + i] = yaws[i];
        }
        this.columnFrameCount++;

        CrowdCache.lastBakeMobs = n;
        CrowdCache.lastBakeFrames = this.columnFrameCount;
        CrowdCache.lastBakeBytes = (long) this.columnFrameCount * (long) n * 16L;
    }

    private void growRecording()
    {
        int newCapFrames = this.recCapacityFrames * 2;
        int n = this.columnMobCount;
        long newCells = (long) newCapFrames * (long) n;
        long oldBytes = (long) this.recCapacityFrames * (long) n * 4L * 4L;
        long newBytes = newCells * 4L * 4L;
        /* Copy-peak: old arrays are still live during arraycopy → guard the
         *  combined old+new footprint, not just the new array. */
        this.guardAllocation(newBytes, oldBytes + newBytes);

        int newCap = (int) newCells;
        float[] nx = new float[newCap];
        float[] ny = new float[newCap];
        float[] nz = new float[newCap];
        float[] nyaw = new float[newCap];

        int used = this.columnFrameCount * n;
        System.arraycopy(this.recX, 0, nx, 0, used);
        System.arraycopy(this.recY, 0, ny, 0, used);
        System.arraycopy(this.recZ, 0, nz, 0, used);
        System.arraycopy(this.recYaw, 0, nyaw, 0, used);

        this.recX = nx;
        this.recY = ny;
        this.recZ = nz;
        this.recYaw = nyaw;
        this.recCapacityFrames = newCapFrames;

        LOGGER.info("[CrowdCache] Bake float-scratch grew to {} frames cap (~{} MB).",
            newCapFrames, newBytes / (1024L * 1024L));
    }

    public void save(File file) throws IOException
    {
        if (!file.getParentFile().exists())
        {
            file.getParentFile().mkdirs();
        }

        /* Snapshot all mutable column state into locals ONCE. save() runs on
         *  the MobArmy-BakeSaver thread; recordFrame writes happen-before
         *  saver.start() (the same server thread calls start()), so these
         *  reads are safely published. Capturing into locals also avoids any
         *  torn re-deref if a stray write occurred. */
        int n = this.uuids.size();
        int f = this.columnFrameCount;
        int cmc = this.columnMobCount;
        boolean rec = this.recX != null;
        float[] rx = this.recX;
        float[] ry = this.recY;
        float[] rz = this.recZ;
        float[] ryaw = this.recYaw;
        short[] lqx = this.qx;
        short[] lqy = this.qy;
        short[] lqz = this.qz;
        short[] lqyaw = this.qyaw;
        double lox = this.ox;
        double loy = this.oy;
        double loz = this.oz;
        double lsx = this.sx;
        double lsy = this.sy;
        double lsz = this.sz;

        /* ---- 1. Compute per-bake quantization origin + scale. ----
         *  Origin = bounding-box MIDPOINT so relative coords are symmetric
         *  [-extent/2, +extent/2] and fit the signed int16 range; scale
         *  auto-widens so int16 spans the full extent, with a 1 cm floor
         *  for small armies. */
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (int fi = 0; fi < f; fi++)
        {
            int b = fi * cmc;
            for (int i = 0; i < n; i++)
            {
                int idx = b + i;
                float vx = rec ? rx[idx] : (float) dequantizePos(lqx[idx], lox, lsx);
                float vy = rec ? ry[idx] : (float) dequantizePos(lqy[idx], loy, lsy);
                float vz = rec ? rz[idx] : (float) dequantizePos(lqz[idx], loz, lsz);
                if (vx < minX) minX = vx;
                if (vy < minY) minY = vy;
                if (vz < minZ) minZ = vz;
                if (vx > maxX) maxX = vx;
                if (vy > maxY) maxY = vy;
                if (vz > maxZ) maxZ = vz;
            }
        }

        /* Empty bake guard — no frames/mobs means no extent. Use a sane
         *  zero origin and floor scale; the loops below simply produce an
         *  empty payload. */
        if (!Double.isFinite(minX))
        {
            minX = 0D;
            minY = 0D;
            minZ = 0D;
            maxX = 0D;
            maxY = 0D;
            maxZ = 0D;
        }

        double sox = (minX + maxX) * 0.5D;
        double soy = (minY + maxY) * 0.5D;
        double soz = (minZ + maxZ) * 0.5D;
        double ssx = scaleFor(maxX - minX);
        double ssy = scaleFor(maxY - minY);
        double ssz = scaleFor(maxZ - minZ);

        /* ---- 2. Build the uncompressed delta-int16 stream. ----
         *  4 shorts (x,y,z,yaw) × 2 bytes = 8 bytes/mob/frame. Compute in long
         *  first: recordFrame's MAX_SAVABLE_CELLS gate bounds bake caches, but a
         *  LOADED cache being re-saved carries a frame count straight from disk
         *  with no such gate, so guard the int rawLen against overflow here. */
        long rawLen64 = (long) f * (long) n * 8L;

        if (rawLen64 > Integer.MAX_VALUE)
        {
            throw new IOException("CrowdCache delta stream " + rawLen64 + " bytes exceeds int rawLen limit "
                + "(mobs=" + n + ", frames=" + f + "); needs a chunked v4 format.");
        }

        int rawLen = (int) rawLen64;
        byte[] raw = new byte[rawLen];

        /* Previous-frame quantized values, for delta encoding. */
        short[] prevX = new short[n];
        short[] prevY = new short[n];
        short[] prevZ = new short[n];
        short[] prevYaw = new short[n];

        int p = 0;
        for (int fi = 0; fi < f; fi++)
        {
            int b = fi * cmc;
            for (int i = 0; i < n; i++)
            {
                int idx = b + i;
                float vx = rec ? rx[idx] : (float) dequantizePos(lqx[idx], lox, lsx);
                float vy = rec ? ry[idx] : (float) dequantizePos(lqy[idx], loy, lsy);
                float vz = rec ? rz[idx] : (float) dequantizePos(lqz[idx], loz, lsz);
                float vyaw = rec ? ryaw[idx] : dequantizeYaw(lqyaw[idx]);

                short qcx = quantizePos(vx, sox, ssx);
                short qcy = quantizePos(vy, soy, ssy);
                short qcz = quantizePos(vz, soz, ssz);
                short qcyaw = quantizeYaw(vyaw);

                short dx = (short) (qcx - prevX[i]);
                short dy = (short) (qcy - prevY[i]);
                short dz = (short) (qcz - prevZ[i]);
                short dyaw = (short) (qcyaw - prevYaw[i]);

                raw[p++] = (byte) (dx >>> 8);
                raw[p++] = (byte) dx;
                raw[p++] = (byte) (dy >>> 8);
                raw[p++] = (byte) dy;
                raw[p++] = (byte) (dz >>> 8);
                raw[p++] = (byte) dz;
                raw[p++] = (byte) (dyaw >>> 8);
                raw[p++] = (byte) dyaw;

                prevX[i] = qcx;
                prevY[i] = qcy;
                prevZ[i] = qcz;
                prevYaw[i] = qcyaw;
            }
        }

        /* ---- 3. DEFLATE the delta stream. ---- */
        byte[] compressed = deflate(raw);

        /* ---- 4. Diagnostic: bytes-before (legacy raw-float layout) vs
         *  bytes-after (quantized + delta + DEFLATE). Logged once/bake. */
        long bytesBefore = (long) f * n * 16L;
        long bytesAfter = compressed.length;
        double ratio = bytesBefore > 0 ? (double) bytesAfter / (double) bytesBefore : 0D;
        LOGGER.info("[CrowdCache] Compressed {} mobs x {} frames: {} -> {} bytes payload "
                + "({}% of legacy raw, {}x smaller). origin=({}, {}, {}) scale=({}, {}, {}) m/step",
            n, f, bytesBefore, bytesAfter,
            String.format("%.2f", ratio * 100D),
            String.format("%.1f", bytesBefore > 0 ? (double) bytesBefore / (double) Math.max(1L, bytesAfter) : 0D),
            String.format("%.2f", sox), String.format("%.2f", soy), String.format("%.2f", soz),
            String.format("%.4f", ssx), String.format("%.4f", ssy), String.format("%.4f", ssz));

        /* ---- 4b. Probes: post-save int16 playback-resident footprint. ---- */
        CrowdCache.lastBakeMobs = n;
        CrowdCache.lastBakeFrames = f;
        CrowdCache.lastBakeBytes = (long) f * (long) n * 8L;
        LOGGER.info("[CrowdCache] In-RAM int16 footprint after save: {} bytes ({} mobs x {} frames x 8B).",
            CrowdCache.lastBakeBytes, n, f);

        /* ---- 5. Write header + payload. ----
         *  BufferedOutputStream is essential — without it every write is a
         *  separate syscall. */
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), 256 * 1024)))
        {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(n);

            for (int i = 0; i < n; i++)
            {
                UUID u = this.uuids.get(i);
                out.writeLong(u.getMostSignificantBits());
                out.writeLong(u.getLeastSignificantBits());
                String type = i < this.entityTypes.size() ? this.entityTypes.get(i) : "";
                out.writeUTF(type == null ? "" : type);
            }

            out.writeDouble(sox);
            out.writeDouble(soy);
            out.writeDouble(soz);
            out.writeDouble(ssx);
            out.writeDouble(ssy);
            out.writeDouble(ssz);

            out.writeInt(f);
            out.writeInt(rawLen);
            out.writeInt(compressed.length);
            out.write(compressed, 0, compressed.length);
        }
    }

    public static CrowdCache load(File file) throws IOException
    {
        /* Buffered + sized for fast bulk read. */
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file), 256 * 1024)))
        {
            int magic = in.readInt();
            if (magic != MAGIC)
            {
                throw new IOException("Bad CrowdCache magic 0x" + Integer.toHexString(magic) + " in " + file);
            }
            int version = in.readInt();
            if (version != VERSION && version != VERSION_V2 && version != VERSION_LEGACY)
            {
                throw new IOException("Unsupported CrowdCache version " + version);
            }

            CrowdCache cache = new CrowdCache();
            int mobCount = in.readInt();
            for (int i = 0; i < mobCount; i++)
            {
                long msb = in.readLong();
                long lsb = in.readLong();
                cache.uuids.add(new UUID(msb, lsb));

                /* v2+ stores entity type per uuid; v1 didn't. Default to
                 *  empty string for legacy caches → respawn won't work for
                 *  them, but existing entities still get teleported. */
                if (version == VERSION || version == VERSION_V2)
                {
                    cache.entityTypes.add(in.readUTF());
                }
                else
                {
                    cache.entityTypes.add("");
                }
            }

            if (version == VERSION)
            {
                loadCompressed(in, cache, mobCount);
            }
            else
            {
                loadLegacyFloats(in, cache, mobCount);
            }

            return cache;
        }
    }

    /* ===== helpers ===== */

    /**
     * Allocate the loaded int16 columns (8 B/mob/frame). OOM-guarded against
     * the full footprint. Leaves {@link #columnFrameCount} for the caller to
     * set only after a successful fill, so a mid-load failure never reports a
     * full frame count over a partially-filled buffer.
     */
    private void allocLoaded(int mobCount, int frameCount) throws IOException
    {
        /* Cell count in long first: frameCount*mobCount can exceed 2^31-1 for a
         *  large on-disk cache, and an int multiply would wrap to a small/negative
         *  array size, then the fill loop writes out-of-bounds. Reject cleanly. */
        long cells64 = (long) frameCount * (long) mobCount;

        if (cells64 > Integer.MAX_VALUE)
        {
            throw new IOException("CrowdCache loaded cell count " + cells64 + " exceeds int array limit "
                + "(mobs=" + mobCount + ", frames=" + frameCount + ").");
        }

        long bytes = cells64 * 2L * 4L;
        this.guardAllocation(bytes, bytes);
        this.columnMobCount = mobCount;
        int cells = (int) cells64;
        this.qx = new short[cells];
        this.qy = new short[cells];
        this.qz = new short[cells];
        this.qyaw = new short[cells];
    }

    /** Read the v3 quantized + delta + DEFLATE frame block into int16 columns. */
    private static void loadCompressed(DataInputStream in, CrowdCache cache, int mobCount) throws IOException
    {
        cache.ox = in.readDouble();
        cache.oy = in.readDouble();
        cache.oz = in.readDouble();
        cache.sx = in.readDouble();
        cache.sy = in.readDouble();
        cache.sz = in.readDouble();

        int frameCount = in.readInt();
        int rawLen = in.readInt();
        int compLen = in.readInt();

        byte[] compressed = new byte[compLen];
        in.readFully(compressed);

        byte[] raw = inflate(compressed, rawLen);

        cache.allocLoaded(mobCount, frameCount);

        short[] prevX = new short[mobCount];
        short[] prevY = new short[mobCount];
        short[] prevZ = new short[mobCount];
        short[] prevYaw = new short[mobCount];

        int p = 0;
        for (int fi = 0; fi < frameCount; fi++)
        {
            int outBase = fi * mobCount;
            for (int i = 0; i < mobCount; i++)
            {
                short dx = (short) (((raw[p] & 0xFF) << 8) | (raw[p + 1] & 0xFF));
                short dy = (short) (((raw[p + 2] & 0xFF) << 8) | (raw[p + 3] & 0xFF));
                short dz = (short) (((raw[p + 4] & 0xFF) << 8) | (raw[p + 5] & 0xFF));
                short dyaw = (short) (((raw[p + 6] & 0xFF) << 8) | (raw[p + 7] & 0xFF));
                p += 8;

                short cqx = (short) (prevX[i] + dx);
                short cqy = (short) (prevY[i] + dy);
                short cqz = (short) (prevZ[i] + dz);
                short cqyaw = (short) (prevYaw[i] + dyaw);

                int idx = outBase + i;
                cache.qx[idx] = cqx;
                cache.qy[idx] = cqy;
                cache.qz[idx] = cqz;
                cache.qyaw[idx] = cqyaw;

                prevX[i] = cqx;
                prevY[i] = cqy;
                prevZ[i] = cqz;
                prevYaw[i] = cqyaw;
            }
        }

        cache.columnFrameCount = frameCount;
    }

    /**
     * Read the legacy v1/v2 raw-float frame block. There is no quantization
     * header on disk, so derive origin/scale from the float bounding box (the
     * same midpoint + {@link #scaleFor} pipeline {@link #save(File)} uses), then
     * quantize into the int16 store so a legacy cache lands in the same
     * representation as a v3 one. This adds the ≤5 mm / ~0.0055° quantization
     * the v3 format already imposes on every cache — invisible for a
     * teleport-driven crowd.
     */
    private static void loadLegacyFloats(DataInputStream in, CrowdCache cache, int mobCount) throws IOException
    {
        int frameCount = in.readInt();

        /* Cell count in long first — same int-overflow guard as allocLoaded: a
         *  large legacy cache could wrap frameCount*mobCount to an undersized
         *  array and the fill loop would write out-of-bounds. */
        long cells64 = (long) frameCount * (long) mobCount;

        if (cells64 > Integer.MAX_VALUE)
        {
            throw new IOException("CrowdCache legacy cell count " + cells64 + " exceeds int array limit "
                + "(mobs=" + mobCount + ", frames=" + frameCount + ").");
        }

        int cells = (int) cells64;

        long bytes = cells64 * 4L * 4L;
        cache.guardAllocation(bytes, bytes);
        float[] lx = new float[cells];
        float[] ly = new float[cells];
        float[] lz = new float[cells];
        float[] lyaw = new float[cells];

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        int p = 0;
        for (int fr = 0; fr < frameCount; fr++)
        {
            for (int m = 0; m < mobCount; m++)
            {
                float vx = in.readFloat();
                float vy = in.readFloat();
                float vz = in.readFloat();
                float vyaw = in.readFloat();
                lx[p] = vx;
                ly[p] = vy;
                lz[p] = vz;
                lyaw[p] = vyaw;
                if (vx < minX) minX = vx;
                if (vy < minY) minY = vy;
                if (vz < minZ) minZ = vz;
                if (vx > maxX) maxX = vx;
                if (vy > maxY) maxY = vy;
                if (vz > maxZ) maxZ = vz;
                p++;
            }
        }

        if (!Double.isFinite(minX))
        {
            minX = 0D;
            minY = 0D;
            minZ = 0D;
            maxX = 0D;
            maxY = 0D;
            maxZ = 0D;
        }

        cache.ox = (minX + maxX) * 0.5D;
        cache.oy = (minY + maxY) * 0.5D;
        cache.oz = (minZ + maxZ) * 0.5D;
        cache.sx = scaleFor(maxX - minX);
        cache.sy = scaleFor(maxY - minY);
        cache.sz = scaleFor(maxZ - minZ);

        cache.allocLoaded(mobCount, frameCount);
        for (int idx = 0; idx < cells; idx++)
        {
            cache.qx[idx] = quantizePos(lx[idx], cache.ox, cache.sx);
            cache.qy[idx] = quantizePos(ly[idx], cache.oy, cache.sy);
            cache.qz[idx] = quantizePos(lz[idx], cache.oz, cache.sz);
            cache.qyaw[idx] = quantizeYaw(lyaw[idx]);
        }
        cache.columnFrameCount = frameCount;
    }

    /**
     * OOM-guard: refuse a columnar allocation whose transient peak exceeds
     * {@link #HEAP_BUDGET_FRACTION} of {@code -Xmx}. Logs first (the {@code :7777}
     * HTTP debugger is dead while the render thread is blocked, so the log is
     * the primary signal), then throws {@link OutOfMemoryError} so the
     * bake/load path fails cleanly instead of GC-death-spiralling.
     *
     * @param requestedBytes the size of the array(s) being allocated
     * @param peakBytes      the worst transient footprint (e.g. old+new on a grow)
     */
    private void guardAllocation(long requestedBytes, long peakBytes)
    {
        long max = Runtime.getRuntime().maxMemory();
        long budget = (long) (max * HEAP_BUDGET_FRACTION);

        if (peakBytes > budget)
        {
            LOGGER.error("[CrowdCache] REFUSING columnar allocation: transient peak {} bytes exceeds {}% of "
                    + "-Xmx ({} of {} bytes). request={} mobs={} frames={}. Raise -Xmx or shorten the bake.",
                peakBytes, (int) (HEAP_BUDGET_FRACTION * 100D), budget, max,
                requestedBytes, this.columnMobCount, this.columnFrameCount);
            throw new OutOfMemoryError("CrowdCache columnar peak " + peakBytes + " bytes exceeds "
                + (int) (HEAP_BUDGET_FRACTION * 100D) + "% heap budget " + budget);
        }
    }

    /** DEFLATE a byte array; returns the exact-length compressed payload. */
    private static byte[] deflate(byte[] raw)
    {
        Deflater deflater = new Deflater(DEFLATE_LEVEL, true);
        try
        {
            deflater.setInput(raw);
            deflater.finish();

            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(64, raw.length / 2));
            byte[] buffer = new byte[64 * 1024];
            while (!deflater.finished())
            {
                int written = deflater.deflate(buffer);
                bos.write(buffer, 0, written);
            }
            return bos.toByteArray();
        }
        finally
        {
            deflater.end();
        }
    }

    /** INFLATE a DEFLATE payload back into a byte array of known length. */
    private static byte[] inflate(byte[] compressed, int rawLen) throws IOException
    {
        Inflater inflater = new Inflater(true);
        try
        {
            inflater.setInput(compressed);

            byte[] raw = new byte[rawLen];
            int total = 0;
            while (total < rawLen)
            {
                int produced = inflater.inflate(raw, total, rawLen - total);
                if (produced == 0)
                {
                    /* No more output and not finished → corrupt/truncated. */
                    if (inflater.finished() || inflater.needsInput())
                    {
                        break;
                    }
                }
                total += produced;
            }

            if (total != rawLen)
            {
                throw new IOException("CrowdCache inflate produced " + total + " of " + rawLen + " expected bytes");
            }
            return raw;
        }
        catch (DataFormatException e)
        {
            throw new IOException("CrowdCache DEFLATE payload corrupt", e);
        }
        finally
        {
            inflater.end();
        }
    }

}
