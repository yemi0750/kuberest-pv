package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Unused PVC In Namespace
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponseAllInNamespace {
    /** selfLink */
    String selfLink;
    /** PersistentVolume List */
    UnusedPVCList pvc;
}