package com.cloudzcp.kuberest.api.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * PVC, PVC와 bound된 PV
 */
@AllArgsConstructor
@Getter
@Setter
public class MountedPVC {
    /** PVC name */
    String name;
    /** Bounded PV name */
    String boundedPV;
}