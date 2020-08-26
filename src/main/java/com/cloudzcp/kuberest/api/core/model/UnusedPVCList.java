package com.cloudzcp.kuberest.api.core.model;

import java.util.HashMap;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Unused PVC list, count
 */
@AllArgsConstructor
@Getter
@Setter
public class UnusedPVCList {
    /** Unused PVC List */
    List<UnusedPVC> unusedList;
    /** Unused PVC Count */
    HashMap<String, Integer> unusedCount;
}