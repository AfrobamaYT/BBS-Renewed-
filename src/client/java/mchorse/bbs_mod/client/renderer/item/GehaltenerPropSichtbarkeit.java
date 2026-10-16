package mchorse.bbs_mod.client.renderer.item;

import java.util.ArrayDeque;
import java.util.Deque;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.forms.forms.Form;

/** Scope the existing runtime override around a draw; authoring and animation states stay intact. */
public final class GehaltenerPropSichtbarkeit
{
    private record Sicht(Form form, Boolean vorher, boolean hidden)
    {}

    private static final ThreadLocal<Deque<Sicht>> STAPEL = ThreadLocal.withInitial(ArrayDeque::new);
    private static boolean registriert;

    private GehaltenerPropSichtbarkeit()
    {}

    public static synchronized void registrieren()
    {
        if (registriert)
        {
            return;
        }

        FormRenderEvents.BEFORE.register((form, context) ->
        {
            boolean hidden = !GehaltenerPropEigentum.darfZeichnen(form, context);
            STAPEL.get().push(new Sicht(form, form.visible.getRuntimeValue(), hidden));
            if (hidden)
            {
                form.visible.setRuntimeValue(false);
            }
        });
        FormRenderEvents.AFTER.register((form, context) ->
        {
            Deque<Sicht> stack = STAPEL.get();
            /* Registration can occur during an earlier BEFORE listener's first item extraction. */
            if (stack.isEmpty())
            {
                return;
            }
            Sicht scope = stack.pop();
            if (scope.form() != form)
            {
                throw new IllegalStateException("Held-prop render scopes are unbalanced");
            }
            if (scope.hidden() && Boolean.FALSE.equals(form.visible.getRuntimeValue()))
            {
                form.visible.setRuntimeValue(scope.vorher());
            }
        });
        registriert = true;
    }
}
