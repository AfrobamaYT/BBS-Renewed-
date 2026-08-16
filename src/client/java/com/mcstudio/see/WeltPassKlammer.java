package com.mcstudio.see;

import com.mcstudio.util.RenderThread;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


final class WeltPassKlammer
{
	private final UIFilmPanel panel;
	private final List<Replay> versteckt = new ArrayList<>();
	private int povVorher;

	WeltPassKlammer(UIFilmPanel panel)
	{
		this.panel = panel;
	}

	/** Vorbedingungsfehler (Besitz-Guard) oder null. Vom Aufrufer-Thread rufen, NIE Render-Thread. */
	static String besitzGuard()
	{
		{
			return "{\"error\":\"Blender faehrt die BBS-kamera (Drive BBS camera an) — erst freigeben, "
				+ "sonst zeigt der sweep die Blender-pose statt der film-fahrt\"}";
		}

		return null;
	}

	/** Herrichten (auf dem Render-Thread via submit): POV merken+setzen, Blender-Replays verstecken. */
	void anwenden()
	{
		RenderThread.submit(() ->
		{
			this.povVorher = this.panel.getController().getPovMode();
			this.panel.getController().setPov(UIFilmController.CAMERA_MODE_CAMERA);

			for (Replay replay : this.panel.getData().replays.getList())
			{
				if (!replay.enabled.get())
				{
					continue;
				}

				if (!(replay.form.get() instanceof ModelForm modelForm))
				{
					continue;
				}

				File bobj = mchorse.bbs_mod.BBSMod.getAssetsPath("models/" + modelForm.model.get() + "/model.bobj");

				if (bobj.isFile())
				{
					replay.enabled.set(false);
					this.versteckt.add(replay);
				}
			}

			if (!this.versteckt.isEmpty())
			{
				this.panel.getController().createEntities();
			}

			return "";
		});
	}

	/** Wiederherstellen (finally-Pfad): Replays an, POV zurueck, Cursor zurueck. Wirft nie. */
	void zuruecknehmen(int cursor)
	{
		try
		{
			RenderThread.submit(() ->
			{
				for (Replay replay : this.versteckt)
				{
					replay.enabled.set(true);
				}

				if (!this.versteckt.isEmpty())
				{
					this.panel.getController().createEntities();
				}

				this.panel.getController().setPov(this.povVorher);
				this.panel.setCursor(cursor);

				return "";
			});
		}
		catch (Throwable ignored)
		{
		}
	}
}
