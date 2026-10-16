package com.mcstudio.see;

import com.mcstudio.util.RenderThread;

/** Partial tick of the next WeltbildFahrt frame, handed to the render thread. A class of its own so
 * WeltbildFahrt keeps the lambda set of the loaded version and swaps into a running game. */
public final class WeltbildFahrtRest
{
	private static volatile float rest;

	/** Fahrt thread: remember the rest; the next scrub job applies it. */
	public static void vormerken(float rest)
	{
		WeltbildFahrtRest.rest = rest;
	}

	/** Render thread, inside the scrub job: rest and cursor change between two frames. */
	public static void anwenden()
	{
		SubTick.setzen(rest);
	}

	/** Releases the rest on the render thread, so no running frame sees it change. */
	public static void loesen()
	{
		RenderThread.submit(() ->
		{
			SubTick.loesen();

			return "";
		});
	}

	private WeltbildFahrtRest()
	{}
}
