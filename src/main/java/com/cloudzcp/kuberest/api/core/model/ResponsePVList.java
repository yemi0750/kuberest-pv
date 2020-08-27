package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Unused PV List
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponsePVList {
    /** selfLink */
    String selfLink;
    /** PV List */
    UnusedPVList pv;
}