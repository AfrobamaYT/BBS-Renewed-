package net.minecraft.client.renderer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** BBS-Shim fuer Minecraft vor 1.21.2: 1.21.4s Shader-Defines (Werte und Schalter). */
public record ShaderDefines(Map<String, String> values, Set<String> flags)
{
    public static final ShaderDefines EMPTY = new ShaderDefines(Map.of(), Set.of());

    public static Builder builder()
    {
        return new Builder();
    }

    public ShaderDefines withOverrides(ShaderDefines andere)
    {
        Map<String, String> werte = new LinkedHashMap<>(this.values);
        Set<String> schalter = new LinkedHashSet<>(this.flags);

        werte.putAll(andere.values);
        schalter.addAll(andere.flags);

        return new ShaderDefines(Map.copyOf(werte), Set.copyOf(schalter));
    }

    /** Wie 1.21.4: je Wert "#define NAME WERT", je Schalter "#define NAME", jeweils mit Zeilenende. */
    public String asSourceDirectives()
    {
        StringBuilder aus = new StringBuilder();

        this.values.forEach((name, wert) -> aus.append("#define ").append(name).append(' ').append(wert).append('\n'));
        this.flags.forEach((name) -> aus.append("#define ").append(name).append('\n'));

        return aus.toString();
    }

    public boolean isEmpty()
    {
        return this.values.isEmpty() && this.flags.isEmpty();
    }

    public static class Builder
    {
        private final Map<String, String> werte = new LinkedHashMap<>();
        private final Set<String> schalter = new LinkedHashSet<>();

        public Builder define(String name, String wert)
        {
            this.werte.put(name, wert);
            return this;
        }

        public Builder define(String name, float wert)
        {
            return this.define(name, String.valueOf(wert));
        }

        public Builder define(String name)
        {
            this.schalter.add(name);
            return this;
        }

        public ShaderDefines build()
        {
            return new ShaderDefines(Map.copyOf(this.werte), Set.copyOf(this.schalter));
        }
    }
}
