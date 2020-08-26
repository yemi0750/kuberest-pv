package com.cloudzcp.kuberest.api.core.controller;

import com.cloudzcp.kuberest.api.core.model.ResponseMountedPod;
import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Unused Kubernetes Resource를 조회 및 삭제하는 REST API - Dev Controller<br>
 * Resource 종류는 PersistentVolume, PersistentVolumeClaim<br>
 * 
 * 개발용
 */
@RestController
@RequestMapping("/api/v1/unused")
public class DevController {
    
    @Autowired private UnusedResourceService unusedResourceService;

    /**
     * client의 모든 Pod를 검사하여 spec.volume에 mount된 pvc 목록 검출
     * 
     * @return ResponseMountedPod
     */
    @GetMapping(value = "pods")
    public ResponseMountedPod getMountedPod() {
        ResponseMountedPod result = unusedResourceService.printPVCMountedByPod();
        return result;
    }
}