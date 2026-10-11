package com.afrobama.bbstools.client.blender;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.afrobama.bbstools.kernel.BrueckenMeldung;

/**
 * Macht MIXIN-HANDLER hotswapbar — die letzte grenze des kernel-swaps.
 *
 * <h2>Das problem, gemessen</h2>
 *
 * <p>SpongePowered Mixin webt handler-rümpfe beim klassenladen in die ZIELKLASSE. Live
 * nachgemessen an {@code UIPropTransform} (2026-07-26): die zielklasse trägt
 * {@code handler$zop000$bbstools$drawBlenderFeedback} als eigene methode. Die zielklasse
 * liegt im {@code KnotClassLoader} und wird vom kernel-swap NIE getauscht — der handler-code
 * ist damit strukturell unerreichbar. 118 von 720 dateien im mod sind mixins; ihr code
 * brauchte bisher für jede änderung einen boot.</p>
 *
 * <h2>Die lösung</h2>
 *
 * <p>Der gewobene handler wird zum EINZEILER, der hierher delegiert. Damit ist der gewobene
 * teil byte-stabil (er ändert sich nie mehr), und die eigentliche logik liegt in einer
 * normalen klasse im kernel-paket — voll swapbar.</p>
 *
 * <pre>
 *   // VORHER — logik im handler, braucht einen boot bei jeder änderung:
 *   &#64;Inject(method = "render", at = &#64;At("TAIL"))
 *   private void bbstools$drawFeedback(UIContext context, CallbackInfo ci)
 *   {
 *       if (this.blenderSession == null) return;
 *       ... 40 zeilen zeichen-logik ...
 *   }
 *
 *   // NACHHER — einzeiler, logik ist hotswapbar:
 *   &#64;Inject(method = "render", at = &#64;At("TAIL"))
 *   private void bbstools$drawFeedback(UIContext context, CallbackInfo ci)
 *   {
 *       MixinBruecke.rufe("BlenderFeedbackLogik", "zeichne", this, context);
 *   }
 * </pre>
 *
 * <h2>Warum nicht {@link KernelFassade}</h2>
 *
 * <p>Die fassade sucht ihre methode bei JEDEM aufruf per reflection (bewusst, damit sie den
 * tausch überlebt). Für einen aufruf pro G/R/S-start ist das richtig. Ein mixin-handler
 * feuert aber pro frame oder pro entity — dort kostet reflection zu viel.</p>
 *
 * <p>Diese brücke cached deshalb einen {@link MethodHandle}, ABER mit der generation im
 * schlüssel: nach einem swap steigt die generation, der cache wird als veraltet erkannt und
 * neu aufgebaut. Damit kostet ein aufruf im normalfall einen map-lookup plus einen
 * invoke — und überlebt den tausch trotzdem. Genau die bedingung, unter der der kommentar
 * in {@code KernelFassade} einen cache erlaubt: "muss der cache die generation im schlüssel
 * führen".</p>
 *
 * <h2>Robustheit</h2>
 *
 * <p>Ein mixin-handler, der eine exception nach oben durchreicht, reisst den render- oder
 * tick-durchlauf ab. Deshalb schluckt jeder aufruf hier {@link Throwable} und liefert
 * {@code null}. Ein ausgelassener frame ist besser als ein abgestürztes spiel —
 * dieselbe begründung wie in {@link CallbackStubs}.</p>
 *
 * <p><b>GESCHLUCKT HEISST NICHT MEHR STILL (2026-07-28).</b> Bis dahin war jeder
 * auflösungsfehler ein nacktes {@code return null} ohne eine zeile im log. Gefunden wurden so
 * zwei handler, die pro frame und pro replay ins leere liefen: die kernel-klassen
 * {@code WorldTransformBakeLogik} und {@code FilmEditorWorldTransformLogik} existierten gar
 * nicht — nicht im baum und nirgends in der historie. Beide mixins sind am 2026-07-29
 * entfernt worden (der entwurf steht in {@code docs/OFFEN-WORLD-TRANSFORM-OPTIMIERUNG.md},
 * damit die option nicht mit dem code verschwindet).
 * Jeder auflösungsfehler geht jetzt durch {@link BrueckenMeldung} — EINMAL laut ins
 * log, danach ein {@code containsKey} und zurück, damit der pro-frame-pfad nichts kostet.
 * Statisch prüfbar ohne laufendes spiel: {@code python tools/bruecken-ziele.py}.</p>
 */
public final class MixinBruecke
{
    private MixinBruecke() {}

    /** Kernel-pakete, in denen nach der ziel-klasse gesucht wird, in dieser reihenfolge. */
    private static final String[] PAKETE = {
        "com.afrobama.blendertransform",
        "com.afrobama.bbstools.kernel",
    };

    /**
     * Handle-cache. Schlüssel enthält die GENERATION — nach einem swap greift kein alter
     * eintrag mehr, weil sich der schlüssel ändert. Alte einträge bleiben liegen; das ist
     * bewusst, denn ein aufräumen bräuchte eine referenz auf die alte generation, und genau
     * solche referenzen sind die ursache des classloader-leaks.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, MethodHandle> CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Handles für {@link #rufe}, ohne je aufruf einen schlüssel zu verketten: klasse → methode →
     * je argumentzahl. Die namen kommen als literale aus den handlern, ihr hash ist also schon
     * gerechnet. Vorher entstand je aufruf ein verketteter string, dessen hash neu gerechnet
     * wurde: 2,2 % des renderthreads im film-editor, danach 0,6 % (gemessen 27.09.2026).
     */
    private static final java.util.concurrent.ConcurrentHashMap<String,
        java.util.concurrent.ConcurrentHashMap<String, Eintrag>> TABELLE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** Die handles einer methode je argumentzahl, gültig für genau eine generation. */
    private record Eintrag(int generation, MethodHandle[] jeZahl)
    {}

    /** Generation, für die der cache gilt. Steigt sie, sind alle einträge ungültig. */
    private static volatile int cacheGeneration = -1;

    /** Zählt aufrufe, die ins leere liefen — sichtbar über den mess-kanal. */
    public static volatile long fehlschlaege;

    /** Letzter fehler im klartext, für die diagnose. */
    public static volatile String letzterFehler = "(keiner)";

    /**
     * Ruft eine statische kernel-methode aus einem mixin-handler.
     *
     * @param klasse    einfacher klassenname, z.B. {@code "BlenderHud"}
     * @param methode   methodenname
     * @param argumente argumente; ihre laufzeit-typen bestimmen die signatur. KEINE
     *                  kernel-typen übergeben — nur primitive, JDK-, Minecraft- und
     *                  BBS-typen, sonst ist man beim identitäts-problem zurück.
     * @return rückgabewert, oder {@code null} bei void und bei jedem fehler
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

            /* Fixed-arity adapters are prepared once; varargs retain JDK collection rules. */
            return handle.isVarargsCollector()
                ? handle.invokeWithArguments(argumente)
                : (Object) handle.invokeExact(argumente);
        }
        catch (Throwable t)
        {
            /* Niemals nach oben durchreichen — siehe klassen-javadoc. Aber auch nicht still:
             * dieser pfad ist ohnehin teuer (gefüllter stacktrace), die verkettung für den
             * schlüssel fällt daneben nicht ins gewicht. */
            fehlschlaege++;
            letzterFehler = klasse + "#" + methode + ": "
                + t.getClass().getSimpleName() + " " + t.getMessage();

            BrueckenMeldung.einmal("rufe|" + klasse + "#" + methode, "aufruf warf",
                "MixinBruecke", klasse, methode, t);

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
            /* SWAP PASSIERT — alle handles zeigen auf tote klassen. Der cache wird geleert,
             * bevor ein einziger alter eintrag benutzt werden kann. */
            CACHE.clear();
            TABELLE.clear();
            cacheGeneration = jetzt;
        }

        java.util.concurrent.ConcurrentHashMap<String, Eintrag> jeMethode = TABELLE.get(klasse);
        Eintrag eintrag = jeMethode == null ? null : jeMethode.get(methode);
        int zahl = argumente.length;

        if (eintrag != null && eintrag.generation() == jetzt && zahl < eintrag.jeZahl().length
            && eintrag.jeZahl()[zahl] != null)
        {
            return eintrag.jeZahl()[zahl];
        }

        /* Keep array adapters separate from griff()'s typed handles and pre-swap entries. */
        String schluessel = "array|" + jetzt + "|" + klasse + "#" + methode + "|" + argumente.length;
        MethodHandle vorhanden = CACHE.get(schluessel);

        if (vorhanden != null)
        {
            merken(klasse, methode, zahl, jetzt, vorhanden);

            return vorhanden;
        }

        Class<?> ziel = zielKlasseFinden(klasse);

        if (ziel == null)
        {
            /* Hier stand bis 2026-07-28 ein nacktes `return null`. Der aufrufende handler tat
             * dann pro frame nichts, ohne eine zeile im log — so blieben zwei tote features
             * (WorldTransformBakeLogik, FilmEditorWorldTransformLogik) unbemerkt.
             * `schluessel` wird als melde-schlüssel WIEDERVERWENDET: er ist oben bereits
             * gebaut, der fehlerpfad kostet damit keine zusätzliche verkettung. */
            BrueckenMeldung.einmal(schluessel, "kernel-klasse in keinem paket gefunden",
                "MixinBruecke", klasse, methode);

            return null;
        }

        java.lang.reflect.Method m = passendeMethode(ziel, methode, argumente);

        if (m == null)
        {
            /* Klasse da, methode nicht — fast immer ein tippfehler oder eine umbenennung, die
             * den einzeiler im mixin nicht mitbekommen hat. Sie kompiliert ja weiter. */
            BrueckenMeldung.einmal(schluessel,
                "keine STATISCHE methode mit passender parameter-zahl", "MixinBruecke",
                klasse, methode);

            return null;
        }

        m.setAccessible(true);

        MethodHandle handle = MethodHandles.lookup().unreflect(m);

        if (!handle.isVarargsCollector())
        {
            handle = handle.asType(MethodType.genericMethodType(argumente.length))
                .asSpreader(Object[].class, argumente.length);
        }

        CACHE.put(schluessel, handle);
        merken(klasse, methode, zahl, jetzt, handle);

        return handle;
    }

    /** Legt einen fertigen handle in die {@link #TABELLE}; ein verlorener wettlauf baut ihn nur neu. */
    private static void merken(String klasse, String methode, int zahl, int generation, MethodHandle handle)
    {
        java.util.concurrent.ConcurrentHashMap<String, Eintrag> jeMethode =
            TABELLE.computeIfAbsent(klasse, (k) -> new java.util.concurrent.ConcurrentHashMap<>());
        Eintrag alt = jeMethode.get(methode);
        MethodHandle[] jeZahl = alt == null || alt.generation() != generation
            ? new MethodHandle[zahl + 1]
            : java.util.Arrays.copyOf(alt.jeZahl(), Math.max(alt.jeZahl().length, zahl + 1));

        jeZahl[zahl] = handle;
        jeMethode.put(methode, new Eintrag(generation, jeZahl));
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

        /* ⛔ OHNE KERNEL DEN NORMALEN CLASSLOADER NEHMEN — nicht null zurückgeben.
         *
         * Hier stand `if (loader == null) return null;`. Folge: JEDER mixin-handler, der
         * über diese brücke geht, tat bei einem normalen boot GAR NICHTS — die logik war
         * nur erreichbar, wenn vorher ein kernel-swap lief.
         *
         * Gemessen 2026-07-26: der keybind-editor (Shift+F9) kam nie, weil
         * UIBaseMenuKeybindMixin ausschliesslich `MixinBruecke.rufe(...)` ruft und die
         * brücke ohne kernel null lieferte. Dasselbe galt für alle anderen handler nach
         * dem brücken-muster — sie liefen nur nach einem swap. Ein feature, das einen
         * hotswap voraussetzt, um überhaupt zu existieren, ist kaputt.
         *
         * Der kernel bleibt der bevorzugte weg (dort ist die klasse hotswapbar); ohne
         * ihn nehmen wir die klasse, die ohnehin geladen ist. */
        if (loader == null)
        {
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

        /* ⛔ AUCH HIER DER FALLBACK, NICHT NUR WENN DER LOADER FEHLT.
         *
         * Der `loader == null`-zweig oben greift nur, wenn die KernelLoader-KLASSE fehlt.
         * Sie ist aber fast immer da (der agent lädt sie beim start) — nur ein aktiver
         * KERNEL fehlt beim normalen boot. Dann wirft `laden(...)` für jedes paket, die
         * schleife läuft durch, und hier stand `return null`.
         *
         * Gemessen 2026-07-26: brücken-aufruf bewirkte NICHTS (root-kinder 7 -> 7),
         * derselbe aufruf direkt funktionierte (7 -> 8). Damit tat JEDER handler nach
         * dem brücken-muster bei einem normalen boot gar nichts — sichtbar wurde es am
         * keybind-editor, der nie aufging. */
        return ohneKernelLaden(einfacherName);
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

    /** Die agent-klasse, über die der kernel erreichbar ist. */
    /**
     * Lädt die logik-klasse über den NORMALEN classloader — der fallback ohne kernel.
     *
     * <p>Damit läuft jeder brücken-handler auch bei einem frischen boot. Der einzige
     * unterschied zum kernel-weg: die klasse ist dann nicht hotswapbar, bis ein swap
     * lief. Das ist allemal besser als ein handler, der stillschweigend nichts tut.</p>
     */
    private static Class<?> ohneKernelLaden(String einfacherName)
    {
        for (String paket : PAKETE)
        {
            try
            {
                return Class.forName(paket + "." + einfacherName, false,
                    MixinBruecke.class.getClassLoader());
            }
            catch (Throwable naechstesPaket)
            {
                /* in einem der anderen pakete weitersuchen */
            }
        }

        return null;
    }

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
     * <p>WOFÜR: 18 mixin-dateien nutzen {@code CallbackInfoReturnable} oder
     * {@code cancellable = true}. Deren handler müssen entscheiden, ob BBS' eigener rumpf
     * noch laufen darf — diese entscheidung ist LOGIK und gehört damit in den kernel, nicht
     * in den gewobenen teil.</p>
     *
     * <p>Der handler bleibt trotzdem ein einzeiler:</p>
     * <pre>
     *   &#64;Inject(method = "processDragFrame", at = &#64;At("HEAD"), cancellable = true)
     *   private void bbstools$drag(UIContext context, CallbackInfo ci)
     *   {
     *       if (MixinBruecke.rufeUndAbbrechen("BlenderDrag", "frame", this, context)) ci.cancel();
     *   }
     * </pre>
     *
     * <p>Bei einem fehler wird {@code false} geliefert — also NICHT abgebrochen. Das ist die
     * sichere richtung: der ursprüngliche BBS-rumpf läuft weiter, statt dass ein feature
     * still ausfällt, weil die brücke nicht durchkam.</p>
     */
    public static boolean rufeUndAbbrechen(String klasse, String methode, Object... argumente)
    {
        Object ergebnis = rufe(klasse, methode, argumente);

        return ergebnis instanceof Boolean && (Boolean) ergebnis;
    }

    /**
     * Liest ein {@code @Shadow}-feld der ZIELKLASSE für den kernel.
     *
     * <p>WOFÜR: 16 mixin-dateien greifen per {@code @Shadow} auf felder der zielklasse zu
     * (etwa {@code this.blenderSession} in {@code UIPropTransformBlenderMouseMixin}). Die
     * kernel-logik kann das nicht — sie kennt die zielklasse nicht und darf ihre typen auch
     * nicht in der signatur führen.</p>
     *
     * <p>Der ausweg: der handler reicht {@code this} als {@code Object} über die brücke, und
     * die kernel-logik liest die felder darüber. Der zugriff läuft über
     * {@code objekt.getClass()}, also loader-unabhängig — die falle, die in SYMPTOME.md
     * dokumentiert ist.</p>
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
                    "MixinBruecke", zielObjekt.getClass().getSimpleName(), feldName, t);

                return null;
            }
        }

        /* Kein treffer in der ganzen oberklassen-kette. Ein @Shadow-feld, das umbenannt wurde,
         * sieht für den handler exakt aus wie "wert ist null" — deshalb melden. Der schlüssel
         * enthält die zielklasse, weil dasselbe feld an mehreren typen gelesen wird. */
        BrueckenMeldung.einmal("feldLesen|" + zielObjekt.getClass().getName() + "|" + feldName,
            "feld in keiner oberklasse gefunden", "MixinBruecke",
            zielObjekt.getClass().getSimpleName(), feldName);

        return null;
    }

    /**
     * Schreibt ein {@code @Shadow}-feld der zielklasse.
     *
     * <p>Gegenstück zu {@link #feldLesen} — manche handler setzen zustand an der zielklasse
     * (z.B. {@code this.blenderHudText = ...}). Auch das ist logik und gehört in den kernel.</p>
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
                    "MixinBruecke", zielObjekt.getClass().getSimpleName(), feldName, t);

                return false;
            }
        }

        /* Siehe feldLesen: ein nicht gefundenes feld ist von "nichts zu tun" nicht zu
         * unterscheiden, wenn niemand den rückgabewert prüft — und das tut fast kein handler. */
        BrueckenMeldung.einmal("feldSchreiben|" + zielObjekt.getClass().getName() + "|" + feldName,
            "feld in keiner oberklasse gefunden", "MixinBruecke",
            zielObjekt.getClass().getSimpleName(), feldName);

        return false;
    }

    /**
     * Holt einen wiederverwendbaren griff auf eine kernel-methode — für PRO-ENTITY-pfade.
     *
     * <h2>Warum es das braucht</h2>
     *
     * <p>{@link #rufe} kostet gemessen <b>1307 ns</b> mehr als ein direkter aufruf (median über
     * 9 läufe, 200k runden mit warmlauf; map-lookup + MethodHandle-invoke + boxing). Für einen
     * aufruf pro frame ist das nichts. Sechs mixins feuern aber PRO ENTITY — bei army-scale
     * 3000× pro frame. Das wären <b>23,5% eines 16,7ms-frames</b>, nur für den aufrufweg.
     * Genau diese mixins existieren, um zeit zu sparen; sie über {@code rufe} zu leiten wäre
     * absurd.</p>
     *
     * <h2>Die lösung</h2>
     *
     * <p>Den griff EINMAL pro frame holen, dann die schleife direkt dagegen laufen lassen:</p>
     * <pre>
     *   // einmal am frame-anfang (oder batch-start):
     *   MethodHandle h = MixinBruecke.griff("BeispielLogik", "pruefe", 2);
     *
     *   // dann pro entity, ohne lookup:
     *   for (Entity e : entities) { if (h != null) h.invoke(e, dist); }
     * </pre>
     *
     * <p>Aus 3000 brücken-lookups pro frame wird EINER. Die hotswapbarkeit bleibt: der griff
     * wird pro frame neu geholt, ein swap zwischen zwei frames wirkt also spätestens im
     * nächsten frame. Ein griff DARF NICHT über frame-grenzen hinweg gecacht werden — dann
     * zeigte er nach einem swap auf eine tote generation.</p>
     *
     * @param klasse      einfacher klassenname im kernel-paket
     * @param methode     methodenname
     * @param parameterZahl anzahl der parameter (zur unterscheidung überladener methoden)
     * @return der griff, oder {@code null} wenn kein agent/kernel läuft oder die methode fehlt
     */
    public static MethodHandle griff(String klasse, String methode, int parameterZahl)
    {
        try
        {
            int jetzt = generation();

            if (jetzt != cacheGeneration)
            {
                CACHE.clear();
                cacheGeneration = jetzt;
            }

            String schluessel = jetzt + "|" + klasse + "#" + methode + "|" + parameterZahl;
            MethodHandle vorhanden = CACHE.get(schluessel);

            if (vorhanden != null)
            {
                return vorhanden;
            }

            Class<?> ziel = zielKlasseFinden(klasse);

            if (ziel == null)
            {
                BrueckenMeldung.einmal(schluessel, "kernel-klasse in keinem paket gefunden",
                    "MixinBruecke", klasse, methode);

                return null;
            }

            for (java.lang.reflect.Method m : ziel.getDeclaredMethods())
            {
                if (!m.getName().equals(methode) || m.getParameterCount() != parameterZahl
                    || !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                {
                    continue;
                }

                m.setAccessible(true);

                MethodHandle handle = MethodHandles.lookup().unreflect(m);
                CACHE.put(schluessel, handle);

                return handle;
            }

            /* Die schleife lief durch: die klasse ist da, aber keine statische methode dieses
             * namens mit dieser parameter-zahl. Ein griff == null bedeutet für den aufrufer
             * "pfad überspringen" — ohne meldung wäre die optimierung lautlos aus. */
            BrueckenMeldung.einmal(schluessel,
                "keine STATISCHE methode mit passender parameter-zahl", "MixinBruecke",
                klasse, methode);

            return null;
        }
        catch (Throwable t)
        {
            fehlschlaege++;
            letzterFehler = "griff " + klasse + "#" + methode + ": "
                + t.getClass().getSimpleName();

            BrueckenMeldung.einmal("griff|" + klasse + "#" + methode, "griff warf",
                "MixinBruecke", klasse, methode, t);

            return null;
        }
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
