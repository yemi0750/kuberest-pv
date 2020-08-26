package com.cloudzcp.kuberest.api.core.controller;

import java.io.IOException;

import com.cloudzcp.kuberest.api.core.model.ResponseConfig;
import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Unused Kubernetes Resource를 조회 및 삭제하는 REST API - Config Controller<br>
 * Resource 종류는 PersistentVolume, PersistentVolumeClaim<br>
 * 
 * Config 변경
 */
@RestController
@RequestMapping("/api/v1/unused")
public class ConfigController {
    
    @Autowired private UnusedResourceService unusedResourceService;
    
    /**
     * 특정 config file로 kubernetes client 생성
     * 
     * @param file config file
     * @return ResponseConfig
     * @throws IOException IOException
     */
    @PostMapping(value = "config", consumes = { "multipart/form-data" })
    public ResponseConfig setConfig(@RequestPart MultipartFile file) throws IOException {
        ResponseConfig result = unusedResourceService.setConfig(file);
        return result;
    }
}