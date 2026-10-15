package mchorse.bbs_mod.compat.vertex;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.HashSet;
import java.util.Set;

/**
 * vor1205: der offene Vertex der 1.21-API (addVertex + set*), je Thread genau einer.
 *
 * <p>1.20.4 verlangt die Attribute in der Reihenfolge des Vertex-Formats und ein endVertex;
 * BBS ruft sie in gemischter Reihenfolge und ohne endVertex (gemessen: setUv vor setColor und
 * umgekehrt, je 7 Ketten). Darum wird gesammelt und erst ausgegeben, wenn der Vertex fertig ist:
 * beim naechsten addVertex, vor jedem alten vertex()-Aufruf eines BufferBuilders (sonst
 * landet fremde Geometrie mitten in einem Quad) und vor end().</p>
 */
public final class Vertexpuffer
{
    private static final ThreadLocal<Vertexpuffer> OFFEN = ThreadLocal.withInitial(Vertexpuffer::new);
    private static final Set<Class<?>> OHNE_FORMAT = new HashSet<>();

    private VertexConsumer besitzer;
    private float x, y, z;
    private int farbe;
    private float u, v;
    private int ueberlagerung;
    private int licht;
    private float nx, ny, nz;

    public static Vertexpuffer offen()
    {
        return OFFEN.get();
    }

    /** Offenen Vertex dieses Threads ausgeben (gleich wem er gehoert). */
    public static void abschliessen()
    {
        OFFEN.get().ausgeben();
    }

    public void beginnen(VertexConsumer wem, float x, float y, float z)
    {
        this.ausgeben();
        this.besitzer = wem;
        this.x = x;
        this.y = y;
        this.z = z;
        this.farbe = 0xFFFFFFFF;
        this.u = this.v = 0F;
        this.ueberlagerung = OverlayTexture.NO_OVERLAY;
        this.licht = LightTexture.FULL_BRIGHT;
        this.nx = 0F;
        this.ny = 1F;
        this.nz = 0F;
    }

    private void pruefen(VertexConsumer wem)
    {
        if (this.besitzer != wem)
        {
            throw new IllegalStateException("Vertex-Attribut ohne vorheriges addVertex an " + wem.getClass().getName());
        }
    }

    public void farbe(VertexConsumer wem, int argb)
    {
        this.pruefen(wem);
        this.farbe = argb;
    }

    public void uv(VertexConsumer wem, float u, float v)
    {
        this.pruefen(wem);
        this.u = u;
        this.v = v;
    }

    public void ueberlagerung(VertexConsumer wem, int gepackt)
    {
        this.pruefen(wem);
        this.ueberlagerung = gepackt;
    }

    public void licht(VertexConsumer wem, int gepackt)
    {
        this.pruefen(wem);
        this.licht = gepackt;
    }

    public void normale(VertexConsumer wem, float x, float y, float z)
    {
        this.pruefen(wem);
        this.nx = x;
        this.ny = y;
        this.nz = z;
    }

    public void ausgeben()
    {
        VertexConsumer ziel = this.besitzer;

        if (ziel == null)
        {
            return;
        }

        /* Zuerst freigeben: das Schreiben ruft vertex() auf, und dessen Haken leert wieder hier. */
        this.besitzer = null;

        int a = this.farbe >>> 24, r = this.farbe >> 16 & 255, g = this.farbe >> 8 & 255, b = this.farbe & 255;
        VertexFormat format = ziel instanceof BufferBuilder bauer ? ((FormatZugriff) (Object) bauer).bbs$format() : null;

        if (format == null)
        {
            if (OHNE_FORMAT.add(ziel.getClass()))
            {
                System.err.println("[bbs vertex] " + ziel.getClass().getName() + ": Format unbekannt, Attribute in Entity-Reihenfolge");
            }

            ziel.vertex(this.x, this.y, this.z).color(r, g, b, a).uv(this.u, this.v)
                .overlayCoords(this.ueberlagerung & 0xFFFF, this.ueberlagerung >>> 16)
                .uv2(this.licht & 0xFFFF, this.licht >>> 16).normal(this.nx, this.ny, this.nz).endVertex();

            return;
        }

        for (VertexFormatElement element : format.getElements())
        {
            switch (element.getUsage())
            {
                case POSITION -> ziel.vertex(this.x, this.y, this.z);
                case COLOR -> ziel.color(r, g, b, a);
                case NORMAL -> ziel.normal(this.nx, this.ny, this.nz);
                case UV ->
                {
                    switch (element.getIndex())
                    {
                        case 0 -> ziel.uv(this.u, this.v);
                        case 1 -> ziel.overlayCoords(this.ueberlagerung & 0xFFFF, this.ueberlagerung >>> 16);
                        default -> ziel.uv2(this.licht & 0xFFFF, this.licht >>> 16);
                    }
                }
                /* PADDING ueberspringt BufferBuilder selbst. GENERIC sind Iris' Zusatzattribute
                 * (mc_Entity, Tangente, ...): Vanilla schreibt sie nie, Iris fuellt sie in seinem
                 * BufferBuilder-Mixin -- also hier wie Vanilla nichts tun. */
                case PADDING, GENERIC -> {}
                default -> throw new IllegalStateException("Vertex-Element " + element + " vor 1.20.5 nicht abgebildet");
            }
        }

        ziel.endVertex();
    }
}
