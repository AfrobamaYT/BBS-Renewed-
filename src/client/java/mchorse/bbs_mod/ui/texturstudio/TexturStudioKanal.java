package mchorse.bbs_mod.ui.texturstudio;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Ein Browser mit der Gastseite des Texture Studios (assets/bbs/html/texturstudio.html) und der
 * Draht zu ihr. Hin: {@code executeJavaScript}. Zurueck: {@code window.cefQuery}, die uebliche
 * JCEF-Bruecke ueber einen {@link CefMessageRouter} an Rinkus Client.
 *
 * <p>⛔ Nur anfassen, wenn Rinku geladen ist ({@link TexturStudio#verfuegbar()}) -- jede Zeile
 * hier laedt Rinku- und CEF-Klassen.</p>
 */
public final class TexturStudioKanal implements AutoCloseable
{
    /**
     * Eine erfundene https-Adresse, deren Anfragen BBS selbst beantwortet ({@link TexturStudioSeite}
     * liest /assets/bbs/html/... aus den Mod-Ressourcen). ⛔ Nicht mod://: Rinku 3.0.5 haengt dafuer nur
     * einen Handler an, meldet das Schema aber nicht bei Chromium an -- ERR_UNKNOWN_URL_SCHEME
     * (gemessen 2026-10-13). Eine echte https-Herkunft ist fuer das Photopea-iframe ohnehin besser.
     */
    private static final String DOMAIN = "texturstudio.bbs";
    public static final String SEITE = "https://" + DOMAIN + "/texturstudio.html";

    /** Ein Router fuer alle Browser; die Antwort findet ihren Kanal ueber den Browser. */
    private static CefMessageRouter router;
    private static final Map<CefBrowser, TexturStudioKanal> KANAELE = new ConcurrentHashMap<>();

    private final RinkuBrowser browser;
    private final Consumer<byte[]> bild;
    private boolean geladen;
    private boolean bereit;
    private byte[] wartend;

    /**
     * @param bild bekommt jedes geaenderte Bild des Editors als PNG, auf dem Hauptthread
     */
    public TexturStudioKanal(int breite, int hoehe, Consumer<byte[]> bild)
    {
        registrieren();

        this.bild = bild;
        this.browser = Rinku.createBrowser(SEITE, false, Math.max(1, breite), Math.max(1, hoehe));
        KANAELE.put(this.browser, this);
    }

    public RinkuBrowser browser()
    {
        return this.browser;
    }

    /** Hat der Editor sein erstes "done" geschickt? Vorher kommt kein Bild zurueck. */
    public boolean bereit()
    {
        return this.bereit;
    }

    /** Eine PNG als neues Dokument im Editor oeffnen; vor dem Laden der Seite wird gewartet. */
    public void lade(byte[] png)
    {
        if (!this.geladen)
        {
            this.wartend = png;

            return;
        }

        this.browser.executeJavaScript("bbsLade('" + Base64.getEncoder().encodeToString(png) + "');", SEITE, 0);
    }

    private void empfange(String nachricht)
    {
        if (nachricht.equals("geladen"))
        {
            this.geladen = true;

            if (this.wartend != null)
            {
                byte[] png = this.wartend;

                this.wartend = null;
                this.lade(png);
            }
        }
        else if (nachricht.equals("bereit"))
        {
            this.bereit = true;
        }
        else if (nachricht.startsWith("bild:"))
        {
            this.bild.accept(Base64.getDecoder().decode(nachricht.substring(5)));
        }
    }

    @Override
    public void close()
    {
        KANAELE.remove(this.browser);
        this.browser.close(true);
    }

    /** Vom Client-Start gerufen: die Bruecke anmelden, sobald (oder falls schon) Rinku laeuft. */
    static void frueh()
    {
        if (Rinku.isInitialized())
        {
            registrieren();
        }
        else
        {
            Rinku.scheduleForInit((erfolg) ->
            {
                if (erfolg)
                {
                    registrieren();
                }
            });
        }
    }

    private static void registrieren()
    {
        if (router != null)
        {
            return;
        }

        Rinku.getApp().getHandle().registerSchemeHandlerFactory("https", DOMAIN,
            (browser, frame, schema, anfrage) ->
            {
                /* Without the query: the guide page takes its video and start that way (ERR_FAILED
                 * before, the name check saw "guide.html?v=..."). */
                String url = anfrage.getURL().replaceFirst("[?#].*$", "");

                return new TexturStudioSeite(url.substring(url.lastIndexOf('/') + 1));
            });

        router = CefMessageRouter.create();
        router.addHandler(new CefMessageRouterHandlerAdapter()
        {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long id, String anfrage, boolean dauerhaft, CefQueryCallback antwort)
            {
                TexturStudioKanal kanal = KANAELE.get(browser);

                if (kanal == null)
                {
                    return false;
                }

                antwort.success("");
                /* CEF ruft auf seinem eigenen Thread; Texturen und UI gehoeren dem Hauptthread. */
                Minecraft.getInstance().execute(() -> kanal.empfange(anfrage));

                return true;
            }
        }, true);
        Rinku.getClient().getHandle().addMessageRouter(router);
    }
}
