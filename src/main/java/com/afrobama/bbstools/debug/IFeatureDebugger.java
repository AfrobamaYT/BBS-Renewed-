// Coded by Afrobama/Claude — Universal debugger contract for every
// BBSTools tier feature. Each tier item implements this interface and
// registers via BBSDebugRegistry. Gives every feature a consistent
// /bbsdebug <name> command + status dump + HUD integration for free.
//
// Usage:
//   class MyFeatureDebugger implements IFeatureDebugger {
//     @Override public String getName() { return "myfeature"; }
//     @Override public void writeStatus(StringBuilder sb) { ... }
//     @Override public void writeStats(StringBuilder sb) { ... }
//   }
//   BBSDebugRegistry.register(new MyFeatureDebugger());
//
// Now `/bbsdebug myfeature` prints status+stats, `/bbsdebug grand-dashboard`
// includes the feature, `/bbsdebug dump-all` writes to file.
package com.afrobama.bbstools.debug;

import java.util.Optional;

public interface IFeatureDebugger
{
    /** Short subcommand name. e.g. "accelrender", "crowdgpu", "veil". */
    String getName();

    /** Write current state (flags, last-tick values, init status). */
    void writeStatus(StringBuilder sb);

    /** Write counters + histogram percentiles. */
    void writeStats(StringBuilder sb);

    /** Is this feature currently active? (e.g., ENABLED flag check). */
    default boolean isEnabled()
    {
        return true;
    }

    /** Hot-toggle hook for /bbsdebug <name> on|off. No-op default. */
    default void setEnabled(boolean v)
    {
    }

    /** Optional HUD section for Tier 4b BBSPerfHud. */
    default Optional<FeatureHudSection> hud()
    {
        return Optional.empty();
    }

    /** Sort priority for grand-dashboard (lower=earlier). Default 100. */
    default int priority()
    {
        return 100;
    }

    /** Optional HUD section. Implemented by features that opt-in. */
    interface FeatureHudSection
    {
        int height();
        void render(int x, int y);
    }
}
