package mchorse.bbs_mod.cubic.ik;

import java.util.List;

public record ModelIKConfig(List<Chain> chains)
{
    public record Chain(String controller, String locator, String root, boolean enabled)
    {
    }
}
