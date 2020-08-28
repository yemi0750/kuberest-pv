package com.cloudzcp.kuberest.api.core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.cloudzcp.kuberest.api.core.model.MountedPVC;
import com.cloudzcp.kuberest.api.core.model.UnusedPVList;
import com.cloudzcp.kuberest.api.core.model.UnusedPVCList;
import com.cloudzcp.kuberest.api.core.model.MountedPod;
import com.cloudzcp.kuberest.api.core.model.ResponseAll;
import com.cloudzcp.kuberest.api.core.model.ResponseAllInNamespace;
import com.cloudzcp.kuberest.api.core.model.ResponseConfig;
import com.cloudzcp.kuberest.api.core.model.ResponsePV;
import com.cloudzcp.kuberest.api.core.model.ResponsePVC;
import com.cloudzcp.kuberest.api.core.model.ResponsePVCList;
import com.cloudzcp.kuberest.api.core.model.ResponsePVList;
import com.cloudzcp.kuberest.api.core.model.ResponseMountedPod;
import com.cloudzcp.kuberest.api.core.model.ResponseResult;
import com.cloudzcp.kuberest.api.core.model.UnusedPV;
import com.cloudzcp.kuberest.api.core.model.UnusedPVC;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.PersistentVolumeList;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Unused Kubernetes Resource (PV, PVC)를 조회 및 삭제하는 REST API - Service<br>
 * Resource 종류는 PersistentVolume, PersistentVolumeClaim
 */
@Service
public class UnusedResourceService {

    /** kubernetes client Map - contextID:KubernetesClient*/
    private ConcurrentHashMap<String, KubernetesClient> clientMap = new ConcurrentHashMap<String, KubernetesClient>();
    private static final String DEFAULT_CONTEXTID = "Default";

    private static final String APIURL = "api/v1/unused";
    private static final String APIURL_NS = "api/v1/unused/ns/";
    private static final String APIURL_PVCS = "api/v1/unused/pvcs";
    private static final String APIURL_PVS = "api/v1/unused/pvs";
    private static final String APIURL_CONFIG = "api/v1/unused/config";
    private static final String TYPE_BAD_REQUEST = "bad request / type = [ exclude / include / release ]";
    private static final String PVC_NOT_FOUND = "No PVC results found.";
    private static final String PV_NOT_FOUND = "No PV results found.";

    /** DefaultClient를 생성해서 clientMap에 추가, contextID를 final 변수에 저장 */
    UnusedResourceService() {
        KubernetesClient clientTemp = new DefaultKubernetesClient();
        clientMap.put(DEFAULT_CONTEXTID, clientTemp);
    }

    /**
     * clientMap에서 현재 session의 contextID 값을 key로 가지는 client를 찾아서 반환<br>
     * session에 contextID 값이 지정되지 않은 경우 DefaultClient를 client로 등록
     * @param request HttpServletRequest
     * @return KubernetesClient
     */
    private KubernetesClient getClient(HttpServletRequest request) {

        HttpSession session = request.getSession();
        String contextID = (String) session.getAttribute("contextID");

        if (contextID == null) {
            session.setAttribute("contextID", DEFAULT_CONTEXTID);
            contextID = DEFAULT_CONTEXTID;
        }

        return clientMap.get(contextID);
    }

    /**
     * 특정 config file로 kubernetes client 생성
     * @param conf config file
     * @param request HttpServletRequest
     * @return ResponseConfig
     * @throws IOException IOException
     */
    public ResponseConfig setConfig(MultipartFile conf, HttpServletRequest request) throws IOException {

        String content = new String(conf.getBytes());
        Config kubeconfig = Config.fromKubeconfig(content);
        HttpSession session = request.getSession();

        KubernetesClient clientTemp = new DefaultKubernetesClient(kubeconfig);
        String contextID = clientTemp.getConfiguration().getCurrentContext().getName();
        session.setAttribute("contextID", contextID);
        clientMap.put(contextID, clientTemp);

        ResponseConfig response = new ResponseConfig(APIURL_CONFIG, (Object)clientMap.get(contextID).getConfiguration().getContexts());
        return response;
    }

    /**
     * client의 context 반환
     * 
     * @param request HttpServletRequest
     * @return ResponseConfig
     */
    public ResponseConfig getConfig(HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        Object clientcontext = client.getConfiguration().getContexts();

        return new ResponseConfig(APIURL_CONFIG, clientcontext);
    }
    
    /**
     * 모든 Pod를 검사하여 spec.volume에 mount된 pvc 목록을 검출<br>
     * podName, podNamespace, mount된 pvcList(pvcName, PV에 bound된 PVName)
     * @param client KubernetesClient
     * @return ResponseMountedPod
     */
    private ResponseMountedPod findAllPVCMountedByPod(KubernetesClient client){

        List<MountedPod> mountedPodList = new ArrayList<MountedPod>();

        List<Pod> pods = client.pods().inAnyNamespace().list().getItems();
        for(Pod pod : pods) {
            String podName = pod.getMetadata().getName();
            String podNamespace = pod.getMetadata().getNamespace();

            List<MountedPVC> mountedPVCList = pod.getSpec().getVolumes()
                .stream()
                .filter(volume -> volume.getPersistentVolumeClaim() != null)
                .map(volume -> getMountedPVC(podNamespace, volume, client))
                .collect(Collectors.toList());
            
            if (mountedPVCList.size() > 0) {
                MountedPod mountedPod = new MountedPod(podName, podNamespace, mountedPVCList);
                mountedPodList.add(mountedPod);
            }
        }

        ResponseMountedPod response = new ResponseMountedPod(mountedPodList);
        return response;
    }

    /**
     * volume에서 pvc의 이름(claimName)찾고, 해당 pvc에 bound된 pv 이름 찾아서 객체 생성
     * @param podNamespace pod namespace
     * @param volume pod spec.volumes 목록 중 persistentVolumeClaim
     * @param client KubernetesClient
     * @return MountedPVC
     */
    private MountedPVC getMountedPVC (String podNamespace, Volume volume, KubernetesClient client) {
        
        String pvcName = volume.getPersistentVolumeClaim().getClaimName();
        String boundedPVNname = client.persistentVolumeClaims().inNamespace(podNamespace).withName(pvcName).get().getSpec().getVolumeName();
        
        return new MountedPVC(pvcName, boundedPVNname);
    }
    
    /**
     * findAllPVCMountedByPod()로 검출한 모든 pod의 pvc 목록을 출력
     * @param request HttpServletRequest
     * @return ResponseMountedPod
     * @see #findAllPVCMountedByPod(KubernetesClient)
     */
    public ResponseMountedPod printPVCMountedByPod(HttpServletRequest request){

        KubernetesClient client = getClient(request);
        ResponseMountedPod response = findAllPVCMountedByPod(client);

        return response;
    }

    /**
     * 단일 PVC가 Pod spec에 명시되어 있는지 여부 검사<br>
     * PVC는 name, namespace로 특정
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pvcName pvc name
     * @param pvcNamespace pvc namespace
     * @return boolean. pvc가 pod spec.volume에 명시된 경우 true
     */
    private boolean isMountedByPod(List<MountedPod> mountedPodList, String pvcName, String pvcNamespace){

        if (!mountedPodList.isEmpty()) {
            for (MountedPod mountedPod : mountedPodList){
                List<MountedPVC> mountedPVCList = mountedPod.getMountedPVC();
                for (MountedPVC mountedPVC : mountedPVCList) {
                    if (mountedPVC.getName().equals(pvcName) && mountedPod.getNamespace().equals(pvcNamespace)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * PVC Unused 여부 검사<br>
     * (검사 기준 1) DeletionTimestamp 존재하지 않으면 status가 Terminating인 경우로 판단, return false<br>
     * (검사 기준 2) Pod의 spec에 명시되어 있지 않으면 return true
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pvc pvc
     * @return boolean. pvc가 unused인 경우 true
     */
    private boolean isUnusedPVC(List<MountedPod> mountedPodList, PersistentVolumeClaim pvc) {

        String pvcName = pvc.getMetadata().getName();
        String pvcNamespace = pvc.getMetadata().getNamespace();

        if (pvc.getMetadata().getDeletionTimestamp() != null){
            //terminating
            return false;
        }

        if (!isMountedByPod(mountedPodList, pvcName, pvcNamespace)){
            return true;
        } else {
            return false;
        }
    }

    /**
     * PV Unused 여부 검사<br>
     * (검사 기준 1) DeletionTimestamp 존재하지 않으면 status가 Terminating인 경우로 판단, return false<br>
     * (검사 기준 2) Status.Phase가 Bound가 아니면 return true<br>
     * (검사 기준 3) Bound된 PV가 Pod의 spec에 명시되어 있지 않으면 return true
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pv pv
     * @return boolean. pv가 unused인 경우 true
     */
    private boolean isUnusedPV(List<MountedPod> mountedPodList, PersistentVolume pv) {

        if (pv.getMetadata().getDeletionTimestamp() != null) {
            //terminating
            return false;
        }

        String pvStatus = pv.getStatus().getPhase();
        if (!pvStatus.equals("Bound")) {
            return true;
        }

        String pvcName = pv.getSpec().getClaimRef().getName();
        String pvcNamespace = pv.getSpec().getClaimRef().getNamespace();
        if (!isMountedByPod(mountedPodList, pvcName, pvcNamespace)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * PVC가 Unused Resource로 판단된 이유를 반환
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pvc pvc
     * @return String. Unused Type
     */
    private String checkUnusedPVCType(List<MountedPod> mountedPodList, PersistentVolumeClaim pvc) {

        String pvcStatus = pvc.getStatus().getPhase();
        String pvcName = pvc.getMetadata().getName();
        String pvcNamespace = pvc.getMetadata().getNamespace();

        if (!isMountedByPod(mountedPodList, pvcName, pvcNamespace)){
            if (!pvcStatus.equals("Bound")) {
                return "PVC is not mounted by Pod / PVC Status is not 'Bound'";
            } else {
                return "PVC is not mounted by Pod";
            }
        }
        return "";
    }

    /**
     * PV가 Unused Resource로 판단된 이유를 반환
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pv pv
     * @return String. Unused Type
     */
    private String checkUnusedPVType(List<MountedPod> mountedPodList, PersistentVolume pv) {

        String pvStatus = pv.getStatus().getPhase();
        if (!pvStatus.equals("Bound")) {
            return "PV Status is not 'Bound'";
        }

        String pvcName = pv.getSpec().getClaimRef().getName();
        String pvcNamespace = pv.getSpec().getClaimRef().getNamespace();
        if (!isMountedByPod(mountedPodList, pvcName, pvcNamespace)) {
            return "PVC("+pvcName+") connected to PV is not mounted by Pod";
        }
        return "";
    }

    /**
     * Unused PVC의 정보로 UnusedPVC 객체 생성<br>
     * PVC 기본 정보, bound된 PVName, unusedType, 'unused-delete-list' label의 value
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pvc pvc
     * @return UnusedPVC
     */
    private UnusedPVC getUnusedPVC(List<MountedPod> mountedPodList, PersistentVolumeClaim pvc) {

        String pvcName = pvc.getMetadata().getName();
        String pvcNamespace = pvc.getMetadata().getNamespace();
        String pvcStatus = pvc.getStatus().getPhase();
        String pvcBoundedPV = pvc.getSpec().getVolumeName();
        String pvcStorageClassName = pvc.getSpec().getStorageClassName();
        String pvUnusedType = checkUnusedPVCType(mountedPodList, pvc);
        String pvUnusedDeleteList = "";
        if (pvc.getMetadata().getLabels() != null) {
            pvUnusedDeleteList = pvc.getMetadata().getLabels().get("unused-delete-list");
        } else {
            pvUnusedDeleteList = null;
        }
        
        return new UnusedPVC(pvcName, pvcNamespace, pvcStatus, pvcBoundedPV, pvcStorageClassName, pvUnusedDeleteList, pvUnusedType);
    }

    /**
     * Unused PV의 정보로 UnusedPV 객체 생성<br>
     * PV 기본 정보, bound된 PVCName, unusedType, 'unused-delete-list' label의 value
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pv pv
     * @return UnusedPV
     */
    private UnusedPV getUnusedPV(List<MountedPod> mountedPodList, PersistentVolume pv) {

        String pvName = pv.getMetadata().getName();
        String pvVolumeReclaimPolicy = pv.getSpec().getPersistentVolumeReclaimPolicy();
        String pvStatus = pv.getStatus().getPhase();
        String pvBoundedPVC = "";
        if (pv.getSpec().getClaimRef() != null) {
            pvBoundedPVC = pv.getSpec().getClaimRef().getName();
        }
        String pvStorageClassName = pv.getSpec().getStorageClassName();
        String pvUnusedType = checkUnusedPVType(mountedPodList, pv);
        String pvUnusedDeleteList = "";
        if (pv.getMetadata().getLabels() != null) {
            pvUnusedDeleteList = pv.getMetadata().getLabels().get("unused-delete-list");
        } else {
            pvUnusedDeleteList = null;
        }

        return new UnusedPV(pvName, pvVolumeReclaimPolicy, pvStatus, pvBoundedPVC, pvStorageClassName, pvUnusedDeleteList, pvUnusedType);
    }

    /**
     * PVC List의 각 PVC의 Unused 여부, 조건 만족 여부 검사<br>
     * 조건을 만족하는 Unused PVC 목록과 수 반환
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pvcList pvc list
     * @param count count 출력 여부
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @return UnusedPVCList
     */
    private UnusedPVCList getPVCList(List<MountedPod> mountedPodList, PersistentVolumeClaimList pvcList, Boolean count, String storageclassname, String status, String label) {

        List<UnusedPVC> unusedPVCList = new ArrayList<UnusedPVC>();
        HashMap<String, Integer> unusedPVCCount = new HashMap<String, Integer>();

        if (count == null || !count) {
            unusedPVCList = pvcList.getItems()
                .stream()
                .filter(pvc -> isUnusedPVC(mountedPodList, pvc))
                .filter(pvc -> checkPVCFields(pvc, storageclassname, status, label))
                .map(pvc -> getUnusedPVC(mountedPodList, pvc))
                .collect(Collectors.toList());
        }
        if (count == null || count) {
            int total = pvcList.getItems().size();
            long unused = pvcList.getItems()
                .stream()
                .filter(pvc -> isUnusedPVC(mountedPodList, pvc))
                .filter(pvc -> checkPVCFields(pvc, storageclassname, status, label))
                .count();
            unusedPVCCount.put("total", total);
            unusedPVCCount.put("unused", (int)unused);
        }

        UnusedPVCList pvc = new UnusedPVCList(unusedPVCList, unusedPVCCount);
        return pvc;
    }

    /**
     * PV List의 각 PV의 Unused 여부, 조건 만족 여부 검사<br>
     * 조건을 만족하는 Unused PV 목록과 수 반환
     * @param mountedPodList pod spec에 명시된 pvc list
     * @param pvList pv list
     * @param count count 출력 여부
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @return UnusedPVList
     */
    private UnusedPVList getPVList(List<MountedPod> mountedPodList, PersistentVolumeList pvList, Boolean count, String storageclassname, String status, String label) {

        List<UnusedPV> unusedPVList = new ArrayList<UnusedPV>();
        HashMap<String, Integer> unusedPVCount = new HashMap<String, Integer>();

        if (count == null || !count) {
            unusedPVList = pvList.getItems()
                .stream()
                .filter(pv -> isUnusedPV(mountedPodList, pv))
                .filter(pv -> checkPVFields(pv, storageclassname, status, label))
                .map(pv -> getUnusedPV(mountedPodList, pv))
                .collect(Collectors.toList());
        }
        if (count == null || count) {
            int total = pvList.getItems().size();
            long unused = pvList.getItems()
                .stream()
                .filter(pv -> isUnusedPV(mountedPodList, pv))
                .filter(pv -> checkPVFields(pv, storageclassname, status, label))
                .count();
            unusedPVCount.put("total", total);
            unusedPVCount.put("unused", (int)unused);
        }

        UnusedPVList pv = new UnusedPVList(unusedPVList, unusedPVCount);
        return pv;
    }


    /**
     * client가 조회할 수 있는 모든 Resource 중 Unused Resource를 검출하여 응답 구성<br>
     * 검출 대상이 되는 PVC list와 PV list 구성 - (조건 1) 'unused-delete-list' label 존재 여부 및 value 검사<br>
     * getPVList(), getPVCList() - (조건 2~4) 검사
     * @param count counnt 출력 여부
     * @param deleteList (조건 1) 'unused-delete-list' label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponseAll
     * @see #getPVList(List, PersistentVolumeList, Boolean, String, String, String)
     * @see #getPVCList(List, PersistentVolumeClaimList, Boolean, String, String, String)
     */
    public ResponseAll findAll(Boolean count, String deleteList, String storageclassname, String status, String label, HttpServletRequest request){

        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        PersistentVolumeClaimList pvcList;
        PersistentVolumeList pvList;

        if (deleteList != null) {
            pvcList = client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", deleteList).list();
            pvList = client.persistentVolumes().withLabel("unused-delete-list", deleteList).list();
        } else {
            pvcList = client.persistentVolumeClaims().inAnyNamespace().list();
            pvList = client.persistentVolumes().list();
        }

        UnusedPVList pv = getPVList(mountedPodList, pvList, count, storageclassname, status, label);
        UnusedPVCList pvc = getPVCList(mountedPodList, pvcList, count, storageclassname, status, label);
        ResponseAll response = new ResponseAll(APIURL, pv, pvc);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 Resource 중 Unused Resource를 검출하여 응답 구성<br>
     * PV는 namespace에 속하지 않으므로 조회되지 않음<br>
     * 검출 대상이 되는 PVC list구성 - namespace, (조건 1) 'unused-delete-list' label 존재 여부 및 value 검사<br>
     * getPVCList() - (조건 2~4) 검사
     * @param namespace namespace
     * @param count count 출력 여부
     * @param deleteList (조건 1) 'unused-delete-list' label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponseAllInNamespace
     * @see #getPVCList(List, PersistentVolumeClaimList, Boolean, String, String, String)
     */
    public ResponseAllInNamespace findAll(String namespace, Boolean count, String deleteList, String storageclassname, String status, String label, HttpServletRequest request){

        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        PersistentVolumeClaimList pvcList;

        if (deleteList != null) {
            pvcList = client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", deleteList).list();
        } else {
            pvcList = client.persistentVolumeClaims().inNamespace(namespace).list();
        }

        UnusedPVCList pvc = getPVCList(mountedPodList, pvcList, count, storageclassname, status, label);
        ResponseAllInNamespace response = new ResponseAllInNamespace(APIURL_NS + namespace, pvc);
        return response;
    }


    /**
     * client가 조회할 수 있는 모든 PVC 중 Unused PVC를 검출하여 응답 구성<br>
     * 검출 대상이 되는 PVC list구성 - (조건 1) 'unused-delete-list' label 존재 여부 및 value 검사<br>
     * getPVCList() - (조건 2~4) 검사
     * @param deleteList (조건 1) 'unused-delete-list' label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponsePVCList
     * @see #getPVCList(List, PersistentVolumeClaimList, Boolean, String, String, String)
     */
    public ResponsePVCList findPVCList(String deleteList, String storageclassname, String status, String label, HttpServletRequest request){

        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();
        
        PersistentVolumeClaimList pvcList;

        if (deleteList != null) {
            pvcList = client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", deleteList).list();
        } else {
            pvcList = client.persistentVolumeClaims().inAnyNamespace().list();
        }

        UnusedPVCList pvc = getPVCList(mountedPodList, pvcList, false, storageclassname, status, label);
        ResponsePVCList response = new ResponsePVCList(APIURL_PVCS, pvc);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PVC 중 Unused PVC를 검출하여 응답 구성<br>
     * 검출 대상이 되는 PVC list구성 - namespace, (조건 1) 'unused-delete-list' label 존재 여부 및 value 검사<br>
     * getPVCList() - (조건 2~4) 검사
     * @param namespace namespace
     * @param deleteList (조건 1) 'unused-delete-list' label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponsePVCList
     * @see #getPVCList(List, PersistentVolumeClaimList, Boolean, String, String, String)
     * 
     */
    public ResponsePVCList findPVCList(String namespace, String deleteList, String storageclassname, String status, String label, HttpServletRequest request){

        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        PersistentVolumeClaimList pvcList;

        if (deleteList != null) {
            pvcList = client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", deleteList).list();
        } else {
            pvcList = client.persistentVolumeClaims().inNamespace(namespace).list();
        }

        UnusedPVCList pvc = getPVCList(mountedPodList, pvcList, false, storageclassname, status, label);
        ResponsePVCList response = new ResponsePVCList(APIURL_NS + namespace+"/pvcs", pvc);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PV 중 Unused PV를 검출하여 응답 구성<br>
     * 검출 대상이 되는 PV list구성 - (조건 1) 'unused-delete-list' label 존재 여부 및 value 검사<br>
     * getPVList() - (조건 2~4) 검사
     * @param deleteList (조건 1) 'unused-delete-list' label의 value (exclude : 삭제 방지 대상 / include : 삭제 대상)
     * @param storageclassname (조건 2) storageClassname
     * @param status (조건 3) status.phase
     * @param label (조건 4) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponsePVList
     * @see #getPVList(List, PersistentVolumeList, Boolean, String, String, String)
     */
    public ResponsePVList findPVList(String deleteList, String storageclassname, String status, String label, HttpServletRequest request){

        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        PersistentVolumeList pvList;

        if (deleteList != null) {
            pvList = client.persistentVolumes().withLabel("unused-delete-list", deleteList).list();
        } else {
            pvList = client.persistentVolumes().list();
        }

        UnusedPVList pv = getPVList(mountedPodList, pvList, false, storageclassname, status, label);
        ResponsePVList response = new ResponsePVList(APIURL_PVS, pv);
        return response;
    }

    /**
     * 단일 PVC 존재 여부, Unused 여부 검사<br>
     * PVC는 name, namespace로 특정<br>
     * Unused인 경우 해당 PVC의 상세 정보와 unusedType으로 응답 구성
     * @param pvcNamespace pvc namespace
     * @param pvcName pvc name
     * @param request HttpServletRequest
     * @return ResponsePVC
     */
    public ResponsePVC findPVC(String pvcNamespace, String pvcName, HttpServletRequest request){

        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        String pvcUnusedType;
        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).get();

        if (pvc == null) {
            pvcUnusedType = pvcName + " not found";
        } else if (!isUnusedPVC(mountedPodList, pvc)) {
            pvcUnusedType = pvcName + " is used resource";
        } else {
            pvcUnusedType = checkUnusedPVCType(mountedPodList, pvc);
        }

        ResponsePVC response = new ResponsePVC(APIURL_NS + pvcNamespace + "/pvcs/" + pvcName, pvcUnusedType, pvc);
        return response;
    }

    /**
     * 단일 PVC 존재 여부, Unused 여부 검사<br>
     * PV는 name으로 특정<br>
     * Unused인 경우 해당 PV의 상세 정보와 unusedType으로 응답 구성
     * @param pvName pv name
     * @param request HttpServletRequest
     * @return ResponsePV
     */
    public ResponsePV findPV(String pvName, HttpServletRequest request){
        
        KubernetesClient client = getClient(request);
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        String pvUnusedType;
        PersistentVolume pv = client.persistentVolumes().withName(pvName).get();

        if (pv == null) {
            pvUnusedType = pvName + " not found";
        } else if (!isUnusedPV(mountedPodList, pv)) {
            pvUnusedType = pvName + " is used resource";
        } else {
            pvUnusedType = checkUnusedPVType(mountedPodList, pv);
        }

        ResponsePV response = new ResponsePV(APIURL_PVS + "/" + pvName, pvUnusedType, pv);
        return response;
    }


    
    /**
     * PVC의 unused-delete-list label 존재 여부와 value 검사
     * @param pvc pvc
     * @return String. pvc의 'unused-delete-list' label의 value
     */
    private String checkUnusedPVCDeleteListLabel(PersistentVolumeClaim pvc) {

        if (pvc.getMetadata().getLabels() == null) {
            return "null";
        } else {
            String result = pvc.getMetadata().getLabels().get("unused-delete-list");
            return (result == null) ? "null" : result;
        }
    }

    /**
     * PV의 unused-delete-list label 존재 여부와 value 검사
     * @param pv pv
     * @return String. pv의 'unused-delete-list' label의 value
     */
    private String checkUnusedPVDeleteListLabel(PersistentVolume pv) {

        if (pv.getMetadata().getLabels() == null) {
            return "null";
        } else {
            String result = pv.getMetadata().getLabels().get("unused-delete-list");
            return (result == null) ? "null" : result;
        }
    }

    /**
     * PVC의 조건 만족 여부 검사
     * @param pvc pvc
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @return boolean. pvc가 조건 1~3을 만족하는 경우 true
     */
    private boolean checkPVCFields(PersistentVolumeClaim pvc, String storageclassname, String status, String label) {

        boolean result = true;

        if (storageclassname != null){
            String pvcStorageClassName = pvc.getSpec().getStorageClassName();
            result = result && pvcStorageClassName != null && pvcStorageClassName.equals(storageclassname);
        }
        if (status != null) {
            String pvcStatus = pvc.getStatus().getPhase();
            result = result && pvcStatus.equals(status);
        }
        if (label != null) {
            if (pvc.getMetadata().getLabels() == null) {
                result = false;
            } else {
                String[] pvcLabel = label.split(":");
                String pvcLabelKey = pvcLabel[0];
                String pvcLabelValueReal = pvc.getMetadata().getLabels().get(pvcLabelKey);
                if (pvcLabel.length > 1) { //value도 있는 경우
                    String pvcLabelValue = pvcLabel[1];
                    result = result && pvcLabelValueReal != null && pvcLabelValueReal.equals(pvcLabelValue);
                } else {
                    result = result && pvcLabelValueReal != null;
                }
            }
        }
        return result;
    }

    /**
     * PV 조건 만족 여부 검사
     * @param pv pv
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @return boolean. pv가 조건 1~3을 만족하는 경우 true
     */
    private boolean checkPVFields(PersistentVolume pv, String storageclassname, String status, String label) {

        boolean result = true;

        if (storageclassname != null){
            String pvStorageClassName = pv.getSpec().getStorageClassName();
            result = result && pvStorageClassName != null && pvStorageClassName.equals(storageclassname);
        }
        if (status != null) {
            String pvStatus = pv.getStatus().getPhase();
            result = result && pvStatus.equals(status);
        }
        if (label != null) {
            if (pv.getMetadata().getLabels() == null) {
                result = false;
            } else {
                String[] pvLabel = label.split(":");
                String pvLabelKey = pvLabel[0];
                String pvLabelValueReal = pv.getMetadata().getLabels().get(pvLabelKey);
                if (pvLabel.length > 1) { //value도 있는 경우
                    String pvLabelValue = pvLabel[1];
                    result = result && pvLabelValueReal != null && pvLabelValueReal.equals(pvLabelValue);
                } else {
                    result = result && pvLabelValueReal != null;
                }
            }
        }
        return result;
    }

    /**
     * PVC의 'unused-delete-list' label의 value를 desired value로 변경하고, 변경 여부 반환
     * @param pvc pvc
     * @param type 'unused-delete-list' label의 desired value
     * @param client KubernetesClient
     * @return boolean. pvc의 'unused-delete-list' label의 value가 desired value로 변경되면 true
     */
    private boolean checkPVCLabelEdited(PersistentVolumeClaim pvc, String type, KubernetesClient client) {

        String pvcName = pvc.getMetadata().getName();
        String pvcNamespace = pvc.getMetadata().getNamespace();

        if (type.equals("exclude")) {
            if (!checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                PersistentVolumeClaim pvcTemp = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                if (checkUnusedPVCDeleteListLabel(pvcTemp).equals("exclude")) {
                    return true;
                }
            }
        } else if (type.equals("include")) {
            if (checkUnusedPVCDeleteListLabel(pvc).equals("null")) {
                PersistentVolumeClaim pvcTemp = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                if (checkUnusedPVCDeleteListLabel(pvcTemp).equals("include")) {
                    return true;
                }
            }
        } else if (type.equals("release")) {
            if (!checkUnusedPVCDeleteListLabel(pvc).equals("null")) {
                PersistentVolumeClaim pvcTemp = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                if (checkUnusedPVCDeleteListLabel(pvcTemp).equals("null")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * PV의 'unused-delete-list' label의 value를 desired value로 변경하고, 변경 여부 반환
     * @param pv pv
     * @param type 'unused-delete-list' label의 desired value
     * @param client KubernetesClient
     * @return boolean. pv의 'unused-delete-list' label의 value가 desired value로 변경되면 true
     */
    private boolean checkPVLabelEdited(PersistentVolume pv, String type, KubernetesClient client) {

        String pvName = pv.getMetadata().getName();

        if (type.equals("exclude")) {
            if (!checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                PersistentVolume pvTemp = client.persistentVolumes().withName(pvName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                if (checkUnusedPVDeleteListLabel(pvTemp).equals("exclude")) {
                    return true;
                }
            }
        } else if (type.equals("include")) {
            if (checkUnusedPVDeleteListLabel(pv).equals("null")) {
                PersistentVolume pvTemp = client.persistentVolumes().withName(pvName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                if (checkUnusedPVDeleteListLabel(pvTemp).equals("include")) {
                    return true;
                }
            }
        } else if (type.equals("release")) {
            if (!checkUnusedPVDeleteListLabel(pv).equals("null")) {
                PersistentVolume pvTemp = client.persistentVolumes().withName(pvName).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                if (checkUnusedPVDeleteListLabel(pvTemp).equals("null")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * PVC List의 각 PVC의 Unused 여부, 조건 만족 여부 검사<br>
     * 'unused-delete-list'를 key로 가지는 label의 value를 변경하고, 변경 성공한 수 반환
     * @param pvcList pvc list
     * @param type 'unused-delete-list' label의 desired value
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @param client KubernetesClient
     * @return String. pvcList 각 pvc의 'unused-delete-list' label의 value 변경 결과
     */
    private String putLabelOnPVCList(PersistentVolumeClaimList pvcList, String type, String storageclassname, String status, String label, KubernetesClient client) {

        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        long count = pvcList.getItems()
            .stream()
            .filter(pvc -> isUnusedPVC(mountedPodList, pvc))
            .filter(pvc -> checkPVCFields(pvc, storageclassname, status, label))
            .filter(pvc -> checkPVCLabelEdited(pvc, type, client))
            .count();

            if (count > 0) {
                return count + " pvcs " + type + "d";
            } else {
                return PVC_NOT_FOUND;
            }
    }

    /**
     * PV List의 각 PV의 Unused 여부, 조건 만족 여부 검사<br>
     * 'unused-delete-list'를 key로 가지는 label의 value를 변경하고, 변경 성공한 수 반환
     * @param pvList pv list
     * @param type 'unused-delete-list' label의 desired value
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @param client KubernetesClient
     * @return String. pvList 각 pv의 'unused-delete-list' label의 value 변경 결과
     */
    private String putLabelOnPVList(PersistentVolumeList pvList, String type, String storageclassname, String status, String label, KubernetesClient client) {

        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        long count = pvList.getItems()
            .stream()
            .filter(pv -> isUnusedPV(mountedPodList, pv))
            .filter(pv -> checkPVFields(pv, storageclassname, status, label))
            .filter(pv -> checkPVLabelEdited(pv, type, client))
            .count();

        if (count > 0) {
            return count + " pvs " + type + "d";
        } else {
            return PV_NOT_FOUND;
        }
    }
    
    /**
     * client가 조회할 수 있는 모든 Resource에 labe 추가/수정<br>
     * label의 key : 'unused-delete-list' / value : type 변수 값<br>
     * putLabelOnUnusedPVCList(), putLabelOnUnusedPVList() - (조건 1~3) 검사, label 추가
     * @param type 'unused-delete-list' label의 desired value
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponseResult
     * @see #putLabelOnUnusedPVCList(String, String, String, String, String, HttpServletRequest)
     * @see #putLabelOnUnusedPVList(String, String, String, String, HttpServletRequest)
     */
    public ResponseResult putLabelOnUnusedAll(String type, String storageclassname, String status, String label, HttpServletRequest request) {
        
        String resultPVC = putLabelOnUnusedPVCList(type, storageclassname, status, label, request).getResult();
        String resultPV = putLabelOnUnusedPVList(type, storageclassname, status, label, request).getResult();
        String result = resultPVC + " / " + resultPV;

        ResponseResult response = new ResponseResult(APIURL, result);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PVC에 labe 추가/수정<br>
     * label의 key : 'unused-delete-list' / value : type 변수 값<br>
     * type 값이 exclude, include, release가 아닌 경우 BAD_REQUEST<br>
     * 검출 대상이 되는 PVC list 구성 - namespace 검사<br>
     * putLabelOnPVCList() - (조건 1~3) 검사
     * @param namespace namespace
     * @param type 'unused-delete-list' label의 desired value
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponseResult
     * @see #putLabelOnPVCList(PersistentVolumeClaimList, String, String, String, String, KubernetesClient)
     */
    public ResponseResult putLabelOnUnusedPVCList(String namespace, String type, String storageclassname, String status, String label, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;

        if (type.equals("exclude") || type.equals("include") || type.equals("release")) {
            PersistentVolumeClaimList pvcList = client.persistentVolumeClaims().inNamespace(namespace).list();
            result = putLabelOnPVCList(pvcList, type, storageclassname, status, label, client);
        } else {
            result = TYPE_BAD_REQUEST;
        }
        
        ResponseResult response = new ResponseResult(APIURL_NS + namespace + "/pvcs", result);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PVC에 labe 추가/수정<br>
     * label의 key : 'unused-delete-list' / value : type 변수 값<br>
     * type의 값이 exclude, include, release가 아닌 경우 BAD_REQUEST<br>
     * putLabelOnPVCList() - (조건 1~3) 검사
     * @param type 'unused-delete-list' label의 desired value
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponseResult
     * @see #putLabelOnPVCList(PersistentVolumeClaimList, String, String, String, String, KubernetesClient)
     */
    public ResponseResult putLabelOnUnusedPVCList(String type, String storageclassname, String status, String label, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;

        if (type.equals("exclude") || type.equals("include") || type.equals("release")) {
            PersistentVolumeClaimList pvcList = client.persistentVolumeClaims().inAnyNamespace().list();
            result = putLabelOnPVCList(pvcList, type, storageclassname, status, label, client);
        } else {
            result = TYPE_BAD_REQUEST;
        }
        
        ResponseResult response = new ResponseResult(APIURL_PVCS, result);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PV에 labe 추가/수정<br>
     * label의 key : 'unused-delete-list' / value : type 변수 값<br>
     * type의 값이 exclude, include, release가 아닌 경우 BAD_REQUEST<br>
     * putLabelOnPVList() - (조건 1~3) 검사
     * @param type 'unused-delete-list' label의 desired value
     * @param storageclassname (조건 1) storageClassname
     * @param status (조건 2) status.phase
     * @param label (조건 3) label (key:value / key)
     * @param request HttpServletRequest
     * @return ResponseResult
     * @see #putLabelOnPVList(PersistentVolumeList, String, String, String, String, KubernetesClient)
     */
    public ResponseResult putLabelOnUnusedPVList(String type, String storageclassname, String status, String label, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;

        if (type.equals("exclude") || type.equals("include") || type.equals("release")) {
            PersistentVolumeList pvList = client.persistentVolumes().list();
            result = putLabelOnPVList(pvList, type, storageclassname, status, label, client);
        } else {
            result = TYPE_BAD_REQUEST;
        }
        
        ResponseResult response = new ResponseResult(APIURL_PVS, result);
        return response;
    }

    /**
     * 단일 PVC 존재 여부, Unused 여부 검사, Unused인 경우 PVC에 label 추가/수정<br>
     * PVC는 name, namespace로 특정<br>
     * label의 key : 'unused-delete-list' / value : type 변수 값<br>
     * 'unused-delete-list' label의 current value가 "exclude"이고 desired value가 "include"인 경우 수정되지 않음<br>
     * type의 값이 exclude, include, release가 아닌 경우 BAD_REQUEST
     * @param pvcNamespace pvc namespace
     * @param pvcName pvc name
     * @param type 'unused-delete-list' label의 desired value
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult putLabelOnUnusedPVC(String pvcNamespace, String pvcName, String type, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).get();
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        if (pvc == null) {
            result = pvcName + " not found";
        } else if (isUnusedPVC(mountedPodList, pvc)){
            if (type.equals("exclude")) {
                if (checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                    result = "Already excluded";
                } else {
                    pvc = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                    if (checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                        result = "excluded";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("include")) {
                if (checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                    result = "Can't include " + pvcName + " / " + pvcName + " is excluded";
                } else if (checkUnusedPVCDeleteListLabel(pvc).equals("include")) {
                    result = "Already included";
                } else {
                    pvc = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                    if (checkUnusedPVCDeleteListLabel(pvc).equals("include")) {
                        result = "included";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("release")) {
                if (checkUnusedPVCDeleteListLabel(pvc).equals("null")) {
                    result = "Already released";
                } else {
                    pvc = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                    if (checkUnusedPVCDeleteListLabel(pvc).equals("null")) {
                        result = "released";
                    } else {
                        result = "failed";
                    }
                }
                
            } else {
                result = TYPE_BAD_REQUEST;
            }
        } else {
            result = pvcName + " is used resource";
        }
        
        ResponseResult response = new ResponseResult(APIURL_NS + pvcNamespace + "/pvcs/" + pvcName, result);
        return response;
    }

    /**
     * 단일 PV 존재 여부, Unused 여부 검사, Unused인 경우 PV에 label 추가/수정<br>
     * PV는 name으로 특정<br>
     * label의 key : 'unused-delete-list' / value : type 변수 값<br>
     * 'unused-delete-list' label의 current value가 "exclude"이고 desired value가 "include"인 경우 수정되지 않음<br>
     * type의 값이 exclude, include, release가 아닌 경우 BAD_REQUEST
     * @param pvName pv name
     * @param type 'unused-delete-list' label의 desired value
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult putLabelOnUnusedPV(String pvName, String type, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        PersistentVolume pv = client.persistentVolumes().withName(pvName).get();
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        if (pv == null) {
            result = pvName + " not found";
        } else if (isUnusedPV(mountedPodList, pv)){
            if (type.equals("exclude")) {
                if (checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                    result = "Already excluded";
                } else {
                    pv = client.persistentVolumes().withName(pvName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                    if (checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                        result = "excluded";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("include")) {
                if (checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                    result = "Can't include " + pvName + " / " + pvName + " is excluded";
                } else if (checkUnusedPVDeleteListLabel(pv).equals("include")) {
                    result = "Already included";
                } else {
                    pv = client.persistentVolumes().withName(pvName).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                    if (checkUnusedPVDeleteListLabel(pv).equals("include")) {
                        result = "included";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("release")) {
                if (checkUnusedPVDeleteListLabel(pv).equals("null")) {
                    result = "Already released";
                } else {
                    pv = client.persistentVolumes().withName(pvName).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                    if (checkUnusedPVDeleteListLabel(pv).equals("null")) {
                        result = "released";
                    } else {
                        result = "failed";
                    }
                }
                
            } else {
                result = TYPE_BAD_REQUEST;
            }
        } else {
            result = pvName + " is used resource";
        }

        ResponseResult response = new ResponseResult(APIURL_PVS + "/" + pvName, result);
        return response;
    }


    /**
     * 'unused-delete-list : include' label을 가지는 Kubernetes Resource를 삭제하는 command로 응답 구성
     * @param type Kubernetes Resource 종류
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedAllScript(String type) {

        String command = "kubectl delete " + type + " -l=unused-delete-list=include";
        String selfLink;

        if (type.equals("pv,pvc")) {
            selfLink = APIURL;
        } else if (type.equals("pvc")) {
            selfLink = APIURL_PVCS;
        } else {
            selfLink = APIURL_PVS;
        }

        ResponseResult response = new ResponseResult(selfLink, command);
        return response;
    }

    /**
     * 특정 namespace의 'unused-delete-list : include' label을 가지는 Kubernetes Resource를 삭제하는 command로 응답 구성
     * @param type Kubernetes Resource 종류
     * @param namespace namespace
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedAllScript(String type, String namespace) {

        String command = "kubectl delete " + type + " -l=unused-delete-list=include -n " + namespace;
        
        ResponseResult response = new ResponseResult(APIURL_NS + namespace + "/pvcs", command);
        return response;
    }

    /**
     * 단일 PVC를 삭제
     * @param pvc pvc
     * @param client KubernetesClient
     * @return boolean. 삭제 성공 시 return true
     */
    private boolean isDeleted(PersistentVolumeClaim pvc, KubernetesClient client) {
        String pvcName = pvc.getMetadata().getName();
        String pvcNamespace = pvc.getMetadata().getNamespace();

        if (client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).delete()) {
            return true;
        }
        return false;
    }

    /**
     * 단일 PV를 삭제
     * @param pv pv
     * @param client KubernetesClient
     * @return boolean. 삭제 성공 시 true
     */
    private boolean isDeleted(PersistentVolume pv, KubernetesClient client) {
        String pvName = pv.getMetadata().getName();

        if (client.persistentVolumes().withName(pvName).delete()) {
            return true;
        }
        return false;
    }

    /**
     * client가 조회할 수 있는 모든 Resource 중 'unused-delete-list : include' label 가지는 Unused Resource 삭제<br>
     * deleteUnusedPVC(), deleteUnusedPV()
     * @param request HttpServletRequest
     * @return ResponseResult
     * @see #deleteUnusedPVC(HttpServletRequest)
     * @see #deleteUnusedPV(HttpServletRequest)
     */
    public ResponseResult deleteUnusedAll(HttpServletRequest request) {

        String result = "";
        String resultPVC = deleteUnusedPVC(request).getResult();
        String resultPV = deleteUnusedPV(request).getResult();

        result += resultPVC + " / " + resultPV;

        ResponseResult response = new ResponseResult(APIURL, result);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PVC 중 'unused-delete-list : include' label 가지는 Unused PVC 삭제
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedPVC(HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        int count = (int) client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", "include").list().getItems()
            .stream()
            .filter(pvc -> isUnusedPVC(mountedPodList, pvc))
            .filter(pvc -> isDeleted(pvc, client))
            .count();

        if (count > 0) {
            result = count + " PVCs deleted";
        } else {
            result = PVC_NOT_FOUND;
        }

        ResponseResult response = new ResponseResult(APIURL_PVCS, result);
        return response;
    }

    /**
     * 특정 namespace의 PVC 중 'unused-delete-list : include' label 가지는 Unused PVC 삭제
     * @param namespace namespace 지정
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedPVC(String namespace, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        int count = (int) client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", "include").list().getItems()
            .stream()
            .filter(pvc -> isUnusedPVC(mountedPodList, pvc))
            .filter(pvc -> isDeleted(pvc, client))
            .count();

        if (count > 0) {
            result = count + " PVCs deleted";
        } else {
            result = PVC_NOT_FOUND;
        }

        ResponseResult response = new ResponseResult(APIURL_NS + namespace + "/pvcs", result);
        return response;
    }

    /**
     * client가 조회할 수 있는 모든 PV 중 'unused-delete-list : include' label 가지는 Unused PV 삭제
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedPV(HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        int count = (int) client.persistentVolumes().withLabel("unused-delete-list", "include").list().getItems()
            .stream()
            .filter(pv -> isUnusedPV(mountedPodList, pv))
            .filter(pv -> isDeleted(pv, client))
            .count();

        if (count > 0) {
            result = count + " PVs deleted";
        } else {
            result = PV_NOT_FOUND;
        }

        ResponseResult response = new ResponseResult(APIURL_PVS, result);
        return response;
    }

    /**
     * 단일 PVC 존재 여부, Unused 여부 검사, Unused인 경우 PVC 삭제<br>
     * PVC는 name, namespace로 특정
     * @param pvcNamespace pvc namespace
     * @param pvcName pvc name
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedPVC(String pvcNamespace, String pvcName, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).get();
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        if (pvc == null) {
            result = pvcName + " not found";
        } else if (isUnusedPVC(mountedPodList, pvc)) {
            if (client.persistentVolumeClaims().inNamespace(pvcNamespace).withName(pvcName).delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        } else {
            result = pvcName + " is used resource";
        }

        ResponseResult response = new ResponseResult(APIURL_NS + pvcNamespace + "/pvcs/" + pvcName, result);
        return response;
    }

    /**
     * 단일 PV 존재 여부, Unused 여부 검사, Unused인 경우 PV 삭제<br>
     * PV는 name으로 특정
     * @param pvName pv name
     * @param request HttpServletRequest
     * @return ResponseResult
     */
    public ResponseResult deleteUnusedPV(String pvName, HttpServletRequest request) {

        KubernetesClient client = getClient(request);
        String result;
        PersistentVolume pv = client.persistentVolumes().withName(pvName).get();
        List<MountedPod> mountedPodList = findAllPVCMountedByPod(client).getPodSpecMountedVolume();

        if (pv == null) {
            result = pvName + " not found";
        } else if (isUnusedPV(mountedPodList, pv)){
            if (client.persistentVolumes().withName(pvName).delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        } else {
            result = pvName + " is used resource";
        }

        ResponseResult response = new ResponseResult(APIURL_PVS + "/" + pvName, result);
        return response;
    }
}