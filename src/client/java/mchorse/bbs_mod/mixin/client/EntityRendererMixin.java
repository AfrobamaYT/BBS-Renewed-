package mchorse.bbs_mod.mixin.client;

import com.afrobama.bbstools.client.blender.MixinBruecke;

import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.MobForm;
import net.minecraft.client.renderer.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kein Namensschild an einer Spieler-MobForm.
 *
 * <p>⚠️ 26.2: {@code renderNameTag} heisst {@code submitNameDisplay}. Genommen ist die
 * VIER-Argument-Fassung — sie ist die, die {@code EntityRenderer.submit} ruft, also genau die
 * Stelle, an der bis 1.21.1 {@code renderNameTag} aus {@code render} gerufen wurde (am
 * Bytecode geprueft). Der Deskriptor steht ausgeschrieben, weil es eine zweite, {@code final}e
 * Ueberladung mit einem zusaetzlichen {@code int} gibt.
 *
 * <p>⚠️ Wie vorher deckt der Haken nur die in {@code EntityRenderer} deklarierte Fassung ab:
 * ein Renderer, der {@code submitNameDisplay} ueberschreibt, geht daran vorbei — dasselbe galt
 * fuer {@code renderNameTag}.
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin
{
    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    public void onRenderLabelIfPresent(CallbackInfo info)
    {
        if (MixinBruecke.rufeUndAbbrechen("NamensschildLogik", "unterdruecken"))
        {
            info.cancel();
        }
    }
}
