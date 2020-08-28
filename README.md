# kuberest pv

## Usage
(TODO)

## Environments
| Name | Description |
|--|--|
| Provider | AWS EKS |
| Cluster Name | kym-cluster-01-context |
| Version | v1 |
| Resources |  |

## Specifications
- [Spring Boot - Initializr](https://start.spring.io/)
- [Kubernetes - API Client Libraries](https://kubernetes.io/ko/docs/reference/using-api/client-libraries/)   

| Name | Version | Notes |
|--|--|--|
| Java | 14 |  |
| Spring Boot | 2.3.2. RELEASE |  |
| Spring Boot - Web |  |  |
| [Fabric8](https://github.com/fabric8io/kubernetes-client) | 4.10.3 | Kubernetes Java Client |
| [Springfox](https://springfox.github.io/springfox/docs/current/) | 3.0.0 |  |
| [Springfox - Swagger UI](https://springfox.github.io/springfox/docs/current/#springfox-swagger-ui) |  |  |   
| Lombok | 1.18.12 |  |
| Maven Javadoc Plugin | 3.1.0 |  |

## Release
```bash
kubectl create -f kubernetes/sa-crb.yaml
kubectl create -f kubernetes/deployment.yaml
kubectl expose deployment.apps/kuberest --type=LoadBalancer --name=kuberest -n kuberest
kubectl edit service/kubernetes -n kuberest
```
아래의 내용 추가
```yaml
metadata: 
  annotations:
    service.beta.kubernetes.io/aws-load-balancer-type: nlb
```

## References
- [Swagger](https://swagger.io/)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)
