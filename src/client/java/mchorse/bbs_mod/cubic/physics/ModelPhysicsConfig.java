package mchorse.bbs_mod.cubic.physics;

import java.util.List;

public record ModelPhysicsConfig(List<Chain> chains)
{
    public record Chain(String attach, String root, String end, float gravity, float damping, int iterations)
    {
    }
}

