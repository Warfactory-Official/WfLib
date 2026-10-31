package com.wf.wflib.recon.api;


/** Where a {@link TargetSource} puts what it found. */
@FunctionalInterface
public interface TargetSink {
    void accept(TargetSnapshot snapshot);
}
