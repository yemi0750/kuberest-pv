package com.cloudzcp.kuberest.api.core.controller;

import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/unused")
public class DevController {
    
    @Autowired private UnusedResourceService unusedResourceService;

    @GetMapping(value = "pods")
    public JSONObject getMountedPod() {
        JSONObject result = unusedResourceService.printPVCMountedByPod();
        return result;
    }
}