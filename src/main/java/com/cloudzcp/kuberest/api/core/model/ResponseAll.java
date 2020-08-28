package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Unused PV, PVC 
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponseAll {
    /** selfLink */
    String selfLink;
    /** PersistentVolume List */
    UnusedPVList pv;
    /** PersistentVolumeClaim List */
    UnusedPVCList pvc;
}