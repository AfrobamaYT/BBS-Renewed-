package mchorse.bbs_mod;

import mchorse.bbs_mod.utils.watchdog.WatchDog;
import java.io.File;
import net.minecraft.client.Minecraft;

public class BBSResources
{
    private static WatchDog watchDog;

    /**
     * Was beim Client-Start SOFORT gehen muss.
     *
     * <p>⚠️ 26.2: {@code getFormCategories().setup()} ist hier RAUS und heisst jetzt
     * {@link #formulareAufbauen()}. Grund ist kein Umbenennen, sondern der Lebenszyklus: der
     * Aufbau legt Vorlage-Formen an, darunter {@code new ItemStack(Items.STICK)}. In 26.2
     * sind die Item-KOMPONENTEN zu diesem Zeitpunkt noch nicht gebunden, und der
     * ItemStack-Konstruktor liest sie:</p>
     *
     * <pre>
     * NullPointerException: Components not bound yet
     *     at Holder$Reference.components
     *     at ItemStack.&lt;init&gt;
     *     at ExtraFormSection.initiate
     * </pre>
     *
     * <p>Der Dateiwaechter dagegen haengt an nichts aus der Registry und bleibt hier.</p>
     */
    public static void init()
    {
        setupWatchdog();
    }

    /**
     * Der Teil des Aufbaus, der GEBUNDENE Registries braucht -- aufgerufen aus
     * {@code CLIENT_STARTED}, nicht aus dem Initialisierer.
     */
    private static boolean formulareStehen;

    /**
     * Der Teil des Aufbaus, der GEBUNDENE Registries braucht.
     *
     * <p>⚠️ 26.2 -- ZWEIMAL ZU FRUEH GEWESEN, beide Male am laufenden Start gemessen:</p>
     * <ol>
     * <li>Im Initialisierer ({@code onInitializeClient}): "Components not bound yet".</li>
     * <li>In {@code CLIENT_STARTED}: dieselbe Ausnahme. Auch dort sind sie noch nicht da.</li>
     * </ol>
     *
     * <p>Grund: Item-Komponenten sind datengetrieben geworden. Sie kommen mit dem
     * Datenpaket, also erst wenn eine WELT laedt -- nicht beim Start des Clients. Ein
     * {@code new ItemStack(...)} davor liest einen Holder, dessen Komponenten noch null sind.</p>
     *
     * <p>Deshalb haengt der Aufbau jetzt am Welt-Beitritt. Das passt auch fachlich: die
     * Formkategorien sind Vorlagen fuer den BBS-Editor, und den oeffnet man ohnehin nur in
     * einer Welt. Das Flag sorgt dafuer, dass ein zweiter Beitritt nichts neu aufbaut.</p>
     */
    public static void formulareAufbauen()
    {
        if (formulareStehen)
        {
            return;
        }

        formulareStehen = true;

        BBSModClient.getFormCategories().setup();
    }

    /** Builds the palette again, but only once a world has built it: before that the item components are not bound */
    public static void formulareNeu()
    {
        if (formulareStehen) BBSModClient.getFormCategories().setup();
    }

    public static void setupWatchdog()
    {
        File assetsFolder = BBSMod.getAssetsFolder();

        watchDog = new WatchDog(assetsFolder, false, (runnable) -> Minecraft.getInstance().execute(runnable));
        watchDog.getProxy().register(BBSModClient.getTextures());
        watchDog.getProxy().register(BBSModClient.getModels());
        watchDog.getProxy().register(mchorse.bbs_mod.fonts.FontManager.get());
        watchDog.getProxy().register(BBSModClient.getSounds());
        watchDog.getProxy().register(BBSModClient.getFormCategories());

        watchDog.start();
    }

    public static void stopWatchdog()
    {
        if (watchDog != null)
        {
            watchDog.stop();
            watchDog = null;
        }
    }

    public static void tick()
    {
        if (watchDog != null)
        {
            watchDog.getProxy().tick();
        }
    }
}