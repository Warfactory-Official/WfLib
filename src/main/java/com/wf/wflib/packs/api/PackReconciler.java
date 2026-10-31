package com.wf.wflib.packs.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Client: runs on every server pack list of one host; a problem text disconnects, null = matches. */
@FunctionalInterface
public interface PackReconciler {
    @Nullable
    String reconcile(List<PackEntry> server);
}
