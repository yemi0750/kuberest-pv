package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class UnusedPV {
    /** name */
    String name;
    /** volumeReclaimPolicy */
    String volumeReclaimPolicy;
    /** status */
    String status;
    /** bound된 pvc name */
    String boundedPVC;
    /** storageClassName */
    String storageClassName;
    /** 'unused-delete-list' label의 value */
    String unusedDeleteList;
    /** unused type */
    String unusedType;
}