package mchorse.bbs_mod.compat.alt;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.List;

/**
 * vor1205: VertexFormat von 1.20.4 (Konstruktor mit Namen -> Element, Elemente frei erzeugbar,
 * Versatz = Summe der Bytegroessen davor; so rechnet auch Mojangs Konstruktor).
 */
public final class MojangFormat
{
    public static final VertexFormat LEER = new VertexFormat(ImmutableMap.of());

    private MojangFormat()
    {}

    public static VertexFormatElement.Usage nutzung(VertexFormatElement element)
    {
        return element.getUsage();
    }

    public static int anzahl(VertexFormatElement element)
    {
        return element.getCount();
    }

    public static VertexFormatElement.Type typ(VertexFormatElement element)
    {
        return element.getType();
    }

    public static int versatz(VertexFormat format, VertexFormatElement element)
    {
        int versatz = 0;

        for (VertexFormatElement e : format.getElements())
        {
            if (e == element) return versatz;

            versatz += e.getByteSize();
        }

        throw new IllegalArgumentException("Element gehoert nicht zum Format: " + element);
    }

    public static VertexFormat bauen(List<VertexFormats.Attribut> attribute)
    {
        ImmutableMap.Builder<String, VertexFormatElement> elemente = ImmutableMap.builder();
        String vorher = null;

        for (int i = 0; i < attribute.size(); i++)
        {
            VertexFormats.Attribut attribut = attribute.get(i);
            GpuFormat format = attribut.format();
            String name = attribut.name().equals(vorher) ? "bbs_spalte_" + i : attribut.name();

            elemente.put(name, new VertexFormatElement(0, typ(format), format.normalized()
                ? VertexFormatElement.Usage.COLOR : VertexFormatElement.Usage.GENERIC, format.componentCount()));
            vorher = attribut.name();
        }

        return new VertexFormat(elemente.build());
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
            default -> throw new UnsupportedOperationException("Vertex-Attributformat " + format + " hat vor 1.20.5 keinen Elementtyp");
        };
    }
}
