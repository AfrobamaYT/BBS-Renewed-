package com.mcstudio.nano;

import java.util.ArrayList;
import java.util.List;

/**
 * NanoZonen — die Klickflaechen einer {@link NanoFlaeche}: waehrend des Zeichnens gemeldet,
 * danach fuer Hover und Klick getroffen. Doppelt gepuffert (imBau -> aktiv beim Uebernehmen),
 * damit ein halber Zeichenlauf nie halbe Zonen hinterlaesst. Render-Thread only.
 */
final class NanoZonen
{
	record Zone(String id, float x, float y, float w, float h, Runnable aktion) {}

	private final List<Zone> aktiv = new ArrayList<>();
	private final List<Zone> imBau = new ArrayList<>();

	void bauBeginnen()
	{
		this.imBau.clear();
	}

	void melden(String id, float x, float y, float w, float h, Runnable aktion)
	{
		this.imBau.add(new Zone(id, x, y, w, h, aktion));
	}

	void uebernehmen()
	{
		this.aktiv.clear();
		this.aktiv.addAll(this.imBau);
	}

	/** Oberste Zone unter (mx,my) — zuletzt gemeldete liegt obenauf. null = keine. */
	Zone treffe(float mx, float my)
	{
		if (mx < 0)
		{
			return null;
		}

		for (int i = this.aktiv.size() - 1; i >= 0; i--)
		{
			Zone zone = this.aktiv.get(i);

			if (mx >= zone.x() && mx <= zone.x() + zone.w() && my >= zone.y() && my <= zone.y() + zone.h())
			{
				return zone;
			}
		}

		return null;
	}
}
