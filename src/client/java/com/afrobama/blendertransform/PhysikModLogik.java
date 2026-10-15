package com.afrobama.blendertransform;

import com.afrobama.bbstools.client.fracture.PhysicsMaskOverride;
import net.diebuddies.physics.PhysicsEntity;
import net.diebuddies.physics.settings.mobs.MobPhysicsType;
import net.diebuddies.physics.settings.mobs.MobSetting;
import net.minecraft.world.entity.Entity;

/**
 * Logik hinter den drei Mixins, die in PhysicsMod hineinreichen — {@code BoxRigidBodyMaskMixin},
 * {@code ConvexRigidBodyMaskMixin} und {@code MixinConfigMobs}.
 *
 * <p>⛔ EINE DATEI, DAMIT ES EINE IST, DIE FEHLEN DARF. Alles hier steht auf Typen aus
 * {@code net.diebuddies.*}, und PhysicsMod ist eine {@code compileOnly}-Abhaengigkeit: ohne
 * installierte Mod laesst sich diese Klasse gar nicht laden. Das ist ungefaehrlich, weil sie
 * nur aus Mixins gerufen wird, die es ohne PhysicsMod ebenfalls nicht gibt — aber es waere
 * gefaehrlich, PhysicsMod-Typen ueber mehrere Logik-Klassen zu streuen. So gibt es genau eine
 * Stelle, die mit der Mod steht und faellt.</p>
 */
public final class PhysikModLogik
{
    private PhysikModLogik() {}

    /**
     * {@code BoxRigidBody.create} / {@code ConvexRigidBody.create} @HEAD — beide identisch.
     *
     * <p>Der Bruch-Code setzt die Maske ueber {@link PhysicsMaskOverride} fuer die Dauer eines
     * Aufbaus; ist keine gesetzt, bleibt PhysicsMods eigene stehen.</p>
     */
    public static void maskeSetzen(PhysicsEntity entitaet)
    {
        Byte maske = PhysicsMaskOverride.get();

        if (maske != null && entitaet != null)
        {
            entitaet.physicsMask = maske;
        }
    }

    /**
     * {@code ConfigMobs.getMobSetting} @HEAD — Schauspieler bekommen keine Physik.
     *
     * <p>⚠️ {@code Class.forName} STATT {@code instanceof}: {@code ActorEntity} liegt in
     * BBS' eigenem Klassenlader, und dieses Mixin wird gegen PhysicsMod gewoben. Ein direkter
     * Typbezug wuerde die Klasse hier festverdrahten, wo sie nicht zwingend erreichbar ist —
     * fehlt sie, faellt der Handler still durch und PhysicsMod entscheidet wie immer.</p>
     *
     * @return die erzwungene Einstellung, oder {@code null} wenn PhysicsMod entscheiden soll
     */
    public static MobSetting einstellungFuer(Entity entitaet)
    {
        if (entitaet == null)
        {
            return null;
        }

        try
        {
            Class<?> schauspieler = Class.forName("mchorse.bbs_mod.entity.ActorEntity");

            if (schauspieler.isInstance(entitaet))
            {
                return new MobSetting(MobPhysicsType.OFF, 0D, 0D, null);
            }
        }
        catch (ClassNotFoundException ohneBBS)
        {
            /* Kein Grund zu meckern: dann gibt es hier auch keine Schauspieler. */
        }

        return null;
    }
}
