package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/** 
 * Unused PVC List
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponsePVCList {
    /** selfLink */
    String selfLink;
    /** PVC List */
    UnusedPVCList pvc;
}