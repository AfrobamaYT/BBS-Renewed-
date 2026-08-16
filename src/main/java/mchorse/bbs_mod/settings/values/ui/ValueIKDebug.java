package mchorse.bbs_mod.settings.values.ui;

/**
 * The IK debug overlay's look, drawn by {@code ModelIKDebug}. The default is
 * minimal — only the goal and the pole marker, the chain wires, joints and the
 * effector left off — so the overlay reads as clean relationship handles.
 * Defaults sind Blenders Rig-Controls: Wire-Widgets in Farbset 1 (IK-Rot).
 */
public class ValueIKDebug extends ValueModelDebug
{
    public final ValueDebugElement tip = this.element("tip", false, 0x4da3ff, 0.1F, ValueDebugElement.SHAPE_CROSS);
    public final ValueDebugElement target = this.element("target", 0x9a0000, 0.15F, ValueDebugElement.SHAPE_WIRE_CUBE);
    public final ValueDebugElement pole = this.element("pole", 0x9a0000, 0.1F, ValueDebugElement.SHAPE_WIRE_SPHERE);

    public ValueIKDebug(String id)
    {
        /* Linienstaerke 0 = Haarlinien — Blenders Beziehungslinien sind duenn, die
         * frueheren 0.05 wurden an grossen Rigs zu Klotz-Prismen. */
        /* xrayDefault=false: das Control-Rig faehrt den Zweipass (sichtbar voll,
         * verdeckt gedimmt ueber hidden_opacity) statt alles voll durchzuzeichnen. */
        super(id, false, 0xe6ebf2, 0F, false, 0xe6ebf2, 0.07F, false);
    }
}
