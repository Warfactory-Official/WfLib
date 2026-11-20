package com.wf.wflib.ui.api.widget;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.ColorProp;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture of {@code aspect} (width / height) scaled to fit inside {@code w x h}, centred, with a {@code border}
 * {@code pad} thick around it; size = the whole {@code w x h}. Draws nothing while {@code aspect} is not positive.
 */
public record ImageFit(ResourceLocation texture, String w, String h, String aspect, float pad, String border)
        implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        NumProp pw = binder.num(w);
        NumProp ph = binder.num(h);
        NumProp ratio = binder.num(aspect);
        ColorProp edge = binder.color(border);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = Math.max(0, pw.getFloat(ev));
                height = Math.max(0, ph.getFloat(ev));
            }

            @Override
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                float a = ratio.getFloat(ev);
                if (!(a > 0)) {
                    return;
                }
                float availW = width - 2 * pad;
                float availH = height - 2 * pad;
                float iw = Math.min(availW, availH * a);
                float ih = iw / a;
                float x0 = x + (width - iw) / 2;
                float y0 = y + (height - ih) / 2;
                Canvas canvas = ev.canvas;
                int argb = edge.get(ev);
                if (pad > 0 && (argb >>> 24) != 0) {
                    canvas.fill(x0 - pad, y0 - pad, x0 + iw + pad, y0 + ih + pad, argb);
                }
                canvas.sprite(texture, x0, y0, x0 + iw, y0 + ih, 0xFFFFFFFF);
            }
        };
    }
}
