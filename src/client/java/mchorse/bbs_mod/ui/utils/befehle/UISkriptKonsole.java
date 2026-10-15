package mchorse.bbs_mod.ui.utils.befehle;

import com.mojang.blaze3d.platform.InputConstants;
import com.blenderbridge.BridgeExec;
import com.google.gson.JsonObject;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;

/**
 * In-Game-Skriptkonsole (P8e): Java-Zeile eintippen, Enter — laeuft auf der
 * warmen In-Prozess-JShell ({@link BridgeExec}), Ergebnis/stdout/Fehler landen
 * im Log darunter. Kein neuer Ausfuehrungsweg: dieselbe JShell, dieselbe
 * Sicherung (dev-Umgebung oder -Dblenderbridge.exec=true, sonst ok=false),
 * derselbe Deadlock-Guard wie der Bridge-Socket.
 *
 * <p>eval() verlangt den Client-MAIN-Thread — der Enter-Callback eines
 * UI-Elements laeuft genau dort.</p>
 */
public class UISkriptKonsole extends UIOverlayPanel
{
    private final UITextbox eingabe;
    private final UIStringList log;

    /** Eingabe-Historie wie in jeder Shell: Pfeil-hoch/-runter blättern, neuester zuletzt. */
    private final java.util.List<String> historie = new java.util.ArrayList<>();
    private int historieIndex = -1;

    public static void oeffnen(UIContext context)
    {
        UIOverlay.addOverlay(context, new UISkriptKonsole(), 420, 300);
    }

    public UISkriptKonsole()
    {
        super(L10n.lang("bbs.ui.script_console.title"));

        /* delayedInput: Callback NUR bei Enter — ohne das fuehrt JEDES getippte Zeichen
         * aus (gefunden per /ui/tippe: "7*6" lief als drei Einzel-Snippets). */
        this.eingabe = new HistorienEingabe(10000, (code) -> this.ausfuehren(code));
        this.eingabe.delayedInput();
        /* Klick auf eine Log-Zeile uebernimmt sie in die Eingabe ("> "-Befehle ohne
         * Praefix) — schneller als abtippen, ohne Clipboard-Umweg. */
        this.log = new UIStringList((zeilen) ->
        {
            String zeile = zeilen.get(0);

            this.eingabe.setText(zeile.startsWith("> ") ? zeile.substring(2) : zeile);
        });

        this.eingabe.relative(this.content).x(6).y(1F, -26).w(1F, -12).h(20);
        this.log.relative(this.content).x(6).y(6).w(1F, -12).h(1F, -36);

        this.content.add(this.eingabe, this.log);
    }

    private void ausfuehren(String code)
    {
        if (code == null || code.trim().isEmpty())
        {
            return;
        }

        this.historie.add(code);
        this.historieIndex = -1;

        this.zeilen("> " + code);

        JsonObject ergebnis = BridgeExec.eval(code);
        boolean ok = ergebnis.get(BridgeExec.KEY_OK).getAsBoolean();
        String stdout = ergebnis.get(BridgeExec.KEY_STDOUT).getAsString();
        String result = ergebnis.get(BridgeExec.KEY_RESULT).getAsString();

        if (!stdout.isEmpty())
        {
            this.zeilen(stdout);
        }

        if (ok)
        {
            this.zeilen(result.isEmpty() ? "ok" : result);
        }
        else
        {
            this.zeilen("FEHLER: " + ergebnis.get(BridgeExec.KEY_ERROR).getAsString());
        }

        this.eingabe.setText("");
        this.log.update();
        this.log.scroll.setScroll(this.log.scroll.scrollSize);
    }

    private void zeilen(String text)
    {
        for (String zeile : text.split("\n"))
        {
            this.log.add(zeile);
        }
    }

    /** Blättert relativ; -1 = neuester. Index klemmt an den Enden statt zu rollen. */
    private void blaettern(int richtung)
    {
        if (this.historie.isEmpty())
        {
            return;
        }

        int neu = (this.historieIndex < 0 ? this.historie.size() : this.historieIndex) + richtung;

        this.historieIndex = Math.max(0, Math.min(neu, this.historie.size() - 1));
        this.eingabe.setText(this.historie.get(this.historieIndex));
        this.eingabe.textbox.moveCursorToEnd();
    }

    private class HistorienEingabe extends UITextbox
    {
        public HistorienEingabe(int length, java.util.function.Consumer<String> callback)
        {
            super(length, callback);
        }

        @Override
        public boolean subKeyPressed(mchorse.bbs_mod.ui.framework.UIContext context)
        {
            if (this.isFocused())
            {
                if (context.isPressed(InputConstants.KEY_UP))
                {
                    UISkriptKonsole.this.blaettern(-1);

                    return true;
                }

                if (context.isPressed(InputConstants.KEY_DOWN))
                {
                    UISkriptKonsole.this.blaettern(1);

                    return true;
                }
            }

            return super.subKeyPressed(context);
        }
    }
}
