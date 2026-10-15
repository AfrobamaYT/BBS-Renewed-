package mchorse.bbs_mod.compat;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.alt.GpuFormat;
import mchorse.bbs_mod.compat.alt.VertexFormats;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.2's attribute view of a VertexFormat, for the versions before 26.1. Formats built by
 * {@link VertexFormats} carry 26.2's list; vanilla formats are described from their Mojang
 * elements. Injected into VertexFormat, see {@link PoseStackRotations}.
 */
public interface VertexFormatAlt
{
    default void bbs$setAlt(List<VertexFormats.Attribut> attribute, int groesse, int stepRate)
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default List<VertexFormats.Attribut> bbs$attribute()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default int bbs$groesse()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    /** 26.2: 0 = per vertex, n = advance once every n instances. */
    default int getStepRate()
    {
        throw new UnsupportedOperationException("compat-Mixin fehlt");
    }

    default boolean bbsEigenes()
    {
        return this.bbs$attribute() != null;
    }

    /** 26.2's attributes of this format; vanilla formats described from their elements. */
    default List<VertexFormats.Attribut> bbsAttribute()
    {
        if (this.bbs$attribute() != null) return this.bbs$attribute();

        VertexFormat format = (VertexFormat) this;
        List<VertexFormats.Attribut> liste = new ArrayList<>();
        List<String> namen = format.getElementAttributeNames();

        for (int i = 0; i < format.getElements().size(); i++)
        {
            var element = format.getElements().get(i);
            liste.add(new VertexFormats.Attribut(namen.get(i), mchorse.bbs_mod.compat.alt.MojangFormat.versatz(format, element), gpuFormat(element)));
        }

        return liste;
    }

    /** 26.2's VertexFormat.getElement(name): here the first attribute of that name (offset, format). */
    default VertexFormats.Attribut getElement(String name)
    {
        for (VertexFormats.Attribut attribut : this.bbsAttribute())
        {
            if (attribut.name().equals(name)) return attribut;
        }

        return null;
    }

    default int bbsGroesse()
    {
        return this.bbs$attribute() != null ? this.bbs$groesse() : ((VertexFormat) this).getVertexSize();
    }

    private static GpuFormat gpuFormat(com.mojang.blaze3d.vertex.VertexFormatElement element)
    {
        boolean normiert = mchorse.bbs_mod.compat.alt.MojangFormat.nutzung(element) == com.mojang.blaze3d.vertex.VertexFormatElement.Usage.NORMAL
            || mchorse.bbs_mod.compat.alt.MojangFormat.nutzung(element) == com.mojang.blaze3d.vertex.VertexFormatElement.Usage.COLOR;
        String kanaele = switch (mchorse.bbs_mod.compat.alt.MojangFormat.anzahl(element)) { case 1 -> "R"; case 2 -> "RG"; case 3 -> "RGB"; default -> "RGBA"; };
        String typ = switch (mchorse.bbs_mod.compat.alt.MojangFormat.typ(element))
        {
            case FLOAT -> "32_FLOAT";
            case UBYTE -> normiert ? "8_UNORM" : "8_UINT";
            case BYTE -> normiert ? "8_SNORM" : "8_SINT";
            case USHORT -> normiert ? "16_UNORM" : "16_UINT";
            case SHORT -> normiert ? "16_SNORM" : "16_SINT";
            case UINT -> "32_UINT";
            case INT -> "32_SINT";
        };

        return GpuFormat.valueOf(kanaele + typ);
    }
}
