package mchorse.bbs_mod.compat.mixin;

import com.mojang.blaze3d.systems.CommandEncoder;
import mchorse.bbs_mod.compat.CommandEncoderAlt;
import org.spongepowered.asm.mixin.Mixin;

/** Adds {@link CommandEncoderAlt} to Mojang's CommandEncoder interface at runtime (Loom injects it for compiling). */
@Mixin(CommandEncoder.class)
public interface CommandEncoderAltMixin extends CommandEncoderAlt
{}
