// Coded by Afrobama/Claude — wire UICameraPreviewOverlay into dashboard
package com.afrobama.bbstools.client.mixin;

import com.afrobama.bbstools.client.campreview.CameraPreview;
import com.afrobama.bbstools.client.campreview.CameraPreviewRegistry;
import com.afrobama.bbstools.client.campreview.UICameraPreviewOverlay;
import com.afrobama.bbstools.client.keybinds.BBSToolsKeys;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inject the camera-preview overlay container into {@link UIDashboard}'s
 * overlay layer once at construction. The overlay self-syncs its child
 * windows with {@code CameraPreviewRegistry} on every render, so newly
 * added previews appear automatically without further wiring.
 *
 * <p>Targets the {@code <init>} TAIL so {@code this.overlay} (inherited
 * from UIBaseMenu) is fully constructed and ready to receive children.</p>
 */
@Mixin(value = UIDashboard.class, remap = false)
public abstract class UIDashboardCamPreviewMixin
{
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 0)
    private void bbstools$wireCamPreviewOverlay(CallbackInfo ci)
    {
        /* Logik → com.afrobama.blendertransform.UiDashboardCamPreviewLogik (bruecken-muster,
         * siehe MixinBruecke). Der gewobene teil bleibt dieser einzeiler. */
        com.afrobama.bbstools.client.blender.MixinBruecke.rufe(
            "UiDashboardCamPreviewLogik", "wireCamPreviewOverlay", (UIDashboard) (Object) this);

        /* Phase 10 redesign: clip-driven viewports replace this.
         *  Mixin stays registered (still needed for the framebuffer
         *  pre-pass pipeline wiring) but the draggable-window overlay
         *  + Ctrl+Shift+{P,L,X} keybinds are inert. */
        /*
        try
        {
            UIDashboard self = (UIDashboard) (Object) this;
            UIElement overlayField = ((UIBaseMenuAccessor) (Object) self).bbstools$getRoot();
            overlayField.add(new UICameraPreviewOverlay());

            overlayField.keys().register(BBSToolsKeys.CAMPREVIEW_ADD, () ->
            {
                CameraPreview p = CameraPreviewRegistry.addAtPlayer("Camera "
                    + (CameraPreviewRegistry.list().size()));
                try
                {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc != null && mc.getWindow() != null)
                    {
                        CameraPreviewRegistry.autoLayout(
                            mc.getWindow().getScaledWidth(),
                            mc.getWindow().getScaledHeight());
                    }
                }
                catch (Throwable ignored) {}
                bbstools$chat("§a[BBSTools] §fAdded preview '" + p.name + "' (auto-laid out)");
            });

            overlayField.keys().register(BBSToolsKeys.CAMPREVIEW_LAYOUT, () ->
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.getWindow() == null) return;
                int w = mc.getWindow().getScaledWidth();
                int h = mc.getWindow().getScaledHeight();
                CameraPreviewRegistry.autoLayout(w, h);
                bbstools$chat("§a[BBSTools] §fAuto-layout applied ("
                    + CameraPreviewRegistry.list().size() + " windows)");
            });

            overlayField.keys().register(BBSToolsKeys.CAMPREVIEW_CLEAR, () ->
            {
                int n = CameraPreviewRegistry.list().size();
                CameraPreviewRegistry.clear();
                bbstools$chat("§a[BBSTools] §fCleared " + n + " preview(s)");
            });
        }
        catch (Throwable ignored)
        {
        }
        */
    }

    private static void bbstools$chat(String msg)
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null)
            {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
            }
        }
        catch (Throwable ignored) {}
    }

}
