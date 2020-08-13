package com.cloudzcp.kuberest.api.core.controller;

import java.io.IOException;

import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/unused")
public class ConfigController {
    
    @Autowired private UnusedResourceService unusedResourceService;
    
    @PostMapping(value = "config", consumes = { "multipart/form-data" })
    public JSONObject setConfig(@RequestPart MultipartFile file) throws IOException {
        JSONObject result = unusedResourceService.setConfig(file);
        return result;
    }
}