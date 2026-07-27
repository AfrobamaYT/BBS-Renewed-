package com.afrobama.bbstools.client.mixin;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code UIBaseMenu#root} so our keybind-wire mixin can
 * attach a child. The field's actual type is the inner
 * {@code UIBaseMenu.UIRootElement} (subclass of UIElement); Mixin
 * accessors require exact-type match.
 */
@Mixin(UIBaseMenu.class)
public interface UIBaseMenuAccessor
{
    @Accessor("root")
    UIBaseMenu.UIRootElement bbstools$getRoot();
}
