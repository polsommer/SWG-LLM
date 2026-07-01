// color.java
package script;

import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Binary-compatible upgrade of the legacy color:
 * - Keeps ctor color(int r,int g,int b,int a), getters, and BLACK/BLUE/GREEN/RED/WHITE constants.
 * - Adds robust utilities for parsing, packing, blending, and color math.
 */
public class color {

	// ===== Legacy constants (unchanged) =====
	public static final color BLACK = new color(  0,   0,   0, 255);
	public static final color BLUE  = new color(  0,   0, 255, 255);
	public static final color GREEN = new color(  0, 255,   0, 255);
	public static final color RED   = new color(255,   0,   0, 255);
	public static final color WHITE = new color(255, 255, 255, 255);

	// ===== Extra handy presets =====
	public static final color TRANSPARENT = new color(0, 0, 0, 0);
	public static final color CYAN        = new color(0, 255, 255, 255);
	public static final color MAGENTA     = new color(255, 0, 255, 255);
	public static final color YELLOW      = new color(255, 255, 0, 255);
	public static final color ORANGE      = new color(255, 128, 0, 255);
	public static final color PURPLE      = new color(128, 0, 128, 255);
	public static final color GRAY        = new color(128, 128, 128, 255);
	public static final color LIGHT_GRAY  = new color(192, 192, 192, 255);
	public static final color DARK_GRAY   = new color(64, 64, 64, 255);

	// ===== Getters (legacy) =====
	public int getR() { return m_r; }
	public int getG() { return m_g; }
	public int getB() { return m_b; }
	public int getA() { return m_a; }

	// ===== Legacy ctor (kept) + fixed range check =====
	public color(int r, int g, int b, int a) {
		if (!inByte(r) || !inByte(g) || !inByte(b) || !inByte(a)) {
			throw new IllegalArgumentException("color value arg out of valid range 0..255");
		}
		m_r = r; m_g = g; m_b = b; m_a = a;
	}

	// ===== Additional factories =====
	/** Construct from normalized floats [0..1]. Values are clamped. */
	public static color fromFloats(double r, double g, double b, double a) {
		return new color(toByte(r), toByte(g), toByte(b), toByte(a));
	}

	/** Parse "#RRGGBB" or "#AARRGGBB" (case-insensitive). */
	public static color fromHex(String hex) {
		if (hex == null) throw new IllegalArgumentException("hex is null");
		String s = hex.trim();
		if (s.startsWith("#")) s = s.substring(1);
		if (s.length() == 6) {
			int rgb = (int)Long.parseLong(s, 16);
			return fromRGB(rgb | 0xFF000000);
		} else if (s.length() == 8) {
			int argb = (int)Long.parseLong(s, 16);
			return fromARGB(argb);
		}
		throw new IllegalArgumentException("Unsupported hex format: " + hex);
	}

	/** From packed ARGB int: [31..24]=A, [23..16]=R, [15..8]=G, [7..0]=B. */
	public static color fromARGB(int argb) {
		return new color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
	}

	/** From packed RGBA int: [31..24]=R, [23..16]=G, [15..8]=B, [7..0]=A. */
	public static color fromRGB(int rgb) {
		return new color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
	}

	/** From packed ABGR int (useful for some native textures): [31..24]=A,[23..16]=B,[15..8]=G,[7..0]=R. */
	public static color fromABGR(int abgr) {
		return new color(abgr & 0xFF, (abgr >> 8) & 0xFF, (abgr >> 16) & 0xFF, (abgr >>> 24) & 0xFF);
	}

	// ===== Packing =====
	public int toARGB() { return ((m_a & 0xFF) << 24) | ((m_r & 0xFF) << 16) | ((m_g & 0xFF) << 8) | (m_b & 0xFF); }
	public int toRGBA() { return ((m_r & 0xFF) << 24) | ((m_g & 0xFF) << 16) | ((m_b & 0xFF) << 8) | (m_a & 0xFF); }
	public int toABGR() { return ((m_a & 0xFF) << 24) | ((m_b & 0xFF) << 16) | ((m_g & 0xFF) << 8) | (m_r & 0xFF); }

	public String toHexRGB()  { return String.format(Locale.ROOT, "#%02X%02X%02X", m_r, m_g, m_b); }
	public String toHexARGB() { return String.format(Locale.ROOT, "#%02X%02X%02X%02X", m_a, m_r, m_g, m_b); }

	// ===== Alpha & mutation-friendly helpers (return new instances) =====
	public boolean isOpaque()      { return m_a == 255; }
	public boolean isTransparent() { return m_a == 0; }

	public color withR(int r)      { return new color(clamp8(r), m_g, m_b, m_a); }
	public color withG(int g)      { return new color(m_r, clamp8(g), m_b, m_a); }
	public color withB(int b)      { return new color(m_r, m_g, clamp8(b), m_a); }
	public color withA(int a)      { return new color(m_r, m_g, m_b, clamp8(a)); }
	public color withOpacity(double a01){ return withA(toByte(a01)); }

	// ===== Blending (gamma-agnostic; for UI/markers this is fine) =====
	/** Porter-Duff "over": this over bg. */
	public color over(color bg) {
		Objects.requireNonNull(bg, "bg");
		double fa = m_a / 255.0;
		double ba = bg.m_a / 255.0;
		double outA = fa + ba * (1 - fa);
		if (outA <= 0) return TRANSPARENT;
		int r = (int)Math.round((m_r * fa + bg.m_r * ba * (1 - fa)) / outA);
		int g = (int)Math.round((m_g * fa + bg.m_g * ba * (1 - fa)) / outA);
		int b = (int)Math.round((m_b * fa + bg.m_b * ba * (1 - fa)) / outA);
		int a = (int)Math.round(outA * 255);
		return new color(clamp8(r), clamp8(g), clamp8(b), clamp8(a));
		// For physically-correct blending, convert to linear with srgb8ToLinear(), blend, then back.
	}

	/** Multiply blend mode (this * src). Alpha is composited "over". */
	public color multiply(color src) {
		color rgb = new color((m_r * src.m_r) / 255, (m_g * src.m_g) / 255, (m_b * src.m_b) / 255, m_a);
		return rgb.over(src.withA( (int)Math.max(m_a, src.m_a) )); // keep reasonable alpha
	}

	/** Screen blend mode (1 - (1-a)*(1-b)). */
	public color screen(color src) {
		int r = 255 - ((255 - m_r) * (255 - src.m_r) / 255);
		int g = 255 - ((255 - m_g) * (255 - src.m_g) / 255);
		int b = 255 - ((255 - m_b) * (255 - src.m_b) / 255);
		return new color(r,g,b, Math.max(m_a, src.m_a));
	}

	/** Overlay blend mode. */
	public color overlay(color src) {
		int r = overlayChan(m_r, src.m_r);
		int g = overlayChan(m_g, src.m_g);
		int b = overlayChan(m_b, src.m_b);
		return new color(r,g,b, Math.max(m_a, src.m_a));
	}
	private static int overlayChan(int a, int b) {
		return (a < 128) ? (a * b / 128) : (255 - ((255 - a) * (255 - b) / 128));
	}

	// ===== HSL/HSV helpers =====
	public color lighten(double pct){ return adjustHSL(0, 0, +pct); }   // +lightness
	public color darken(double pct) { return adjustHSL(0, 0, -pct); }   // -lightness
	public color saturate(double pct){ return adjustHSL(0, +pct, 0); }  // +saturation
	public color desaturate(double pct){ return adjustHSL(0, -pct, 0); }
	public color rotateHue(double degrees){ return adjustHSL(degrees, 0, 0); }
	public color grayscale(){ return desaturate(1.0); }
	public color invert(){ return new color(255 - m_r, 255 - m_g, 255 - m_b, m_a); }

	private color adjustHSL(double dHue, double dSat, double dLight) {
		double[] hsl = rgbToHsl(m_r, m_g, m_b);
		double h = (hsl[0] + dHue) % 360.0; if (h < 0) h += 360.0;
		double s = clamp01(hsl[1] + dSat);
		double l = clamp01(hsl[2] + dLight);
		int[] rgb = hslToRgb(h, s, l);
		return new color(rgb[0], rgb[1], rgb[2], m_a);
	}

	/** Linear sRGB luminance (gamma-correct), WCAG 2.1 */
	public double luminance() {
		double r = srgb8ToLinear(m_r);
		double g = srgb8ToLinear(m_g);
		double b = srgb8ToLinear(m_b);
		return 0.2126 * r + 0.7152 * g + 0.0722 * b;
	}

	/** WCAG contrast ratio vs other (>= 1.0). */
	public double contrastRatio(color other) {
		double l1 = this.luminance();
		double l2 = other.luminance();
		double hi = Math.max(l1, l2);
		double lo = Math.min(l1, l2);
		return (hi + 0.05) / (lo + 0.05);
	}

	/** Linear interpolation between this and other (t in [0,1]). */
	public color lerp(color other, double t) {
		double u = clamp01(t);
		int r = (int)Math.round(m_r + (other.m_r - m_r) * u);
		int g = (int)Math.round(m_g + (other.m_g - m_g) * u);
		int b = (int)Math.round(m_b + (other.m_b - m_b) * u);
		int a = (int)Math.round(m_a + (other.m_a - m_a) * u);
		return new color(clamp8(r), clamp8(g), clamp8(b), clamp8(a));
	}

	/** Random opaque color (seed optional). */
	public static color randomOpaque(Random rnd) {
		if (rnd == null) rnd = new Random();
		return new color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256), 255);
	}

	// ===== Object overrides =====
	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof color)) return false;
		color c = (color) o;
		return m_r == c.m_r && m_g == c.m_g && m_b == c.m_b && m_a == c.m_a;
	}
	@Override public int hashCode() {
		int v = toARGB();
		v ^= (v >>> 13); v ^= (v << 7); // cheap mix
		return v;
	}
	@Override public String toString() { return toHexARGB(); }

	// ===== Private helpers =====
	private static boolean inByte(int v){ return v >= 0 && v <= 255; }
	private static int clamp8(int v){ return (v < 0) ? 0 : (v > 255 ? 255 : v); }
	private static int toByte(double f){ return clamp8((int)Math.round(clamp01(f) * 255.0)); }
	private static double clamp01(double v){ return (v < 0) ? 0 : (v > 1 ? 1 : v); }

	// sRGB <-> Linear
	private static double srgb8ToLinear(int v){
		double s = v / 255.0;
		return (s <= 0.04045) ? (s / 12.92) : Math.pow((s + 0.055) / 1.055, 2.4);
	}
	@SuppressWarnings("unused")
	private static int linearToSrgb8(double l){
		double s = (l <= 0.0031308) ? (12.92 * l) : (1.055 * Math.pow(l, 1/2.4) - 0.055);
		return clamp8((int)Math.round(s * 255.0));
	}

	// HSL conversions
	private static double[] rgbToHsl(int r, int g, int b){
		double rf = r/255.0, gf = g/255.0, bf = b/255.0;
		double max = Math.max(rf, Math.max(gf, bf));
		double min = Math.min(rf, Math.min(gf, bf));
		double h, s, l = (max + min)/2.0;
		if (max == min) { h = 0; s = 0; }
		else {
			double d = max - min;
			s = l > 0.5 ? d / (2.0 - max - min) : d / (max + min);
			if (max == rf)      h = (gf - bf)/d + (gf < bf ? 6 : 0);
			else if (max == gf) h = (bf - rf)/d + 2;
			else                h = (rf - gf)/d + 4;
			h *= 60.0;
		}
		return new double[]{ h, s, l };
	}

	private static int[] hslToRgb(double h, double s, double l){
		double c = (1 - Math.abs(2*l - 1)) * s;
		double hp = (h % 360.0) / 60.0;
		double x = c * (1 - Math.abs(hp % 2 - 1));
		double r1=0,g1=0,b1=0;
		if      (0<=hp && hp<1){ r1=c; g1=x; b1=0; }
		else if (1<=hp && hp<2){ r1=x; g1=c; b1=0; }
		else if (2<=hp && hp<3){ r1=0; g1=c; b1=x; }
		else if (3<=hp && hp<4){ r1=0; g1=x; b1=c; }
		else if (4<=hp && hp<5){ r1=x; g1=0; b1=c; }
		else if (5<=hp && hp<6){ r1=c; g1=0; b1=x; }
		double m = l - c/2.0;
		int r = clamp8((int)Math.round((r1 + m) * 255.0));
		int g = clamp8((int)Math.round((g1 + m) * 255.0));
		int b = clamp8((int)Math.round((b1 + m) * 255.0));
		return new int[]{ r,g,b };
	}

	// ===== Private fields (legacy names) =====
	private final int m_r;
	private final int m_g;
	private final int m_b;
	private final int m_a;
}
