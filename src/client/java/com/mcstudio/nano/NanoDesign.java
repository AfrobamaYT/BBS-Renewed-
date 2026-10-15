package com.mcstudio.nano;

/**
 * Design-Sprache des UI-Umbaus — Farben und Bausteine aus dem Designsystem
 * (Claude-Design-Projekt „BBS Design System": styles.css + foundations/bausteine).
 *
 * <p>Jede Flaeche zeichnet ueber diese Klasse, damit die Panels nicht auseinanderlaufen.
 * Alle Masse sind <b>GUI-Pixel</b> und werden mit {@code k} multipliziert — die
 * {@link NanoFlaeche} zeichnet in Textur-Pixeln ({@code k = flaeche.skala()}).</p>
 *
 * <p>⛔ Die NanoVG-Schriften heissen <b>{@code sans}</b> und <b>{@code bold}</b>.
 * Ein anderer Name zeichnet lautlos nichts.</p>
 */
public final class NanoDesign
{
	private NanoDesign() {}

	/* ===== Tiefen-Leiter (styles.css) — dunkelste Ebene liegt hinten ===== */
	public static final int NAHT       = 0x0D0D0D;
	public static final int TOPBAR     = 0x161616;
	public static final int STATUSBAR  = 0x191919;
	public static final int MENUE      = 0x181818;
	public static final int EDITOR     = 0x1D1D1D;
	public static final int REGION     = 0x242424;
	public static final int KOPF       = 0x2B2B2B;
	public static final int PANEL      = 0x2F2F2F;
	public static final int PANELKOPF  = 0x353535;
	public static final int WIDGET     = 0x3D3D3D;
	public static final int WIDGET_HI  = 0x4A4A4A;
	public static final int FELD       = 0x505050;
	public static final int FELD_HI    = 0x5B5B5B;

	/* ===== Text ===== */
	public static final int TXT        = 0xE0E0E0;
	public static final int TXT_DIM    = 0x9C9C9C;
	public static final int TXT_OFF    = 0x616161;
	public static final int TXT_MAX    = 0xFFFFFF;

	/* ===== Akzente ===== */
	public static final int BLAU       = 0x4772B3;
	public static final int BLAU_HELL  = 0x7FB0EF;
	public static final int ORANGE     = 0xE8862D;
	public static final int BERNSTEIN  = 0xFFC24D;
	public static final int ROT        = 0xE5484D;
	public static final int GRUEN      = 0x7CBF6B;
	public static final int ACHSE_X    = 0xE25A4E;
	public static final int ACHSE_Y    = 0x75B84E;
	public static final int ACHSE_Z    = 0x4A8CD8;

	/* ===== Kanten und Auswahl (mit Alpha) ===== */
	public static final int LINIE      = 0x303030;
	public static final int A_AUSWAHL  = 0x614772B3;  /* rgba(71,114,179,.38) */
	public static final int A_ZEILE    = 0x1F4772B3;  /* rgba(71,114,179,.12) — Spur gewaehlt */
	public static final int HOVER      = 0x2A2A2A;

	/* ===== Masse in GUI-Pixeln ===== */
	public static final float H_TOPBAR   = 34F;
	public static final float H_KOPF     = 26F;
	public static final float H_ZEILE    = 22F;
	public static final float H_WIDGET   = 20F;
	public static final float H_STATUS   = 22F;
	public static final float R_WIDGET   = 4F;
	public static final float R_PANEL    = 6F;
	public static final float R_AREA     = 7F;

	/** Schrift-Kennungen der NanoVG-Atlanten. */
	public static final String SANS = "sans";
	public static final String BOLD = "bold";

	/* ===================== Bausteine ===================== */

	/**
	 * Flaechen-Kopf mit Editor-Typ-Waehler links. Gibt das x zurueck, ab dem Inhalt folgen darf.
	 */

	/** Ist die Flaeche zu schmal fuer Beiwerk? Dann nur das Noetigste zeichnen. */
	public static boolean eng(float b, float k)
	{
		/* 120 GUI-px: darunter reicht es nicht mal fuer ein schmales Feld. Die urspruenglichen
		   190 waren zu hoch — die Outliner-Flaeche ist im Werks-Layout ~130 breit, das Suchfeld
		   waere nie erschienen. */
		return b < 120F * k;
	}


	/** Text auf die verfuegbare Breite kuerzen (Noto Sans ~0.52 em je Zeichen). */
	public static String kuerzen(String text, float platz, float groesse)
	{
		int passt = (int) (platz / (groesse * 0.52F));

		if (passt >= text.length() || passt < 2)
		{
			return text;
		}

		return text.substring(0, Math.max(1, passt - 1)) + "…";
	}

	/** Grobe Textbreite in Pixeln. */
	public static float breite(String text, float groesse)
	{
		return text.length() * groesse * 0.52F;
	}

}
