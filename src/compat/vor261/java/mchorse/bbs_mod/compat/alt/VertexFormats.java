package mchorse.bbs_mod.compat.alt;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.compat.VertexFormatAlt;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.2's VertexFormat.builder(stepRate) with named GpuFormat attributes, for the versions
 * before 26.1. It builds a Mojang VertexFormat (registered elements, so BufferBuilder and
 * vanilla code still accept it) and keeps 26.2's own attribute list on it
 * ({@link VertexFormatAlt}); the raw GL layer binds from that list, never from Mojang's
 * offsets.
 */
public final class VertexFormats
{
    private VertexFormats()
    {}

    public static Builder builder(int stepRate)
    {
        return new Builder(stepRate);
    }

    /** One attribute as 26.2 describes it; columns of a matrix are separate entries with the same name. */
    public record Attribut(String name, int offset, GpuFormat format)
    {}

    public static class Builder
    {
        private final List<Attribut> attribute = new ArrayList<>();
        private final int stepRate;
        private int offset;

        private Builder(int stepRate)
        {
            this.stepRate = stepRate;
        }

        private void anlegen(String name, int offset, GpuFormat format)
        {
            if (this.attribute.size() >= 16) throw new IllegalArgumentException("Having more than 16 attributes are not supported");
            if (offset % format.byteAlignment() != 0) throw new IllegalArgumentException(name + " is not aligned to " + format.byteAlignment() + " as required by " + format);

            this.attribute.add(new Attribut(name, offset, format));
        }

        private void eindeutig(String name)
        {
            for (Attribut attribut : this.attribute)
            {
                if (attribut.name().equals(name)) throw new IllegalArgumentException("Another vertex attribute exists with the name " + name);
            }
        }

        public Builder addAttribute(String name, GpuFormat format)
        {
            this.eindeutig(name);
            this.anlegen(name, this.offset, format);
            this.offset += format.blockSize();

            return this;
        }

        public Builder addAttribute(String name, int stride, GpuFormat format)
        {
            this.eindeutig(name);
            this.anlegen(name, this.offset, format);
            this.offset += stride;

            return this;
        }

        public Builder addAttribute(String name, GpuFormat format, int columns)
        {
            this.eindeutig(name);

            for (int i = 0; i < columns; i++)
            {
                this.anlegen(name, this.offset, format);
                this.offset += format.blockSize();
            }

            return this;
        }

        public Builder addAttribute(String name, int offset, int stride, GpuFormat format, int columns)
        {
            this.eindeutig(name);
            int ende = offset;

            for (int i = 0; i < columns; i++)
            {
                this.anlegen(name, ende, format);
                ende += stride;
            }

            this.offset = Math.max(this.offset, ende);

            return this;
        }

        public VertexFormat build()
        {
            if (this.offset % 4 != 0) throw new IllegalStateException("Vertex size must be a multiple of 4, was " + this.offset);

            VertexFormat format = mojang(this.attribute);

            ((VertexFormatAlt) format).bbs$setAlt(List.copyOf(this.attribute), this.offset, this.stepRate);

            return format;
        }
    }

    /**
     * A Mojang format with one registered element per attribute, so that the GL program binds
     * every name to its own location. Matrix columns after the first get a placeholder name:
     * GL places a matrix's columns at consecutive locations after the first.
     */
    /** Mojang-Format aus den Attributen; der versionsabhaengige Teil steht in MojangFormat. */
    public static VertexFormat mojang(List<Attribut> attribute)
    {
        return MojangFormat.bauen(attribute);
    }
}
