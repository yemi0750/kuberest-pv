package com.cloudzcp.kuberest.api.core.service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.springframework.stereotype.Service;

import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.api.model.PersistentVolumeList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

@Service
public class UnusedResourceService {

    private static KubernetesClient client = new DefaultKubernetesClient();

    private static String apiurl = "api/v1/unused";
    private static String apiurl_ns = "api/v1/unused/ns/";
    private static String apiurl_pvcs = "api/v1/unused/pvcs";
    private static String apiurl_pvs = "api/v1/unused/pvs";

    public JSONArray findAllPVCMountedByPod(){

        JSONArray pod_object_list = new JSONArray();

        client.pods().inAnyNamespace().list().getItems().forEach(
            pod -> {
                JSONObject object = new JSONObject();
                JSONArray object_list = new JSONArray();
                object.put("name", pod.getMetadata().getName());
                object.put("namespace", pod.getMetadata().getNamespace());

                pod.getSpec().getVolumes().forEach(
                    volume -> {
                        if (volume.getPersistentVolumeClaim() != null){
                            JSONObject pvc_temp = new JSONObject();
                            String claimName = volume.getPersistentVolumeClaim().getClaimName();
                            pvc_temp.put("claimName", claimName);
                            pvc_temp.put("boundedPV", client.persistentVolumeClaims().inNamespace(pod.getMetadata().getNamespace()).withName(claimName).get().getSpec().getVolumeName());
                            object_list.add(pvc_temp);
                        }
                        
                    }
                );

                if (object_list.size() > 0) {
                    object.put("pvcs", object_list);
                    pod_object_list.add(object);
                }
            }
        );

        return pod_object_list;
    }

    public JSONObject printPVCMountedByPod(){

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        JSONArray mountedByPods = findAllPVCMountedByPod();

        data.put("PodSpecMountVolume", mountedByPods);
        response.put("data", data);

        return response;
    }

    public Boolean isMountedByPod(JSONArray mountedByPods, String claimName, String namespace){

        Boolean result = false;

        if (!mountedByPods.isEmpty()) {
            for (int i=0; i<mountedByPods.size(); i++){
                JSONObject pod = (JSONObject)mountedByPods.get(i);
                JSONArray pvcs = (JSONArray)pod.get("pvcs");
                for (int j=0; j<pvcs.size(); j++) {
                    if (((JSONObject)pvcs.get(j)).get("claimName").toString().equals(claimName) && pod.get("namespace").toString().equals(namespace)){
                        result = true;
                        return result;
                    }
                }
            }
        }

        return result;
    }

    public Boolean isUnusedPVC(JSONArray mountedByPods, PersistentVolumeClaim pvc) {

        if (pvc.getMetadata().getDeletionTimestamp() != null){
            //terminating
            return false;
        }
        if (!isMountedByPod(mountedByPods, pvc.getMetadata().getName(), pvc.getMetadata().getNamespace())){
            return true;
        } else {
            return false;
        }
    }

    public Boolean isUnusedPV(JSONArray mountedByPods, PersistentVolume pv) {

        Boolean result = false;

        if (pv.getMetadata().getDeletionTimestamp() != null) {
            //terminating
            return false;
        }
        if (!pv.getStatus().getPhase().equals("Bound")) {
            return true;
        }
        if (pv.getSpec().getClaimRef() != null){
            if (!isMountedByPod(mountedByPods, pv.getSpec().getClaimRef().getName(), pv.getSpec().getClaimRef().getNamespace())) {
                return true;
            } else {
                return false;
            }
        }

        return result;
    }

    public String checkUnusedPVCType(JSONArray mountedByPods, PersistentVolumeClaim pvc) {

        String result = "";

        if (!isMountedByPod(mountedByPods, pvc.getMetadata().getName(), pvc.getMetadata().getNamespace())){
            if (!pvc.getStatus().getPhase().equals("Bound")) {
                result = "PVC is not mounted by Pod / PVC Status is not 'Bound'";
            } else {
                result = "PVC is not mounted by Pod";
            }
        }

        return result;
    }

    public String checkUnusedPVType(JSONArray mountedByPods, PersistentVolume pv) {

        String result = "";

        if (!pv.getStatus().getPhase().equals("Bound")) {
            return "PV Status is not 'Bound'";
        }
        if (pv.getSpec().getClaimRef() != null){
            if (!isMountedByPod(mountedByPods, pv.getSpec().getClaimRef().getName(), pv.getSpec().getClaimRef().getNamespace())) {
                result = "PVC("+pv.getSpec().getClaimRef().getName()+") connected to PV is not mounted by Pod";
            }
        }

        return result;
    }

    public JSONObject getPVCList(PersistentVolumeClaimList pvc_list, Boolean count, String storageclassname, String status, String label) {

        JSONObject pvc_object_count = new JSONObject();
        JSONArray pvc_object_list = new JSONArray();
        JSONObject pvc_object = new JSONObject();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (count == null || !count) {
            pvc_list.getItems().forEach(
                pvc -> {
                    if(isUnusedPVC(mountedByPods, pvc)){
                        if (checkPVCFields(pvc, storageclassname, status, label)){
                            JSONObject object = new JSONObject();
                            object.put("name", pvc.getMetadata().getName());
                            object.put("namespace", pvc.getMetadata().getNamespace());
                            object.put("status", pvc.getStatus().getPhase());
                            object.put("boundedPV", pvc.getSpec().getVolumeName());
                            object.put("storageClassName", pvc.getSpec().getStorageClassName());
                            object.put("unusedType", checkUnusedPVCType(mountedByPods, pvc));
                            pvc_object_list.add(object);
                        }
                    }
                }
            );
            pvc_object.put("unusedList", pvc_object_list);
        }
        if (count == null || count) {
            pvc_object_count.put("total", pvc_list.getItems().size());
            pvc_object_count.put("unused", pvc_list.getItems().stream().filter(pvc -> isUnusedPVC(mountedByPods, pvc)&&checkPVCFields(pvc, storageclassname, status, label)).count());
            pvc_object.put("count", pvc_object_count);
        }

        return pvc_object;
    }

    public JSONObject getPVList(PersistentVolumeList pv_list, Boolean count, String storageclassname, String status, String label) {

        JSONObject pv_object_count = new JSONObject();
        JSONArray pv_object_list = new JSONArray();
        JSONObject pv_object = new JSONObject();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (count == null || !count) {
            pv_list.getItems().forEach(
                pv -> {
                    if(isUnusedPV(mountedByPods, pv)){
                        if (checkPVFields(pv, storageclassname, status, label)){
                            JSONObject object = new JSONObject();
                            object.put("name", pv.getMetadata().getName());
                            object.put("status", pv.getStatus().getPhase());
                            object.put("volumeReclaimPolicy", pv.getSpec().getPersistentVolumeReclaimPolicy());
                            object.put("storageClassName", pv.getSpec().getStorageClassName());
                            object.put("unusedType", checkUnusedPVType(mountedByPods, pv));
                            if (pv.getSpec().getClaimRef() != null) {
                                object.put("boundedPVC", pv.getSpec().getClaimRef().getName());
                            }
                            pv_object_list.add(object);
                        }
                    }
                }
            );
            pv_object.put("unusedList", pv_object_list);
        }
        if (count == null || count) {
            pv_object_count.put("total", pv_list.getItems().size());
            pv_object_count.put("unused", pv_list.getItems().stream().filter(pv -> isUnusedPV(mountedByPods, pv)&&checkPVFields(pv, storageclassname, status, label)).count());
            pv_object.put("count", pv_object_count);
        }

        return pv_object;
    }
    
    public JSONObject findAll(Boolean count, String deleteList, String storageclassname, String status, String label){

        JSONObject pvc_object = new JSONObject();
        JSONObject pv_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolumeClaimList pvc_list;
        PersistentVolumeList pv_list;

        data.put("selfLink", apiurl);
        if (deleteList != null) {
            pvc_list = client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", deleteList).list();
            pv_list = client.persistentVolumes().withLabel("unused-delete-list", deleteList).list();
        } else {
            pvc_list = client.persistentVolumeClaims().inAnyNamespace().list();
            pv_list = client.persistentVolumes().list();
        }

        pv_object = getPVList(pv_list, count, storageclassname, status, label);
        data.put("PV", pv_object);

        pvc_object = getPVCList(pvc_list, count, storageclassname, status, label);
        data.put("PVC", pvc_object);
        response.put("data", data);

        return response;
    }

    public JSONObject findAll(String namespace, Boolean count, String deleteList, String storageclassname, String status, String label){

        JSONObject pvc_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolumeClaimList pvc_list;

        data.put("selfLink", apiurl_ns+namespace);
        if (deleteList != null) {
            pvc_list = client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", deleteList).list();
        } else {
            pvc_list = client.persistentVolumeClaims().inNamespace(namespace).list();
        }

        pvc_object = getPVCList(pvc_list, count, storageclassname, status, label);
        data.put("PVC", pvc_object);
        response.put("data", data);

        return response;
    }

    public JSONObject findPVCList(String deleteList, String storageclassname, String status, String label){

        JSONObject pvc_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolumeClaimList pvc_list;

        data.put("selfLink", apiurl_pvcs);
        if (deleteList != null) {
            pvc_list = client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", deleteList).list();
        } else {
            pvc_list = client.persistentVolumeClaims().inAnyNamespace().list();
        }

        pvc_object = getPVCList(pvc_list, false, storageclassname, status, label);
        data.put("PVC", pvc_object);
        response.put("data", data);
        return response;
    }

    public JSONObject findPVCList(String namespace, String deleteList, String storageclassname, String status, String label){

        JSONObject pvc_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolumeClaimList pvc_list;

        data.put("selfLink", apiurl_ns+namespace+"/pvcs");
        if (deleteList != null) {
            pvc_list = client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", deleteList).list();
        } else {
            pvc_list = client.persistentVolumeClaims().inNamespace(namespace).list();
        }

        pvc_object = getPVCList(pvc_list, false, storageclassname, status, label);
        data.put("PVC", pvc_object);
        response.put("data", data);
        return response;
    }

    public JSONObject findPVC(String namespace, String name){

        JSONObject pvc_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(name).get();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (pvc == null) {

        } else if (!isUnusedPVC(mountedByPods, pvc)) {
            data.put("unusedType", name+" is used resource");
        } else {
            JSONObject object = new JSONObject();
            object.put("unusedType", checkUnusedPVCType(mountedByPods, pvc));
            object.put("detail", pvc);
            pvc_object.put(name, object);
            data.put("PVC", pvc_object);
        }

        data.put("selfLink", apiurl_ns+namespace+"/pvcs/"+name);
        response.put("data", data);
        return response;
    }

    public JSONObject findPVList(String deleteList, String storageclassname, String status, String label){

        JSONObject pv_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolumeList pv_list;

        data.put("selfLink", apiurl_pvs);
        if (deleteList != null) {
            pv_list = client.persistentVolumes().withLabel("unused-delete-list", deleteList).list();
        } else {
            pv_list = client.persistentVolumes().list();
        }

        pv_object = getPVList(pv_list, false, storageclassname, status, label);
        data.put("PV", pv_object);
        response.put("data", data);
        return response;
    }

    public JSONObject findPV(String name){
        
        JSONObject pv_object = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        PersistentVolume pv = client.persistentVolumes().withName(name).get();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (pv == null) {

        } else if (!isUnusedPV(mountedByPods, pv)) {
            data.put("unusedType", name+" is used resource");
        } else {
            JSONObject object = new JSONObject();
            object.put("unusedType", checkUnusedPVType(mountedByPods, pv));
            object.put("detail", pv);
            pv_object.put(name, object);
            data.put("PVC", pv_object);
        }

        data.put("selfLink", apiurl_pvs+name);
        response.put("data", data);
        return response;
    }

    public void createJSONFile(JSONObject obj, String filename){

        String home = System.getProperty("user.home");
        FileWriter file;

        try {
            file = new FileWriter(home + "/Downloads/" + filename + ".json");
            file.write(obj.toJSONString());
            file.flush();
            file.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        
    }

    public JSONObject deleteUnusedPVC(String namespace, String pvc_name) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = pvc_name+" is used resource";

        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(pvc_name).get();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (pvc == null) {
            result = pvc_name+" not found";
        }
        if (isUnusedPVC(mountedByPods, pvc)){
            if (client.persistentVolumeClaims().inNamespace(namespace).withName(pvc_name).delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        }

        data.put("name", pvc_name);
        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public JSONObject deleteUnusedPV(String pv_name) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = pv_name+" is used resource";

        PersistentVolume pv = client.persistentVolumes().withName(pv_name).get();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (pv == null) {
            result = pv_name+" not found";
        }
        if (isUnusedPV(mountedByPods, pv)){
            if (client.persistentVolumes().withName(pv_name).delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        }

        data.put("name", pv_name);
        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public String checkUnusedPVCDeleteListLabel(PersistentVolumeClaim pvc) {

        String result = "";

        if (pvc.getMetadata().getLabels() == null) {
            return "null";
        } else {
            result = pvc.getMetadata().getLabels().get("unused-delete-list");
        }

        return (result == null) ? "null" : result;
    }

    public String checkUnusedPVDeleteListLabel(PersistentVolume pv) {

        String result = "";

        if (pv.getMetadata().getLabels() == null) {
            return "null";
        } else {
             result = pv.getMetadata().getLabels().get("unused-delete-list");
        }

        return (result == null) ? "null" : result;
    }

    //PVC가 세가지 조건을 충족하는지 여부를 검사
    public Boolean checkPVCFields(PersistentVolumeClaim pvc, String storageclassname, String status, String label) {

        Boolean result = true;

        if (storageclassname != null){
            result = result && pvc.getSpec().getStorageClassName() != null && pvc.getSpec().getStorageClassName().equals(storageclassname);
        }
        if (status != null) {
            result = result && pvc.getStatus().getPhase().equals(status);
        }
        if (label != null) {
            if (pvc.getMetadata().getLabels() == null) {
                result = false;
            } else {
                String[] key_value = label.split("=");
                if (key_value.length > 1) { //value도 있는 경우
                    result = result && pvc.getMetadata().getLabels().get(key_value[0]) != null && pvc.getMetadata().getLabels().get(key_value[0]).equals(key_value[1]);
                } else {
                    result = result && pvc.getMetadata().getLabels().get(key_value[0]) != null;
                }
            }
        }

        return result;
    }

    public Boolean checkPVFields(PersistentVolume pv, String storageclassname, String status, String label) {

        Boolean result = true;

        if (storageclassname != null){
            result = result && pv.getSpec().getStorageClassName() != null && pv.getSpec().getStorageClassName().equals(storageclassname);
        }
        if (status != null) {
            result = result && pv.getStatus().getPhase().equals(status);
        }
        if (label != null) {
            if (pv.getMetadata().getLabels() == null) {
                result = false;
            } else {
                String[] key_value = label.split("=");
                if (key_value.length > 1) { //value도 있는 경우
                    result = result && pv.getMetadata().getLabels().get(key_value[0]) != null && pv.getMetadata().getLabels().get(key_value[0]).equals(key_value[1]);
                } else {
                    result = result && pv.getMetadata().getLabels().get(key_value[0]) != null;
                }
            }
        }

        return result;
    }

    public String putLabelOnPVCList(PersistentVolumeClaimList  pvc_list, String type, String storageclassname, String status, String label) {

        AtomicInteger count = new AtomicInteger();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        pvc_list.getItems().forEach(
            pvc -> {
                if (isUnusedPVC(mountedByPods, pvc)){
                    if (checkPVCFields(pvc, storageclassname, status, label)){
                        String pvc_name = pvc.getMetadata().getName();
                        String pvc_namespace = pvc.getMetadata().getNamespace();
                        if (type.equals("exclude")) {
                            PersistentVolumeClaim pvc_temp = client.persistentVolumeClaims().inNamespace(pvc_namespace).withName(pvc_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                            if (checkUnusedPVCDeleteListLabel(pvc_temp).equals("exclude")) {
                                count.incrementAndGet();
                            }
                        } else if (type.equals("include")) {
                            if (!checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                                PersistentVolumeClaim pvc_temp = client.persistentVolumeClaims().inNamespace(pvc_namespace).withName(pvc_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                                if (checkUnusedPVCDeleteListLabel(pvc_temp).equals("include")) {
                                    count.incrementAndGet();
                                }
                            }
                        } else if (type.equals("release")) {
                            PersistentVolumeClaim pvc_temp = client.persistentVolumeClaims().inNamespace(pvc_namespace).withName(pvc_name).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                            if (checkUnusedPVCDeleteListLabel(pvc_temp).equals("null")) {
                                count.incrementAndGet();
                            }
                        }
                    }
                }
            }
        );

        return Integer.toString(count.get())+" pvcs "+type+"d";
    }

    public String putLabelOnPVList(PersistentVolumeList pv_list, String type, String storageclassname, String status, String label) {

        AtomicInteger count = new AtomicInteger();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        pv_list.getItems().forEach(
            pv -> {
                if (isUnusedPV(mountedByPods, pv)){
                    if (checkPVFields(pv, storageclassname, status, label)){
                        String pv_name = pv.getMetadata().getName();
                        if (type.equals("exclude")) {
                            PersistentVolume pv_temp = client.persistentVolumes().withName(pv_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                            if (checkUnusedPVDeleteListLabel(pv_temp).equals("exclude")) {
                                count.incrementAndGet();
                            }
                        } else if (type.equals("include")) {
                            if (!checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                                PersistentVolume pv_temp = client.persistentVolumes().withName(pv_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                                if (checkUnusedPVDeleteListLabel(pv_temp).equals("include")) {
                                    count.incrementAndGet();
                                }
                            }
                        } else if (type.equals("release")) {
                            PersistentVolume pv_temp = client.persistentVolumes().withName(pv_name).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                            if (checkUnusedPVDeleteListLabel(pv_temp).equals("null")) {
                                count.incrementAndGet();
                            }
                        }
                    }
                }
            }
        );

        return Integer.toString(count.get())+" pvs "+type+"d";
    }

    public JSONObject putLabelOnUnusedAll(String type, String storageclassname, String status, String label) {
        
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        String result = "";
        String result_pvc = ((JSONObject)putLabelOnUnusedPVCList(type, storageclassname, status, label).get("data")).get("result").toString();
        String result_pv = ((JSONObject)putLabelOnUnusedPVList(type, storageclassname, status, label).get("data")).get("result").toString();
        result += result_pvc + " / " + result_pv;

        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public JSONObject putLabelOnUnusedPVCList(String namespace, String type, String storageclassname, String status, String label) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result;

        if (type.equals("exclude") || type.equals("include") || type.equals("release")) {
            PersistentVolumeClaimList pvc_list = client.persistentVolumeClaims().inNamespace(namespace).list();
            result = putLabelOnPVCList(pvc_list, type, storageclassname, status, label);
        } else {
            result = "bad request / type = [ exclude / include / release ]";
        }
        
        data.put("selfLink", apiurl_ns+namespace+"/pvcs");
        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public JSONObject putLabelOnUnusedPVCList(String type, String storageclassname, String status, String label) {
        
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result;

        if (type.equals("exclude") || type.equals("include") || type.equals("release")) {
            PersistentVolumeClaimList pvc_list = client.persistentVolumeClaims().inAnyNamespace().list();
            result = putLabelOnPVCList(pvc_list, type, storageclassname, status, label);
        } else {
            result = "bad request / type = [ exclude / include / release ]";
        }
        
        data.put("selfLink", apiurl_pvcs);
        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public JSONObject putLabelOnUnusedPVList(String type, String storageclassname, String status, String label) {
        
        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result;

        if (type.equals("exclude") || type.equals("include") || type.equals("release")) {
            PersistentVolumeList pv_list = client.persistentVolumes().list();
            result = putLabelOnPVList(pv_list, type, storageclassname, status, label);
        } else {
            result = "bad request / type = [ exclude / include / release ]";
        }
        
        data.put("selfLink", apiurl_pvs);
        data.put("result", result);
        response.put("data", data);
        return response;

    }

    public JSONObject putLabelOnUnusedPVC(String namespace, String pvc_name, String type) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = "";

        PersistentVolumeClaim pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(pvc_name).get();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (pvc == null) {
            result = pvc_name+" not found";
        } else if (!isUnusedPVC(mountedByPods, pvc)){
            result = pvc_name+" is used resource";
        } else {
            if (type.equals("exclude")) {
                if (checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                    result = "Already excluded";
                } else {
                    pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(pvc_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                    if (checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                        result = "excluded";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("include")) {
                if (checkUnusedPVCDeleteListLabel(pvc).equals("exclude")) {
                    result = "Can't include "+pvc_name+" / "+pvc_name+" is excluded";
                } else if (checkUnusedPVCDeleteListLabel(pvc).equals("include")) {
                    result = "Already included";
                } else {
                    pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(pvc_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                    if (checkUnusedPVCDeleteListLabel(pvc).equals("include")) {
                        result = "included";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("release")) {
                if (checkUnusedPVCDeleteListLabel(pvc).equals("release")) {
                    result = "Already included";
                } else {
                    pvc = client.persistentVolumeClaims().inNamespace(namespace).withName(pvc_name).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                    if (checkUnusedPVCDeleteListLabel(pvc).equals("null")) {
                        result = "released";
                    } else {
                        result = "failed";
                    }
                }
                
            } else {
                result = "bad request / type = [ exclude / include / release ]";
            }
        }
        
        data.put("selfLink", apiurl_ns+namespace+"/pvcs/"+pvc_name);
        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public JSONObject putLabelOnUnusedPV(String pv_name, String type) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = "";

        PersistentVolume pv = client.persistentVolumes().withName(pv_name).get();
        JSONArray mountedByPods = findAllPVCMountedByPod();

        if (pv == null) {
            result = pv_name+" not found";
        } else if (!isUnusedPV(mountedByPods, pv)){
            result = pv_name+" is used resource";
        } else {
            if (type.equals("exclude")) {
                if (checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                    result = "Already excluded";
                } else {
                    pv = client.persistentVolumes().withName(pv_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "exclude").endMetadata().done();
                    if (checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                        result = "excluded";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("include")) {
                if (checkUnusedPVDeleteListLabel(pv).equals("exclude")) {
                    result = "Can't include "+pv_name+" / "+pv_name+" is excluded";
                } else if (checkUnusedPVDeleteListLabel(pv).equals("include")) {
                    result = "Already included";
                } else {
                    pv = client.persistentVolumes().withName(pv_name).edit().editOrNewMetadata().addToLabels("unused-delete-list", "include").endMetadata().done();
                    if (checkUnusedPVDeleteListLabel(pv).equals("include")) {
                        result = "included";
                    } else {
                        result = "failed";
                    }
                }
            } else if (type.equals("release")) {
                if (checkUnusedPVDeleteListLabel(pv).equals("release")) {
                    result = "Already included";
                } else {
                    pv = client.persistentVolumes().withName(pv_name).edit().editMetadata().removeFromLabels("unused-delete-list").endMetadata().done();
                    if (checkUnusedPVDeleteListLabel(pv).equals("null")) {
                        result = "released";
                    } else {
                        result = "failed";
                    }
                }
                
            } else {
                result = "bad request / type = [ exclude / include / release ]";
            }
        }

        data.put("selfLink", apiurl_pvs+pv_name);
        data.put("result", result);
        response.put("data", data);
        return response;
    }

    public JSONObject deleteUnusedAllScript(String type) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        String command = "kubectl delete "+type+" -l=unused-delete-list=include";

        data.put("command", command);
        response.put("data", data);

        return response;
    }

    public JSONObject deleteUnusedAllScript(String type, String namespace) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        String command = "kubectl delete "+type+" -l=unused-delete-list=include -n "+namespace;

        data.put("command", command);
        response.put("data", data);

        return response;
    }

    public JSONObject deleteUnusedAll() {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();

        String result = "";
        String result_pvc = ((JSONObject)deleteUnusedPVC().get("data")).get("result").toString();
        String result_pv = ((JSONObject)deleteUnusedPV().get("data")).get("result").toString();
        if (!result_pvc.equals("deleted")) {
            result += "PVC / " + result_pvc;
        }
        if (!result_pv.equals("deleted")) {
            result = "PV / " + result_pv;
        }

        if (result.equals("")) {
            result = "deleted";
        }
        data.put("result", result);
        response.put("data", data);

        return response;
    }

    public JSONObject deleteUnusedPVC() {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = "";

        int size = client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", "include").list().getItems().size();
        if (size > 0) {
            if (client.persistentVolumeClaims().inAnyNamespace().withLabel("unused-delete-list", "include").delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        } else {
            result = "No results found. Please add unused-delete-list PVC";
        }

        data.put("result", result);
        response.put("data", data);

        return response;
    }

    public JSONObject deleteUnusedPVC(String namespace) {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = "";

        int size = client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", "include").list().getItems().size();
        if (size > 0) {
            if (client.persistentVolumeClaims().inNamespace(namespace).withLabel("unused-delete-list", "include").delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        } else {
            result = "No results found. Please add unused-delete-list PVC";
        }

        data.put("result", result);
        response.put("data", data);

        return response;
    }

    public JSONObject deleteUnusedPV() {

        JSONObject data = new JSONObject();
        JSONObject response = new JSONObject();
        String result = "";

        int size = client.persistentVolumes().withLabel("unused-delete-list", "include").list().getItems().size();
        if (size > 0) {
            if (client.persistentVolumes().withLabel("unused-delete-list", "include").delete()) {
                result = "deleted";
            } else {
                result = "failed";
            }
        } else {
            result = "No results found. Please add unused-delete-list PV";
        }

        data.put("result", result);
        response.put("data", data);

        return response;
    }
}