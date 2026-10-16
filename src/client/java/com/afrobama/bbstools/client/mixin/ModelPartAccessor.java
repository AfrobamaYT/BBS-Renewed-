package com.afrobama.bbstools.client.mixin;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code ModelPart#children} (private in vanilla 1.21.1) so
 * CrowdLODMixin can walk the part tree to hide named subtrees by
 * distance band. Vanilla MC offers no public traversal API for child
 * parts — Accessor mixin is the cleanest no-access-widener approach.
 */
@Mixin(ModelPart.class)
public interface ModelPartAccessor
{
    @Accessor("children")
    Map<String, ModelPart> bbstools$getChildren();
}
