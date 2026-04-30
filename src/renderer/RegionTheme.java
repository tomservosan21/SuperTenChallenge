package renderer;

public final class RegionTheme {
    public enum PaletteFamily {
        COOL,
        WARM,
        EARTH,
        ARCANE
    }

    public enum TexturePattern {
        SMOOTH,
        BRICK,
        ROUGH,
        LINES
    }

    public final PaletteFamily paletteFamily;
    public final int paletteVariant; // 1..4
    public final TexturePattern wallPattern;

    public RegionTheme(PaletteFamily paletteFamily, int paletteVariant, TexturePattern wallPattern) {
        this.paletteFamily = paletteFamily;
        this.paletteVariant = paletteVariant;
        this.wallPattern = wallPattern;
    }
}