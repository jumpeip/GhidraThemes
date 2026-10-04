/* ###
 * GhidraThemes - theme switcher with colorblind-friendly themes for Ghidra.
 */
package ghidrathemes;

import java.awt.Color;

import generic.theme.*;
import ghidra.util.Msg;

/**
 * Base class for the colorblind themes.
 * <p>
 * Each concrete subclass is a {@link DiscoverableGTheme}, so Ghidra finds it through the class
 * searcher (the class name must end in "Theme"). That has two useful side effects: the themes
 * also show up in Ghidra's own <i>Edit &rarr; Theme &rarr; Switch...</i> dialog, and the
 * choice survives a restart because Ghidra stores the theme's class name in its preferences
 * and re-creates it on startup.
 * <p>
 * When constructed, the theme takes every directly-defined color from Ghidra's application
 * defaults (all the {@code *.theme.properties} files, including those of other installed
 * extensions), runs it through the {@link ColorVision} transform and stores the colors that
 * actually changed. Colors defined as references to other ids are left alone, so they
 * automatically follow their (transformed) targets.
 */
public abstract class AbstractColorVisionTheme extends DiscoverableGTheme {

	private static final String SELECTED_BG = SystemThemeIds.BG_VIEW_SELECTED_ID;
	private static final String SELECTED_FG = SystemThemeIds.FG_VIEW_SELECTED_ID;

	private final ColorVision vision;
	private final boolean dark;

	@SuppressWarnings("this-escape") // populate() only uses fields set above
	protected AbstractColorVisionTheme(ColorVision vision, boolean dark) {
		super(buildName(vision, dark), dark ? LafType.FLAT_DARK : LafType.FLAT_LIGHT);
		this.vision = vision;
		this.dark = dark;
		populate();
	}

	/**
	 * Builds the display name for a colorblind theme.
	 * @param vision the color vision type
	 * @param dark true for the dark-background variant
	 * @return the theme name, e.g. "Colorblind Dark - Red (Protanopia)"
	 */
	public static String buildName(ColorVision vision, boolean dark) {
		return (dark ? "Colorblind Dark - " : "Colorblind Light - ") + vision.getMenuLabel();
	}

	/** @return the color vision adjustment this theme applies */
	public ColorVision getVision() {
		return vision;
	}

	/** @return true if this theme uses the dark look and feel and dark defaults */
	public boolean isDark() {
		return dark;
	}

	private void populate() {
		ThemeManager themeManager = ThemeManager.getInstance();
		if (themeManager == null) {
			return; // no theming (e.g. some headless/test contexts)
		}

		GThemeValueMap defaults;
		try {
			defaults = dark ? themeManager.getApplicationDarkDefaults()
					: themeManager.getApplicationLightDefaults();
		}
		catch (RuntimeException e) {
			Msg.debug(AbstractColorVisionTheme.class,
				"Could not read theme defaults for " + getName(), e);
			return;
		}

		int changed = 0;
		for (ColorValue value : defaults.getColors()) {
			if (value.isIndirect()) {
				continue; // references follow whatever they point to
			}
			Color original = value.getRawValue();
			if (original == null) {
				continue;
			}
			Color adjusted = vision.transform(original);
			if (adjusted != original) {
				setColor(value.getId(), adjusted);
				changed++;
			}
		}

		if (vision == ColorVision.GRAYSCALE) {
			// The Look and Feel's selection highlight is blue and is not part of the
			// application defaults, so neutralize it explicitly. Swing's own selection colors
			// reference these system ids, so this reaches tables, trees, lists, etc.
			setColor(SELECTED_BG,
				dark ? new Color(0x5C, 0x5C, 0x5C) : new Color(0xB8, 0xB8, 0xB8));
			setColor(SELECTED_FG, dark ? Color.WHITE : Color.BLACK);
		}

		Msg.trace(AbstractColorVisionTheme.class, getName() + ": adjusted " + changed + " colors");
	}
}
