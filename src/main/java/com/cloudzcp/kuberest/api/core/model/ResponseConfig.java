package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Kubernetes Client의 Context
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponseConfig {
    /** selfLink */
    String selfLink;
    /** context */
    Object context;
}