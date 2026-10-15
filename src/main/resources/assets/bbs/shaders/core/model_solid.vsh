#version 330
/* separate_shader_objects: erst ab 26.3 */

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

/* Die SOLID-VORSCHAU von BBS' Modellweg -- Blenders Workbench-Engine im Kleinen.
 *
 * Was er zeichnet: die Grundfarbe der Form, beleuchtet allein aus der Normalen. Keine
 * Textur, keine Lichtkarte, kein Overlay, kein Nebel, kein Zwei-Pass. Genau das ist der
 * Sinn: der Zeichendurchgang soll billig sein, nicht huebsch.
 *
 * ⛔ ER LIEST DIE UV NICHT -- UND DIE BINDUNG MUSS TROTZDEM STEHEN.
 * ModelVAO.zeichnen setzt IMMER alle drei Puffer (Position an 0, Normale an 1, UV0 an 2).
 * Eine Luecke in der Bindungsnummerierung waere ein zweiter Fehler, kein Aufraeumen. Ein
 * Attribut, das das Programm nicht kennt, laeuft dagegen ins Leere und kostet nichts --
 * dieselbe Lage wie in model_no_shading.vsh, nur umgekehrt (dort bleibt die Normale
 * ungelesen, hier die UV).
 *
 * ⚠️ SICHTRAUM-NORMALE, KEINE NormalMat.
 * Der beschattete Weg (model.vsh) holt sich eine eigene Normalenmatrix aus dem
 * ModelInfo-Block. Dieser Shader nicht -- und das ist eine Entscheidung, kein Vergessen:
 *
 *   1. Der ModelInfo-Block steckt in ModelVAOPipeline.GRUPPE_MODELL, und die Gruppe
 *      buendelt ihn UNTRENNBAR mit Sampler0/1/2. Wer sie erklaert, muss alle drei Sampler
 *      im Programm halten -- fuer einen Shader, der keine einzige Textur liest, waeren das
 *      drei Bindungen, die niemand fuellt. Ohne die Gruppe braucht diese Pipeline nur
 *      DYNAMIC_TRANSFORMS und PROJECTION, also die zwei, die jede Pipeline im Baum hat.
 *   2. Blenders Studio-Licht ist VORGABEMAESSIG kameragebunden ("World Space Lighting" ist
 *      aus). Die Normale im Sichtraum zu beleuchten ist dort also nicht die Abkuerzung,
 *      sondern das Verhalten. Dreht man die Kamera, wandert das Licht mit -- gewollt.
 *
 * ⚠️ FALLE: mat3(ModelViewMat) ist NICHT die Inverse-Transponierte. Bei UNGLEICHMAESSIGER
 * Skalierung (Knochen, der nur in einer Achse gestaucht ist) kippt die Normale um einige
 * Grad. Das Ergebnis ist eine minimal falsche Helligkeit an genau diesen Teilen -- fuer
 * eine Vorschau tragbar, fuer den Export nie sichtbar (dort laeuft der volle Weg). Wer es
 * exakt braucht, muss GRUPPE_MODELL erklaeren und NormalMat lesen, siehe Punkt 1.
 */

/*location 0*/ in vec3 Position;
/*location 1*/ in vec3 Normal;

/*location 0*/ out vec3 sichtNormale;
/*location 1*/ out vec4 grundfarbe;

void main()
{
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    /* Nicht hier normalisieren: zwischen den Ecken wird linear interpoliert, und eine im
     * Vertex normalisierte Normale ist im Fragment trotzdem wieder kuerzer als 1. Der
     * Fragmentshader macht es, dort ist es richtig. */
    sichtNormale = mat3(ModelViewMat) * Normal;

    /* Die Formfarbe kommt aus ColorModulator (DynamicTransforms), so wie in
     * model_no_shading.vsh -- ModelVAORenderer schreibt r/g/b/a ueber writeTransform genau
     * dorthin. ⚠️ Der Fragmentshader darf sie NICHT noch einmal mit ColorModulator
     * multiplizieren, das wuerde jede Farbe quadrieren. */
    grundfarbe = ColorModulator;
}
