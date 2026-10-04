# GhidraThemes

A Ghidra 12.1.4 extension that adds a **Themes** menu for switching themes in one click,
including colorblind-friendly themes.

```
Themes
  Dark Theme              (Ghidra's Flat Dark)
  Light Theme             (Ghidra's Flat Light)
  Default Theme           (the OS default: Windows on Windows, Mac on macOS, Flat Light elsewhere)
  ----------------
  Colorblind  >  Red (Protanopia)
                 Green (Deuteranopia)
                 Blue (Tritanopia)
                 Gray (Grayscale)
                 ----------------
                 Use Dark Background
```

The active theme has a check mark. Picking a theme saves it to Ghidra's preferences, so it
is still active after a restart.

## How the colorblind themes work

Each colorblind theme starts from Ghidra's own color defaults (every `*.theme.properties`
file, including those of other installed extensions) and adjusts every color that carries
meaning (syntax highlighting, markers, errors, diffs, and so on):

* **Red / Green / Blue**: daltonization. Each color is run through a simulation of the
  deficiency (Machado et al. 2009). The information the viewer would lose is moved into
  channels they can still see, and the color is then rescaled to its original brightness so
  text keeps its contrast. Whites, grays and blacks don't change.
* **Gray**: every color becomes its perceived luminance. The look-and-feel selection
  highlight is also neutralized.

Colorblind themes use Flat Light by default. Tick **Use Dark Background** to switch them to
Flat Dark. If a colorblind theme is already active, it switches immediately.

The colorblind themes are real Ghidra themes, so they also appear in
*Edit → Theme → Switch…*. You can fine-tune them there with *Edit → Theme → Configure* and
save the result as your own theme.

## Build

Requires JDK 21 and Gradle.

```bat
set GHIDRA_INSTALL_DIR=D:\ghidra_12.1.4_PUBLIC
D:\gradle-9.5.0\bin\gradle
```

or `gradle -PGHIDRA_INSTALL_DIR=D:\ghidra_12.1.4_PUBLIC`. Gradle writes the installable zip
to `dist\`.

## Install

1. In Ghidra's project window: **File → Install Extensions…**, click **+**, and choose the
   zip from `dist\`.
2. Restart Ghidra.
3. Enable the plugin in the tools where you want the menu:
   * **Project window**: **File → Configure…**, then check **GhidraThemesPlugin** (it is
     listed under *Miscellaneous*).
   * **CodeBrowser**: Ghidra offers to configure the new plugin the first time you open the
     tool. You can also go to **File → Configure → Miscellaneous** and check
     **GhidraThemesPlugin**.

Themes apply to the whole application, so enabling the plugin in one tool is enough. If you
enable it in several tools, all their menus stay in sync.

## Source layout

| File | Purpose |
|------|---------|
| `GhidraThemesPlugin.java` | Builds the menu, switches themes, and keeps the check marks in sync |
| `ColorVision.java` | The color math for protanopia, deuteranopia, tritanopia and grayscale |
| `AbstractColorVisionTheme.java` | Base theme that builds its color set from Ghidra's defaults |
| `*LightTheme.java` / `*DarkTheme.java` | The eight concrete themes, found by Ghidra's class searcher |
