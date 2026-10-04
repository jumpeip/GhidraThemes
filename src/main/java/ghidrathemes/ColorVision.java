/* ###
 * GhidraThemes - theme switcher with colorblind-friendly themes for Ghidra.
 */
package ghidrathemes;

import java.awt.Color;

/**
 * The color-vision adjustments offered by the colorblind themes.
 * <p>
 * The red, green and blue variants use <em>daltonization</em>: each color is run through a
 * simulation of the color-vision deficiency (Machado, Oliveira &amp; Fernandes, 2009, severity
 * 1.0), the information that would be lost is computed as the difference between the original
 * and simulated color, and that difference is shifted into channels the viewer can still
 * distinguish. The result is then rescaled to the original luminance so contrast against the
 * background is preserved. Neutral colors (whites, grays, blacks) come through unchanged, so only the
 * colors that carry meaning (syntax highlighting, markers, errors, diffs, ...) move.
 * <p>
 * The gray variant converts every color to its perceived luminance, for achromatopsia or for
 * anyone who prefers a monochrome workspace.
 * <p>
 * All math is done in linear RGB; alpha is preserved.
 */
public enum ColorVision {

	/** Red-blind (L-cone) color blindness. */
	PROTANOPIA("Red", "Protanopia", new double[][] {
		{ 0.152286, 1.052583, -0.204868 },
		{ 0.114503, 0.786281, 0.099216 },
		{ -0.003882, -0.048116, 1.051998 } }, ErrorShift.RED_GREEN),

	/** Green-blind (M-cone) color blindness. */
	DEUTERANOPIA("Green", "Deuteranopia", new double[][] {
		{ 0.367322, 0.860646, -0.227968 },
		{ 0.280085, 0.672501, 0.047413 },
		{ -0.011820, 0.042940, 0.968881 } }, ErrorShift.RED_GREEN),

	/** Blue-blind (S-cone) color blindness. */
	TRITANOPIA("Blue", "Tritanopia", new double[][] {
		{ 1.255528, -0.076749, -0.178779 },
		{ -0.078411, 0.930809, 0.147602 },
		{ 0.004733, 0.691367, 0.303900 } }, ErrorShift.BLUE_YELLOW),

	/** Luminance-only (grayscale) rendering. */
	GRAYSCALE("Gray", "Grayscale", null, null);

	/** Where the "lost" color information is redistributed. */
	private enum ErrorShift {
		/** Push red/green error into green and blue, which protans and deutans can see. */
		RED_GREEN(new double[][] {
			{ 0.0, 0.0, 0.0 },
			{ 0.7, 1.0, 0.0 },
			{ 0.7, 0.0, 1.0 } }),
		/** Push blue/yellow error into red and green, which tritans can see. */
		BLUE_YELLOW(new double[][] {
			{ 1.0, 0.0, 0.7 },
			{ 0.0, 1.0, 0.7 },
			{ 0.0, 0.0, 0.0 } });

		final double[][] matrix;

		ErrorShift(double[][] matrix) {
			this.matrix = matrix;
		}
	}

	private static final double[] SRGB_TO_LINEAR = new double[256];
	static {
		for (int i = 0; i < 256; i++) {
			double c = i / 255.0;
			SRGB_TO_LINEAR[i] =
				c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
		}
	}

	private final String shortName;
	private final String scientificName;
	private final double[][] simulation;
	private final ErrorShift shift;

	ColorVision(String shortName, String scientificName, double[][] simulation,
			ErrorShift shift) {
		this.shortName = shortName;
		this.scientificName = scientificName;
		this.simulation = simulation;
		this.shift = shift;
	}

	/** @return a short label such as "Red" */
	public String getShortName() {
		return shortName;
	}

	/** @return the clinical name such as "Protanopia" */
	public String getScientificName() {
		return scientificName;
	}

	/** @return the label used in menus, e.g. "Red (Protanopia)" */
	public String getMenuLabel() {
		return this == GRAYSCALE ? "Gray (Grayscale)"
				: shortName + " (" + scientificName + ")";
	}

	/**
	 * Returns the adjusted version of the given color for this kind of color vision.
	 * @param color the original color
	 * @return the adjusted color (a new instance), or the same instance when nothing changes
	 */
	public Color transform(Color color) {
		if (color == null) {
			return null;
		}
		double r = SRGB_TO_LINEAR[color.getRed()];
		double g = SRGB_TO_LINEAR[color.getGreen()];
		double b = SRGB_TO_LINEAR[color.getBlue()];

		double[] out;
		if (this == GRAYSCALE) {
			double y = luminance(r, g, b);
			out = new double[] { y, y, y };
		}
		else {
			double[] lin = { r, g, b };
			double[] sim = multiply(simulation, lin);
			double[] err = { r - sim[0], g - sim[1], b - sim[2] };
			double[] corr = multiply(shift.matrix, err);
			out = new double[] { clamp(r + corr[0]), clamp(g + corr[1]), clamp(b + corr[2]) };

			// Keep the original brightness so text keeps its contrast against the
			// background (plain daltonization tends to wash reds out to light pink).
			double originalY = luminance(r, g, b);
			double newY = luminance(out[0], out[1], out[2]);
			if (newY > 1e-6) {
				double scale = originalY / newY;
				out[0] = clamp(out[0] * scale);
				out[1] = clamp(out[1] * scale);
				out[2] = clamp(out[2] * scale);
			}
		}

		int nr = toSrgb(out[0]);
		int ng = toSrgb(out[1]);
		int nb = toSrgb(out[2]);
		if (nr == color.getRed() && ng == color.getGreen() && nb == color.getBlue()) {
			return color;
		}
		return new Color(nr, ng, nb, color.getAlpha());
	}

	private static double[] multiply(double[][] m, double[] v) {
		return new double[] {
			m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
			m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
			m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2] };
	}

	private static double luminance(double r, double g, double b) {
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	}

	private static double clamp(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}

	private static int toSrgb(double linear) {
		double c = Math.max(0.0, Math.min(1.0, linear));
		double s = c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
		return (int) Math.round(Math.max(0.0, Math.min(1.0, s)) * 255.0);
	}
}
