package mchorse.bbs_mod.ui.utils.befehle;

/**
 * Eine ausführbare Zeile der Befehls-Palette — egal ob sie aus einem Keybind
 * oder einer Kontextmenü-Action geerntet wurde.
 */
public class PalettenBefehl
{
    public final String zeile;
    public final Runnable lauf;

    public PalettenBefehl(String zeile, Runnable lauf)
    {
        this.zeile = zeile;
        this.lauf = lauf;
    }
}
