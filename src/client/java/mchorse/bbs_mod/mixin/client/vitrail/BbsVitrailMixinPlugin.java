package mchorse.bbs_mod.mixin.client.vitrail;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Optional integration with an explicit frozen ABI, without loading any GPU/device class. */
public final class BbsVitrailMixinPlugin implements IMixinConfigPlugin
{
    private boolean present;

    @Override
    public void onLoad(String mixinPackage)
    {
        FabricLoader.getInstance().getModContainer("vitrail").ifPresent(mod ->
        {
            String version = mod.getMetadata().getVersion().getFriendlyString();
            /* 0.13.0-beta: the classes and members BBS uses are unchanged against 0.12 (javap of all
             * 47 referenced, 2026-10-14: only additions), and it brings the block crack back. */
            if (!version.equals("0.12.0-beta") && !version.equals("0.13.0-beta"))
                throw new IllegalStateException("BBS Vitrail ABI requires 0.12.0-beta or 0.13.0-beta, found " + version);
            this.present = true;
        });
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return this.present; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
