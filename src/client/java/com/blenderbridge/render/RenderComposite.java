// Coded by Afrobama/Claude — Weg A (P7): BBS stoesst den Blender-Composite-Render an.
package com.blenderbridge.render;

import com.blenderbridge.BlenderBridgeClient;
import com.blenderbridge.BridgeProtocol;
import com.google.gson.JsonObject;
import com.mcstudio.see.WeltbildFahrt;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RenderComposite — der Weg-A-Orchestrator: EIN Anstoss aus BBS rendert den offenen Film als
 * Hybrid-Composite (BBS-Welt + Blender-Modelle, Eevee) fertig heraus.
 *
 * <p>ABLAUF ({@code /render_composite?start=&end=[&mux=]}):
 * (1) {@link WeltbildFahrt#fahren} schreibt je Tick einen UI-freien Welt-Frame (Kamerafahrt des
 * offenen Films), (2) eine {@code RENDER}-Nachricht geht ueber die Bruecke an das Blender-Addon
 * (render_bridge.py: build_composite + render_composite), (3) das Addon antwortet
 * {@code RENDER_RESULT} mit Ausgabepfad/Fehler, das hier die wartende Route aufloest.
 * Der Nutzer verlaesst BBS nie — Blender ist der unsichtbare Render-Dienst.</p>
 *
 * <p>ZEITBASIS (§3): {@code start}/{@code end} sind TICKS des Films. Gerendert werden die
 * Ausgabeframes j = round(start·fps/20) .. round(end·fps/20), jeder zum Zeitpunkt t = j·20/fps
 * Ticks — bei fps 20 ist j == Tick (der alte Stand), sonst liefert {@link WeltbildFahrt} den
 * Welt-Frame per Sub-Tick-Fahrt zwischen zwei Ticks. Das Addon setzt die Blender-Szene auf
 * dieselbe fps, importiert die Kamera-Keys bei frame = tick·fps/20 und rendert die Frames
 * frameStart..frameEnd; beide Grenzen reisen AUSDRUECKLICH mit, weil Pythons round()
 * kaufmaennisch zur geraden Zahl rundet und Javas Math.round aufwaerts — geraten wuerde
 * um einen Frame versetzt compositet.</p>
 *
 * <p>Neue, lazy geladene Klasse — die einzige Aenderung an geladenen Klassen ist der eine
 * {@code RENDER_RESULT}-Fall in {@code BlenderBridgeClient.dispatch} (Body-Swap).</p>
 */
public final class RenderComposite
{
	/** Offene Auftraege: requestId -> Future, das der RENDER_RESULT-Dispatch aufloest. */
	private static final Map<String, CompletableFuture<JsonObject>> OFFEN = new ConcurrentHashMap<>();

	private RenderComposite()
	{
	}

	/** Der ganze Weg A, synchron auf dem Aufrufer-Thread (nie der Render-Thread). */
	public static String rendern(int start, int end, boolean mux)
	{
		return rendern(start, end, mux, "EEVEE");
	}

	/** Wie oben, mit Engine-Wahl ("EEVEE" oder "CYCLES" — P7-Seitenpanel), fps 20. */
	public static String rendern(int start, int end, boolean mux, String engine)
	{
		return rendern(start, end, mux, engine, 20);
	}

	/**
	 * Wie oben, mit Ausgabe-fps (REDACTED Abnahme 1 bei 24/30/60): start/end bleiben Ticks,
	 * gerendert werden die Ausgabeframes dazwischen — Herleitung im Klassenkommentar.
	 */
	public static String rendern(int start, int end, boolean mux, String engine, int fps)
	{
		if (fps <= 0)
		{
			return "{\"error\":\"fps muss > 0 sein (bekommen: " + fps + ")\"}";
		}

		int frameStart = (int) Math.round(start * fps / 20.0);
		int frameEnd = (int) Math.round(end * fps / 20.0);
		int frames = frameEnd - frameStart + 1;

		/* 1. Welt-Frames je Ausgabeframe (prueft selbst, ob ein Film offen ist — Fehler reicht durch). */
		String fahrt = WeltbildFahrt.fahren("bbs_frame", frameStart, frames, null, fps);

		if (!fahrt.contains("\"ok\":true"))
		{
			return "{\"schritt\":\"weltbild-fahrt\",\"antwort\":" + fahrt + "}";
		}

		/* 2. RENDER an das Addon. */
		String requestId = UUID.randomUUID().toString().replace("-", "");
		File ausgabe = new File(WeltbildFahrt.verzeichnis(), "composite");
		Minecraft mc = Minecraft.getInstance();

		CompletableFuture<JsonObject> future = new CompletableFuture<>();

		OFFEN.put(requestId, future);

		long seq = BlenderBridgeClient.STATE.nextOutboundSeq();
		JsonObject nachricht = BridgeProtocol.envelope("RENDER", "render", seq, BridgeProtocol.ORIGIN_BBS);
		JsonObject fields = BridgeProtocol.getFields(nachricht);

		fields.addProperty("requestId", requestId);
		fields.addProperty("film", com.blenderbridge.FilmIdentity.currentFilmId());
		fields.addProperty("start", start);
		fields.addProperty("end", end);
		fields.addProperty("fps", fps);
		fields.addProperty("frameStart", frameStart);
		fields.addProperty("frameEnd", frameEnd);
		fields.addProperty("bbsFrameDir", WeltbildFahrt.verzeichnis().getAbsolutePath());
		fields.addProperty("bbsFramePattern", "bbs_frame_%04d.png");
		fields.addProperty("outDir", ausgabe.getAbsolutePath());
		fields.addProperty("mux", mux);
		fields.addProperty("engine", engine);
		fields.addProperty("width", mc.getWindow().getWidth());
		fields.addProperty("height", mc.getWindow().getHeight());

		BlenderBridgeClient.SERVER.send(nachricht);
		System.out.println("[blenderbridge] RENDER -> addon: ticks " + start + ".." + end
			+ " @ " + fps + " fps = frames " + frameStart + ".." + frameEnd
			+ ", out " + ausgabe.getAbsolutePath());

		/* 3. Auf das RENDER_RESULT warten. Budget: Warmup + ~3s je Frame, laut scheitern. */
		try
		{
			/* Budget engine-bewusst: Cycles braucht ~5s+/frame (gemessen 5.1 warm) plus
			 * kernel-warmup — die Eevee-formel (60+3n) toetete jeden vollen Cycles-film. */
			boolean cycles = "CYCLES".equalsIgnoreCase(engine);
			long budget = cycles ? 180L + frames * 10L : 60L + frames * 3L;
			JsonObject ergebnis = future.get(budget, TimeUnit.SECONDS);
			boolean ok = ergebnis.has("ok") && ergebnis.get("ok").getAsBoolean();

			return "{\"ok\":" + ok + ",\"fahrt\":" + fahrt + ",\"render\":" + ergebnis + "}";
		}
		catch (TimeoutException e)
		{
			return "{\"error\":\"kein RENDER_RESULT im budget — laeuft das addon (tick gepumpt)?\","
				+ "\"requestId\":\"" + requestId + "\"}";
		}
		catch (Exception e)
		{
			return "{\"error\":\"warten fehlgeschlagen: " + e.toString().replace("\"", "'") + "\"}";
		}
		finally
		{
			OFFEN.remove(requestId);
		}
	}

	/** Vom Dispatch gerufen (Fall "RENDER_RESULT" in BlenderBridgeClient). Nie werfen. */
	public static void handleResult(JsonObject message)
	{
		try
		{
			JsonObject fields = BridgeProtocol.getFields(message);
			String requestId = fields.has("requestId") ? fields.get("requestId").getAsString() : "";
			CompletableFuture<JsonObject> future = OFFEN.get(requestId);

			if (future == null)
			{
				System.out.println("[blenderbridge] RENDER_RESULT ohne offenen auftrag (requestId="
					+ requestId + ") — ignoriert");
				return;
			}

			future.complete(fields);
		}
		catch (Throwable t)
		{
			System.out.println("[blenderbridge] RENDER_RESULT unlesbar: " + t);
		}
	}

	private static int zahl(String s, int sonst)
	{
		if (s == null || s.isEmpty())
		{
			return sonst;
		}

		try
		{
			return Integer.parseInt(s.trim());
		}
		catch (NumberFormatException e)
		{
			return sonst;
		}
	}
}
