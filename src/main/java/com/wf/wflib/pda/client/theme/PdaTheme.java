package com.wf.wflib.pda.client.theme;

/**
 * One PDA look: colour tokens, drawn-chrome style and effect strengths. Themes never change geometry (sizes,
 * padding, positions), only what is drawn inside the same rectangles, so layouts and hit boxes stay valid.
 * Colours are ARGB ints; effect strengths are 0..1 of the cap the layer enforces.
 */
public record PdaTheme(String id, String name, int back, int bar, int panel, int hover, int press, int edge, int hi,
                       int text, int dim, int accent, int alert, int field, int caret, int select,
                       int chamfer, float scanlines, float vignette, float noise, float flicker, int bezel, int bezelScrew,
                       boolean hazard, boolean glow) {
    public static final PdaTheme CLASSIC = new PdaTheme("classic", "CLASSIC",
            0xFF3A3E3A, 0xFF2C2F2C, 0xFF454A45, 0xFF545B53, 0xFF626B5F, 0xFF787F76, 0x00000000,
            0xFFD2D6CC, 0xFF8D9488, 0xFFA9D46C, 0xFFD0584A, 0xFF2C2F2C, 0xFFA9D46C, 0x66A9D46C,
            0, 0, 0, 0, 0, 0, 0, false, false);

    /** Neutral, flat GAMMA screen. The original PDA housing is retained. */
    public static final PdaTheme GAMMA = new PdaTheme("gamma", "GAMMA",
            0xFF17191B, 0xFF111315, 0xD52B2D2F, 0xFF414446, 0xFF55595C, 0xFF787C7E, 0xFFE2E5E6,
            0xFFDADCDD, 0xFF969B9E, 0xFFE2E5E6, 0xFFF2F2F2, 0xE0141618, 0xFFE2E5E6, 0x666D777C,
            0, 0.025f, 0.08f, 0.009f, 0, 0, 0, false, false);

    /** Saved tactical preferences migrate to the replacement skin. */
    @Deprecated public static final PdaTheme TACTICAL = GAMMA;

    public static final PdaTheme ZONE = new PdaTheme("zone", "ZONE",
            0xFF2A2F26, 0xFF1C2018, 0xFF363C30, 0xFF454D3C, 0xFF566048, 0xFF6F7A5C, 0xFF8F9B78,
            0xFFC9D3B0, 0xFF7D8868, 0xFFE0B84A, 0xFFD8573C, 0xFF1C2018, 0xFFE0B84A, 0x66E0B84A,
            3, 0.16f, 0.30f, 0.04f, 0, 0xFF6F7A5C, 0xFF8F9B78, false, false);

    public static final PdaTheme PHOSPHOR = new PdaTheme("phosphor", "PHOSPHOR",
            0xFF04100A, 0xFF031009, 0xFF06180D, 0xFF0B2A17, 0xFF12401F, 0xFF1F8F4A, 0xFF39FF7A,
            0xFF4DFF8C, 0xFF23964F, 0xFF9DFFC0, 0xFFFF5A3C, 0xFF031009, 0xFF9DFFC0, 0x669DFFC0,
            0, 0.28f, 0.45f, 0.06f, 0.05f, 0xFF1F8F4A, 0xFF39FF7A, false, true);

    /** The effect caps, so a theme file cannot make the screen unreadable. */
    public static final float MAX_SCANLINES = 0.30f;
    public static final float MAX_VIGNETTE = 0.50f;
    public static final float MAX_NOISE = 0.08f;
    public static final float MAX_FLICKER = 0.08f;

    public static double unsigned(int argb) {
        return argb & 0xFFFFFFFFL;
    }
}
