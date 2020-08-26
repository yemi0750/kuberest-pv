package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 단일 Unused PVC
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponsePVC {
    /** selfLink */
    String selfLink;
    /** unused type */
    String unusedType;
    /** PVC */
    Object PVC;
}