package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.renderpearl.api.commands.RenderPass;

/**
 * Ein hochgeladenes Modell-Netz, das sich selbst in einen Zeichendurchgang legen kann.
 *
 * <h2>26.2: warum die Schnittstelle anders aussieht</h2>
 *
 * <p>Frueher hiess sie {@code render(VertexFormat, r, g, b, a, light, overlay)} — das Netz
 * band seinen eigenen VAO und setzte Farbe, Licht und Overlay als konstante
 * Vertexattribute gleich mit. Beides gibt es in 26.2 nicht mehr: gezeichnet wird in einen
 * {@link RenderPass}, und ein Attribut ohne Puffer kennt die neue Schnittstelle nicht.
 *
 * <p>Die Aufgaben sind darum getrennt: das Netz liefert Geometrie ({@link #zeichnen}), der
 * Aufrufer bestimmt vorher Pipeline, Textur und Uniforms. Farbe, Licht und Overlay stehen
 * jetzt im Uniform-Block, den {@link ModelVAORenderer} fuellt.
 */
public interface IModelVAO
{
    /**
     * Legt die eigenen Vertexpuffer an die Bindungen der Pipeline und zeichnet.
     *
     * <p>⚠️ Der Aufrufer muss VORHER {@code setPipeline}, die Texturen und die Uniforms
     * gesetzt haben. Diese Methode setzt bewusst nichts davon — sonst muesste jedes Netz
     * wissen, in welchem Durchgang es gerade steckt.
     *
     * <p>Die Bindungsnummern sind der Vertrag mit {@link ModelVAOPipeline}: 0 Position,
     * 1 Normale, 2 Textur-UV. Wer hier etwas verschiebt, muss die Pipeline mitziehen —
     * der Compiler sieht diesen Zusammenhang nicht.
     */
    void zeichnen(RenderPass pass);

    /** Anzahl der Ecken, die {@link #zeichnen} abschickt. */
    int eckenZahl();
}
