package mchorse.bbs_mod;

import mchorse.bbs_mod.data.types.MapType;

/**
 * Wandert alte Schluessel der bbs.json in ihre heutigen Kategorien. Unveraendert aus
 * {@link BBSSettings} herausgeloest — die Datei stand ueber der 800-Zeilen-Grenze und darf
 * nur noch schrumpfen. Einziger Aufrufer: {@code SettingsManager.load}.
 */
public final class BBSSettingsMigration
{
	private BBSSettingsMigration()
	{}

	public static boolean migrateLegacySettings(MapType root)
	{
		MapType appearance = root.getMap("appearance");
		MapType personalization = root.getMap("personalization");
		boolean migrated = false;

		migrated |= migrateLegacyValue(appearance, personalization, "primary_color");
		migrated |= migrateLegacyValue(appearance, personalization, "tooltip_style", "theme");
		migrated |= migrateLegacyValue(appearance, personalization, "track_width");
		migrated |= migrateLegacyValue(appearance, personalization, "keyframe_default_shape");

		if (migrated)
		{
			root.put("personalization", personalization);
		}

		/* One count for both overlay kinds became two (BBS FS 8ce6f0238); both start from the old one. */
		MapType recording = root.getMap("recording");
		boolean overlays = migrateLegacyValue(recording, recording, "pose_transform_overlays", "pose_overlays");

		overlays |= migrateLegacyValue(recording, recording, "pose_transform_overlays", "transform_overlays");

		if (overlays)
		{
			root.put("recording", recording);
			migrated = true;
		}

		return migrated;
	}

	private static boolean migrateLegacyValue(MapType oldCategory, MapType newCategory, String key)
	{
		return migrateLegacyValue(oldCategory, newCategory, key, key);
	}

	private static boolean migrateLegacyValue(MapType oldCategory, MapType newCategory, String oldKey, String newKey)
	{
		if (newCategory.has(newKey) || !oldCategory.has(oldKey))
		{
			return false;
		}

		newCategory.put(newKey, oldCategory.get(oldKey).copy());

		return true;
	}
}
