package mchorse.bbs_mod.ui.texturstudio;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Liefert eine Seite aus assets/bbs/html/ an den eingebetteten Browser.
 *
 * <p>⛔ Nicht Rinkus ModScheme: es sucht mit {@code ClassLoader.getResourceAsStream("/assets/...")},
 * und ein fuehrender Schraegstrich findet ueber den ClassLoader nie etwas -- "Resource ... NOT
 * found!", im Browser ERR_FAILED (gemessen 2026-10-13, Rinku 3.0.5). {@code Class.getResourceAsStream}
 * versteht den absoluten Pfad.</p>
 */
public final class TexturStudioSeite implements CefResourceHandler
{
    private final String name;
    private byte[] daten;
    private int position;

    public TexturStudioSeite(String name)
    {
        this.name = name;
    }

    @Override
    public boolean processRequest(CefRequest anfrage, CefCallback weiter)
    {
        /* Nur einfache Dateinamen, kein Weg aus dem html-Ordner heraus. */
        if (!this.name.matches("[A-Za-z0-9_.-]+") || this.name.startsWith("."))
        {
            weiter.cancel();

            return false;
        }

        try (InputStream strom = TexturStudioSeite.class.getResourceAsStream("/assets/bbs/html/" + this.name))
        {
            if (strom == null)
            {
                weiter.cancel();

                return false;
            }

            this.daten = strom.readAllBytes();
        }
        catch (IOException e)
        {
            weiter.cancel();

            return false;
        }

        weiter.Continue();

        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse antwort, IntRef laenge, StringRef umleitung)
    {
        antwort.setMimeType(this.name.endsWith(".js") ? "text/javascript" : "text/html");
        antwort.setStatus(200);
        laenge.set(this.daten.length);
    }

    @Override
    public boolean readResponse(byte[] ziel, int maximal, IntRef gelesen, CefCallback weiter)
    {
        int anzahl = Math.min(maximal, this.daten.length - this.position);

        if (anzahl <= 0)
        {
            gelesen.set(0);

            return false;
        }

        System.arraycopy(this.daten, this.position, ziel, 0, anzahl);
        this.position += anzahl;
        gelesen.set(anzahl);

        return true;
    }

    @Override
    public void cancel()
    {}
}
