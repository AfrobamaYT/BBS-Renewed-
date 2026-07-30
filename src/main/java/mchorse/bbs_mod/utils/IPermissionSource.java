package mchorse.bbs_mod.utils;

import net.minecraft.server.permissions.PermissionSet;

/**
 * Traegt die Rechtestufe, mit der eine BBS-Entity Befehle ausfuehrt.
 *
 * Bis 1.21.1 gab es dafuer Entity.getPermissionLevel(); ActorEntity gab 4 zurueck,
 * GunProjectileEntity 2. 26.2 hat die Methode ERSATZLOS gestrichen und baut den
 * CommandSourceStack einer Entity fest mit PermissionSet.NO_PERMISSIONS (am Bytecode von
 * createCommandSourceStackForNameResolution nachgesehen).
 *
 * Ohne diese Schnittstelle wuerde die Stufe beim Port still auf 0 fallen: Gun-Befehle und
 * Actor-Befehle wuerden nach dem Update kommentarlos nichts mehr tun. Das ist kein
 * Compiler-fehler, sondern genau die Sorte versteckter Unfertigkeit, die am Ergebnis
 * gemessen werden muss.
 */
public interface IPermissionSource
{
    PermissionSet getCommandPermissions();
}
