package net.minecraft.client.renderer.entity.state;

/** BBS-Shim fuer Minecraft vor 1.21.2 (siehe EntityRenderState). */
public class LivingEntityRenderState extends EntityRenderState
{
    public float bodyRot;
    public float yRot;
    public float xRot;
    public float scale = 1F;
    public float deathTime;
    public boolean isBaby;
    public float walkAnimationPos;
    public float walkAnimationSpeed;
}
