// Coded by Afrobama/Claude — Tier 2e DH-style upload thread.
//
// Pattern reference: Distant Horizons GLProxy.java — uses 3 GLFW contexts
// (main + upload + read). For our scope, 2 contexts are sufficient:
//   main   = the MC window (render thread)
//   upload = a hidden 64×48 GLFW window sharing the main context's
//            objects, current on a single dedicated worker thread
// Heavy mesh-data uploads (glBufferData/glBufferSubData) can be issued
// against the shared context off the render thread, avoiding render-thread
// stalls while the GPU streams vertex data.
//
// HIDDEN-WINDOW HOW-TO (from GLFW docs and DH GLProxy):
//   glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
//   long uploadWindow = glfwCreateWindow(64, 48, "BBSTools-Upload",
//       NULL, mainWindowHandle);   // 5th arg = share context with main
// The worker thread keeps glfwMakeContextCurrent(uploadWindow) for its
// lifetime. Buffer objects created on the main context (e.g. via
// glGenBuffers on the render thread) are visible to the upload context
// because of the shared list — we just bind+sub-data them.
//
// RISK: AMD+Linux+Mesa drivers have a history of shared-context bugs
//       (especially with glFenceSync + multi-context). Default OFF on
//       Linux to avoid soft-locking the user's GPU driver.
// SAFETY: if glfwCreateWindow returns 0 (init failed), we log warn and
//         leave ENABLED=false so subsequent submit() calls run inline
//         on the caller's thread (no behavioural difference, just no
//         async benefit).
package com.afrobama.bbstools.perf;

import com.afrobama.bbstools.debug.IFeatureDebugger;
import com.afrobama.bbstools.debug.RollingHistogram;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

//? if >=26.3 {
/*import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDLVideo;
*///?} else {
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
//?}
import static org.lwjgl.system.MemoryUtil.NULL;

public final class BBSUploadThread implements IFeatureDebugger
{
    private static final Logger LOG = LoggerFactory.getLogger("bbstools/upload-thread");

    public static final BBSUploadThread INSTANCE = new BBSUploadThread();

    private static final boolean OS_IS_WINDOWS =
        System.getProperty("os.name", "").toLowerCase().contains("win");

    /** Default OFF (opt-in) per the project's "new perf paths must be
     *  opt-in" rule. On non-Windows the user has to flip it on manually
     *  via /bbsdebug upload-thread on AND understand the AMD+Linux+Mesa
     *  shared-context driver risk. On Windows it's documented-safe but
     *  still opt-in by default. */
    public static volatile boolean ENABLED = false;

    /** Set true once the hidden GLFW window + worker thread are up. If
     *  init failed (or hasn't been called yet), submit() falls back to
     *  inline execution on the calling thread. */
    private static volatile boolean initialized = false;

    private static volatile long uploadWindow = NULL;
    //? if >=26.3 {
    /*private static volatile long uploadContext = NULL;
    *///?}
    private static volatile Thread workerThread;
    private static volatile boolean running = false;

    /** Queue of jobs awaiting execution on the upload context. Pulled in
     *  FIFO order; each runs to completion before the next starts. */
    private static final ConcurrentLinkedQueue<UploadJob> queue = new ConcurrentLinkedQueue<>();

    public final AtomicLong jobsExecuted = new AtomicLong();
    public final AtomicLong driverErrors = new AtomicLong();
    public final AtomicLong bytesUploaded = new AtomicLong();
    public final RollingHistogram jobLatency = new RollingHistogram(1024);

    private BBSUploadThread() {}

    /** Typed upload job. */
    public static final class UploadJob
    {
        final int vbo;
        final long offset;
        final ByteBuffer data;
        final Runnable rawGl;            /* nullable: when set, used in place of vbo upload */
        final Runnable onComplete;       /* nullable: scheduled on render thread after upload */

        UploadJob(int vbo, long offset, ByteBuffer data, Runnable rawGl, Runnable onComplete)
        {
            this.vbo = vbo;
            this.offset = offset;
            this.data = data;
            this.rawGl = rawGl;
            this.onComplete = onComplete;
        }
    }

    /** Initialise the hidden shared-context window + worker thread. Must
     *  be called on the render thread AFTER MC has created its own
     *  window (we need its handle to pass as share-context arg). Safe to
     *  call multiple times — second call is a no-op. If creation fails,
     *  ENABLED is forced false and the method returns false (callers
     *  silently fall back to inline submit). */
    public static synchronized boolean initOnClient(long mainWindowHandle)
    {
        if (initialized) return true;
        if (mainWindowHandle == NULL)
        {
            LOG.warn("initOnClient called with NULL main-window handle — aborting");
            return false;
        }
        try
        {
            //? if >=26.3 {
            /*long mainContext = SDLVideo.SDL_GL_GetCurrentContext();

            if (mainContext == NULL)
            {
                throw new IllegalStateException("The upload worker requires a current OpenGL context");
            }

            uploadWindow = SDLVideo.SDL_CreateWindow("BBSTools-Upload", 64, 48,
                SDLVideo.SDL_WINDOW_OPENGL | SDLVideo.SDL_WINDOW_HIDDEN);
            *///?} else {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            uploadWindow = glfwCreateWindow(64, 48, "BBSTools-Upload",
                NULL, mainWindowHandle);
            //?}
            if (uploadWindow == NULL)
            {
                //? if >=26.3 {
                /*LOG.warn("SDL_CreateWindow(shared) failed: {}", SDLError.SDL_GetError());
                *///?} else {
                LOG.warn("glfwCreateWindow(shared) returned NULL — upload thread disabled");
                //?}
                ENABLED = false;
                return false;
            //? if >=26.3 {
            /*}
            try
            {
                if (!SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 1))
                {
                    throw new IllegalStateException(SDLError.SDL_GetError());
                }

                uploadContext = SDLVideo.SDL_GL_CreateContext(uploadWindow);

                if (uploadContext == NULL)
                {
                    throw new IllegalStateException(SDLError.SDL_GetError());
                }
            }
            finally
            {
                SDLVideo.SDL_GL_SetAttribute(SDLVideo.SDL_GL_SHARE_WITH_CURRENT_CONTEXT, 0);

                if (!SDLVideo.SDL_GL_MakeCurrent(mainWindowHandle, mainContext))
                {
                    throw new IllegalStateException("Cannot restore Minecraft OpenGL context: " + SDLError.SDL_GetError());
                }
            *///?}
            }
            running = true;
            workerThread = new Thread(BBSUploadThread::workerLoop, "BBSTools-Upload");
            workerThread.setDaemon(true);
            workerThread.start();
            initialized = true;
            //? if >=26.3 {
            /*LOG.info("BBSTools-Upload thread up — hidden shared SDL window=0x{} (64x48), ENABLED={}, os.windows={}",
            *///?} else {
            LOG.info("BBSTools-Upload thread up — hidden shared GLFW window=0x{} (64x48), ENABLED={}, os.windows={}",
            //?}
                Long.toHexString(uploadWindow), ENABLED, OS_IS_WINDOWS);
            return true;
        }
        catch (Throwable t)
        {
            //? if >=26.3 {
            /*if (uploadContext != NULL)
            {
                SDLVideo.SDL_GL_DestroyContext(uploadContext);
                uploadContext = NULL;
            }

            if (uploadWindow != NULL)
            {
                SDLVideo.SDL_DestroyWindow(uploadWindow);
                uploadWindow = NULL;
            }
            *///?}
            LOG.warn("BBSUploadThread init failed — falling back to inline upload: {}", t.toString(), t);
            ENABLED = false;
            return false;
        }
    }

    /** Submit a typed VBO upload. {@code data} must already be in native
     *  byte order and positioned for reading (call .flip() first).
     *  {@code onComplete} (nullable) is dispatched onto the render
     *  thread via {@code MinecraftClient.execute} once the upload + a
     *  glFinish on the upload context have committed.
     *
     *  When the upload thread is disabled or not yet initialised, the
     *  upload runs inline on the calling thread and onComplete runs
     *  inline immediately after — so callers don't need to special-case
     *  the disabled branch.
     *
     *  Counters always increment regardless of path. */
    public static void submit(int vbo, long offset, ByteBuffer data, Runnable onComplete)
    {
        if (!ENABLED || !initialized)
        {
            doUploadInline(vbo, offset, data);
            INSTANCE.jobsExecuted.incrementAndGet();
            if (data != null) INSTANCE.bytesUploaded.addAndGet(data.remaining());
            BBSPerfConfig.uploadThreadJobsCompleted++;
            if (data != null) BBSPerfConfig.uploadThreadBytesUploaded += data.remaining();
            if (onComplete != null) onComplete.run();
            return;
        }
        queue.offer(new UploadJob(vbo, offset, data, null, onComplete));
    }

    /** Legacy free-form GL job. The supplied Runnable executes with the
     *  upload context current. Use only for ops where binding/uploading
     *  exactly one VBO via {@link #submit(int, long, ByteBuffer, Runnable)}
     *  is too restrictive. */
    public void submit(Runnable glJob)
    {
        if (glJob == null) return;
        if (!ENABLED || !initialized) { glJob.run(); jobsExecuted.incrementAndGet(); return; }
        queue.offer(new UploadJob(0, 0L, null, glJob, null));
    }

    /** Inline fallback path. Runs on whichever thread called submit(). */
    private static void doUploadInline(int vbo, long offset, ByteBuffer data)
    {
        if (vbo <= 0 || data == null) return;
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, offset, data);
    }

    private static void workerLoop()
    {
        try
        {
            //? if >=26.3 {
            /*if (!SDLVideo.SDL_GL_MakeCurrent(uploadWindow, uploadContext))
            {
                throw new IllegalStateException(SDLError.SDL_GetError());
            }
            *///?} else {
            glfwMakeContextCurrent(uploadWindow);
            //?}
            /* GLCapabilities must be created per-thread for LWJGL's GL
             *  bindings to function. The shared object list is fine, but
             *  the per-thread function-pointer table is not — without
             *  this, GL15.glBindBuffer NPEs the first time. */
            GLCapabilities caps = GL.createCapabilities();
            LOG.info("BBSTools-Upload worker: context current, GL caps version OK ({} extensions)",
                caps == null ? "?" : "init");
        }
        catch (Throwable t)
        {
            LOG.warn("Worker failed to make context current — disabling: {}", t.toString(), t);
            ENABLED = false;
            running = false;
            return;
        }

        while (running)
        {
            UploadJob job = queue.poll();
            if (job == null)
            {
                try { Thread.sleep(1); } catch (InterruptedException ie) { /* keep running */ }
                continue;
            }
            long t0 = System.nanoTime();
            try
            {
                if (job.rawGl != null)
                {
                    job.rawGl.run();
                }
                else if (job.vbo > 0 && job.data != null)
                {
                    GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, job.vbo);
                    GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, job.offset, job.data);
                    /* glFinish blocks until the GPU has fully consumed
                     *  the upload, so the render thread sees coherent
                     *  data the moment onComplete fires. Without this,
                     *  a subsequent draw on the main context could see
                     *  the buffer mid-upload (driver-dependent). */
                    GL11.glFinish();
                    INSTANCE.bytesUploaded.addAndGet(job.data.remaining());
                    BBSPerfConfig.uploadThreadBytesUploaded += job.data.remaining();
                }
                INSTANCE.jobsExecuted.incrementAndGet();
                BBSPerfConfig.uploadThreadJobsCompleted++;
                if (job.onComplete != null)
                {
                    /* Schedule completion on render thread — uses the
                     *  MC client task scheduler so it lands on the main
                     *  context in a controlled spot. Reflection-routed
                     *  to keep this main-set file free of MC client
                     *  imports (source-set split). */
                    runOnRenderThread(job.onComplete);
                }
            }
            catch (Throwable t)
            {
                INSTANCE.driverErrors.incrementAndGet();
                LOG.warn("Upload job threw on worker (driver error?): {}", t.toString(), t);
                /* If we're crashing in a tight loop, back off to avoid
                 *  spamming the log + maxing one core. */
                try { Thread.sleep(10); } catch (InterruptedException ie) { /* keep running */ }
            }
            INSTANCE.jobLatency.record(System.nanoTime() - t0);
        }

        /* Clean shutdown path. */
        try
        {
            //? if >=26.3 {
            /*if (!SDLVideo.SDL_GL_MakeCurrent(uploadWindow, NULL))
            *///?} else {
            glfwMakeContextCurrent(NULL);
            if (uploadWindow != NULL)
            //?}
            {
                //? if >=26.3 {
                /*throw new IllegalStateException(SDLError.SDL_GetError());
            }

            runOnRenderThread(() -> {
                SDLVideo.SDL_GL_DestroyContext(uploadContext);
                SDLVideo.SDL_DestroyWindow(uploadWindow);
                uploadContext = NULL;
                *///?} else {
                glfwDestroyWindow(uploadWindow);
                //?}
                uploadWindow = NULL;
            //? if >=26.3 {
            /*});
            *///?} else {
            }
            //?}
        }
        catch (Throwable t)
        {
            LOG.warn("Worker cleanup error: {}", t.toString());
        }
    }

    /** Hop onto the render thread via reflection (avoid net.minecraft
     *  imports from the main source set). Falls back to inline run if
     *  reflection fails (defensive). */
    private static void runOnRenderThread(Runnable r)
    {
        try
        {
            //? if >=26.3 {
            /*Class<?> mc = Class.forName("net.minecraft.client.Minecraft");
            *///?} else {
            Class<?> mc = Class.forName("net.minecraft.client.MinecraftClient");
            //?}
            Object instance = mc.getMethod("getInstance").invoke(null);
            mc.getMethod("execute", Runnable.class).invoke(instance, r);
        }
        catch (Throwable t)
        {
            LOG.warn("runOnRenderThread reflection failed; running inline: {}", t.toString());
            r.run();
        }
    }

    /** Stop the worker + tear down the hidden window. Safe to call from
     *  CLIENT_STOPPING. After this, further submit() calls fall through
     *  to the inline path. */
    public static synchronized void shutdown()
    {
        if (!initialized) return;
        running = false;
        try
        {
            if (workerThread != null) workerThread.join(2000);
        }
        catch (InterruptedException ie) { /* swallow */ }
        initialized = false;
        LOG.info("BBSTools-Upload thread shut down");
    }

    /* ----------------------------- IFeatureDebugger ----------------------------- */

    @Override public String getName() { return "upload-thread"; }
    @Override public boolean isEnabled() { return ENABLED; }
    @Override public void setEnabled(boolean v) { ENABLED = v; }
    @Override public int priority() { return 70; }

    @Override
    public void writeStatus(StringBuilder sb)
    {
        sb.append("enabled=").append(ENABLED).append('\n');
        sb.append("initialized=").append(initialized).append('\n');
        sb.append("os.windows=").append(OS_IS_WINDOWS).append('\n');
        sb.append("uploadWindow=0x").append(Long.toHexString(uploadWindow)).append('\n');
        sb.append("queueDepth=").append(queue.size()).append('\n');
    }

    @Override
    public void writeStats(StringBuilder sb)
    {
        sb.append("jobsExecuted=").append(jobsExecuted.get()).append('\n');
        sb.append("bytesUploaded=").append(bytesUploaded.get()).append('\n');
        sb.append("driverErrors=").append(driverErrors.get()).append('\n');
        sb.append("jobLatency: ");
        jobLatency.writeSummary(sb);
        sb.append('\n');
    }

}
