package com.afrobama.bbstools.kernel.logik;

import mchorse.bbs_mod.morphing.IMorphProvider;
import net.minecraft.nbt.CompoundTag;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.Tag;
*///?}

/**
 * Logik hinter {@code PlayerEntityMixin} — die Gestalt eines Spielers ueberlebt den Ausloggen.
 *
 * <p>Geschrieben und gelesen wird unter dem Schluessel {@code BBSMorph} in den Spielerdaten,
 * jeweils am TAIL der Vanilla-Methode.</p>
 *
 * <p>⛔ HIER FAELLT NICHTS STILL AUS. Bricht das Schreiben weg, verliert der Spieler seine
 * Gestalt beim naechsten Laden — ohne Fehlermeldung, weil ein fehlender NBT-Schluessel voellig
 * normal aussieht. Genau deshalb wurde dieses Mixin am 2026-08-10 schon einmal von der Bruecke
 * zurueckgezogen: solange die KernelBruecke ohne Agent {@code null} lieferte, war der Handler
 * ein Einzeiler ins Leere. Der Rueckfallpfad in der Bruecke ist die Vorbedingung dafuer, dass
 * das hier stehen darf.</p>
 *
 * <p>⚠️ Beide Methoden nehmen {@code this} als {@code Object}: der {@code instanceof}-Test auf
 * {@link IMorphProvider} braucht den gewobenen Spieler, und die Schnittstelle kommt erst durch
 * ein anderes Mixin an die Klasse.</p>
 */
public final class SpielerFormSpeichernLogik
{
    private SpielerFormSpeichernLogik() {}

    /** {@code Player.addAdditionalSaveData} @TAIL */
    //? if >=1.21.6 {
    public static void schreiben(Object spieler, ValueOutput ausgabe)
    //?} else {
    /*public static void schreiben(Object spieler, CompoundTag ausgabe)
    *///?}
    {
        if (!(spieler instanceof IMorphProvider traeger))
        {
            return;
        }

        /* ⚠️ toNbt() ist als Tag deklariert, liefert aber einen CompoundTag (fromNbt nimmt
         * genau den wieder entgegen). Der Codec braucht den konkreten Typ, also einmal pruefen
         * statt blind casten — ein anderer Tag-Typ waere ein echter Fehler im Morph und soll
         * nicht still als leeres Feld enden. */
        if (traeger.getMorph().toNbt() instanceof CompoundTag daten)
        {
            //? if >=1.21.6 {
            ausgabe.store("BBSMorph", CompoundTag.CODEC, daten);
            //?} else {
            /*ausgabe.put("BBSMorph", daten);
            *///?}
        }
    }

    /**
     * {@code Player.readAdditionalSaveData} @TAIL
     *
     * <p>{@code read} gibt ein Optional — die alte {@code contains}-Pruefung faellt damit weg,
     * und der Fall "Feld da, aber kaputt" wird still uebersprungen statt zu werfen.</p>
     */
    //? if >=1.21.6 {
    public static void lesen(Object spieler, ValueInput eingabe)
    //?} else {
    /*public static void lesen(Object spieler, CompoundTag eingabe)
    *///?}
    {
        if (!(spieler instanceof IMorphProvider traeger))
        {
            return;
        }

        //? if >=1.21.6 {
        eingabe.read("BBSMorph", CompoundTag.CODEC).ifPresent(daten -> traeger.getMorph().fromNbt(daten));
        //?} else {
        /*if (eingabe.contains("BBSMorph"))
        {
            traeger.getMorph().fromNbt(eingabe.getCompound("BBSMorph"));
        }
        *///?}
    }
}
