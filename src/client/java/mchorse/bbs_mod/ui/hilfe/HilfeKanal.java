package mchorse.bbs_mod.ui.hilfe;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import mchorse.bbs_mod.ui.texturstudio.TexturStudioSeite;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.browser.CefMessageRouter.CefMessageRouterConfig;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefMessageRouterHandlerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One browser with the guide page (assets/bbs/html/guide.html) and the wire to YouTube's player in
 * it. There: {@code executeJavaScript} calls the IFrame API (play, pause, seek, rate). Back:
 * {@code window.cefQuery} reports time, length, state and errors twice a second.
 *
 * <p>The page comes from an https origin BBS answers itself, named in reverse-DNS form: YouTube's
 * embedded player wants a Referer that identifies the app (Required Minimum Functionality), and
 * refuses an embed without one (Error 153, measured 2026-10-14).</p>
 *
 * <p>⛔ Only touch with Rinku loaded -- every line here loads Rinku and CEF classes.</p>
 */
public final class HilfeKanal implements AutoCloseable
{
    private static final String DOMAIN = "io.github.afrobamayt.bbsrenewed";
    private static final String SEITE = "https://" + DOMAIN + "/guide.html";

    private static CefMessageRouter router;
    private static final Map<CefBrowser, HilfeKanal> KANAELE = new ConcurrentHashMap<>();

    public final RinkuBrowser browser;

    /** What the player said last: seconds, length, YouTube state (-1 unstarted, 0 ended, 1 playing, 2 paused, 3 buffering, 5 cued), video. */
    public volatile double zeit;
    public volatile double laenge;
    public volatile int zustand = -1;
    public volatile String video = "";
    /** The player could not start: no connection, or YouTube refused (its error code). */
    public volatile String fehler;

    /** @param warten make the player only on {@link #starten()} (the one-time note stands in its place) */
    public HilfeKanal(String video, int sekunde, boolean stumm, boolean warten)
    {
        registrieren();

        this.video = video;
        this.browser = Rinku.createBrowser(SEITE + "?v=" + video + "&t=" + sekunde + (stumm ? "&mute=1" : "") + (warten ? "&wait=1" : ""), false, 1, 1);
        KANAELE.put(this.browser, this);
    }

    public void starten()
    {
        this.befehl("bbsStart()");
    }

    public void spielen()
    {
        this.befehl("bbsPlay()");
    }

    public void pause()
    {
        this.befehl("bbsPause()");
    }

    public void springe(String video, double sekunde)
    {
        this.video = video;
        this.zeit = sekunde;
        this.befehl("bbsSeek('" + video.replaceAll("[^A-Za-z0-9_-]", "") + "'," + sekunde + ")");
    }

    public void tempo(double tempo)
    {
        this.befehl("bbsRate(" + tempo + ")");
    }

    public void neuLaden()
    {
        this.fehler = null;
        this.browser.reload();
    }

    private void befehl(String js)
    {
        this.browser.executeJavaScript(js, SEITE, 0);
    }

    private void empfange(String nachricht)
    {
        String[] teile = nachricht.split(":", 5);

        if (teile[0].equals("z") && teile.length == 5)
        {
            this.zeit = Double.parseDouble(teile[1]);
            this.laenge = Double.parseDouble(teile[2]);
            this.zustand = Integer.parseInt(teile[3]);
            this.video = teile[4];
            this.fehler = null;
        }
        else if (teile[0].equals("f"))
        {
            this.fehler = teile.length > 1 ? teile[1] : "?";
        }
    }

    @Override
    public void close()
    {
        KANAELE.remove(this.browser);
        this.browser.close(true);
    }

    /** From client start: hook up page and wire as soon as (or if already) Rinku runs -- browsers
     * from Rinku's pool only know the wires that existed when they were made. */
    public static void frueh()
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

        Rinku.getApp().getHandle().registerSchemeHandlerFactory("https", DOMAIN, (browser, frame, schema, anfrage) ->
        {
            String url = anfrage.getURL().replaceFirst("[?#].*$", "");

            return new TexturStudioSeite(url.substring(url.lastIndexOf('/') + 1));
        });

        /* An own function name: Texture Studio's router already answers window.cefQuery */
        router = CefMessageRouter.create(new CefMessageRouterConfig("bbsHilfeQuery", "bbsHilfeQueryCancel"));
        router.addHandler(new CefMessageRouterHandlerAdapter()
        {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long id, String anfrage, boolean dauerhaft, CefQueryCallback antwort)
            {
                HilfeKanal kanal = KANAELE.get(browser);

                if (kanal == null)
                {
                    return false;
                }

                antwort.success("");
                Minecraft.getInstance().execute(() -> kanal.empfange(anfrage));

                return true;
            }
        }, true);
        Rinku.getClient().getHandle().addMessageRouter(router);
    }
}
