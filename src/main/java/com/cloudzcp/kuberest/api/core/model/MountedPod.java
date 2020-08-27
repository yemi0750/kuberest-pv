package com.cloudzcp.kuberest.api.core.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Pod, Pod에 mount된 PVC, PVC와 bound된 PV 목록
 */
@AllArgsConstructor
@Getter
@Setter
public class MountedPod {
    /** pod name */
    String name;
    /** pod namespace */
    String namespace;
    /** mounted PVC List */
    List<MountedPVC> mountedPVC;
}