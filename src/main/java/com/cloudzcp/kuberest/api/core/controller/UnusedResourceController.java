package com.cloudzcp.kuberest.api.core.controller;

import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/unused")
public class UnusedResourceController {
    JSONObject result;

    @Autowired private UnusedResourceService unusedResourceService;

    @GetMapping
    public JSONObject getUnusedResourceList(@RequestParam(required = false)Boolean count) {
        result = unusedResourceService.findAll(count);
        return result;
    }

    @GetMapping(value = "ns/{namespace}")
    public JSONObject getUnusedResourceListInNamespace(@PathVariable String namespace, @RequestParam(required = false)Boolean count) {
        result = unusedResourceService.findAll(namespace, count);
        return result;
    }

    @GetMapping(value = "pvcs")
    public JSONObject getUnusedPVCList() {
        result = unusedResourceService.findPVCList();
        return result;
    }

    @GetMapping(value = "ns/{namespace}/pvcs")
    public JSONObject getUnusedPVCListInNamespace(@PathVariable String namespace) {
        result = unusedResourceService.findPVCList(namespace);
        return result;
    }

    @GetMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public JSONObject describeUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name) {
        result = unusedResourceService.findPVC(namespace, pvc_name);
        return result;
    }

    @GetMapping(value = "pvs")
    public JSONObject getUnusedPVList() {
        result = unusedResourceService.findPVList();
        return result;
    }

    @GetMapping(value = "pvs/{pv_name}")
    public JSONObject describeUnusedPV(@PathVariable String pv_name) {
        result = unusedResourceService.findPV(pv_name);
        return result;
    }
}