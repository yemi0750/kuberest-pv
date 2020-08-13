package com.cloudzcp.kuberest.api.core.controller;

import java.io.IOException;

import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/unused")
public class UnusedResourceController {

    @Autowired private UnusedResourceService unusedResourceService;

    @PostMapping(value = "config", consumes = { "multipart/form-data" })
    public JSONObject setConfig(@RequestPart MultipartFile file) throws IOException {
        JSONObject result = unusedResourceService.setConfig(file);
        return result;
    }

    @GetMapping
    public JSONObject getUnusedResourceList(@RequestParam(required = false)Boolean count, @RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.findAll(count, deleteList, storageclassname, status, label);
        return result;
    }

    @GetMapping(value = "ns/{namespace}")
    public JSONObject getUnusedResourceListInNamespace(@PathVariable String namespace, @RequestParam(required = false)Boolean count, @RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.findAll(namespace, count, deleteList, storageclassname, status, label);
        return result;
    }

    @GetMapping(value = "pvcs")
    public JSONObject getUnusedPVCList(@RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.findPVCList(deleteList, storageclassname, status, label);
        return result;
    }

    @GetMapping(value = "ns/{namespace}/pvcs")
    public JSONObject getUnusedPVCListInNamespace(@PathVariable String namespace, @RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.findPVCList(namespace, deleteList, storageclassname, status, label);
        return result;
    }

    @GetMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public JSONObject describeUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name) {
        JSONObject result = unusedResourceService.findPVC(namespace, pvc_name);
        return result;
    }

    @GetMapping(value = "pvs")
    public JSONObject getUnusedPVList(@RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.findPVList(deleteList, storageclassname, status, label);
        return result;
    }

    @GetMapping(value = "pvs/{pv_name}")
    public JSONObject describeUnusedPV(@PathVariable String pv_name) {
        JSONObject result = unusedResourceService.findPV(pv_name);
        return result;
    }
    
    @DeleteMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public JSONObject deleteUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name){
        JSONObject result = unusedResourceService.deleteUnusedPVC(namespace, pvc_name);
        return result;
    }

    @DeleteMapping(value = "pvs/{pv_name}")
    public JSONObject deleteUnusedPV(@PathVariable String pv_name){
        JSONObject result = unusedResourceService.deleteUnusedPV(pv_name);
        return result;
    }

    @PutMapping
    public JSONObject putLabelOnUnusedAll(@RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.putLabelOnUnusedAll(type, storageclassname, status, label);
        return result;
    }

    @PutMapping(value = "ns/{namespace}/pvcs")
    public JSONObject putLabelOnUnusedPVCInNamespace(@PathVariable String namespace, @RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.putLabelOnUnusedPVCList(namespace, type, storageclassname, status, label);
        return result;
    }

    @PutMapping(value = "pvcs")
    public JSONObject putLabelOnUnusedPVC(@RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.putLabelOnUnusedPVCList(type, storageclassname, status, label);
        return result;
    }
    
    @PutMapping(value = "pvs")
    public JSONObject putLabelOnUnusedPVList(@RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        JSONObject result = unusedResourceService.putLabelOnUnusedPVList(type, storageclassname, status, label);
        return result;
    }

    @PutMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public JSONObject putLabelOnUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name, @RequestParam(value = "type")String type) {
        JSONObject result = unusedResourceService.putLabelOnUnusedPVC(namespace, pvc_name, type);
        return result;
    }
    
    @PutMapping(value = "pvs/{pv_name}")
    public JSONObject putLabelOnUnusedPV(@PathVariable String pv_name, @RequestParam(value = "type")String type) {
        JSONObject result = unusedResourceService.putLabelOnUnusedPV(pv_name, type);
        return result;
    }

    @DeleteMapping
    public JSONObject deleteUnusedAll(@RequestParam(required = false)Boolean script) {
        JSONObject result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedAll();
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pv,pvc");
        }
        return result;
    }

    @DeleteMapping(value = "pvcs")
    public JSONObject deleteUnusedPVCList(@RequestParam(required = false)Boolean script) {
        JSONObject result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedPVC();
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pvc");
        }
        return result;
    }

    @DeleteMapping(value = "ns/{namespace}/pvcs")
    public JSONObject deleteUnusedPVCListInNamespace(@PathVariable String namespace, @RequestParam(required = false)Boolean script) {
        JSONObject result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedPVC(namespace);
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pvc", namespace);
        }
        return result;
    }
    
    @DeleteMapping(value = "pvs")
    public JSONObject deleteUnusedPVList(@RequestParam(required = false)Boolean script) {
        JSONObject result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedPV();
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pv");
        }
        return result;
    }
}