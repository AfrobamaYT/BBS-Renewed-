// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (EXEC channel: in-JVM JShell).
package com.blenderbridge;

import com.google.gson.JsonObject;

import com.mojang.logging.LogUtils;

import net.fabricmc.loader.api.FabricLoader;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;
import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.execution.LocalExecutionControl;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControl.ClassBytecodes;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BridgeExec — the EXEC channel: run a Java-code string INSIDE the live BBS JVM and hand the
 * result back to the caller (a dev tool, e.g. the Blender add-on). This is the reliable-control
 * seam that mouse-clicking never gave us: load a world, open the film editor, add a camera clip —
 * directly, by calling the real {@code net.minecraft.*} / {@code mchorse.*} APIs.
 *
 * <p>WHY JShell (and the KnotClassLoader trick — the ONE critical detail): JShell compiles and
 * runs each snippet in-process. Its DEFAULT {@link LocalExecutionControl} loads the compiled
 * snippet classes in a {@link URLClassLoader} whose PARENT is the SYSTEM class loader (the JDK's
 * package-private {@code DefaultLoaderDelegate} does {@code new URLClassLoader(new URL[0])} with no
 * explicit parent → system loader). Under Fabric, {@code net.minecraft.*}, {@code mchorse.*} and
 * {@code com.blenderbridge.*} are all loaded by the <b>KnotClassLoader</b>, which is NOT reachable
 * from the system loader. So a pure-Java snippet like {@code 1+1} works, but
 * {@code MinecraftClient.getInstance()} throws {@code NoClassDefFoundError:
 * net/minecraft/client/MinecraftClient}. The fix (ported verbatim from mcstudio's proven
 * StudioServer): give JShell a {@link LocalExecutionControl} whose loader's parent is the
 * KnotClassLoader — i.e. {@code BridgeExec.class.getClassLoader()}, which IS the KnotClassLoader
 * that loaded this class at runtime. See {@link KnotLoaderDelegate} / {@link SnippetClassLoader}.</p>
 *
 * <p>THREADING: JShell is NOT thread-safe, and every snippet here touches BBS/MC state, which is
 * only legal on the client MAIN thread. Both constraints are satisfied by the SAME rule: only ever
 * call {@link #eval(String)} from the main thread. The bridge's {@code dispatch()} runs in the
 * client tick (main thread), so the EXEC dispatch case calls us on the right thread already. This
 * class therefore takes NO locks and needs NO executor — single-threaded by construction. The only
 * synchronisation is {@code synchronized} lazy-init, in case a future caller ever races the first
 * eval from another thread (it degrades safely, it does not run the snippet off-thread).</p>
 *
 * <p>LIFECYCLE: the JShell instance is expensive to build (fresh compiler + execution engine,
 * ~seconds cold). It is created lazily on the FIRST {@link #eval(String)} and REUSED forever. Never
 * rebuilt per call.</p>
 *
 * <p>GATING: the EXEC channel executes arbitrary code, so it is OFF unless this is clearly a dev
 * session — {@link FabricLoader#isDevelopmentEnvironment()} is true, OR the system property
 * {@code -Dblenderbridge.exec=true} is set. In a shipped mod both are false and {@link #eval} short-
 * circuits to {@code {"ok":false,"error":"exec disabled"}} without ever touching JShell. The
 * transport itself is loopback-only ({@link BridgeServer} binds {@code 127.0.0.1}), so even when
 * enabled it is not remotely reachable.</p>
 */
public final class BridgeExec
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Opt-in dev flag: {@code -Dblenderbridge.exec=true} force-enables EXEC even outside a dev run. */
    public static final String EXEC_PROPERTY = "blenderbridge.exec";

    /** Result keys (the JSON contract handed back to the caller). */
    public static final String KEY_OK = "ok";
    public static final String KEY_STDOUT = "stdout";
    public static final String KEY_RESULT = "result";
    public static final String KEY_ERROR = "error";

    /**
     * The one warm JShell instance. Created lazily on first {@link #eval} and reused forever —
     * rebuilding it per call is the whole latency. {@code volatile} + synchronised init so a stray
     * concurrent first-call cannot build two engines (see class-doc: normal use is single-threaded).
     */
    private static volatile JShell jshell;

    /** Latch so a runtime without jdk.jshell degrades to ONE clear error, not repeated init attempts. */
    private static volatile boolean triedInit;

    private BridgeExec()
    {
        /* static-only helper */
    }

    /**
     * True if the EXEC channel is permitted in this process. Dev run OR explicit opt-in property.
     * Read fresh each call (cheap) so a test can flip the property without a restart.
     */
    public static boolean isEnabled()
    {
        if (Boolean.getBoolean(EXEC_PROPERTY))
        {
            return true;
        }

        try
        {
            return FabricLoader.getInstance().isDevelopmentEnvironment();
        }
        catch (Throwable t)
        {
            /* FabricLoader unavailable for some reason — fail closed. */
            return false;
        }
    }

    /**
     * Run a Java-code string on the warm in-JVM JShell and return a structured result.
     *
     * <p>MUST be called on the client MAIN thread (see class-doc): JShell is single-threaded and the
     * snippet may touch BBS/MC state. The bridge's {@code dispatch()} already runs in the tick.</p>
     *
     * @param code one or more Java statements/expressions (JShell snippet syntax)
     * @return {@code {"ok":bool, "stdout":string, "result":string, "error":string|null}} —
     *         {@code ok} is false when EXEC is disabled, JShell is unavailable, the snippet is
     *         REJECTED (compile error), or it threw. {@code stdout} is whatever the snippet printed
     *         to {@code System.out}/{@code System.err}. {@code result} is the value of the last
     *         expression snippet (or empty for pure statements). {@code error} carries the failure
     *         message (or is absent when {@code ok} is true).
     */
    public static JsonObject eval(String code)
    {
        JsonObject out = new JsonObject();

        if (!isEnabled())
        {
            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_STDOUT, "");
            out.addProperty(KEY_RESULT, "");
            out.addProperty(KEY_ERROR, "exec disabled (not a dev environment; set -D" + EXEC_PROPERTY + "=true to enable)");
            return out;
        }

        if (code == null || code.trim().isEmpty())
        {
            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_STDOUT, "");
            out.addProperty(KEY_RESULT, "");
            out.addProperty(KEY_ERROR, "empty code");
            return out;
        }

        /* DEADLOCK-GUARD (2026-07-18, nach 2 game-freezes in einer session): ein snippet, das den
         * MC-executor per submit(...).get()/join() auf dem MAIN-thread AUF sich selbst warten lässt,
         * friert den render-thread hart ein (EXEC läuft schon auf dem main-thread; JShell join()t den
         * snippet-thread, der auf die main-queue wartet → zirkulär). Das dossier warnt davor, aber ein
         * text-hinweis hat es nicht verhindert — dieser gate lehnt den mustergültigen fall AB, bevor er
         * die JVM einfriert. Erkennt `.submit(` ... `.get(` / `.join(` im selben snippet. Der korrekte
         * weg (EXEC IST schon main-thread → direkt aufrufen; oder submit-then-poll über ZWEI calls)
         * steht in der fehlermeldung. Bewusst simpel/textuell (kein AST): false-positives sind billig
         * (der aufrufer formuliert um), ein false-negative ist ein game-freeze. */
        String flat = code.replaceAll("\\s+", "");
        if (flat.contains(".submit(") && (flat.contains(").get(") || flat.contains(").join(")))
        {
            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_STDOUT, "");
            out.addProperty(KEY_RESULT, "");
            out.addProperty(KEY_ERROR, "DEADLOCK-GUARD: submit(...).get()/join() im EXEC friert den "
                + "main-thread ein (EXEC laeuft schon AUF dem main-thread). Ruf die main-thread-arbeit "
                + "DIREKT auf (kein submit noetig), oder nutze submit-then-poll ueber ZWEI getrennte "
                + "EXEC-calls (call 1: submit + handle merken; call 2: future.isDone()/get pollen).");
            return out;
        }

        /* RENDER-THREAD-GUARD (2026-07-18, nach einem game-CRASH): EXEC laeuft auf dem MAIN-thread,
         * NICHT dem RENDER-thread. GL-/screen-operationen (setScreen, captureMainFramebuffer,
         * RenderSystem.*) direkt via EXEC → "Rendersystem called from wrong thread" → crash. Der
         * korrekte weg sind die FERTIGEN routes die RenderThread.submit() schon machen (z.b. /shot
         * fuer framebuffer-capture). Textueller check (kein AST): false-positive = umformulieren,
         * false-negative = game-crash. */
        /* ⛔ ROHE GL-AUFRUFE GEHOEREN DAZU (2026-07-29, nach dem naechsten crash).
         *
         * Der guard oben fing nur RenderSystem ab. Ein `org.lwjgl.opengl.GL11.glPolygonMode(...)`
         * ging glatt durch — und riss die instanz sofort, ohne eine einzige verwertbare zeile im
         * log (die JVM stirbt nativ, nicht mit einer java-exception). Genau dieselbe ursache:
         * GL-zustand vom falschen thread und ausserhalb eines zeichen-durchlaufs.
         *
         * Der fehler ist besonders teuer, weil er wie ein billiger test aussieht: "ich setze den
         * zustand kurz und schaue nach". Es gibt kein "kurz" — der aufruf ist sofort toedlich. */
        boolean rohesGl = flat.contains("org.lwjgl.opengl.GL")
            || flat.matches("(?s).*\\bGL(11|12|13|14|15|20|21|30|31|32|33|40|41|42|43|44|45|46)\\s*\\..*");

        /* ⛔ GUI-SKALIERUNG SCHREIBEN GEHOERT DAZU (2026-08-04, nach dem naechsten stillen Tod).
         *
         * `options.guiScale().set(2)` ueber EXEC hat die Testinstanz 02:11:22 nativ getoetet:
         * kein hs_err, kein crash-report, kein WER-dump, das Log endet mitten im Bild. Letzte
         * Zeile davor: `[Thread-193] applyPreviewSizeToBBS` — der Setter loest die
         * Groessenaenderung samt Framebuffer-Neuanlage aus, hier also auf dem EXEC-thread.
         * Dieselbe Ursache wie setScreen, nur ohne jede verwertbare Spur.
         *
         * NUR der SCHREIBZUGRIFF wird geblockt: `guiScale().get()` ist ein harmloser Lesewert
         * und wird bei jeder Koordinaten-Umrechnung gebraucht. Ein Wächter, der das Lesen
         * mitnimmt, wird umgangen statt befolgt. */
        /* ⛔ PUFFER-GROESSEN AENDERN GEHOERT AUCH DAZU (2026-08-04, dritter stiller tod
         * desselben abends).
         *
         * `stencil.resize(1280, 720)` ueber EXEC hat die instanz 03:14:05 nativ getoetet --
         * wieder ohne hs_err, ohne absturzbericht, ohne dump; der herzschlag stand bis zur
         * letzten sekunde auf frameAgeMs=44. Ein resize legt texturen neu an, das ist reine
         * GL-arbeit und gehoert auf den render-thread.
         *
         * Der guiScale-fall zwei stunden vorher war dieselbe ursache ueber einen anderen
         * namen. Deshalb hier die operation selbst, nicht nur ihr ausloeser. */
        boolean pufferGroesse = flat.contains("resizeGUI(")
            || flat.contains(".resize(")
            || flat.contains(".setSize(");

        if (flat.contains("setScreen(") || flat.contains("captureMainFramebuffer(")
            || flat.contains("RenderSystem.") || istGuiSkalenSetzer(flat)
            || pufferGroesse || rohesGl)
        {
            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_STDOUT, "");
            out.addProperty(KEY_RESULT, "");
            out.addProperty(KEY_ERROR, "RENDER-THREAD-GUARD: setScreen/captureMainFramebuffer/"
                + "RenderSystem, guiScale().set(...) (fenstergroesse: MC_GROESSE beim start), "
                + "resize/resizeGUI/setSize an puffern und texturen "
                + "und rohe GL-aufrufe (org.lwjgl.opengl.GL*) sind "
                + "RENDER-thread-operationen; EXEC laeuft auf dem MAIN-thread → "
                + "direkter aufruf crasht das game (wrong thread; bei rohem GL ohne log-zeile). "
                + "Nutze die fertigen routes: /shot?name=X fuer einen framebuffer-frame "
                + "(RenderThread-korrekt). Fuer andere render-thread-arbeit eine route bauen, die "
                + "RenderThread.submit(...) macht — oder den zustand aus einem echten "
                + "render-hook setzen (WorldRenderEvents), nicht aus EXEC.");
            return out;
        }

        if (!ensureJshell())
        {
            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_STDOUT, "");
            out.addProperty(KEY_RESULT, "");
            out.addProperty(KEY_ERROR, "jdk.jshell unavailable (add --add-modules=jdk.jshell to run vmArgs)");
            return out;
        }

        /* Capture System.out + System.err for the duration of THIS eval. The snippet runs in-process
         * (LocalExecutionControl), so its prints go to the game's real streams unless we redirect. We
         * tee nothing — we take them over fully and restore in the finally. Because eval is main-thread
         * and single-threaded, no other game code prints concurrently during the swap. */
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(captured, true, StandardCharsets.UTF_8);

        System.setOut(capture);
        System.setErr(capture);

        List<SnippetEvent> events;

        try
        {
            events = jshell.eval(code);
        }
        catch (Throwable t)
        {
            /* JShell.eval itself blew up (not a snippet exception — those come back as events). */
            System.setOut(originalOut);
            System.setErr(originalErr);
            capture.flush();

            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_STDOUT, captured.toString(StandardCharsets.UTF_8));
            out.addProperty(KEY_RESULT, "");
            out.addProperty(KEY_ERROR, "jshell.eval threw: " + t);
            return out;
        }
        finally
        {
            System.setOut(originalOut);
            System.setErr(originalErr);
            capture.flush();
        }

        return reduce(events, captured.toString(StandardCharsets.UTF_8));
    }

    /**
     * Reduce JShell's {@code List<SnippetEvent>} to the result contract. A single {@code eval} call
     * may hold several snippets (JShell splits multi-statement input); we surface the last value, and
     * fail as soon as any snippet is rejected or throws.
     */
    private static JsonObject reduce(List<SnippetEvent> events, String stdout)
    {
        JsonObject out = new JsonObject();
        out.addProperty(KEY_STDOUT, stdout == null ? "" : stdout);

        String result = "";
        String error = null;

        for (SnippetEvent event : events)
        {
            if (event.exception() != null)
            {
                /* The snippet ran but threw — surface the runtime exception. */
                error = "exception: " + event.exception();
                break;
            }

            if (event.status() == Snippet.Status.REJECTED)
            {
                /* Compile error — pull JShell's diagnostics. */
                error = "rejected: " + diagnostics(event.snippet());
                break;
            }

            if (event.value() != null)
            {
                /* Value of an expression snippet (statements/imports carry a null value). */
                result = event.value();
            }
        }

        if (error != null)
        {
            out.addProperty(KEY_OK, false);
            out.addProperty(KEY_RESULT, result);
            out.addProperty(KEY_ERROR, error);
            return out;
        }

        out.addProperty(KEY_OK, true);
        out.addProperty(KEY_RESULT, result);
        return out;
    }

    /**
     * Wirft die warme JShell weg, damit die NAECHSTE {@link #eval} eine frische baut.
     *
     * <h2>⛔ WOFUER — EV SAH NACH EINEM HOTSWAP DIE ALTE KLASSE (2026-08-02 gemessen)</h2>
     *
     * <p>Ein Ausdruck, der eine frisch getauschte Methode aufruft, wurde abgelehnt, obwohl die
     * JVM die neue Signatur laengst trug. Gemessen an einer eigenen Sondenklasse:</p>
     * <pre>
     *   getDeclaredMethods()        -> [f(int,boolean)]     die JVM ist richtig
     *   eval("Sonde.f(1,true)")     -> abgelehnt: required int, found int,boolean
     *   Reflexion .invoke(1,true)   -> "V2-1-true"          laeuft
     * </pre>
     *
     * <p><b>Der Hotswap ist unschuldig.</b> Dasselbe passiert ohne jeden Hotswap nach einem
     * reinen Compile. Die Ursache steht im Klassenkopf unter LIFECYCLE: die JShell wird einmal
     * gebaut und ewig weiterbenutzt — und javacs Symboltabelle liest eine Klasse, die sie
     * einmal gelesen hat, nie wieder von der Platte. Der Ausweg war bisher ein
     * Reflexions-Umweg pro Aufruf; das hier behebt die Ursache.</p>
     *
     * <h2>Warum nicht einfach immer neu bauen</h2>
     *
     * <p>Die Instanz kostet Sekunden (frischer Compiler plus die KnotClassLoader-Verdrahtung,
     * siehe Klassenkopf). Genau darum wird sie wiederverwendet. Dieser Aufruf ist deshalb eine
     * ANSAGE, kein Automatismus — er gehoert hinter einen Klassentausch oder einen Compile,
     * nicht in jede Messung.</p>
     *
     * <h2>⚠️ Die alte Instanz wird bewusst NICHT geschlossen</h2>
     *
     * <p>Der uebliche Weg, das hier auszuloesen, ist ein {@code eval}-Aufruf — also aus einer
     * LAUFENDEN Auswertung heraus. Ein {@code close()} wuerde dann die JShell schliessen, die
     * gerade den aufrufenden Schnipsel ausfuehrt; das Verhalten dabei ist nicht zugesichert.
     * Die Referenz fallen zu lassen ist sicher: die laufende Auswertung haelt ihre eigene und
     * laeuft zu Ende, danach raeumt der GC auf. Preis ist ein kurzlebiger Mehrverbrauch je
     * Zuruecksetzen — vertretbar, weil das hier selten und auf Ansage passiert.</p>
     */
    public static String neuAufbauen()
    {
        synchronized (BridgeExec.class)
        {
            jshell = null;

            /* Muss mit zurueck: ensureJshell() kehrt bei gesetztem Riegel sofort um und wuerde
             * sonst fuer immer "jshell == null" melden, statt neu zu bauen. */
            triedInit = false;
        }

        LOGGER.info("[bridge-exec] JShell zurueckgesetzt — die naechste Auswertung baut neu auf (Sekunden).");

        /* ⚠️ RUECKGABE STATT void, UND ZWAR ABSICHTLICH: der Aufrufer (tools/hotswap.ps1) muss
         * belegen koennen, dass das hier WIRKLICH gelaufen ist. Ein void-Aufruf liefert eine
         * leere Antwort — und eine leere Antwort ist von einem stillen Fehlschlag nicht zu
         * unterscheiden. Der Exitcode taugt dafuer ebenfalls nicht: `dev.py ev` endet auch
         * bei einem ABGELEHNTEN Ausdruck mit 0 (am 2026-08-02 gemessen, als dieser Weg
         * gebaut wurde). Also eine POSITIVE Marke, auf die der Aufrufer prueft — nicht die
         * Abwesenheit eines Fehlertextes. */
        return "JSHELL-ZURUECKGESETZT";
    }

    /**
     * Lazily build + warm the single JShell instance. Returns true if JShell is usable. Synchronised
     * so a stray concurrent first-call can't build two engines. Latches on failure ({@link #triedInit})
     * so a runtime missing the jdk.jshell module degrades to one clear error, not repeated attempts.
     *
     * <p>Nach {@link #neuAufbauen()} laeuft der Aufbau erneut durch — das ist der einzige Weg,
     * eine frisch getauschte Klasse fuer {@code eval} sichtbar zu machen.</p>
     */
    private static boolean ensureJshell()
    {
        if (triedInit)
        {
            return jshell != null;
        }

        synchronized (BridgeExec.class)
        {
            if (triedInit)
            {
                return jshell != null;
            }

            triedInit = true;

            try
            {
                /* THE CRITICAL LINE: build JShell on a LocalExecutionControl whose loader's parent is
                 * the KnotClassLoader (via KnotLoaderDelegate below). That is what lets snippets resolve
                 * net.minecraft.* / mchorse.* / com.blenderbridge.*. Default JShell would parent to the
                 * system loader and fail to find any game class. */
                JShell instance = JShell.builder()
                    .executionEngine(new KnotLocalProvider(), Map.of())
                    .build();

                /* Pre-import + bind the handles an EXEC caller reaches for most. Import/var snippets
                 * return [] so their events carry no value; a failure here is non-fatal (logged). */
                String[] inits =
                {
                    "import net.minecraft.client.MinecraftClient;",
                    "import net.minecraft.client.world.ClientWorld;",
                    "var mc = MinecraftClient.getInstance();"
                };

                for (String init : inits)
                {
                    try
                    {
                        instance.eval(init);
                    }
                    catch (Throwable t)
                    {
                        LOGGER.warn("[blenderbridge] EXEC context-init failed for \"{}\": {}", init, t.toString());
                    }
                }

                jshell = instance;
                LOGGER.info("[blenderbridge] EXEC JShell warmed with Knot-parented loader ({} context imports)", inits.length);
                return true;
            }
            catch (NoClassDefFoundError | Exception t)
            {
                /* NoClassDefFoundError = jdk.jshell module absent at runtime (should not happen: the
                 * runClient vmArgs carry --add-modules=jdk.jshell). Degrade cleanly. */
                LOGGER.error("[blenderbridge] EXEC JShell init failed ({}) — ensure --add-modules=jdk.jshell is on the run vmArgs.", t.toString());
                return false;
            }
        }
    }

    /** Pull JShell diagnostics for a REJECTED snippet (compile errors) into one line. */
    private static String diagnostics(Snippet snippet)
    {
        try
        {
            return jshell.diagnostics(snippet)
                .map(d -> d.getMessage(java.util.Locale.ROOT))
                .reduce((a, b) -> a + "; " + b)
                .orElse("(no diagnostics)");
        }
        catch (Throwable t)
        {
            return "(diagnostics unavailable: " + t + ")";
        }
    }

    /* ==================== custom JShell execution engine (Knot-parented class loader) ==================== */

    /**
     * {@link ExecutionControlProvider} that hands JShell a {@link LocalExecutionControl} backed by
     * {@link KnotLoaderDelegate}. "local" = runs snippets in THIS JVM (so they can touch the live
     * game), not a forked process. The name is namespaced so it never collides with the built-in
     * "local"/"jdi" providers.
     */
    private static final class KnotLocalProvider implements ExecutionControlProvider
    {
        @Override
        public String name()
        {
            return "blenderbridge-knot-local";
        }

        @Override
        public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters)
        {
            return new LocalExecutionControl(new KnotLoaderDelegate());
        }
    }

    /**
     * A {@link LoaderDelegate} whose backing class loader's PARENT is the KnotClassLoader that loaded
     * {@link BridgeExec} — a near-copy of the JDK's package-private {@code DefaultLoaderDelegate},
     * changed only so snippet classes can resolve Fabric / Minecraft / BBS types. Snippet bytecode is
     * defined here directly; anything else (net.minecraft.*, mchorse.*, java.*) delegates up to the
     * Knot parent.
     */
    private static final class KnotLoaderDelegate implements LoaderDelegate
    {
        /* PARENT = BridgeExec.class.getClassLoader() = the KnotClassLoader at runtime. THE fix. */
        private final SnippetClassLoader loader = new SnippetClassLoader(BridgeExec.class.getClassLoader());

        /* Names JShell has installed, so findClass() can hand the live Class back to the engine. */
        private final Map<String, Class<?>> klasses = new HashMap<>();

        @Override
        public void load(ClassBytecodes[] cbcs) throws ExecutionControl.ClassInstallException
        {
            boolean[] loaded = new boolean[cbcs.length];

            try
            {
                for (ClassBytecodes cbc : cbcs)
                {
                    this.loader.declare(cbc.name(), cbc.bytecodes());
                }

                for (int i = 0; i < cbcs.length; i++)
                {
                    Class<?> klass = this.loader.loadClass(cbcs[i].name());
                    this.klasses.put(cbcs[i].name(), klass);
                    loaded[i] = true;
                    /* Force preparation so a later invoke doesn't lazily fail. */
                    klass.getDeclaredMethods();
                }
            }
            catch (Throwable ex)
            {
                throw new ExecutionControl.ClassInstallException("load: " + ex.getMessage(), loaded);
            }
        }

        @Override
        public void classesRedefined(ClassBytecodes[] cbcs)
        {
            for (ClassBytecodes cbc : cbcs)
            {
                this.loader.declare(cbc.name(), cbc.bytecodes());
            }
        }

        @Override
        public void addToClasspath(String cp) throws ExecutionControl.InternalException
        {
            try
            {
                for (String path : cp.split(File.pathSeparator))
                {
                    this.loader.addURL(new File(path).toURI().toURL());
                }
            }
            catch (Exception ex)
            {
                throw new ExecutionControl.InternalException(ex.toString());
            }
        }

        @Override
        public Class<?> findClass(String name) throws ClassNotFoundException
        {
            Class<?> klass = this.klasses.get(name);

            if (klass == null)
            {
                throw new ClassNotFoundException(name + " not found");
            }

            return klass;
        }
    }

    /**
     * {@link URLClassLoader} that defines JShell's generated snippet classes from raw bytes, and
     * delegates every OTHER class to its parent — which we set to the KnotClassLoader so
     * {@code net.minecraft.*} / {@code mchorse.*} / {@code com.blenderbridge.*} resolve. The one
     * behavioural difference from the JDK default: the parent is Knot, not the system loader.
     */
    private static final class SnippetClassLoader extends URLClassLoader
    {
        private final Map<String, byte[]> snippetBytes = new ConcurrentHashMap<>();

        SnippetClassLoader(ClassLoader parent)
        {
            super(new URL[0], parent);
        }

        /** Register (or overwrite, for redefinition) the bytecode for a snippet class name. */
        void declare(String name, byte[] bytes)
        {
            this.snippetBytes.put(name, bytes);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException
        {
            byte[] bytes = this.snippetBytes.get(name);

            if (bytes == null)
            {
                return super.findClass(name);
            }

            return defineClass(name, bytes, 0, bytes.length);
        }

        /* Widen addURL so KnotLoaderDelegate.addToClasspath can reach it. */
        @Override
        public void addURL(URL url)
        {
            super.addURL(url);
        }
    }

	/**
	 * Faengt JEDE Form von {@code guiScale().set(...)} — auch ueber eine Zwischenvariable.
	 *
	 * <p>⛔ WARUM DER TEXTVERGLEICH NICHT REICHTE. Der alte Test war
	 * {@code flat.contains("guiScale().set(")}. Am 2026-08-21 starb die Testinstanz erneut
	 * still (kein hs_err, Log endet nach {@code applyPreviewSizeToBBS} auf dem EXEC-thread) —
	 * weil der Ausdruck die Option zwischenspeicherte:</p>
	 *
	 * <pre>var o = mc.options.guiScale(); o.set(3);</pre>
	 *
	 * <p>Derselbe Tod wie 2026-08-04, derselbe Wächter davor, nur eine Schreibweise weiter.
	 * Ein Wächter, der auf eine EINZELNE Schreibweise prüft, ist kein Wächter. Jetzt reicht
	 * es, dass {@code guiScale} und ein {@code .set(} im selben Ausdruck vorkommen.</p>
	 */
	private static boolean istGuiSkalenSetzer(String flat)
	{
		return flat.contains("guiScale") && flat.contains(".set(");
	}
}
