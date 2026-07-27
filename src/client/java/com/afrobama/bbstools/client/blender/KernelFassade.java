package com.afrobama.bbstools.client.blender;


/**
 * Die tausch-grenze zum hotswap-kernel.
 *
 * <p>WOFÜR: der kernel ({@code com.afrobama.blendertransform}) liegt in einem eigenen
 * classloader und wird bei jeder code-änderung komplett neu geladen. Wer eine kernel-klasse
 * direkt importiert, hält nach dem ersten tausch eine referenz auf eine TOTE klasse — der code
 * läuft weiter, aber es ist der alte, und typ-vergleiche schlagen fehl, weil dieselbe klasse aus
 * zwei loadern für die JVM verschieden ist.</p>
 *
 * <p>Diese fassade liegt im STABILEN loader und greift bei jedem aufruf frisch in die aktuelle
 * kernel-generation. Aufrufer sprechen nur mit ihr und merken vom tausch nichts.</p>
 *
 * <p>GEMESSEN (2026-07-26, 24 generationen in laufender welt): tausch ~140 ms, 137 static-felder
 * automatisch übertragen, FPS 118 → 118, kein crash. Der weg über den classloader ist der grund,
 * warum das geht — DCEVM tötet bei EINER klasse die JVM, weil es objekt-zeiger im heap umbiegen
 * muss; hier wird nichts umgebogen.</p>
 *
 * <h2>Benutzung</h2>
 * <pre>
 *   // statt:  BBSViewportBridge.sync(region, camera, x, y, w, h);
 *   KernelFassade.rufe("BBSViewportBridge", "sync", region, camera, x, y, w, h);
 * </pre>
 *
 * <p>⚠ Argumente und rückgabewerte dürfen KEINE kernel-typen sein, solange der aufrufer sie
 * nicht auch über die fassade bezieht — sonst ist man beim identitäts-problem zurück. Erlaubt
 * sind primitive, JDK-typen, Minecraft- und BBS-typen (die liegen im stabilen loader).</p>
 */
public final class KernelFassade
{
    private KernelFassade() {}

    /** Paket, das im kernel liegt. */
    private static final String KERNEL_PAKET = "com.afrobama.blendertransform";

    /* BEWUSST KEIN METHODEN-CACHE: ein handle auf eine kernel-methode zeigt nach dem
     * nächsten tausch auf eine tote klasse. Die reflection-suche pro aufruf ist der preis
     * dafür, dass die fassade den tausch garantiert überlebt. Wird sie je zum flaschenhals,
     * muss der cache die generation im schlüssel führen — vorher nicht anfassen. */

    /** Klasse des hotpatch-agents, über die der kernel erreichbar ist. Null wenn kein agent. */
    private static volatile Class<?> kernelLoaderKlasse;

    /** Ob schon einmal gemeldet wurde, dass kein agent läuft — verhindert log-fluten. */
    private static volatile boolean fehlendGemeldet;

    /**
     * Ruft eine statische kernel-methode auf.
     *
     * @param klasse    einfacher klassenname im kernel-paket, z.B. {@code "BBSViewportBridge"}
     * @param methode   methodenname
     * @param argumente argumente; ihre laufzeit-typen bestimmen die gesuchte signatur
     * @return rückgabewert der methode, oder {@code null} bei void
     * @throws IllegalStateException wenn der kernel nicht verfügbar ist — bewusst laut, weil ein
     *         stilles {@code null} den fehler an ganz anderer stelle auftauchen liesse
     */
    public static Object rufe(String klasse, String methode, Object... argumente)
    {
        try
        {
            Class<?> ziel = kernelKlasse(klasse);
            Class<?>[] typen = new Class<?>[argumente.length];

            for (int i = 0; i < argumente.length; i++)
            {
                typen[i] = argumente[i] == null ? Object.class : argumente[i].getClass();
            }

            java.lang.reflect.Method m = passendeMethode(ziel, methode, typen);
            m.setAccessible(true);

            return m.invoke(null, argumente);
        }
        catch (Throwable t)
        {
            throw new IllegalStateException(
                "[kernel-fassade] " + klasse + "." + methode + " fehlgeschlagen: " + t, t);
        }
    }

    /**
     * Lädt eine kernel-klasse aus der AKTUELLEN generation.
     *
     * <p>Nicht cachen: nach einem tausch muss der nächste aufruf die neue klasse bekommen.
     * Der loader hat einen eigenen cache, der aufruf ist also billig.</p>
     */
    public static Class<?> kernelKlasse(String einfacherName) throws ClassNotFoundException
    {
        String fqcn = einfacherName.contains(".")
            ? einfacherName
            : KERNEL_PAKET + "." + einfacherName;

        Class<?> loader = agentKlasse();

        if (loader == null)
        {
            /* Kein agent geladen (produktiv-build oder boot ohne -Pfull): dann liegt der
             * kernel-code ganz normal im mod-classloader. Das ist der gutfall, nicht ein
             * fehler — hotswap ist ein dev-werkzeug. */
            return Class.forName(fqcn);
        }

        try
        {
            return (Class<?>) loader.getMethod("laden", String.class).invoke(null, fqcn);
        }
        catch (Throwable t)
        {
            /* Agent da, aber noch kein tausch gelaufen — dann gibt es keine kernel-generation
             * und die klasse kommt aus dem normalen loader. */
            return Class.forName(fqcn);
        }
    }

    /** Ob gerade eine kernel-generation aktiv ist. */
    public static boolean kernelAktiv()
    {
        Class<?> loader = agentKlasse();

        if (loader == null)
        {
            return false;
        }

        try
        {
            return (Boolean) loader.getMethod("aktiv").invoke(null);
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    /**
     * Findet die agent-klasse im system-classloader.
     *
     * <p>Der agent liegt NICHT in Fabrics classloader, sondern im system-loader — deshalb
     * scheitert ein normales {@code Class.forName} von hier aus. Das ist kein fehler, sondern
     * die trennung, die den agent einen tausch überleben lässt.</p>
     */
    private static Class<?> agentKlasse()
    {
        Class<?> k = kernelLoaderKlasse;

        if (k != null)
        {
            return k;
        }

        try
        {
            k = Class.forName("com.afrobama.hotpatch.KernelLoader", false,
                ClassLoader.getSystemClassLoader());
            kernelLoaderKlasse = k;

            return k;
        }
        catch (Throwable t)
        {
            if (!fehlendGemeldet)
            {
                fehlendGemeldet = true;
                System.out.println("[kernel-fassade] kein hotpatch-agent — "
                    + "kernel-aufrufe laufen direkt (normal ohne -Pfull)");
            }

            return null;
        }
    }

    /**
     * Sucht eine methode, deren parameter zu den übergebenen argument-typen passen.
     *
     * <p>NICHT {@code getMethod(name, typen)}: das verlangt EXAKTE typ-gleichheit. Ein argument
     * vom typ {@code Integer} findet dann kein {@code int}-parameter, und eine unterklasse
     * findet ihre oberklasse nicht. Diese suche prüft zuweisbarkeit und entpackt wrapper-typen —
     * sonst wären die meisten aufrufe nicht vermittelbar.</p>
     */
    private static java.lang.reflect.Method passendeMethode(Class<?> ziel, String name,
                                                            Class<?>[] argTypen)
        throws NoSuchMethodException
    {
        for (java.lang.reflect.Method m : ziel.getDeclaredMethods())
        {
            if (!m.getName().equals(name))
            {
                continue;
            }

            Class<?>[] p = m.getParameterTypes();

            if (p.length != argTypen.length)
            {
                continue;
            }

            boolean passt = true;

            for (int i = 0; i < p.length; i++)
            {
                if (!zuweisbar(p[i], argTypen[i]))
                {
                    passt = false;
                    break;
                }
            }

            if (passt)
            {
                return m;
            }
        }

        throw new NoSuchMethodException(ziel.getName() + "." + name
            + " mit " + argTypen.length + " passenden parametern");
    }

    /** Ob ein argument vom typ {@code arg} an einen parameter vom typ {@code param} passt. */
    private static boolean zuweisbar(Class<?> param, Class<?> arg)
    {
        if (param.isAssignableFrom(arg))
        {
            return true;
        }

        /* Primitive gegen ihre wrapper prüfen — reflection liefert argumente immer als
         * objekte, die signatur nennt aber int/float/boolean. */
        if (param == int.class)     return arg == Integer.class;
        if (param == long.class)    return arg == Long.class;
        if (param == float.class)   return arg == Float.class;
        if (param == double.class)  return arg == Double.class;
        if (param == boolean.class) return arg == Boolean.class;
        if (param == short.class)   return arg == Short.class;
        if (param == byte.class)    return arg == Byte.class;
        if (param == char.class)    return arg == Character.class;

        return false;
    }
}
