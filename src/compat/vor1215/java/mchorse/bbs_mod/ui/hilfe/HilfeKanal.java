package mchorse.bbs_mod.ui.hilfe;

/**
 * vor1215: der Guide-Kanal spielt in Rinku (Chromium im Spiel), das es nur fuer 26.3 gibt. Ohne
 * Rinku oeffnet Guides den Guide im Systembrowser; dieser Kanal bleibt darum still.
 */
public final class HilfeKanal implements AutoCloseable
{
    public volatile double zeit;
    public volatile int zustand = -1;
    public volatile String video = "";

    public HilfeKanal(String video, int sekunde, boolean stumm, boolean warten)
    {
        this.video = video;
        this.zeit = sekunde;
    }

    public static void frueh()
    {}

    public void starten()
    {}

    public void spielen()
    {}

    public void pause()
    {}

    public void springe(String video, double sekunde)
    {
        this.video = video;
        this.zeit = sekunde;
    }

    public void tempo(double tempo)
    {}

    public void neuLaden()
    {}

    @Override
    public void close()
    {}
}
