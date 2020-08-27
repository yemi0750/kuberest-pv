package com.cloudzcp.kuberest.api.core.model;

import java.util.HashMap;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Unused PV list, count
 */
@AllArgsConstructor
@Getter
@Setter
public class UnusedPVList {
    /** Unused PV List */
    List<UnusedPV> unusedList;
    /** Unused PV Count */
    HashMap<String, Integer> unusedCount;
}