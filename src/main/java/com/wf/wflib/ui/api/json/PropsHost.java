package com.wf.wflib.ui.api.json;

import com.google.gson.JsonElement;
import com.wf.wflib.ui.api.WidgetDef;

import java.util.List;

/** Expansion context a {@link JsonProps} reads through: parameter substitution + nested widgets in one scope. */
public interface PropsHost {
    String substitute(String text, String path);

    WidgetDef widget(JsonElement json, String path);

    List<WidgetDef> widgets(JsonElement json, String path);
}
