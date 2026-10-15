package mchorse.bbs_mod.integrations.vitrail;

import dev.vitrail.pack.menu.MenuOption;
import dev.vitrail.pack.menu.MenuValues;
import dev.vitrail.pack.source.PackLang;
import dev.vitrail.pack.source.ShaderPackSource;
import dev.vitrail.settings.PackSession;
import dev.vitrail.settings.SettingsFile;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Portable access to the real loaded pack's menu/layers; persistence follows Vitrail's own Apply contract. */
public final class VitrailOptionState
{
    private VitrailOptionState() {}

    public static List<String> sliders(PackSession session)
    {
        return session.menu().optionNames().stream().filter(name -> session.menu().option(name)
            .map(MenuOption::slider).orElse(false)).sorted().toList();
    }

    public static String get(PackSession session, String name)
    {
        require(session, name);
        return MenuValues.of(session.menu(), session.saved().values(), session.forcedText()).applied(name);
    }

    public static Map<String, String> language(PackSession session, String language) throws IOException
    {
        try (ShaderPackSource source = ShaderPackSource.open(session.packPath()))
        {
            PackLang labels = PackLang.read(source, language);
            Map<String, String> result = new LinkedHashMap<>();
            for (String name : session.menu().optionNames()) result.put("option." + name, labels.option(name));
            return Map.copyOf(result);
        }
    }

    public static String write(PackSession session, String name, String text) throws IOException
    {
        MenuOption option = require(session, name);
        String value = text.trim();
        if (option.form() == MenuOption.Form.TOGGLE)
        {
            if (value.equalsIgnoreCase("true")) value = "on";
            else if (value.equalsIgnoreCase("false")) value = "off";
        }
        if (option.form() == MenuOption.Form.FIXED || !option.values().contains(value))
            throw new IllegalArgumentException("Unsupported Vitrail option value: " + name + "=" + text);
        SettingsFile.Stored saved = SettingsFile.read(session.settingsFile());
        MenuValues draft = MenuValues.of(session.menu(), saved.values(), session.forcedText());
        if (draft.forced(name)) throw new IllegalStateException("Vitrail options.txt forces " + name);
        draft.queue(name, value);
        SettingsFile.write(session.settingsFile(), new SettingsFile.Stored(draft.toSave()));
        return value;
    }

    private static MenuOption require(PackSession session, String name)
    {
        return session.menu().option(name).orElseThrow(() -> new IllegalArgumentException("Current Vitrail pack has no menu option: " + name));
    }
}
