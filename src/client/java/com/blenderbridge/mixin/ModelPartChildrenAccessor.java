// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 4 entity export).
package com.blenderbridge.mixin;

import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to {@code ModelPart.children} — the ONE non-public piece of the entity-model tree.
 *
 * <p>WHY (research 2026-07-16, verified against Yarn 1.21.1+build.3): everything else an entity
 * exporter needs on {@link ModelPart} is already public — {@code pivotX/Y/Z}, {@code pitch/yaw/
 * roll}, {@code xScale/yScale/zScale}, {@code visible}. Only the child map
 * ({@code private final Map<String, ModelPart> children}) is locked away. Both proven entity
 * exporters (Igrium's mc-world-export and Figura) solve it with exactly this accessor-mixin
 * rather than reflection: a mixin fails loudly at boot if the mapping changes, reflection fails
 * silently per-call at runtime.</p>
 *
 * <p>BOOT-WINDOW CITIZEN: mixins are woven when the target class loads — this file must be in
 * the tree and registered in {@code blenderbridge.mixins.json} BEFORE the next game boot; it
 * cannot be hot-swapped in later (deploy-fenster-doc, klasse 3). Shipping it early is free: an
 * unused accessor interface has zero runtime cost.</p>
 */
@Mixin(ModelPart.class)
public interface ModelPartChildrenAccessor
{
    @Accessor("children")
    Map<String, ModelPart> blenderbridge$getChildren();
}
