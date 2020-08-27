package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 단일 Unused PV
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponsePV {
    /** selfLink */
    String selfLink;
    /** unused type */
    String unusedType;
    /** PV */
    Object PV;
}