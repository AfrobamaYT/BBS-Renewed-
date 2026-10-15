package com.mojang.blaze3d.vertex;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * vor1205: 1.21s Speicher fuer BufferBuilder. Zwei Rollen: er haelt einen alten BufferBuilder,
 * den BauerAlt.neu fuer jede Bauphase wiederverwendet, und er bietet 1.21s Rohspeicher
 * (reserve/build), den die Schnellpfade direkt beschreiben.
 */
public class ByteBufferBuilder implements AutoCloseable
{
    /* Alte BufferBuilder werden wiederverwendet statt freigegeben: 1.20.1 kennt kein release(),
     * und BBS legt ByteBufferBuilder oft je Bild an (try-with-resources) -- ohne Pool liefe der
     * native Speicher voll. Die Poolgroesse ist durch die gleichzeitig lebenden Speicher begrenzt. */
    private static final ConcurrentLinkedQueue<BufferBuilder> POOL = new ConcurrentLinkedQueue<>();

    private final int kapazitaet;
    private BufferBuilder bauer;

    private long zeiger;
    private int groesse;
    private int belegt;
    private int ausgegeben;

    public ByteBufferBuilder(int kapazitaet)
    {
        this.kapazitaet = Math.max(256, kapazitaet);
    }

    public BufferBuilder bauer()
    {
        if (this.bauer == null)
        {
            BufferBuilder alt = POOL.poll();

            this.bauer = alt != null ? alt : new BufferBuilder(this.kapazitaet);
        }

        return this.bauer;
    }

    /** Wie 1.21: reserviert Bytes und gibt deren Adresse zurueck (gueltig bis zum naechsten reserve). */
    public long reserve(int bytes)
    {
        if (this.zeiger == 0L)
        {
            this.groesse = Math.max(this.kapazitaet, bytes);
            this.zeiger = MemoryUtil.nmemAlloc(this.groesse);
        }
        else if (this.belegt + bytes > this.groesse)
        {
            this.groesse = Math.max(this.groesse * 2, this.belegt + bytes);
            this.zeiger = MemoryUtil.nmemRealloc(this.zeiger, this.groesse);
        }

        if (this.zeiger == 0L)
        {
            throw new OutOfMemoryError("ByteBufferBuilder: " + this.groesse + " Bytes");
        }

        long adresse = this.zeiger + this.belegt;

        this.belegt += bytes;

        return adresse;
    }

    /** Wie 1.21: das seit dem letzten build Reservierte, oder null. */
    public Result build()
    {
        if (this.belegt == this.ausgegeben)
        {
            return null;
        }

        Result ergebnis = new Result(this.ausgegeben, this.belegt - this.ausgegeben);

        this.ausgegeben = this.belegt;

        return ergebnis;
    }

    public void clear()
    {
        this.belegt = this.ausgegeben = 0;

        if (this.bauer != null) this.bauer.clear();
    }

    public void discard()
    {
        this.belegt = this.ausgegeben;

        if (this.bauer != null) this.bauer.discard();
    }

    @Override
    public void close()
    {
        if (this.bauer != null)
        {
            if (this.bauer.building()) this.bauer.discard();

            POOL.offer(this.bauer);
            this.bauer = null;
        }

        if (this.zeiger != 0L)
        {
            MemoryUtil.nmemFree(this.zeiger);
            this.zeiger = 0L;
        }
    }

    public class Result implements AutoCloseable
    {
        private final int anfang;
        private final int laenge;

        private Result(int anfang, int laenge)
        {
            this.anfang = anfang;
            this.laenge = laenge;
        }

        public ByteBuffer byteBuffer()
        {
            return MemoryUtil.memByteBuffer(ByteBufferBuilder.this.zeiger + this.anfang, this.laenge);
        }

        @Override
        public void close()
        {
            if (this.anfang + this.laenge == ByteBufferBuilder.this.belegt)
            {
                ByteBufferBuilder.this.belegt = ByteBufferBuilder.this.ausgegeben = this.anfang;
            }
        }
    }
}
