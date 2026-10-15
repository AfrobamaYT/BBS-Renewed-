package com.afrobama.bbstools.client.mixin.physicsmod;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates the {@code bbstools.physicsmod.mixins.json} config so its
 * mixins only load when PhysicsMod is actually present at runtime.
 * Without this, Mixin would abort the entire config when
 * {@code net.diebuddies.config.ConfigMobs} is missing from the
 * classpath, breaking BBSTools for users who don't have PhysicsMod.
 */
public class BBSPhysicsModMixinPlugin implements IMixinConfigPlugin
{
    private boolean physicsModPresent;

    @Override
    public void onLoad(String mixinPackage)
    {
        try
        {
            this.physicsModPresent = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("physicsmod");
        }
        catch (Throwable t)
        {
            this.physicsModPresent = false;
        }
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        return this.physicsModPresent;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

}
