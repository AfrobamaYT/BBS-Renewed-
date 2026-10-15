package mchorse.bbs_mod.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Foreign renderer integrations apply only when their actual mod is loaded. */
public final class BBSClientMixinPlugin implements IMixinConfigPlugin
{
    private boolean iris;
    private boolean sodium;

    @Override
    public void onLoad(String mixinPackage)
    {
        this.iris = FabricLoader.getInstance().isModLoaded("iris");
        this.sodium = FabricLoader.getInstance().isModLoaded("sodium");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        if (mixinClassName.startsWith("mchorse.bbs_mod.mixin.client.iris.")) return this.iris;
        if (mixinClassName.startsWith("mchorse.bbs_mod.mixin.client.sodium.")) return this.sodium;
        return true;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) {}
}
