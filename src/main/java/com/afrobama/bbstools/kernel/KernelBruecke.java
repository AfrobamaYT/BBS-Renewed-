package com.afrobama.bbstools.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Macht MIXIN-HANDLER in {@code src/main} hotswapbar — die server-taugliche schwester von
 * {@code com.afrobama.bbstools.client.blender.MixinBruecke}.
 *
 * <h2>Warum es diese klasse zusätzlich zu {@code MixinBruecke} braucht</h2>
 *
 * <p>{@code MixinBruecke} liegt unter {@code com.afrobama.bbstools.client.blender} —
 * CLIENT-sourceset. Auf einem dedizierten server existieren client-klassen nicht; sobald ein
 * server-mixin (z.B. an {@code MobEntity}, {@code TriggerBlockEntity}) über die client-brücke
 * delegieren würde, würfe das JVM-classloading beim ersten aufruf einen
 * {@code NoClassDefFoundError}. Diese klasse liegt deshalb unter {@code src/main} und importiert
 * NICHTS aus {@code com.afrobama.bbstools.client.*} — sie ist auf server UND client ladbar.</p>
 *
 * <h2>Funktionsweise (identisch zu {@code MixinBruecke})</h2>
 *
 * <p>Der gewobene mixin-handler wird zum einzeiler, der hierher delegiert. Die eigentliche
 * logik liegt in einer normalen klasse im kernel-paket {@link #PAKETE} — die klassen dort
 * werden über den hotpatch-agent ({@code com.afrobama.hotpatch.KernelLoader}) ausgetauscht und
 * bleiben damit swapbar, ohne dass der gewobene handler-code sich je wieder ändert.</p>
 *
 * <pre>
 *   &#64;Inject(method = "writeNbt", at = &#64;At("TAIL"))
 *   private void bbstools$scrubNullEntries(NbtCompound nbt, CallbackInfo ci)
 *   {
 *       KernelBruecke.rufe("TriggerBlockEntityLogik", "scrubNullEntries", nbt);
 *   }
 * </pre>
 *
 * <h2>⚠ OHNE AGENT LÄUFT ALLES NORMAL WEITER — DAS IST PFLICHT, NICHT OPTIONAL</h2>
 *
 * <p>Ein produktiver dedizierter server hat KEINEN hotpatch-agent attached. In diesem fall
 * liefert {@link #kernelLoaderKlasse()} {@code null} (die {@code Class.forName} schlägt fehl
 * und wird geschluckt), {@link #rufe} liefert dann immer {@code null}, und
 * {@link #rufeUndAbbrechen} liefert immer {@code false}. AUFRUFER MÜSSEN DAS AUSHALTEN — siehe
 * die einzelnen mixin-umbauten, die deshalb bei {@code rufeUndAbbrechen == false} einfach NICHT
 * abbrechen und den ursprünglichen BBS/Minecraft-rumpf normal weiterlaufen lassen. Ohne agent
 * verhält sich der server also exakt wie OHNE diese brücke — kein feature geht kaputt, es ist
 * nur nicht mehr live-patchbar (was auf einem server ohne agent ohnehin nicht erwartet wird).</p>
 *
 * <h2>Robustheit</h2>
 *
 * <p>Ein mixin-handler, der eine exception nach oben durchreicht, reisst den tick/netzwerk-
 * durchlauf ab — auf dem SERVER trifft das ALLE angeschlossenen spieler, nicht nur einen
 * client. Deshalb schluckt jeder aufruf hier {@link Throwable} und liefert {@code null} /
 * {@code false}, genau wie {@code MixinBruecke}.</p>
 *
 * <p><b>GESCHLUCKT HEISST NICHT MEHR STILL (2026-07-28).</b> Jeder auflösungsfehler geht durch
 * {@link BrueckenMeldung} — EINMAL laut ins log, danach nur noch ein {@code containsKey}.
 * ⚠ UNTERSCHIED ZU {@code MixinBruecke}: der zustand "kein agent attached" ist hier der
 * dokumentierte normalfall (siehe oben) und wird als EINE info-zeile gemeldet, nicht als
 * fehler pro aufrufstelle. Als fehler zählt nur: agent da, klasse oder methode trotzdem nicht
 * auflösbar. Statisch prüfbar ohne laufendes spiel: {@code python tools/bruecken-ziele.py}.</p>
 */
public final class KernelBruecke
{
    private KernelBruecke() {}

    /**
     * Kernel-pakete, in denen nach der ziel-klasse gesucht wird, in dieser reihenfolge.
     *
     * <p>{@code com.afrobama.bbstools.kernel.logik} liegt unter {@code src/main} — anders als
     * {@code com.afrobama.blendertransform} (client-only). Server-mixin-logik gehört dorthin.</p>
     */
    private static final String[] PAKETE = {
        "com.afrobama.bbstools.kernel.logik",
    };

    /**
     * Handle-cache. Schlüssel enthält die GENERATION — nach einem swap greift kein alter
     * eintrag mehr, weil sich der schlüssel ändert. Alte einträge bleiben liegen; das ist
     * bewusst, denn ein aufräumen bräuchte eine referenz auf die alte generation, und genau
     * solche referenzen sind die ursache des classloader-leaks (siehe {@code KernelLoader}).
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, MethodHandle> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Generation, für die der cache gilt. Steigt sie, sind alle einträge ungültig. */
    private static volatile int cacheGeneration = -1;

    /** Zählt aufrufe, die ins leere liefen — sichtbar über den mess-kanal. */
    public static volatile long fehlschlaege;

    /** Letzter fehler im klartext, für die diagnose. */
    public static volatile String letzterFehler = "(keiner)";

    /**
     * Ruft eine statische kernel-methode aus einem mixin-handler.
     *
     * <p>OHNE AGENT (produktiver server): liefert immer {@code null}. Aufrufer müssen das als
     * "kein feature ausgelöst" behandeln, nicht als fehler.</p>
     *
     * @param klasse    einfacher klassenname, z.B. {@code "TriggerBlockEntityLogik"}
     * @param methode   methodenname
     * @param argumente argumente; ihre laufzeit-typen bestimmen die signatur. KEINE
     *                  kernel-typen übergeben — nur primitive, JDK-, Minecraft- und
     *                  BBS-typen, sonst ist man beim identitäts-problem zurück.
     * @return rückgabewert, oder {@code null} bei void, ohne agent und bei jedem fehler
     */
    public static Object rufe(String klasse, String methode, Object... argumente)
    {
        try
        {
            MethodHandle handle = handleHolen(klasse, methode, argumente);

            if (handle == null)
            {
                return null;
            }

            return handle.invokeWithArguments(argumente);
        }
        catch (Throwable t)
        {
            /* Niemals nach oben durchreichen — siehe klassen-javadoc. Aber auch nicht still. */
            fehlschlaege++;
            letzterFehler = klasse + "#" + methode + ": "
                + t.getClass().getSimpleName() + " " + t.getMessage();

            BrueckenMeldung.einmal("rufe|" + klasse + "#" + methode, "aufruf warf",
                "KernelBruecke", klasse, methode, t);

            return null;
        }
    }

    /** Holt einen handle aus dem cache oder baut ihn neu, wenn die generation gewechselt hat. */
    private static MethodHandle handleHolen(String klasse, String methode, Object[] argumente)
        throws Throwable
    {
        int jetzt = generation();

        if (jetzt != cacheGeneration)
        {
            /* SWAP PASSIERT (oder: kein agent, generation bleibt -1) — cache leeren, bevor ein
             * alter eintrag benutzt werden kann. */
            CACHE.clear();
            cacheGeneration = jetzt;
        }

        String schluessel = jetzt + "|" + klasse + "#" + methode + "|" + argumente.length;
        MethodHandle vorhanden = CACHE.get(schluessel);

        if (vorhanden != null)
        {
            return vorhanden;
        }

        Class<?> ziel = zielKlasseFinden(klasse);

        if (ziel == null)
        {
            /* ZWEI SEHR VERSCHIEDENE FÄLLE, die vorher beide ein nacktes `return null` waren:
             *
             * (a) kein agent attached — der dokumentierte normalfall auf einem produktiven
             *     server und bei jedem boot ohne hotpatch-tooling. Das ist KEIN fehler; eine
             *     meldung pro aufrufstelle wäre reines rauschen. Also genau EINE info-zeile
             *     für den ganzen prozess, mit konstantem schlüssel.
             *
             * (b) agent da, klasse trotzdem nicht auflösbar — das ist ein echter defekt: der
             *     handler tut nichts und niemand merkt es. Laut melden. */
            if (kernelLoaderKlasse() == null)
            {
                BrueckenMeldung.einmalHinweis("KernelBruecke|kein-agent",
                    "KernelBruecke: kein hotpatch-agent attached — alle handler nach dem "
                    + "brücken-muster in src/main sind inaktiv (erwartet ohne -javaagent).");
            }
            else
            {
                BrueckenMeldung.einmal(schluessel, "kernel-klasse in keinem paket gefunden",
                    "KernelBruecke", klasse, methode);
            }

            return null;
        }

        java.lang.reflect.Method m = passendeMethode(ziel, methode, argumente);

        if (m == null)
        {
            /* Klasse da, methode nicht — tippfehler oder umbenennung, die der einzeiler im
             * mixin nicht mitbekommen hat. Kompiliert weiter, wirkt aber nie. */
            BrueckenMeldung.einmal(schluessel,
                "keine STATISCHE methode mit passender parameter-zahl", "KernelBruecke",
                klasse, methode);

            return null;
        }

        m.setAccessible(true);

        MethodHandle handle = MethodHandles.lookup().unreflect(m);
        CACHE.put(schluessel, handle);

        return handle;
    }

    /**
     * Sucht die klasse in der AKTUELLEN kernel-generation.
     *
     * <p>Der weg führt über {@code KernelLoader.laden}, weil nur der agent weiss, welcher
     * loader gerade aktuell ist. Ein {@code Class.forName} hier würde die kopie DIESES
     * loaders finden — die stabile, nicht die frisch getauschte.</p>
     */
    private static Class<?> zielKlasseFinden(String einfacherName)
    {
        Class<?> loader = kernelLoaderKlasse();

        if (loader == null)
        {
            /* ⛔ HIER STAND `return null` — DER FRUEHE AUSSTIEG, DER ALLES ERKLAERT.
             * Ohne Agent gibt es die KernelLoader-Klasse nicht, und damit war an dieser
             * Zeile Schluss: der Fallback weiter unten wurde NIE erreicht. Genau davor
             * warnt der Zwilling in MixinBruecke ("Der loader == null-zweig oben greift
             * nur, wenn die KernelLoader-KLASSE fehlt") — dort gibt es beide Wege, hier
             * fehlten beide.
             *
             * ⚠️ ICH HABE ZUERST DIE FALSCHE STELLE GEFIXT (2026-08-10): den Fallback ans
             *    Ende der Schleife gehaengt, geswappt, gemessen — immer noch null. Erst
             *    das Nachlesen der ganzen Methode zeigte den Ausstieg zwei Zeilen vorher.
             *    Ein Fix, dessen Wirkung man nicht misst, sieht genauso aus wie keiner. */
            return ohneKernelLaden(einfacherName);
        }

        for (String paket : PAKETE)
        {
            try
            {
                java.lang.reflect.Method laden = loader.getMethod("laden", String.class);

                return (Class<?>) laden.invoke(null, paket + "." + einfacherName);
            }
            catch (Throwable weiter)
            {
                /* Nächstes paket probieren — eine klasse liegt nur in einem davon. */
            }
        }

        /* ⛔ HIER STAND EIN NACKTES `return null` — UND DAMIT TAT JEDER HANDLER NACH DEM
         * BRUECKEN-MUSTER IN src/main GAR NICHTS (2026-08-10 gemessen).
         *
         * Ohne laufenden Hotpatch-Agent gibt es keinen aktiven Kernel; `laden(...)` wirft
         * dann fuer jedes Paket, die Schleife laeuft durch, und hier war Schluss. `rufe`
         * lieferte null, `rufeUndAbbrechen` false — ohne Fehlerzaehler, ohne Logzeile.
         * Gemessen an AudioReaderLogik:
         *
         *     ueber die Bruecke: null      direkt aufgeloest: klasse+methode DA
         *
         * Die Folge war kein Hotswap-Verlust, sondern ein FUNKTIONSVERLUST: MP3 und FLAC
         * blieben stumm, weil der Handler nie etwas zurueckgab. Genau deshalb liess sich
         * bisher kein src/main-Mixin auf diese Bruecke legen — es haette die Funktion
         * abgeschaltet statt sie hotswapbar zu machen.
         *
         * MixinBruecke (client) hat diesen Fallback seit dem 2026-07-26, mit derselben
         * Begruendung und derselben Messung (root-kinder 7 -> 7 ueber die Bruecke, 7 -> 8
         * direkt). Er fehlte hier schlicht.
         *
         * OHNE Agent laedt der normale Classloader — dann ist der Handler zwar nicht
         * hotswapbar, aber er ARBEITET. MIT Agent greift der Weg darueber weiterhin zuerst,
         * also bleibt der Swap erhalten. */
        return ohneKernelLaden(einfacherName);
    }

    /**
     * Die Logik-Klasse ueber den normalen Classloader holen — der Weg ohne Hotpatch-Agent.
     *
     * <p>Was hier zurueckkommt, ist NICHT hotswapbar (es ist die Kopie dieses Loaders). Das
     * ist der bewusste Tausch: lieber ein Handler, der arbeitet und einen Boot braucht, als
     * einer, der still nichts tut.</p>
     */
    private static Class<?> ohneKernelLaden(String einfacherName)
    {
        for (String paket : PAKETE)
        {
            try
            {
                return Class.forName(paket + "." + einfacherName, false,
                    KernelBruecke.class.getClassLoader());
            }
            catch (Throwable naechstesPaket)
            {
                /* in einem der anderen pakete weitersuchen */
            }
        }

        return null;
    }

    /** Findet die methode, deren parameter zu den übergebenen argumenten passen. */
    private static java.lang.reflect.Method passendeMethode(Class<?> ziel, String name,
                                                            Object[] argumente)
    {
        for (java.lang.reflect.Method m : ziel.getDeclaredMethods())
        {
            if (!m.getName().equals(name) || m.getParameterCount() != argumente.length)
            {
                continue;
            }

            if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()))
            {
                continue;
            }

            return m;
        }

        return null;
    }

    /** Aktuelle kernel-generation, oder -1 wenn kein agent läuft. */
    private static int generation()
    {
        Class<?> loader = kernelLoaderKlasse();

        if (loader == null)
        {
            return -1;
        }

        try
        {
            java.lang.reflect.Method g = loader.getMethod("generation");

            return (Integer) g.invoke(null);
        }
        catch (Throwable t)
        {
            return -1;
        }
    }

    /**
     * Die agent-klasse, über die der kernel erreichbar ist — oder {@code null} ohne agent.
     *
     * <p>Der agent liegt im SYSTEM-classloader (nicht in Knot, nicht im server-app-loader) —
     * deshalb explizit dorthin fragen statt {@code Class.forName} ohne loader. Das gilt
     * gleichermassen für client (Knot als kind des system-loaders) und dedizierten server
     * (app-loader als kind des system-loaders): der agent ist von beiden aus über den
     * system-loader erreichbar, FALLS er beim start attached wurde ({@code -javaagent:...}).
     * Ist kein agent attached, wirft {@code Class.forName} und wird hier geschluckt — das ist
     * der normale, erwartete fall auf einem server ohne hotpatch-tooling.</p>
     */
    /* AUTONOMIE 2026-07-27 — GEMESSENER HOTSPOT: 87,1 %% CPU (2224 samples), 2 FPS.
     *
     * Vorher rief diese methode bei JEDEM rufe()/rufeUndAbbrechen() ein Class.forName und
     * schluckte die exception. Ohne attached hotpatch-agent ist der normalfall aber genau
     * die exception — und jede kostet einen befuellten stacktrace. Pro mixin-aufruf, pro
     * entity, pro frame. In der szene mit vielen actors ging die frame-zeit dadurch von
     * 7,5 ms auf 264,8 ms hoch (frame-timeline phase=final, 97,5%% der frame-zeit).
     *
     * Das ergebnis kann sich zur laufzeit NICHT aendern: der agent wird per -javaagent beim
     * JVM-start attached oder nie. Also einmal suchen, ergebnis behalten — auch das
     * negative. GEFUNDEN_NICHTS ist ein sentinel, weil null "noch nicht gesucht" heisst. */
    private static final Class<?> GEFUNDEN_NICHTS = Void.class;
    private static volatile Class<?> kernelLoaderCache;

    private static Class<?> kernelLoaderKlasse()
    {
        Class<?> gemerkt = kernelLoaderCache;

        if (gemerkt == null)
        {
            try
            {
                gemerkt = Class.forName("com.afrobama.hotpatch.KernelLoader", false,
                    ClassLoader.getSystemClassLoader());
            }
            catch (Throwable ohneAgent)
            {
                gemerkt = GEFUNDEN_NICHTS;    /* kein agent — normalfall, nie wieder suchen */
            }

            kernelLoaderCache = gemerkt;
        }

        return gemerkt == GEFUNDEN_NICHTS ? null : gemerkt;
    }

    /**
     * Wie {@link #rufe}, aber für handler mit {@code cancellable = true}.
     *
     * <p>Der handler bleibt ein einzeiler:</p>
     * <pre>
     *   &#64;Inject(method = "tick", at = &#64;At("HEAD"), cancellable = true)
     *   private void bbs$skip(CallbackInfo ci)
     *   {
     *       if (KernelBruecke.rufeUndAbbrechen("MobArmyLogik", "sollUeberspringen", this)) ci.cancel();
     *   }
     * </pre>
     *
     * <p>Bei einem fehler ODER ohne agent wird {@code false} geliefert — also NICHT
     * abgebrochen. Das ist die sichere richtung: der ursprüngliche Minecraft/BBS-rumpf läuft
     * weiter, statt dass ein feature still ausfällt, weil die brücke nicht durchkam.</p>
     */
    public static boolean rufeUndAbbrechen(String klasse, String methode, Object... argumente)
    {
        Object ergebnis = rufe(klasse, methode, argumente);

        return ergebnis instanceof Boolean && (Boolean) ergebnis;
    }

    /**
     * Liest ein {@code @Shadow}-feld der ZIELKLASSE für den kernel.
     *
     * <p>Der zugriff läuft über {@code objekt.getClass()}, also loader-unabhängig.</p>
     *
     * <p>⚠ Der RÜCKGABEWERT darf im kernel nur als {@code Object} behandelt oder auf einen
     * stabilen typ gecastet werden (JDK/Minecraft/BBS). Ein cast auf einen kernel-typ wäre
     * wieder das identitäts-problem.</p>
     */
    public static Object feldLesen(Object zielObjekt, String feldName)
    {
        if (zielObjekt == null)
        {
            return null;
        }

        for (Class<?> k = zielObjekt.getClass(); k != null && k != Object.class; k = k.getSuperclass())
        {
            try
            {
                java.lang.reflect.Field f = k.getDeclaredField(feldName);
                f.setAccessible(true);

                return f.get(zielObjekt);
            }
            catch (NoSuchFieldException weiter)
            {
                /* Feld liegt oft in einer oberklasse — weitersuchen. */
            }
            catch (Throwable t)
            {
                fehlschlaege++;
                letzterFehler = "feldLesen " + feldName + ": " + t.getClass().getSimpleName();

                BrueckenMeldung.einmal("feldLesen|" + feldName, "feld-zugriff warf",
                    "KernelBruecke", zielObjekt.getClass().getSimpleName(), feldName, t);

                return null;
            }
        }

        /* Kein treffer in der ganzen oberklassen-kette. Ein umbenanntes @Shadow-feld sieht für
         * den handler exakt aus wie "wert ist null" — deshalb melden. */
        BrueckenMeldung.einmal("feldLesen|" + zielObjekt.getClass().getName() + "|" + feldName,
            "feld in keiner oberklasse gefunden", "KernelBruecke",
            zielObjekt.getClass().getSimpleName(), feldName);

        return null;
    }

    /**
     * Schreibt ein {@code @Shadow}-feld der zielklasse.
     *
     * @return {@code true} wenn geschrieben wurde
     */
    public static boolean feldSchreiben(Object zielObjekt, String feldName, Object wert)
    {
        if (zielObjekt == null)
        {
            return false;
        }

        for (Class<?> k = zielObjekt.getClass(); k != null && k != Object.class; k = k.getSuperclass())
        {
            try
            {
                java.lang.reflect.Field f = k.getDeclaredField(feldName);
                f.setAccessible(true);
                f.set(zielObjekt, wert);

                return true;
            }
            catch (NoSuchFieldException weiter)
            {
                /* Oberklasse. */
            }
            catch (Throwable t)
            {
                fehlschlaege++;
                letzterFehler = "feldSchreiben " + feldName + ": " + t.getClass().getSimpleName();

                BrueckenMeldung.einmal("feldSchreiben|" + feldName, "feld-zugriff warf",
                    "KernelBruecke", zielObjekt.getClass().getSimpleName(), feldName, t);

                return false;
            }
        }

        /* Siehe feldLesen — fast kein handler prüft den rückgabewert. */
        BrueckenMeldung.einmal("feldSchreiben|" + zielObjekt.getClass().getName() + "|" + feldName,
            "feld in keiner oberklasse gefunden", "KernelBruecke",
            zielObjekt.getClass().getSimpleName(), feldName);

        return false;
    }

    /** Diagnose-zeile für den mess-kanal. */
    public static String stand()
    {
        return "generation=" + generation()
            + " cache=" + CACHE.size()
            + " fehlschlaege=" + fehlschlaege
            + " letzter=" + letzterFehler
            + " " + BrueckenMeldung.zusammenfassung();
    }
}
