package com.wf.wflib.ui.api.widget;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.cam.api.client.CameraFeedCache;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import net.minecraft.resources.ResourceLocation;

/**
 * Live camera feed {@code feed} (feed id; 0 = none) stretched to {@code w x h}; keeps the feed requested
 * at the drawn pixel width. Draws nothing until the feed has a picture ({@link CameraFeedCache#drawable}).
 */
public record Feed(String feed, String w, String h) implements WidgetDef {
    @Override
    public Widget create(Binder binder) {
        NumProp id = binder.num(feed);
        NumProp pw = binder.num(w);
        NumProp ph = binder.num(h);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = Math.max(0, pw.getFloat(ev));
                height = Math.max(0, ph.getFloat(ev));
            }

            @Override
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                int feedId = (int) id.get(ev);
                if (feedId == 0 || width <= 0 || height <= 0) {
                    return;
                }
                Canvas canvas = ev.canvas;
                CameraFeedCache.request(feedId, Math.round(width * canvas.pixelsPerUnit()), 0.0);
                ResourceLocation texture = CameraFeedCache.texture(feedId);
                if (texture != null && CameraFeedCache.drawable(feedId)) {
                    canvas.sprite(texture, x, y, x + width, y + height, 0, 1, 1, 0, 0xFFFFFFFF);
                }
            }
        };
    }
}
