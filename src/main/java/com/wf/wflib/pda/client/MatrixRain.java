package com.wf.wflib.pda.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.Binder;
import com.wf.wflib.ui.UiEval;
import com.wf.wflib.ui.Widget;
import com.wf.wflib.ui.WidgetDef;

/** The Terminal's {@code cmatrix}: covers the log while {@link #active}, nothing otherwise. */
record MatrixRain(float w, float h) implements WidgetDef {
    private static final int BACKGROUND = 0xFF050805;
    private static final int HEAD = 0xFFE4FFE4;
    private static volatile boolean active;

    static boolean active() {
        return active;
    }

    static void start() {
        active = true;
    }

    static void stop() {
        active = false;
    }

    @Override
    public Widget create(Binder binder) {
        return new Widget() {
            private final MatrixModel model = new MatrixModel(System.nanoTime());
            private long last;

            @Override
            public void measure(UiEval ev) {
                width = w;
                height = h;
            }

            @Override
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                if (!active) {
                    last = 0;
                    return;
                }
                Canvas canvas = ev.canvas;
                float cell = canvas.width("W") + 1;
                float lineH = canvas.lineHeight();
                model.resize(Math.max(1, (int) (w / cell)), Math.max(1, (int) (h / lineH)));
                long now = System.nanoTime();
                model.step(last == 0 ? 0 : Math.min((now - last) / 1.0e9F, 0.1F));
                last = now;

                canvas.fill(x, y, x + w, y + h, BACKGROUND);
                for (int c = 0; c < model.columns(); c++) {
                    for (int r = 0; r < model.rows(); r++) {
                        float b = model.brightness(c, r);
                        if (b > 0.03F) {
                            int colour = model.isHead(c, r) ? HEAD : 0xFF000000 | (int) (40 + 190 * b) << 8
                                    | (int) (60 * b * b) << 16 | (int) (30 * b * b);
                            canvas.text(String.valueOf(model.glyph(c, r)), x + c * cell, y + r * lineH, colour, false);
                        }
                    }
                }
            }
        };
    }
}
