package com.wf.wflib.map.api.client;

/** Cursor over a shape. Two shapes under one cursor both get events: {@code exit} may follow another's {@code enter}. */
public interface MapHover {

    void enter();

    void exit();
}
