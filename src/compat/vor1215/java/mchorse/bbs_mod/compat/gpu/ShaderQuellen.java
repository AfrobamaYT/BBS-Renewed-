package mchorse.bbs_mod.compat.gpu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shader-Quellen der Schicht vor 1.21.5: laedt .vsh/.fsh einer Pipeline, loest #moj_import auf
 * (minecraft-Includes zuerst aus assets/bbs/shaders/vor1215/include/) und setzt die Defines
 * hinter die #version-Zeile.
 */
final class ShaderQuellen
{
    private static final Pattern IMPORT = Pattern.compile("^\\s*#moj_import\\s+[<\"]([^>\"]+)[>\"].*$", Pattern.MULTILINE);
    private static final String EIGENE_INCLUDES = "/assets/bbs/shaders/vor1215/include/";

    private ShaderQuellen()
    {}

    static String laden(ResourceLocation shader, String endung, RenderPipeline pipeline)
    {
        ResourceLocation datei = mchorse.bbs_mod.compat.alt.Orte.von(shader.getNamespace(), "shaders/" + shader.getPath() + endung);
        /* Vanilla-Shader von 1.21.6+, die 1.21.4 nicht hat (core/screenquad), liegen unter bbs:shaders/vor1215/. */
        ResourceLocation ersatz = mchorse.bbs_mod.compat.alt.Orte.von("bbs", "shaders/vor1215/" + shader.getPath() + endung);
        String quelle = (shader.getNamespace().equals("minecraft") ? lesen(ersatz) : java.util.Optional.<String>empty()).or(() -> lesen(datei))
            .orElseThrow(() -> new IllegalStateException("Shader fehlt: " + datei + " (Pipeline " + pipeline.getLocation() + ")"));

        /* 1.21.4s eigene Kern-Shader (minecraft) brauchen 1.21.4s Includes, BBS-Shader die von 1.21.6+. */
        quelle = einbinden(quelle, shader.getNamespace(), new HashSet<>(), !shader.getNamespace().equals("minecraft"));

        return definesEinfuegen(quelle, pipeline.getShaderDefines().asSourceDirectives());
    }

    private static String einbinden(String quelle, String namensraum, Set<String> schon, boolean eigene)
    {
        Matcher m = IMPORT.matcher(quelle);
        StringBuilder aus = new StringBuilder();

        while (m.find())
        {
            String ziel = m.group(1);
            ResourceLocation ort = ziel.contains(":") ? mchorse.bbs_mod.compat.alt.Orte.lesen(ziel) : mchorse.bbs_mod.compat.alt.Orte.von(namensraum, ziel);
            String text = "";

            if (schon.add(ort.toString()))
            {
                text = include(ort, eigene).orElseThrow(() -> new IllegalStateException("Shader-Include fehlt: " + ort));
                /* Eingebundene Dateien tragen eigene #version-Zeilen; nur die der Hauptdatei bleibt. */
                text = text.replaceAll("(?m)^\\s*#version[^\\n]*\\n?", "");
                text = einbinden(text, ort.getNamespace(), schon, eigene);
            }

            m.appendReplacement(aus, Matcher.quoteReplacement(text));
        }

        m.appendTail(aus);

        return aus.toString();
    }

    private static Optional<String> include(ResourceLocation ort, boolean eigene)
    {
        if (eigene && ort.getNamespace().equals("minecraft"))
        {
            try (InputStream eigen = ShaderQuellen.class.getResourceAsStream(EIGENE_INCLUDES + ort.getPath()))
            {
                if (eigen != null)
                {
                    return Optional.of(new String(eigen.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            catch (IOException e)
            {
                throw new IllegalStateException("Shader-Include " + ort + " nicht lesbar", e);
            }
        }

        return lesen(mchorse.bbs_mod.compat.alt.Orte.von(ort.getNamespace(), "shaders/include/" + ort.getPath()));
    }

    private static Optional<String> lesen(ResourceLocation datei)
    {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(datei);

        if (resource.isEmpty())
        {
            return Optional.empty();
        }

        try (Reader reader = resource.get().openAsReader())
        {
            StringBuilder text = new StringBuilder();
            char[] puffer = new char[4096];
            int n;

            while ((n = reader.read(puffer)) != -1)
            {
                text.append(puffer, 0, n);
            }

            return Optional.of(text.toString());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Shader " + datei + " nicht lesbar", e);
        }
    }

    /** Defines direkt hinter die #version-Zeile, wie Mojangs Shader-Quelle es tut. */
    private static String definesEinfuegen(String quelle, String defines)
    {
        if (defines.isEmpty())
        {
            return quelle;
        }

        Matcher version = Pattern.compile("^\\s*#version[^\\n]*\\n", Pattern.MULTILINE).matcher(quelle);

        if (!version.find())
        {
            return defines + "\n" + quelle;
        }

        return quelle.substring(0, version.end()) + defines + "\n" + quelle.substring(version.end());
    }
}
