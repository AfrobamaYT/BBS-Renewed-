package net.minecraft.client.renderer.entity.state;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * BBS-Shim fuer Minecraft vor 1.21.2: der Render-Zustand von 1.21.2+, soweit BBS ihn liest.
 * 1.21.1 zeichnet direkt aus der Entitaet; ZustandsRenderer und die vor1212-Mixins fuellen
 * diesen Zustand aus ihr und halten sie in {@link #bbsEntitaet}.
 */
public class EntityRenderState
{
    public double x;
    public double y;
    public double z;
    public float ageInTicks;
    public float partialTick;
    public boolean isInvisible;
    public double distanceToCameraSq;
    public int lightCoords = 15728880;
    public float shadowRadius;
    public final List<ShadowPiece> shadowPieces = new ArrayList<>();
    /** Die Entitaet, aus der dieser Zustand stammt (vor 1.21.2 immer bekannt). */
    public Entity bbsEntitaet;

    public record ShadowPiece(float relativeX, float relativeY, float relativeZ, net.minecraft.world.phys.shapes.VoxelShape shapeBelow, float alpha)
    {}

    public static class LeashState
    {}
}
