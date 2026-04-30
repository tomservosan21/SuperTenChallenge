package renderer;

public final class RegionThemes {
    private static final RegionTheme[] THEMES = new RegionTheme[] {
        // 0..5 = hard-separated first six regions for SIZE=51 validation
        new RegionTheme(RegionTheme.PaletteFamily.COOL,   1, RegionTheme.TexturePattern.SMOOTH), // 0  blue / cool
        new RegionTheme(RegionTheme.PaletteFamily.WARM,   1, RegionTheme.TexturePattern.BRICK),  // 1  brown / warm
        new RegionTheme(RegionTheme.PaletteFamily.EARTH,  2, RegionTheme.TexturePattern.ROUGH),  // 2  green / smoke-earth
        new RegionTheme(RegionTheme.PaletteFamily.ARCANE, 3, RegionTheme.TexturePattern.LINES),  // 3  purple signature
        new RegionTheme(RegionTheme.PaletteFamily.WARM,   4, RegionTheme.TexturePattern.BRICK),  // 4  gold / tan / bronze
        new RegionTheme(RegionTheme.PaletteFamily.EARTH,  4, RegionTheme.TexturePattern.ROUGH),  // 5  olive / oxidized / non-blue

        // 6..15 = scalable continuation, still distinct enough to matter
        new RegionTheme(RegionTheme.PaletteFamily.COOL,   2, RegionTheme.TexturePattern.SMOOTH), // 6
        new RegionTheme(RegionTheme.PaletteFamily.WARM,   2, RegionTheme.TexturePattern.BRICK),  // 7
        new RegionTheme(RegionTheme.PaletteFamily.EARTH,  1, RegionTheme.TexturePattern.ROUGH),  // 8
        new RegionTheme(RegionTheme.PaletteFamily.ARCANE, 2, RegionTheme.TexturePattern.LINES),  // 9
        new RegionTheme(RegionTheme.PaletteFamily.COOL,   3, RegionTheme.TexturePattern.SMOOTH), // 10
        new RegionTheme(RegionTheme.PaletteFamily.WARM,   3, RegionTheme.TexturePattern.BRICK),  // 11
        new RegionTheme(RegionTheme.PaletteFamily.EARTH,  3, RegionTheme.TexturePattern.ROUGH),  // 12
        new RegionTheme(RegionTheme.PaletteFamily.ARCANE, 1, RegionTheme.TexturePattern.LINES),  // 13
        new RegionTheme(RegionTheme.PaletteFamily.COOL,   4, RegionTheme.TexturePattern.SMOOTH), // 14
        new RegionTheme(RegionTheme.PaletteFamily.WARM,   2, RegionTheme.TexturePattern.LINES)   // 15
    };

    private RegionThemes() {
    }

    public static RegionTheme getTheme(int regionIndex) {
        if (regionIndex < 0) {
            return THEMES[0];
        }
        return THEMES[regionIndex % THEMES.length];
    }
}