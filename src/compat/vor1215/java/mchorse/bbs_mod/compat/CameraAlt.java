package mchorse.bbs_mod.compat;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

/** vor1215: 1.21.11s Camera-Zugriffe (position, xRot, yRot) an 1.21.4s Kamera. Mixin CameraAltMixin. */
public interface CameraAlt
{
    default Vec3 position() { return ((Camera) this).getPosition(); }
    default float xRot() { return ((Camera) this).getXRot(); }
    default float yRot() { return ((Camera) this).getYRot(); }
}
