// Coded by Afrobama/Claude — body-parts visibility fix for form-edit panel
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import com.afrobama.bbstools.perf.BBSPerfConfig;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BBS-CML 2.0-dev's {@code FormRendererMixin.bbs$lodSkipBodyParts} culls
 * body parts when the entity's WORLD position is more than
 * {@code sqrt(LOD_DETAIL_RADIUS_SQ)} (default 32 blocks) from the MAIN
 * game camera. In the form-edit panel the entity is typically a
 * {@code StubEntity} at (0,0,0) while the main camera sits where the
 * player is standing in-world — easily >32 blocks away → body parts
 * silently skipped → user sees only the axis gizmo without the mesh.
 *
 * <p><b>Fix:</b> wrap {@code UIPickableFormRenderer.renderUserModel}
 * (the form-edit panel's render entry point) with a save/restore of
 * {@code BBSPerfConfig.ENABLE_LOD}. Set it false at HEAD, restore the
 * prior value at RETURN. Normal in-world gameplay keeps LOD body-parts
 * culling enabled; only the editor preview pays the full-fidelity cost.</p>
 */
@Mixin(value = UIPickableFormRenderer.class, remap = false)
public abstract class UIPickableFormRendererLodBypassMixin
{
    @Unique
    private boolean bbstools$savedLodFlag;

    @Inject(method = "renderUserModel", at = @At("HEAD"), remap = false, require = 0)
    private void bbstools$disableLodForEditor(UIContext context, CallbackInfo ci)
    {
        Object vorher = MixinBruecke.rufe("EditorVorschauLogik", "lodAus");

        this.bbstools$savedLodFlag = vorher != null && (Boolean) vorher;
    }

    @Inject(method = "renderUserModel", at = @At("RETURN"), remap = false, require = 0)
    private void bbstools$restoreLodForEditor(UIContext context, CallbackInfo ci)
    {
        MixinBruecke.rufe("EditorVorschauLogik", "lodZurueck", this.bbstools$savedLodFlag);
    }

}
