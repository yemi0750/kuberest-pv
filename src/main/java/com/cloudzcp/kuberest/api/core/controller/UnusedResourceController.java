package com.cloudzcp.kuberest.api.core.controller;

import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class UnusedResourceController {
    JSONObject result;

    @Autowired private UnusedResourceService unusedResourceService;

    @GetMapping(value = "unused/count")
    public JSONObject countUnusedResource() {
        result = unusedResourceService.countAll(null);
        return result;
    }

    @GetMapping(value = "unused/ns/{namespace}/count")
    public JSONObject countUnusedResourceInNamespace(@PathVariable String namespace) {
        result = unusedResourceService.countAll(namespace);
        return result;
    }

    @GetMapping(value = "unused")
    public JSONObject getUnusedResourceList() {
        result = unusedResourceService.findAll(null);
        return result;
    }

    @GetMapping(value = "unused/ns/{namespace}")
    public JSONObject getUnusedResourceListInNamespace(@PathVariable String namespace) {
        result = unusedResourceService.findAll(namespace);
        return result;
    }

    @GetMapping(value = "unused/pvcs")
    public JSONObject getUnusedPVCList() {
        result = unusedResourceService.findPVC(null);
        return result;
    }

    @GetMapping(value = "unused/ns/{namespace}/pvcs")
    public JSONObject getUnusedPVCListInNamespace(@PathVariable String namespace) {
        result = unusedResourceService.findPVC(namespace);
        return result;
    }

    @GetMapping(value = "unused/ns/{namespace}/pvcs/{pvc_name}")
    public JSONObject describeUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name) {
        result = unusedResourceService.findPVC(namespace, pvc_name);
        return result;
    }

    @GetMapping(value = "unused/pvs")
    public JSONObject getUnusedPVList() {
        result = unusedResourceService.findPV();
        return result;
    }

    @GetMapping(value = "unused/pvs/{pv_name}")
    public JSONObject describeUnusedPV(@PathVariable String pv_name) {
        result = unusedResourceService.findPV(pv_name);
        return result;
    }
}