package com.wf.wflib.ui.api;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.core.api.Hook;
import com.wf.wflib.ui.api.theme.PdaTheme;

/** Chrome a host (the PDA) imposes on the shared widgets. Unset => {@link #NONE}: widgets draw as authored. */
public interface UiSkin {
    Hook<UiSkin> HOOK = Hook.of("ui.skin", new UiSkin() {
        @Override
        public boolean active() {
            return false;
        }

        @Override
        public boolean inPda() {
            return false;
        }

        @Override
        public PdaTheme theme() {
            return PdaTheme.CLASSIC;
        }

        @Override
        public void panel(Canvas c, float x, float y, float w, float h, int back, int edge, float line) {
        }

        @Override
        public void button(Canvas c, float x, float y, float w, float h, int back, int edge, float line,
                           boolean hover, boolean enabled, float inset) {
        }

        @Override
        public void scroll(Canvas c, float x, float y, float h, float thumbY, float thumbH) {
        }

        @Override
        public void popup(String title, String stamp, String body) {
        }

        @Override
        public void textLine(UiEval ev, String value, float x, float y, float width, int colour) {
        }
    });

    static UiSkin current() {
        return HOOK.get();
    }

    /** Skin chrome replaces the widget's own drawing. */
    boolean active();

    /** The host's layout is drawing. */
    boolean inPda();

    PdaTheme theme();

    void panel(Canvas c, float x, float y, float w, float h, int back, int edge, float line);

    void button(Canvas c, float x, float y, float w, float h, int back, int edge, float line, boolean hover,
                boolean enabled, float inset);

    void scroll(Canvas c, float x, float y, float h, float thumbY, float thumbH);

    void popup(String title, String stamp, String body);

    void textLine(UiEval ev, String value, float x, float y, float width, int colour);
}
