/* ###
 * GhidraThemes - theme switcher with colorblind-friendly themes for Ghidra.
 */
package ghidrathemes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import docking.ActionContext;
import docking.action.MenuData;
import docking.action.ToggleDockingAction;
import docking.theme.gui.ThemeUtils;
import generic.theme.*;
import generic.theme.builtin.FlatDarkTheme;
import generic.theme.builtin.FlatLightTheme;
import ghidra.MiscellaneousPluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.framework.main.ApplicationLevelPlugin;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.util.Msg;
import ghidra.util.Swing;

/**
 * Adds a top-level <b>Themes</b> menu:
 * <pre>
 * Themes
 *   Dark Theme
 *   Light Theme
 *   Default Theme
 *   ---------------
 *   Colorblind  >  Red (Protanopia)
 *                  Green (Deuteranopia)
 *                  Blue (Tritanopia)
 *                  Gray (Grayscale)
 *                  ---------------
 *                  Use Dark Background
 * </pre>
 * The menu item for the active theme is checked. Because this is an
 * {@link ApplicationLevelPlugin} it can be added to the project window (front end) as well as
 * to CodeBrowser or any other tool; every copy stays in sync through a {@link ThemeListener}.
 */
//@formatter:off
@PluginInfo(
	status = PluginStatus.RELEASED,
	packageName = MiscellaneousPluginPackage.NAME,
	category = PluginCategoryNames.COMMON,
	shortDescription = "Theme picker with colorblind-friendly themes",
	description = "Adds a Themes menu for switching between the Dark, Light and Default " +
		"themes, and colorblind-friendly themes for red (protanopia), green (deuteranopia) " +
		"and blue (tritanopia) color blindness, plus a grayscale theme. Colorblind themes " +
		"can use a light or dark background."
)
//@formatter:on
public class GhidraThemesPlugin extends Plugin implements ApplicationLevelPlugin {

	static final String MENU = "Themes";
	static final String COLORBLIND_MENU = "Colorblind";

	private static final String GROUP_BASE = "1_base";
	private static final String GROUP_COLORBLIND = "2_colorblind";
	private static final String GROUP_VISION = "1_vision";
	private static final String GROUP_OPTIONS = "2_options";

	/** A menu entry paired with the test that says whether it is the active theme. */
	private record Choice(ToggleDockingAction action, BooleanSupplier isActive) {
	}

	private final List<Choice> choices = new ArrayList<>();
	private final ThemeListener themeListener = e -> Swing.runLater(this::refreshSelection);
	private ToggleDockingAction darkBackgroundAction;

	public GhidraThemesPlugin(PluginTool tool) {
		super(tool);
	}

	@Override
	protected void init() {
		super.init();
		createActions();
		Gui.addThemeListener(themeListener);
		refreshSelection();
	}

	@Override
	protected void dispose() {
		Gui.removeThemeListener(themeListener);
		super.dispose();
	}

	// ------------------------------------------------------------------ actions

	private void createActions() {
		addChoice("Dark Theme", new String[] { MENU, "Dark Theme" }, GROUP_BASE, "a", null,
			"Switch to Ghidra's Flat Dark theme",
			() -> applyTheme(FlatDarkTheme::new),
			() -> activeTheme() instanceof FlatDarkTheme);

		addChoice("Light Theme", new String[] { MENU, "Light Theme" }, GROUP_BASE, "b", null,
			"Switch to Ghidra's Flat Light theme",
			() -> applyTheme(FlatLightTheme::new),
			() -> activeTheme() instanceof FlatLightTheme);

		addChoice("Default Theme", new String[] { MENU, "Default Theme" }, GROUP_BASE, "c", null,
			"Switch to the default theme for this operating system",
			() -> applyTheme(ThemeManager::getDefaultTheme),
			() -> {
				GTheme active = activeTheme();
				return active != null &&
					active.getClass() == ThemeManager.getDefaultTheme().getClass();
			});

		String sub = "a";
		for (ColorVision vision : ColorVision.values()) {
			addChoice("Colorblind " + vision.getShortName(),
				new String[] { MENU, COLORBLIND_MENU, vision.getMenuLabel() },
				GROUP_VISION, sub, GROUP_COLORBLIND,
				describe(vision),
				() -> applyTheme(() -> createColorVisionTheme(vision, isDarkBackground())),
				() -> activeTheme() instanceof AbstractColorVisionTheme t &&
					t.getVision() == vision);
			sub = Character.toString((char) (sub.charAt(0) + 1));
		}

		darkBackgroundAction = new ToggleDockingAction("Colorblind Dark Background", getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				// The menu item has already flipped isSelected(). If a colorblind theme is
				// active, switch it to the matching light/dark variant right away.
				if (activeTheme() instanceof AbstractColorVisionTheme t) {
					applyTheme(() -> createColorVisionTheme(t.getVision(), isSelected()));
				}
			}
		};
		MenuData darkMenu = new MenuData(
			new String[] { MENU, COLORBLIND_MENU, "Use Dark Background" }, null, GROUP_OPTIONS);
		darkMenu.setParentMenuGroup(GROUP_COLORBLIND);
		darkBackgroundAction.setMenuBarData(darkMenu);
		darkBackgroundAction.setDescription(
			"Use a dark background (Flat Dark) instead of a light one for the colorblind themes");
		darkBackgroundAction.setSelected(Gui.isDarkTheme());
		darkBackgroundAction.markHelpUnnecessary();
		tool.addAction(darkBackgroundAction);
	}

	private void addChoice(String name, String[] menuPath, String group, String subGroup,
			String parentGroup, String description, Runnable onSelect,
			BooleanSupplier isActive) {

		ToggleDockingAction action = new ToggleDockingAction("Theme: " + name, getName()) {
			@Override
			public void actionPerformed(ActionContext context) {
				onSelect.run();
			}
		};
		MenuData menuData = new MenuData(menuPath, null, group);
		menuData.setMenuSubGroup(subGroup);
		if (parentGroup != null) {
			menuData.setParentMenuGroup(parentGroup);
		}
		action.setMenuBarData(menuData);
		action.setDescription(description);
		action.markHelpUnnecessary();
		tool.addAction(action);
		choices.add(new Choice(action, isActive));
	}

	private static String describe(ColorVision vision) {
		return switch (vision) {
			case PROTANOPIA -> "Colors adjusted for red-blind (protanopia) color vision";
			case DEUTERANOPIA -> "Colors adjusted for green-blind (deuteranopia) color vision";
			case TRITANOPIA -> "Colors adjusted for blue-blind (tritanopia) color vision";
			case GRAYSCALE -> "All colors converted to grayscale by perceived brightness";
		};
	}

	// ------------------------------------------------------------------ theme switching

	private boolean isDarkBackground() {
		return darkBackgroundAction != null && darkBackgroundAction.isSelected();
	}

	private static GTheme activeTheme() {
		ThemeManager themeManager = ThemeManager.getInstance();
		return themeManager == null ? null : themeManager.getActiveTheme();
	}

	/**
	 * Creates a colorblind theme instance for the given vision type and background.
	 * @param vision the color vision type
	 * @param dark true for the dark-background variant
	 * @return a new theme instance
	 */
	static AbstractColorVisionTheme createColorVisionTheme(ColorVision vision, boolean dark) {
		return switch (vision) {
			case PROTANOPIA -> dark ? new ProtanopiaDarkTheme() : new ProtanopiaLightTheme();
			case DEUTERANOPIA -> dark ? new DeuteranopiaDarkTheme()
					: new DeuteranopiaLightTheme();
			case TRITANOPIA -> dark ? new TritanopiaDarkTheme() : new TritanopiaLightTheme();
			case GRAYSCALE -> dark ? new GrayscaleDarkTheme() : new GrayscaleLightTheme();
		};
	}

	private void applyTheme(Supplier<GTheme> themeSupplier) {
		ThemeManager themeManager = ThemeManager.getInstance();
		if (themeManager == null) {
			return;
		}
		try {
			// Give the user a chance to save unsaved edits made in the Theme Editor.
			if (!ThemeUtils.askToSaveThemeChanges(themeManager)) {
				return;
			}
			GTheme theme = themeSupplier.get();
			if (!theme.hasSupportedLookAndFeel()) {
				Msg.showWarn(this, null, "Theme Not Supported",
					"\"" + theme.getName() + "\" uses a Look and Feel (" +
						theme.getLookAndFeelType().getName() +
						") that is not supported on this platform.");
				return;
			}
			if (!theme.equals(themeManager.getActiveTheme())) {
				themeManager.setTheme(theme);
			}
		}
		catch (Exception e) {
			Msg.showError(this, null, "Theme Error", "Could not switch theme: " + e.getMessage(),
				e);
		}
		finally {
			refreshSelection();
		}
	}

	/** Puts the check mark on the active theme's menu item (and only that one). */
	private void refreshSelection() {
		for (Choice choice : choices) {
			boolean active;
			try {
				active = choice.isActive().getAsBoolean();
			}
			catch (RuntimeException e) {
				active = false;
			}
			choice.action().setSelected(active);
		}
		if (darkBackgroundAction != null &&
			activeTheme() instanceof AbstractColorVisionTheme t) {
			darkBackgroundAction.setSelected(t.isDark());
		}
	}
}
