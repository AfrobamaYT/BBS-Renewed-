package mchorse.bbs_mod.integrations.vitrail;

import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.ProgramTranslator;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.pack.model.ProgramStage;

import java.util.LinkedHashMap;

/** Preserve Vitrail's actual resolved program, targets, options and PBR plan around native deformation. */
public final class VitrailLoadedProgram
{
    private VitrailLoadedProgram() {}

    public static PackProgram.Loaded retainedAlpha(PackProgram.Loaded loaded)
    {
        ProgramTranslator.TranslatedProgram translated = loaded.program();
        TranslatedUnit vertex = translated.stages().get(ProgramStage.VERTEX);
        TranslatedUnit fragment = translated.stages().get(ProgramStage.FRAGMENT);
        if (vertex == null || fragment == null) throw new IllegalArgumentException("Retained entity program requires both stages");
        LinkedHashMap<ProgramStage, TranslatedUnit> stages = new LinkedHashMap<>(translated.stages());
        stages.put(ProgramStage.VERTEX, new TranslatedUnit(vertex.entry(), vertex.stage(),
            VitrailRetainedAlpha.vertex(vertex.text()), vertex.notes(), vertex.drawBuffers(), vertex.blockMembers(), vertex.samplers()));
        stages.put(ProgramStage.FRAGMENT, new TranslatedUnit(fragment.entry(), fragment.stage(),
            VitrailAlphaSource.compose(fragment.text()), fragment.notes(), fragment.drawBuffers(), fragment.blockMembers(), fragment.samplers()));
        ProgramTranslator.TranslatedProgram program = new ProgramTranslator.TranslatedProgram(java.util.Collections.unmodifiableMap(stages),
            translated.uniforms(), translated.samplers(), translated.sampled(), translated.synthesized(), translated.inputs());
        return new PackProgram.Loaded(loaded.packName(), loaded.path(), program, loaded.targets(),
            loaded.samplers(), loaded.alphaTest(), loaded.supplied());
    }

    public static PackProgram.Loaded deform(PackProgram.Loaded loaded, VitrailVertexSource.Kind kind, boolean shadow)
    {
        var translated = loaded.program();
        var vertex = translated.stages().get(ProgramStage.VERTEX);
        if (vertex == null) throw new IllegalArgumentException("Native Vitrail entity program has no vertex stage");
        var stages = new LinkedHashMap<>(translated.stages());
        stages.put(ProgramStage.VERTEX, new TranslatedUnit(vertex.entry(), vertex.stage(),
            VitrailVertexSource.compose(vertex.text(), kind, shadow), vertex.notes(), vertex.drawBuffers(),
            vertex.blockMembers(), vertex.samplers()));
        TranslatedUnit fragment = translated.stages().get(ProgramStage.FRAGMENT);
        if (fragment == null) throw new IllegalArgumentException("Native Vitrail entity program has no fragment stage");
        stages.put(ProgramStage.FRAGMENT, new TranslatedUnit(fragment.entry(), fragment.stage(),
            VitrailAlphaSource.compose(fragment.text()), fragment.notes(), fragment.drawBuffers(),
            fragment.blockMembers(), fragment.samplers()));
        var program = new ProgramTranslator.TranslatedProgram(java.util.Collections.unmodifiableMap(stages),
            translated.uniforms(), translated.samplers(), translated.sampled(), translated.synthesized(), translated.inputs());
        return new PackProgram.Loaded(loaded.packName(), loaded.path(), program, loaded.targets(),
            loaded.samplers(), loaded.alphaTest(), loaded.supplied());
    }
}
