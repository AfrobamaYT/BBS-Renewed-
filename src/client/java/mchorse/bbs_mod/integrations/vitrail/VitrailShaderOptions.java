package mchorse.bbs_mod.integrations.vitrail;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.CommandEncoder;
import dev.vitrail.render.PackChoice;
import dev.vitrail.settings.PackSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Actual Vitrail option entrypoints; curve animation will use uniforms, never a reload per frame. */
public final class VitrailShaderOptions
{
    private VitrailShaderOptions() {}

    public static boolean present() { return PackChoice.session().isPresent(); }
    private static PackSession session()
    { return PackChoice.session().orElseThrow(() -> new IllegalStateException("No loaded Vitrail pack session")); }
    public static List<String> sliders() { return VitrailOptionState.sliders(session()); }
    public static String getOption(String name) { return VitrailOptionState.get(session(), name); }
    public static Map<String, String> language(String language)
    {
        try { return VitrailOptionState.language(session(), language); }
        catch (IOException error) { throw new IllegalStateException("Cannot read current Vitrail pack language", error); }
    }

    public static void setOption(String name, String value)
    {
        RenderSystem.assertOnRenderThread();
        if (((CommandEncoder) RenderSystem.getDevice().createCommandEncoder()).isInRenderPass())
            throw new IllegalStateException("Change Vitrail options outside any render pass");
        PackSession before = session();
        String expected;
        try { expected = VitrailOptionState.write(before, name, value); }
        catch (IOException error) { throw new IllegalStateException("Cannot persist Vitrail option " + name, error); }
        PackChoice.reload(before.gameDirectory());
        if (PackChoice.lastError().isPresent()) throw new IllegalStateException("Vitrail option reload failed: " + PackChoice.lastError().get());
        if (!session().packPath().equals(before.packPath()) || !getOption(name).equals(expected))
            throw new IllegalStateException("Vitrail did not apply requested option " + name + "=" + expected);
    }
}
