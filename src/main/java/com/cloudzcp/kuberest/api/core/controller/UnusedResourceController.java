package com.cloudzcp.kuberest.api.core.controller;

import com.cloudzcp.kuberest.api.core.model.ResponseAll;
import com.cloudzcp.kuberest.api.core.model.ResponseAllInNamespace;
import com.cloudzcp.kuberest.api.core.model.ResponsePV;
import com.cloudzcp.kuberest.api.core.model.ResponsePVC;
import com.cloudzcp.kuberest.api.core.model.ResponsePVCList;
import com.cloudzcp.kuberest.api.core.model.ResponsePVList;
import com.cloudzcp.kuberest.api.core.model.ResponseResult;
import com.cloudzcp.kuberest.api.core.service.UnusedResourceService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 
 * Unused Kubernetes Resource를 조회 및 삭제하는 REST API - Controller <br>
 * Resource 종류는 PersistentVolume, PersistentVolumeClaim
 */
@RestController
@RequestMapping("/api/v1/unused")
public class UnusedResourceController {

    @Autowired private UnusedResourceService unusedResourceService;

    /**
     * 전체 Unused Kubernetes Resource 목록 조회<br>
     * Resource 종류 : PersistentVolume, PersistentVolumeClaim<br>
     * 4가지 조건으로 일부 조회 가능
     * 
     * @param count count 출력 여부
     * @param deleteList (조건 1) unused-delete-list label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @return ResponseAll
     */
    @GetMapping
    public ResponseAll getUnusedAll(@RequestParam(required = false)Boolean count, @RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponseAll result = unusedResourceService.findAll(count, deleteList, storageclassname, status, label);
        return result;
    }

    /**
     * 특정 namespace의 Unused Kubernetes Resource 목록 조회<br>
     * Resource 종류 : PersistentVolumeClaim<br>
     * 4가지 조회 조건으로 일부 조회 가능
     * 
     * @param namespace namespace
     * @param count count 출력 여부
     * @param deleteList (조건 1) unused-delete-list label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @return ResponseAllInNamespace
     */
    @GetMapping(value = "ns/{namespace}")
    public ResponseAllInNamespace getUnusedAllInNamespace(@PathVariable String namespace, @RequestParam(required = false)Boolean count, @RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponseAllInNamespace result = unusedResourceService.findAll(namespace, count, deleteList, storageclassname, status, label);
        return result;
    }

    /**
     * 전체 Unused PVC 목록 조회<br>
     * 4가지 조건으로 일부 조회 가능
     * 
     * @param deleteList (조건 1) unused-delete-list label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @return ResponsePVCList
     */
    @GetMapping(value = "pvcs")
    public ResponsePVCList getUnusedPVCList(@RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponsePVCList result = unusedResourceService.findPVCList(deleteList, storageclassname, status, label);
        return result;
    }

    /**
     * 특정 namespace의 Unused PVC 목록 조회<br>
     * 4가지 조건으로 일부 조회 가능
     * 
     * @param namespace namespace
     * @param deleteList (조건 1) unused-delete-list label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @return ResponsePVCList
     */
    @GetMapping(value = "ns/{namespace}/pvcs")
    public ResponsePVCList getUnusedPVCListInNamespace(@PathVariable String namespace, @RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponsePVCList result = unusedResourceService.findPVCList(namespace, deleteList, storageclassname, status, label);
        return result;
    }

    /**
     * 전체 Unused PV 목록 조회<br>
     * 4가지 조건으로 일부 조회 가능
     * 
     * @param deleteList (조건 1) unused-delete-list label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @return ResponsePVList
     */
    @GetMapping(value = "pvs")
    public ResponsePVList getUnusedPVList(@RequestParam(required = false)String deleteList, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponsePVList result = unusedResourceService.findPVList(deleteList, storageclassname, status, label);
        return result;
    }

    /**
     * 단일 Unused PVC의 상세 정보 조회
     * 
     * @param namespace pvc namespace
     * @param pvc_name pvc name
     * @return ResponsePVC
     */
    @GetMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public ResponsePVC describeUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name) {
        ResponsePVC result = unusedResourceService.findPVC(namespace, pvc_name);
        return result;
    }

    /**
     * 단일 Unused PV의 상세 정보 조회
     * 
     * @param pv_name pv name
     * @return ResponsePV
     */
    @GetMapping(value = "pvs/{pv_name}")
    public ResponsePV describeUnusedPV(@PathVariable String pv_name) {
        ResponsePV result = unusedResourceService.findPV(pv_name);
        return result;
    }
    
    /**
     * 전체 Unused Kubernetes Resource에 label 추가<br>
     * 3가지 조건으로 Resource 지정 가능
     * 
     * @param type unused-delete-list label의 desired value (exclude : 삭제 방지 대상 / include : 삭제 대상 / release : 삭제 지정 해제)
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @return ResponseResult
     */
    @PutMapping
    public ResponseResult putLabelOnUnusedAll(@RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponseResult result = unusedResourceService.putLabelOnUnusedAll(type, storageclassname, status, label);
        return result;
    }

    /**
     * 전체 Unused PVC에 label 추가<br>
     * 3가지 조건으로 Resource 지정 가능
     * 
     * @param type unused-delete-list label의 desired value (exclude : 삭제 방지 대상 / include : 삭제 대상 / release : 삭제 지정 해제)
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @return ResponseResult
     */
    @PutMapping(value = "pvcs")
    public ResponseResult putLabelOnUnusedPVCList(@RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponseResult result = unusedResourceService.putLabelOnUnusedPVCList(type, storageclassname, status, label);
        return result;
    }
    
    /**
     * 특정 namespace의 Unused PVC에 label 추가<br>
     * 3가지 조건으로 Resource 지정 가능
     * 
     * @param namespace namespace
     * @param type unused-delete-list label의 desired value (exclude : 삭제 방지 대상 / include : 삭제 대상 / release : 삭제 지정 해제)
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label(key:value / key)
     * @return ResponseResult
     */
    @PutMapping(value = "ns/{namespace}/pvcs")
    public ResponseResult putLabelOnUnusedPVCListInNamespace(@PathVariable String namespace, @RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponseResult result = unusedResourceService.putLabelOnUnusedPVCList(namespace, type, storageclassname, status, label);
        return result;
    }

    /**
     * 전체 Unused PV에 label 추가<br>
     * 3가지 조건으로 Resource 지정 가능
     * 
     * @param type unused-delete-list label의 desired value (exclude : 삭제 방지 대상 / include : 삭제 대상 / release : 삭제 지정 해제)
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label(key:value / key)
     * @return ResponseResult
     */
    @PutMapping(value = "pvs")
    public ResponseResult putLabelOnUnusedPVList(@RequestParam(value = "type")String type, @RequestParam(required = false)String storageclassname, @RequestParam(required = false)String status, @RequestParam(required = false)String label) {
        ResponseResult result = unusedResourceService.putLabelOnUnusedPVList(type, storageclassname, status, label);
        return result;
    }

    /**
     * 단일 Unused PVC에 label 추가
     * 
     * @param namespace pvc namespace
     * @param pvc_name pvc name
     * @param type unused-delete-list label의 desired value (exclude : 삭제 방지 대상 / include : 삭제 대상 / release : 삭제 지정 해제)
     * @return ResponseResult
     */
    @PutMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public ResponseResult putLabelOnUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name, @RequestParam(value = "type")String type) {
        ResponseResult result = unusedResourceService.putLabelOnUnusedPVC(namespace, pvc_name, type);
        return result;
    }
    
    /**
     * 단일 Unused PV에 label 추가
     * 
     * @param pv_name pv name
     * @param type unused-delete-list label의 desired value (exclude : 삭제 방지 대상 / include : 삭제 대상 / release : 삭제 지정 해제)
     * @return ResponseResult
     */
    @PutMapping(value = "pvs/{pv_name}")
    public ResponseResult putLabelOnUnusedPV(@PathVariable String pv_name, @RequestParam(value = "type")String type) {
        ResponseResult result = unusedResourceService.putLabelOnUnusedPV(pv_name, type);
        return result;
    }

    /**
     * 전체 Unused Kubernetes Resource 삭제<br>
     * "unused-delete-list : include" label을 가지는 Resource만을 대상으로 함
     * 
     * @param script script 여부
     * @return ResponseResult
     */
    @DeleteMapping
    public ResponseResult deleteUnusedAll(@RequestParam(required = false)Boolean script) {
        ResponseResult result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedAll();
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pv,pvc");
        }
        return result;
    }

    /**
     * 전체 Unused PVC 삭제<br>
     * "unused-delete-list : include" label을 가지는 Resource만을 대상으로 함
     * 
     * @param script script 여부
     * @return ResponseResult
     */
    @DeleteMapping(value = "pvcs")
    public ResponseResult deleteUnusedPVCList(@RequestParam(required = false)Boolean script) {
        ResponseResult result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedPVC();
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pvc");
        }
        return result;
    }

    /**
     * 특정 namespace의 Unused PVC 삭제<br>
     * "unused-delete-list : include" label을 가지는 Resource만을 대상으로 함
     * 
     * @param namespace namespace
     * @param script script 여부
     * @return ResponseResult
     */
    @DeleteMapping(value = "ns/{namespace}/pvcs")
    public ResponseResult deleteUnusedPVCListInNamespace(@PathVariable String namespace, @RequestParam(required = false)Boolean script) {
        ResponseResult result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedPVC(namespace);
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pvc", namespace);
        }
        return result;
    }
    
    /**
     * 전체 Unused PV 삭제<br>
     * "unused-delete-list : include" label을 가지는 Resource만을 대상으로 함
     * 
     * @param script script 여부
     * @return ResponseResult
     */
    @DeleteMapping(value = "pvs")
    public ResponseResult deleteUnusedPVList(@RequestParam(required = false)Boolean script) {
        ResponseResult result;

        if (script == null || !script) {
            result = unusedResourceService.deleteUnusedPV();
        } else {
            result = unusedResourceService.deleteUnusedAllScript("pv");
        }
        return result;
    }
    
    /**
     * 단일 Unused PVC 삭제
     * 
     * @param namespace pvc namespace
     * @param pvc_name pvc name
     * @return ResponseResult
     */
    @DeleteMapping(value = "ns/{namespace}/pvcs/{pvc_name}")
    public ResponseResult deleteUnusedPVC(@PathVariable String namespace, @PathVariable String pvc_name){
        ResponseResult result = unusedResourceService.deleteUnusedPVC(namespace, pvc_name);
        return result;
    }

    /**
     * 단일 Unused PV 삭제
     * 
     * @param pv_name pv name
     * @return ResponseResult
     */
    @DeleteMapping(value = "pvs/{pv_name}")
    public ResponseResult deleteUnusedPV(@PathVariable String pv_name){
        ResponseResult result = unusedResourceService.deleteUnusedPV(pv_name);
        return result;
    }
}