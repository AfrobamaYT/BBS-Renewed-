package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Der versionsabhaengige Teil der Vertex-Formate (vor 26.1, Mojangs Builder-API ab 1.21):
 * Format aus Attributen bauen und Elemente beschreiben. vor1205 ersetzt diese Klasse.
 */
public final class MojangFormat
{
    private static final Map<String, VertexFormatElement> ELEMENTE = new HashMap<>();

    public static final VertexFormat LEER = VertexFormat.builder().build();

    private MojangFormat()
    {}

    public static VertexFormatElement.Usage nutzung(VertexFormatElement element)
    {
        return element.usage();
    }

    public static int anzahl(VertexFormatElement element)
    {
        return element.count();
    }

    public static VertexFormatElement.Type typ(VertexFormatElement element)
    {
        return element.type();
    }

    public static int versatz(VertexFormat format, VertexFormatElement element)
    {
        return format.getOffset(element);
    }

    public static VertexFormat bauen(List<VertexFormats.Attribut> attribute)
    {
        VertexFormat.Builder builder = VertexFormat.builder();
        Map<String, Integer> zaehler = new HashMap<>();
        String vorher = null;

        for (int i = 0; i < attribute.size(); i++)
        {
            VertexFormats.Attribut attribut = attribute.get(i);
            String schluessel = typ(attribut.format()) + "/" + nutzung(attribut.format()) + "/" + anzahl(attribut.format());
            int nummer = zaehler.merge(schluessel, 1, Integer::sum) - 1;
            String name = attribut.name().equals(vorher) ? "bbs_spalte_" + i : attribut.name();

            builder.add(name, element(attribut.format(), nummer));
            vorher = attribut.name();
        }

        return builder.build();
    }

    private static synchronized VertexFormatElement element(GpuFormat format, int nummer)
    {
        String schluessel = typ(format) + "/" + nutzung(format) + "/" + anzahl(format) + "/" + nummer;
        VertexFormatElement element = ELEMENTE.get(schluessel);

        if (element == null)
        {
            int id = 0;

            while (id < VertexFormatElement.MAX_COUNT && VertexFormatElement.byId(id) != null) id++;

            if (id >= VertexFormatElement.MAX_COUNT)
            {
                throw new IllegalStateException("No free vertex element id left for " + schluessel + " (Minecraft allows " + VertexFormatElement.MAX_COUNT + ")");
            }

            /* Index > 0 erlaubt Mojang nur fuer UV ("Multiple vertex elements of the same type other
             * than UVs", Absturz beim ersten Modell in echter 1.21.4-Instanz, 2026-10-14). Eindeutig
             * ist das Element schon ueber seine id; die Nummer bleibt nur im Schluessel. */
            element = VertexFormatElement.register(id, 0, typ(format), nutzung(format), anzahl(format));
            ELEMENTE.put(schluessel, element);
        }

        return element;
    }

    private static VertexFormatElement.Type typ(GpuFormat format)
    {
        return switch (format.componentType())
        {
            case FLOAT_32 -> VertexFormatElement.Type.FLOAT;
            case UNORM_8, UINT_8 -> VertexFormatElement.Type.UBYTE;
            case SNORM_8, SINT_8 -> VertexFormatElement.Type.BYTE;
            case UNORM_16, UINT_16 -> VertexFormatElement.Type.USHORT;
            case SNORM_16, SINT_16 -> VertexFormatElement.Type.SHORT;
            case UINT_32 -> VertexFormatElement.Type.UINT;
            case SINT_32 -> VertexFormatElement.Type.INT;
            default -> throw new UnsupportedOperationException("Vertex attribute format " + format + " has no Minecraft element type before 26.1");
        };
    }

    private static VertexFormatElement.Usage nutzung(GpuFormat format)
    {
        return format.normalized() ? VertexFormatElement.Usage.COLOR : VertexFormatElement.Usage.GENERIC;
    }

    private static int anzahl(GpuFormat format)
    {
        return format.componentCount();
    }
}
