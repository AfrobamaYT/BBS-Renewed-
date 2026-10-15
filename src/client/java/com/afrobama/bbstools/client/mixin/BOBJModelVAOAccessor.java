// Coded by Afrobama/Claude
package com.afrobama.bbstools.client.mixin;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.cubic.render.vao.BOBJModelVAO;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for BBS {@link BOBJModelVAO} internals.
 *
 * <p>Originally exposed only {@code dominantBonePerTriangle} for the
 * fracture mixin. Extended for the Veil-based replay batch renderer so
 * we can pull the underlying mesh {@link BOBJLoader.CompiledData}, the
 * bound {@link BOBJArmature} and the raw GL VAO handle without forcing
 * those fields public.
 *
 * <p>Note: {@code data}, {@code armature}, and the vertex/normal/uv
 * buffer ids on BBS' {@code BOBJModelVAO} are already declared public,
 * but routing them through {@code bbstools$} accessors gives us a
 * stable API surface that survives upstream visibility changes.
 */
@Mixin(value = BOBJModelVAO.class, remap = false)
public interface BOBJModelVAOAccessor
{

    @Accessor("data")
    BOBJLoader.CompiledData bbstools$getData();

    @Accessor("armature")
    BOBJArmature bbstools$getArmature();

    /* ⛔ ENTFERNT 2026-07-31: @Accessor("vao") -- das Feld gibt es nicht mehr.
     *
     * 26.2 haelt keine rohe GL-Kennung mehr; BOBJModelVAO fuehrt jetzt drei GpuBuffer
     * (position, normale, texturUV). Ein @Accessor auf ein Feld, das es nicht gibt, ist ein
     * HARTER WEBFEHLER -- der Start scheitert, nicht der Bau.
     *
     * ⚠️ Und tools/mixin-ziele.py sieht das NICHT: es prueft Methodenziele, keine Feldziele.
     * Gefunden hat es ein Arbeiter beim Lesen. Wer die Luecke schliessen will, muss dort die
     * @Accessor/@Shadow-Felder mitpruefen.
     *
     * Aufgerufen wurde bbstools$getVao nirgends (gegrept), also ersatzlos raus. data und
     * armature bleiben -- die Felder existieren weiter (und sind sogar public, der Accessor
     * ist dort bequem, nicht noetig). */
}
