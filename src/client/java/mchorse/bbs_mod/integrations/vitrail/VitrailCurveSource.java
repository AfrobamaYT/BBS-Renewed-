package mchorse.bbs_mod.integrations.vitrail;

import dev.vitrail.pack.source.IncludeExpander.ExpandedUnit;
import dev.vitrail.glsl.GlslLexer;
import dev.vitrail.glsl.GlslLexer.Kind;
import mchorse.bbs_mod.utils.iris.ShaderCurves;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Existing BBS curve transformation on a complete expanded unit, preserving the engine's branch decisions. */
public final class VitrailCurveSource
{
    private static final Map<ExpandedUnit, ExpandedUnit> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<String> STATIC_OPTIONS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final Set<String> UNEINIG = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Pattern OPTION = Pattern.compile("^\\s*#\\s*define\\s+(\\w+)\\s+([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?)\\s*//\\s*(?:\\[|OptionAnnotatedSource)");
    private static final Pattern CONSTANT_USE = Pattern.compile("\\b(?:\\w+\\s+\\w+\\s*\\[[^]]+]|case\\s+[^:]+:|layout\\s*\\([^)]*\\)|texture(?:Gather)?Offsets?\\s*\\([^;]+)");
    private static final Pattern CONST = Pattern.compile("\\bconst\\s+\\w+\\s+(\\w+)\\s*=\\s*([^;]+);");
    private VitrailCurveSource() {}
    public static void reset() { CACHE.clear(); STATIC_OPTIONS.clear(); UNEINIG.clear(); }
    public static boolean requiresReload(String name) { return STATIC_OPTIONS.contains(name); }

    public static ExpandedUnit transform(ExpandedUnit unit, List<String> sliders, boolean enabled)
    {
        if (!enabled || sliders.isEmpty()) return unit;
        ExpandedUnit known = CACHE.get(unit);
        if (known != null) return known;
        Map<String, ShaderCurves.ShaderVariable> variables = new LinkedHashMap<>();
        Set<String> uneinig = new LinkedHashSet<>();
        for (int line = 0; line < unit.lines().size(); line++)
        {
            if (!unit.isLive(line)) continue;
            Matcher option = OPTION.matcher(unit.lines().get(line));
            if (!option.find() || !sliders.contains(option.group(1))) continue;
            String value = option.group(2);
            ShaderCurves.ShaderVariable variable = new ShaderCurves.ShaderVariable(option.group(1), value, !value.matches(".*[.eE].*"));
            ShaderCurves.ShaderVariable previous = variables.putIfAbsent(variable.name, variable);
            if (abweichend(previous, variable)) uneinig.add(variable.name);
        }
        /* Vitrail expands a program once with the pack default and again with the user's chosen
         * value (measured on Somber, fogStrenth 0.993 then 0.998). Throwing here killed the whole
         * pack. The unit carrying the chosen value is the one drawn, so its default wins; any
         * other variant keeps its own #define. */
        for (ShaderCurves.ShaderVariable variable : variables.values())
        {
            if (!abweichend(ShaderCurves.variableMap.get(variable.name), variable)) continue;
            if (gewaehlt(variable)) ShaderCurves.variableMap.put(variable.name, variable);
            else uneinig.add(variable.name);
        }
        for (String name : uneinig)
            if (UNEINIG.add(name)) LOGGER.info("BBS shader curve '{}': a unit with a value other than the chosen one keeps its #define", name);
        variables.keySet().removeAll(uneinig);
        List<GlslLexer.Token> tokens = GlslLexer.lex(unit.text());
        StringBuilder liveCode = new StringBuilder();
        Set<String> staticNames = new LinkedHashSet<>();
        int physicalLine = 0;
        boolean definingName = false;
        for (GlslLexer.Token token : tokens)
        {
            if (token.kind() == Kind.HASH) definingName = "define".equals(token.directive());
            if (unit.isLive(physicalLine) && token.kind() == Kind.IDENTIFIER)
            {
                if (token.directive() != null)
                {
                    if (definingName && !token.text().equals("define")) definingName = false;
                    else if (variables.containsKey(token.text())) staticNames.add(token.text());
                }
                else if (token.text().startsWith(ShaderCurves.UNIFORM_IDENTIFIER))
                    throw new IllegalArgumentException("Shaderpack collides with reserved BBS curve identifier: " + token.text());
            }
            if (unit.isLive(physicalLine) && token.directive() == null && token.kind() != Kind.COMMENT)
                liveCode.append(token.text());
            else liveCode.append(" ");
            physicalLine += (int) token.text().chars().filter(c -> c == '\n').count();
        }
        Matcher required = CONSTANT_USE.matcher(liveCode);
        while (required.find()) names(required.group(), staticNames);
        boolean grew;
        do
        {
            int before = staticNames.size();
            Matcher constants = CONST.matcher(liveCode);
            while (constants.find()) if (staticNames.contains(constants.group(1))) names(constants.group(2), staticNames);
            grew = before != staticNames.size();
        }
        while (grew);
        for (String name : variables.keySet()) if (staticNames.contains(name)) STATIC_OPTIONS.add(name);
        if (variables.containsKey("WATER_WAVE_ITERATIONS")) STATIC_OPTIONS.add("WATER_WAVE_ITERATIONS");
        variables.keySet().removeAll(staticNames);
        variables.remove("WATER_WAVE_ITERATIONS");
        if (variables.isEmpty()) { CACHE.put(unit, unit); return unit; }
        StringBuilder changed = new StringBuilder();
        physicalLine = 0;
        for (GlslLexer.Token token : tokens)
        {
            String spelling = token.text();
            if (unit.isLive(physicalLine) && token.directive() == null && token.kind() == Kind.IDENTIFIER && variables.containsKey(spelling))
                spelling = variables.get(spelling).uniformName;
            changed.append(spelling);
            physicalLine += (int) token.text().chars().filter(c -> c == '\n').count();
        }
        List<String> lines = new java.util.ArrayList<>(List.of(changed.toString().split("\\n", -1)));
        if (lines.size() != unit.lines().size()) throw new IllegalStateException("BBS curve transformation changed physical source lines");
        int insertion = -1;
        for (int line = 0; line < lines.size(); line++) if (lines.get(line).stripLeading().startsWith("#version")) { insertion = line + 1; break; }
        if (insertion < 0) throw new IllegalStateException("BBS curves need a shader version declaration");
        List<String> declarations = variables.values().stream().map(ShaderCurves.ShaderVariable::toUniformDeclaration).toList();
        int added = declarations.size();
        lines.addAll(insertion, declarations);
        BitSet live = new BitSet();
        BitSet originalLive = unit.live();
        for (int line = originalLive.nextSetBit(0); line >= 0; line = originalLive.nextSetBit(line + 1))
            live.set(line < insertion ? line : line + added);
        live.set(insertion, insertion + added);
        ExpandedUnit transformed = new ExpandedUnit(unit.entry(), lines, unit.version(), unit.stats(), live, unit.defines());
        for (ShaderCurves.ShaderVariable variable : variables.values()) ShaderCurves.variableMap.putIfAbsent(variable.name, variable);
        CACHE.put(unit, transformed);
        return transformed;
    }

    private static boolean abweichend(ShaderCurves.ShaderVariable previous, ShaderCurves.ShaderVariable variable)
    {
        return previous != null && (previous.integer != variable.integer || previous.defaultValue != variable.defaultValue);
    }

    private static boolean gewaehlt(ShaderCurves.ShaderVariable variable)
    {
        String wert = VitrailShaderOptions.getOption(variable.name);

        try { return wert != null && Float.parseFloat(wert) == variable.defaultValue; }
        catch (NumberFormatException keineZahl) { return false; }
    }

    private static void names(String source, Set<String> names)
    {
        for (GlslLexer.Token token : GlslLexer.lex(source)) if (token.kind() == Kind.IDENTIFIER) names.add(token.text());
    }
}
