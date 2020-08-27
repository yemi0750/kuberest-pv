package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class UnusedPVC {
    /** name */
    String name;
    /** namespace */
    String namespace;
    /** status */
    String status;
    /** bound된 pv name */
    String boundedPV;
    /** storageClassName */
    String storageClassName;
    /** 'unused-delete-list' label의 value */
    String unusedDeleteList;
    /** unused type */
    String unusedType;
}