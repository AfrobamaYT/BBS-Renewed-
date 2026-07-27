package com.mcstudio.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RenderThread — the shared seam every Studio worker builds on.
 *
 * WHY: anything that touches OpenGL, the framebuffer, or live game state MUST run on the client
 * render thread, but the HTTP server / CompileDaemon run on their own threads. This queues a job
 * onto the render thread and blocks the caller until it finishes — the 1:1 pattern proven in
 * cml-abtest's MiniHarness.onClientJob.
 *
 * OWNER: Lead (Task 0). Read-only for all workers — they CALL submit(...), they do NOT edit this.
 */
public final class RenderThread
{
	/* Jobs waiting to run on the render thread. Drained once per client tick by drain(). */
	private static final ConcurrentLinkedQueue<FutureTask<String>> QUEUE = new ConcurrentLinkedQueue<>();

	private RenderThread()
	{
	}

	/**
	 * Queue a job onto the render thread and block until it produces a result (or times out at 5s).
	 * Call this from any non-render thread (HTTP handler, daemon). NEVER call it FROM the render
	 * thread — that would deadlock (the drain never runs while you wait).
	 */
	public static String submit(Callable<String> job)
	{
		FutureTask<String> task = new FutureTask<>(job);
		QUEUE.add(task);

		try
		{
			return task.get(5L, TimeUnit.SECONDS);
		}
		catch (TimeoutException e)
		{
			return "{\"error\":\"render thread did not run within 5000ms (frozen?)\"}";
		}
		catch (Exception e)
		{
			return "{\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}";
		}
	}

	/**
	 * Run every queued job. MUST be called from the render thread, once per client tick
	 * (wired by StudioClient via ClientTickEvents.END_CLIENT_TICK).
	 */
	public static void drain()
	{
		FutureTask<String> task;

		while ((task = QUEUE.poll()) != null)
		{
			task.run();
		}
	}

	/* Minimal JSON string escaping for error messages (quotes + backslashes). */
	private static String escape(String s)
	{
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
