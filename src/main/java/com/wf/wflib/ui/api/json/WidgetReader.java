package com.wf.wflib.ui.api.json;

import com.wf.wflib.ui.api.WidgetDef;

/** Builds a widget from its JSON object; every key it does not read is reported as unknown. */
@FunctionalInterface
public interface WidgetReader {
    WidgetDef read(JsonProps props);
}
