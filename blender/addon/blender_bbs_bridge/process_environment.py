"""Saubere Umgebung fuer einen Blender-Start — EINE quelle fuer alle aufrufer.

    from blender_start import saubere_umgebung
    subprocess.Popen(["blender", ...], env=saubere_umgebung())

WOFUER (2026-09-13, auftrag REDACTED live-link §1 "reproduzierbaren eigenen Startweg
herstellen"): ein frischer Blender 5.2.1 scheiterte an fehlendem `math` und
`_posixsubprocess`, der traceback zeigte auf mise-Python. Das entfernen von
PYTHONHOME/PYTHONPATH allein half nicht.

⛔ DIE URSACHE IST DER BIBLIOTHEKSPFAD, NICHT DIE PYTHON-VARIABLE. Arch' Blender ist
GEGEN DAS SYSTEM-PYTHON GELINKT:

    ldd /usr/bin/blender -> libpython3.14.so.1.0 => /usr/lib/libpython3.14.so.1.0

mise haelt unter ~/.local/share/mise/installs/python/latest/lib eine EIGENE
libpython3.14.so.1.0 mit DERSELBEN sonamen-version. Steht deren verzeichnis in
LD_LIBRARY_PATH, laedt der dynamische linker sie statt der system-bibliothek — und
dann sucht Blenders Python seine stdlib an mise' prefix, wo sie fuer diesen build
nicht liegt. PYTHONHOME zu loeschen aendert daran nichts, die falsche .so ist da
schon geladen.

2026-09-15 kam eine zweite, unabhaengige Kaperung dazu: Arch-Blender 5.2 setzt sein
`sys.executable`/`sys.prefix` nach dem ersten `python3.14` im PATH. Stand mise dort
vor `/usr/bin`, lud Blender die mise-stdlib auch OHNE LD_LIBRARY_PATH und verlor
`math`/`_struct`. Darum werden mise-Eintraege auch aus PATH entfernt; alle fremden
und System-Eintraege bleiben in ihrer Reihenfolge erhalten.

GEMESSEN 2026-09-13, beide richtungen:
    LD_LIBRARY_PATH=<mise>/lib blender --background --python-expr "import math"
        -> ModuleNotFoundError (cattrs, requests) — die kaperung greift
    ohne  -> sys.executable=/usr/bin/python3.14  sys.prefix=/usr  math OK

⛔ NICHT das globale Python umbauen und mise nicht abschalten — der auftrag verlangt
ausdruecklich einen PROZESSLOKALEN weg. Dieses modul entfernt darum nur die
mise-anteile aus der kind-umgebung und laesst alles andere stehen: ein fremder,
legitimer LD_LIBRARY_PATH-eintrag ueberlebt.
"""
import os
import pathlib

#: Alles unter diesem praefix gilt als mise-eigener bibliotheks-/interpreterpfad.
MISE_WURZEL = str(pathlib.Path.home() / ".local" / "share" / "mise")

#: Zwingen Blenders Python auf einen fremden prefix bzw. schieben fremde module davor.
#: PYTHONHOME allein reicht NICHT (siehe modulkopf) — es gehoert trotzdem weg.
RAUS = ("PYTHONHOME", "PYTHONPATH", "PYTHONEXECUTABLE", "PYTHONSTARTUP", "PYTHONUSERBASE")


def _ohne_mise(wert):
    """Mise-anteile aus einer :-liste streichen, fremde eintraege behalten."""
    if not wert:
        return None
    rest = [t for t in wert.split(os.pathsep) if t and not t.startswith(MISE_WURZEL)]
    return os.pathsep.join(rest) if rest else None


def saubere_umgebung(basis=None):
    """Kopie von `basis` (default os.environ), in der Blender sein eigenes Python findet.

    Gibt ein dict zurueck — der aufrufer reicht es als `env=` an subprocess weiter.
    Aendert NICHTS am eigenen prozess.
    """
    env = dict(os.environ if basis is None else basis)

    for name in RAUS:
        env.pop(name, None)

    # Pfadlisten nur KUERZEN, nicht leeren: dort koennen fremde, noetige pfade stehen.
    for name in ("PATH", "LD_LIBRARY_PATH", "LD_PRELOAD"):
        if name in env:
            gekuerzt = _ohne_mise(env[name])
            if gekuerzt is None:
                env.pop(name, None)
            else:
                env[name] = gekuerzt

    return env


def was_wurde_entfernt(basis=None):
    """Fuer die diagnose/den pruefsatz: was haette die kaperung ausgemacht.

    Liste von (variable, alter wert, neuer wert-oder-None) — leer wenn die umgebung
    ohnehin sauber war.
    """
    alt = dict(os.environ if basis is None else basis)
    neu = saubere_umgebung(alt)
    aenderungen = []
    for name in set(alt) | set(neu):
        if alt.get(name) != neu.get(name):
            aenderungen.append((name, alt.get(name), neu.get(name)))
    return sorted(aenderungen)


if __name__ == "__main__":
    diff = was_wurde_entfernt()
    if not diff:
        print("umgebung ist bereits sauber — nichts zu entfernen")
    else:
        for name, alt, neu in diff:
            print("%-18s %s -> %s" % (name, (alt or "")[:70], neu if neu else "(entfernt)"))
