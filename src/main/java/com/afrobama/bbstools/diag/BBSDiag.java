// Coded by Afrobama/Claude — central diagnostic helper for the
// log-first development style mandated by user feedback. Every new
// feature MUST instrument its lifecycle + state transitions + write
// verification using this class.
//
// Usage:
//   BBSDiag.feature("replay-fastpath").event("draw")
//          .field("n", n).field("ok", drew).emit();
//
//   BBSDiag.feature("sprint-fov").throttled1Hz().event("write")
//          .field("base", base).field("smoothed", smoothed)
//          .field("verify", cir.getReturnValue()).emit();
//
//   BBSDiag.feature("crowd-iris-wrap").sticky("probe-failure")
//          .field("class", classname).field("err", t.toString()).emit();
//
// Throttling modes:
//   .throttled1Hz()        — at most 1 emit/sec per (feature,event) pair
//   .throttledEveryNth(60) — every 60th call, useful for per-frame logs
//   .sticky(key)           — emit once per session per key, useful for
//                            failure modes that would otherwise spam
//
// Log format: "[BBSDIAG/<feature>] event=<event> field1=val1 field2=val2"
// Greppable with: grep -E "\[BBSDIAG/feature-name\]" latest.log
package com.afrobama.bbstools.diag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BBSDiag
{
    private static final ConcurrentHashMap<String, Logger> LOGGERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> LAST_EMIT_NANOS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> CALL_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> STICKY_EMITTED = new ConcurrentHashMap<>();

    /** Master switch — set false in production builds to silence all
     *  diag output. Verify-after-set logs still fire because debugging
     *  is load-bearing per user feedback. */
    public static volatile boolean ENABLED = true;

    private BBSDiag() {}

    public static Builder feature(String name)
    {
        return new Builder(name);
    }

    private static Logger logger(String feature)
    {
        return LOGGERS.computeIfAbsent(feature,
            f -> LoggerFactory.getLogger("bbstools/" + f));
    }

    public static final class Builder
    {
        private final String feature;
        private String event = "log";
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private Throttle throttle = Throttle.ALWAYS;
        private long throttleNanos;
        private long throttleNth;
        private String stickyKey;
        private boolean asWarn;
        private boolean asError;

        private Builder(String feature) { this.feature = feature; }

        public Builder event(String e) { this.event = e; return this; }

        public Builder field(String name, Object value)
        {
            this.fields.put(name, value);
            return this;
        }

        /** Throttle to at most 1 emit per second per (feature, event)
         *  pair — use for per-frame logs that would otherwise spam. */
        public Builder throttled1Hz()
        {
            this.throttle = Throttle.RATE;
            this.throttleNanos = 1_000_000_000L;
            return this;
        }

        public Builder throttledEveryMs(long ms)
        {
            this.throttle = Throttle.RATE;
            this.throttleNanos = ms * 1_000_000L;
            return this;
        }

        /** Emit every Nth call to .emit() for this (feature, event). */
        public Builder throttledEveryNth(long n)
        {
            this.throttle = Throttle.NTH;
            this.throttleNth = n;
            return this;
        }

        /** Emit ONCE per session per key. Use for failure modes that
         *  would otherwise spam every frame. */
        public Builder sticky(String key)
        {
            this.throttle = Throttle.STICKY;
            this.stickyKey = key;
            return this;
        }

        public Builder asWarn() { this.asWarn = true; return this; }
        public Builder asError() { this.asError = true; return this; }

        public void emit()
        {
            if (!ENABLED) return;

            /* Throttle. */
            String tk = feature + "/" + event;
            switch (throttle)
            {
                case ALWAYS:
                    break;
                case RATE:
                {
                    long now = System.nanoTime();
                    Long last = LAST_EMIT_NANOS.get(tk);
                    if (last != null && now - last < throttleNanos) return;
                    LAST_EMIT_NANOS.put(tk, now);
                    break;
                }
                case NTH:
                {
                    long n = CALL_COUNT.merge(tk, 1L, Long::sum);
                    if ((n - 1) % throttleNth != 0) return;
                    break;
                }
                case STICKY:
                {
                    String k = feature + "/" + stickyKey;
                    if (STICKY_EMITTED.putIfAbsent(k, Boolean.TRUE) != null) return;
                    break;
                }
            }

            /* Format: [BBSDIAG/feature] event=<event> k1=v1 k2=v2 ... */
            StringBuilder sb = new StringBuilder(64);
            sb.append("[BBSDIAG/").append(feature).append("] event=").append(event);
            for (Map.Entry<String, Object> e : fields.entrySet())
            {
                sb.append(' ').append(e.getKey()).append('=').append(stringify(e.getValue()));
            }
            String line = sb.toString();

            Logger log = logger(feature);
            if (asError)      log.error(line);
            else if (asWarn)  log.warn(line);
            else              log.info(line);
        }

        private static String stringify(Object v)
        {
            if (v == null) return "null";
            if (v instanceof Float f) return String.format("%.4f", f);
            if (v instanceof Double d) return String.format("%.4f", d);
            return v.toString();
        }
    }

    private enum Throttle { ALWAYS, RATE, NTH, STICKY }

}
