#version 330
/* separate_shader_objects: erst ab 26.3 */

/* Der Fragmentteil der Solid-Vorschau: Grundfarbe mal Studio-Licht, sonst nichts.
 *
 * ⛔ KEIN SAMPLER, UND DAS IST DAS FEATURE.
 * Kein texture(), kein discard nach Textur-Alpha, keine Lichtkarte, kein Overlay, kein
 * Nebel. Damit erklaert die zugehoerige Pipeline auch KEINE Sampler-Bindegruppe.
 *
 * ⚠️ WAS DAS SICHTBAR KOSTET -- offen hingeschrieben, weil es beim ersten Blick wie ein
 * Fehler aussieht: Modelle, deren Umriss aus Textur-Alpha entsteht (Haare, Hut-Schicht,
 * Umhaenge, Blaetter -- in Minecraft-Modellen sehr viel haeufiger als in Blender-Assets),
 * erscheinen als volle Rechtecke. Genau so sieht ein solches Modell auch in Blenders
 * Solid-Modus aus; der Alpha-Zuschnitt ist Teil des Materials, und ein Solid-Modus
 * wertet kein Material aus. Wer die Silhouette braucht, schaltet die Vorschau ab.
 *
 * ═══ DAS LICHT ═══
 *
 * Die WICKEL-FORMEL ist woertlich Blenders Workbench-Engine
 * (source/blender/draw/engines/workbench/shaders/workbench_world_light_lib.glsl):
 *
 *     w_1 = w + 1 ;  clamp((NL + w) / (w_1 * w_1), 0, 1)
 *
 * Sie zieht das Licht um die Silhouette herum, statt es am Terminator hart abreissen zu
 * lassen -- ohne sie wird die abgewandte Haelfte schwarz und ein Darsteller vor dunklem
 * Hintergrund verschwindet zur Haelfte. Blender rechnet sie fuer VIER Lichter
 * gleichzeitig, mit Diffus- und Blinn-Spiegelanteil.
 *
 * ⚠️ WAS HIER ANDERS IST ALS BEI BLENDER, damit niemand mehr hineinliest als drinsteht:
 * die ANORDNUNG der drei Lichter ist die uebliche Drei-Punkt-Ausleuchtung (Fuehrung,
 * Aufhellung, Kante), NICHT Blenders "Default"-Studiolicht -- dessen Richtungen und
 * Farben stehen in einer .sl-Datei, die dieser Baum nicht hat, und geraten waeren sie
 * schlechter als eine benannte Konvention. Der Spiegelanteil fehlt ganz: er kostet
 * einen zweiten Vektor je Licht und traegt zur Frage "sitzt die Pose" nichts bei.
 */

/*location 0*/ in vec3 sichtNormale;
/*location 1*/ in vec4 grundfarbe;

/*location 0*/ out vec4 fragColor;

/* Blenders gewickeltes Lambert. w = 0 ist gewoehnliches N·L, groesseres w schiebt den
 * Terminator um die Silhouette herum. */
float gewickelt(float nl, float w)
{
    float w1 = w + 1.0;

    return clamp((nl + w) / (w1 * w1), 0.0, 1.0);
}

void main()
{
    /* Erst hier normalisieren -- zwischen den Ecken wurde linear interpoliert. */
    vec3 n = normalize(sichtNormale);

    /* Alle Richtungen im SICHTRAUM: +x rechts, +y oben, +z zur Kamera. Sie wandern also
     * mit der Kamera mit, wie Blenders Studio-Licht in seiner Vorgabe. */
    vec3 licht = vec3(0.0);

    /* Fuehrungslicht -- oben links vorn, fast weiss, kaum gewickelt: es macht die Form. */
    licht += vec3(1.00, 0.98, 0.94) * gewickelt(dot(n, normalize(vec3(-0.35, 0.55, 0.75))), 0.05);

    /* Aufhellung -- rechts, kuehl und weit gewickelt: sie hebt die Schattenseite an,
     * damit sie lesbar bleibt, ohne eine zweite Lichtquelle vorzutaeuschen. */
    licht += vec3(0.34, 0.38, 0.46) * gewickelt(dot(n, normalize(vec3(0.75, 0.10, 0.30))), 0.55);

    /* Kantenlicht -- von unten hinten, schwach: es trennt den Darsteller vom Hintergrund. */
    licht += vec3(0.16, 0.16, 0.19) * gewickelt(dot(n, normalize(vec3(0.10, -0.70, -0.65))), 0.80);

    /* Die Summe dreier Lichter kann ueber 1 gehen; ohne Deckel brennt die Stirn aus. */
    licht = min(licht, vec3(1.0));

    /* ⚠️ NUR mit grundfarbe, NICHT zusaetzlich mit ColorModulator: die Formfarbe steckt
     * bereits darin (siehe model_solid.vsh). Ein zweites Multiplizieren wuerde sie
     * quadrieren -- dunkler, aber nicht offensichtlich falsch. */
    fragColor = vec4(grundfarbe.rgb * licht, grundfarbe.a);
}
