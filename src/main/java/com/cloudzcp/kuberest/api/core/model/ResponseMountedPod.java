package com.cloudzcp.kuberest.api.core.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * MountedPod
 */
@AllArgsConstructor
@Getter
@Setter
public class ResponseMountedPod {
    /** Pod-mountedPVC-boundedPV List */
    List<MountedPod> podSpecMountedVolume;
}