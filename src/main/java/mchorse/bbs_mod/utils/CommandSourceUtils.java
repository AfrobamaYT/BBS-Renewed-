package mchorse.bbs_mod.utils;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;

/**
 * Baut den CommandSourceStack einer Entity -- mit der Rechtestufe, die BBS vorher hatte.
 *
 * 26.2 ersetzt Entity.createCommandSourceStack() durch
 * createCommandSourceStackForNameResolution(ServerLevel), und die vergibt fest
 * PermissionSet.NO_PERMISSIONS. Wer sie einfach einsetzt, verliert stillschweigend die
 * Rechte, die ActorEntity (4) und GunProjectileEntity (2) frueher ueber
 * getPermissionLevel() gesetzt hatten.
 *
 * Der Aufbau hier ist derselbe wie in der Vanilla-methode, nur mit gewaehlter Rechtestufe --
 * am Bytecode abgelesen, nicht nachempfunden.
 */
public class CommandSourceUtils
{
    public static CommandSourceStack of(Entity entity, ServerLevel level)
    {
        PermissionSet permissions = entity instanceof IPermissionSource source
            ? source.getCommandPermissions()
            : LevelBasedPermissionSet.ALL;

        return new CommandSourceStack(
            CommandSource.NULL,
            entity.position(),
            entity.getRotationVector(),
            level,
            permissions,
            level.getServer(),
            entity
        );
    }
}
