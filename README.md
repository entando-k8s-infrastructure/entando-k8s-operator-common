[![Build Status](https://img.shields.io/endpoint?url=https%3A%2F%2Fstatusbadge-jx.apps.serv.run%2Fentando-k8s%2Fentando-k8s-operator-common)](https://github.com/entando-k8s/devops-results/tree/logs/jenkins-x/logs/entando-k8s/entando-k8s-operator-common/master)
[Behavioural Scenarios](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/PR-60/allure-maven-plugin/index.html#behaviors)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=alert_status)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=coverage)](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/master/jacoco/index.html)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=vulnerabilities)](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/master/dependency-check-report.html)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=code_smells)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=security_rating)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=entando-k8s_entando-k8s-operator-common&metric=sqale_index)](https://sonarcloud.io/dashboard?id=entando-k8s_entando-k8s-operator-common)


# Entando Kubernetes Operator Common

This project provides a library that can be used to facilitate the development of run-to-completion style
Kubernetes controllers. More specifically, this library would be useful if you have a Kubernetes 
CustomResourceDefinition in response to which you would like create a Kubernetes Deployment and all the resources
required to support it. Typical supporting resources  would be a PersistentVolumeClaims, one or more Secrets, a Service and
optionally an Ingress. If this is indeed your goal, you will find this library useful
and you can consider using it to easily implement your own Kubernetes custom controller.

Once you have implemented some controller code you can build a Container and make your controller the default entrypoint of
your Container. At this point, you are ready to explore our [entando-k8s-controller-coordinator](https://github.com/entando-k8s/entando-k8s-controller-coordinator/blob/master/README.md)
for further instructions on how to register your container with our operator so that it can be executed automatically
when instances of your CustomResourceDefinition are created or modified.

This library as it currently stands is still packaged as a Java Maven library. Unfortunately this means that
you can only consume it from a Java application. However, our plan is to package it as an entirely separate 
executable so that you can access it from your programming language or platform of choice. In the meantime, 
we have simplified communication with this library down tow literally two simple interfaces
that exchange JSON/YAML data structures with the library. We are planning to implement these simple
interfaces in multiple programming languages and their implementations will simply delegate to the
commandline application. These interfaces are:

## [DeploymentProcessor](src/main/java/org/entando/kubernetes/controller/spi/command/DeploymentProcessor.java)

This interface allows you to send your implementation of the [org.entando.kubernetes.controller.spi.deployable.Deployable](src/main/java/org/entando/kubernetes/controller/spi/deployable/Deployable.java)
interfaces to the library. Depending on the other interfaces you have implemented, and the values returned from their getter
methods, this library will then create the Kubernetes resources to facilitate the deployment of your resource.

## [CapabilityProvider](src/main/java/org/entando/kubernetes/controller/spi/capability/CapabilityProvider.java)

This interface allows you to first deploy the common, reusable capabilities that you require in your environment. It
takes a very simple data object as input parameter, a [CapabilityRequirement](https://github.com/entando-k8s/entando-k8s-custom-model/blob/ENG-2284_removing_cluster_infrastructure/src/main/java/org/entando/kubernetes/model/capability/CapabilityRequirement.java)
as input parameter, along with the custom resource you require the capability for. This request will in turn inspect
the current cluster state to determine if there is already a matching capability, and if not, which controller image
is best suited to deploy that capability for you.

## [KubernetesClientForControllers](src/main/java/org/entando/kubernetes/controller/spi/client/KubernetesClientForControllers.java)

This interface doesn't interact with the library directly, but with the Kuberenetes API. It provides some common methods
your custom controller may need to use, specifically in interacting with the standard [status object](https://github.com/entando-k8s/entando-k8s-custom-model/blob/ENG-2284_removing_cluster_infrastructure/src/main/java/org/entando/kubernetes/model/common/EntandoCustomResourceStatus.java)
we require you to use. 

# What do I need to implement?

In implementing your own Kubernetes controller, you basically need to focus on the implementation of 3 different classes.

## Your controller class

This is simply the Java main class that will be executed when your Container spins up. There are no real requirement here, 
except for the fact that you would have to make an instance of the DeploymentProcessor and KubernetesClientForControllers
each available to start with. For most of our projects we use Quarkus and the PicoCLI framework to implement our
controllers. The minimum controller would look something like the [BasicController](src/test/java/org/entando/kubernetes/BasicController.java)
that we use in our behavioural scenario testing. (like BDD except without feature files). As you can see from this example,
your controller will basically retrieve the custom resource being observed, prepare an instance of the Deployable interface
and send it to the DeploymentProcessor.

## Your implementation of the [Deployable](src/main/java/org/entando/kubernetes/controller/spi/deployable/Deployable.java) interface.

The Deployable interface will result in a single Deployment on Kubernetes, and based on how you implement its getter
methods, any selection of other typical Kubernetes resources you would require, such as PersistentVolumeClaims, 
Secrets, Services and Ingresses. In fact, more advanced features are available such as creating a Public OIDC Client 
and a TLS Secret for your Ingress, or making Database schemas available for your datasources, but more about
this later. Apart from all the values return from the getter methods of this class, you also need to provide one
or more implementations of the DeployableContainer interface.

## Your implementation of the [DeployableContainer](src/main/java/org/entando/kubernetes/controller/spi/container/DeployableContainer.java) interface.

The DeployableContainer interface will result in one single Container on the Pod template of your Deployment. This is
where you get to specify which image to use, how much of each resource to allocate and what health checks to perform. Again, you
could even specify a non-public OIDC client to create, and provide more specific details of the database schemas you may
require and possible schema initialization logic you may want to perform.

It is important to note that, by default, this library supports the idea that you implement interfaces that calculate 
the values to provide for the DeploymentProcessor. This was by design to encourage a more dynamic, programmatic approach
to creating the Kubernetes resources. We have implemented some special logic to serialize these interfaces to YAML and
back. Any state that is not made available by an interface will not be serialized, so please don't deviate from the 
public contract of these interfaces and expect anything different to happen.

# How do I start?

We have decided on an approach where we illustrate how to implement the various interfaces and send them to this 
library in a set of exhaustive [behavioural scenarios](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/PR-60/allure-maven-plugin/index.html#behaviors) that we run on every pull request 
This guide will take you through each of the scenarios and give a brief overview of what we do in the scenarios.
We will start off with the most simple possible scenarios and will gradually increase the complexity. You can follow
this up to the point where you feel your requirements have been met.

For each of the scenarios, please navigate to our [behavioral scenarios](https://entando-k8s.github.io/devops-results/entando-k8s-operator-common/PR-60/allure-maven-plugin/index.html#behaviors)
and type the identifier of the scenario in the search box.  



## Step 1: The minimal deployment

### Absolute minimal deployment 

Scenario identifier: absoluteMinimalDeployment


In this scenario we illustrate how you only require a Port and
an Image on your DeployableContainer. In fact, even the port will default to 8080. however, the resulting deployment
is not particularly useful as it doesn't even expose the Port on a Service. You will also notice how the default 
resource limits are imposed from the DeployableContainer, and how the default probes are created.

### Specifying Environment Variables

Scenario identifier: minimalDeploymentWithEnvironmentVariables

In this scenario we illustrate how to specify environment variables. You can specify environment variables directly, or
you could use a reference to a Secret or a ConfigMap. This example illustrates all three cases. Take note that in this
particular scenario, the Secret and ConfigMap are both assumed to exist before the custom resource is created.

### Turning off resource limits

Scenario identifier: absoluteMinimalDeploymentWithoutResourceLimits

Under certain conditions, you may want to turn off all resource limits on your container. In sandbox environments
where resource usage is not an issue, this could significantly improve performance, especially at startup time.

## Step 2: Using Persistent Volumes

### Specifying PersistentVolumeClaims to create and mount

Scenario identifier: createPersistentVolumeClaim

You can control the state of PersistentVolumeClaims to be created directly from your DeployableContainer. You can specify
the storageClass and accessmode required. In the Deployment, you can specify the operating system  user/group ID
to be used as owner for the VolumeMount from the Deployable, and you can specify the mount path from the DeployableContainer

### Using the Entando Operator Config to provide default values for PersistentVolumeClaims

Scenario identifier: createPersistentVolumeClaimUsingDefaults (TBD)

You can also allow the Entando Operator Config to provide defaults for clustered and non-clustered storage classes, and
also for accessmodes

## Step 3: Using Databases

### Requesting a Database Capability on-demand

Scenario identifier: requestDatabaseCapabilityOnDemandAndConnectToIt

If you need to connect to a database, the Entando Operator can find a matching Database provider, or deploy one
on-demand for you. Just submit a CapabilityRequirement to the CapabilityProvider. It will forward your request
to the correct Controller and prepare the necessary connection info for you. Once a database is available, you
can then specify the Database Schema you require and its associated credentials. You can also optionally provide
an image that will populate the initial state of the Schema. (TODO split this into 2 different test cases)

## Step 4: Exposing your container over HTTP

### Requesting a OIDC Capability on-demand

Scenario identifier: requestOidcCapabilityOnDemandAndConnectToIt


## Step 5: Using an OIDC provider (e.g. Keycloak)

### Requesting a OIDC Capability on-demand

Scenario identifier: requestOidcCapabilityOnDemandAndConnectToIt

Today, most web applications utilize OIDC for single sign on. You can request an OIDC capability and once again
the Entando Operator will find the correct Controller to forward you request to. Once the OIDC service has been
made available to your Deployment, you can also create a Client ID and Client Secret on-demand. In Keycloak, 
the Client Secret gets generated for you by Keycloak, but you can use the resulting Kubernetes Secret to configure
your single sign on. Along with your required ClientId, you can also specify roles as well as permissions for
your Keycloak Client service account on other pre-existing Keycloak Clients. In addition to this, you can also
specify an alternative Realm if you would like. This is useful in scenarios 
where you have to share a single Keycloak instance in a multi-tenant setup.

## Step 6: Using more advanced Capability resolution options

### Requesting a OIDC Capability on-demand

Scenario identifier: requestOidcCapabilityOnDemandAndConnectToIt

## Step 7: More advanced Capability resolution techniques.

## Requesting a Capability to be shared at the Cluster level

Scenario identifier: shouldProvideClusterScopeCapability

## Requesting a Capability to be shared at the Namespace level

Scenario identifier: shouldProvideNamespaceScopedCapability

## Requesting a Capability to be shared at the Cluster level but using Labels for identification

Scenario identifier: shouldProvideLabeledCapability

## Requesting a Capability to be dedicated to the requesting resource

Scenario identifier: shouldProvideDedicatedCapability

## Requesting a Capability with a specific name and namespace

Scenario identifier: shouldProvideSpecifiedCapability

## Step 8: Implementing your own Capability Controller

## Typical Directly Deployed Capability

Scenario identifier: ???

## A Capability that points to an externally provided service

Scenario identifier: ???
__

## Some more key interfaces
Here are some of the key interfaces to implement by consumers 

### org.entando.kubernetes.controller.spi.deployable.Deployable  

This is a generic interface that represents a common deployable pod. As a pod, it has a list of containers (represented by a list of DeployableContainer).
There are some classes implementing the Deployable interface, representing each one a different type of deployable (e.g. DatabaseDeployable, PublicIngressingDeployable)
The DeploymentCreator class is responsible to create Deployable instances.

### org.entando.kubernetes.controller.spi.deployable.Secretive

This interface has to be implemented by those Deployable that need Kubernetes secrets for working.
Currently all you have to do with this interface is to override `getSecrets()` method returning all needed secrets and they will be bound to the pod that is about to be deployed.

### org.entando.kubernetes.controller.spi.container.TrustStoreAwareContainer

This interface offers a predefined way to add some TLS environment variable to the implementing DeployableContainer.

### org.entando.kubernetes.controller.spi.container.DeployableContainer

Base interface representing a container to deploy inside a Pod. So it needs at least:

- the docker image of which instantiate the container
- a name qualifier to append to the container name
- the port exposed by the container

### org.entando.kubernetes.controller.spi.container.ServiceBackingContainer  

TBD

### org.entando.kubernetes.controller.spi.deployable.IngressingDeployable  

This interface has to be implemented by those Deployable that contain some IngressingContainer inside their containers list.
Its main functionality is to easily retrieve the IngressingContainer list of a Deployable.
Read more about Kubernetes [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### org.entando.kubernetes.controller.spi.container.IngressingContainer  

IngressingContainer is an interface representing a container that supply ingress functionalities.
Read more about Kubernetes [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/)

### org.entando.kubernetes.controller.spi.deployable.PublicIngressingDeployable  

TBD

### org.entando.kubernetes.controller.spi.container.DbAwareContainer

This interface has to be implemented by those `DeployableContainer` that needs a DB to serve their functionalities.
It takes care of:

- establish a connection between the implementing `DeployableContainer` and the DBMS instance identified by the environment variables supplied in the `addDatabaseConnectionVariables()` method. You can see an example in the [KeycloakDeployableContainer](https://github.com/entando-k8s/entando-k8s-keycloak-controller/blob/master/src/main/java/org/entando/kubernetes/controller/keycloakserver/KeycloakDeployableContainer.java) 
- create the desired DB schema
- populate the created DB schema if a DatabasePopulator is returned by the implemented `useDatabaseSchemas()` method

Entando supports these DBMS: `H2`, `Postgresql`, `MySQL`, `Oracle`. You can choose which one to use for each `DeployableContainer` implementing `DbAware` interface by specifying a `spec.dbms` property in the Kubernetes deployment file.
You can find more info in the [Getting started](http://docs.entando.com/local-install.html#dbms)

### org.entando.kubernetes.controller.spi.container.PersistentVolumeAwareContainer

This interface has to be implemented by those DeployableContainer that needs a [PVC](https://kubernetes.io/docs/concepts/storage/persistent-volumes/#persistentvolumeclaims) in order to persist some data.
This interface has only one method to be overridden: `getVolumeMountPath()`. It returns the path of the volume to claim inside the container that is about to be created.
Once overridden that method, claim operation is automatically made, the PVC is bound to the claimer [CR](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/) and once the owner CR is deleted the PVC is deleted too.

### org.entando.kubernetes.controller.spi.container.SsoAwareContainer

This interface has to be implemented by those DeployableContainer that needs to reach Keycloak to guarantee their functionalities.
It comes with a predefined set of environment variables pointing to the Entando default Keycloak installation.

## Creators

There are a lot of creator classes, each one responsible for the creation of something in particular.
When a `DeployCommand` needs to create something (like a PVC, a Secret, an Ingress, etc.) it asks for creation to the related creator.
The notified creator optionally processes some business logic and then delegates to the related [client](#interactions-with-clusters) the creation of the desired object into the Kubernetes cluster.

## Interactions with clusters

Because of the main goal of this project is to supply a unified interface to interact with Kubernetes clusters, the class `org.entando.kubernetes.controller.support.client.impl.DefaultSimpleK8SClient` is another pivotal component.
In contains a list of clients, each one leveraging Fabric8's `KubernetesClient` in order to supply a "separated by concerns" cluster interaction interface.

So in particolar

# Configuration

Like most Kubernetes applications, this library uses configuration data from four difference sources: 
Kubernetes Secrets and Configmaps,  OS Environment variables and JVM System Properties. OS Environment Variables
override equivalent JVM System Properties. JVM System Properties and OS Environment Variables are 
considered equivalent if the OS Environment Variable is the uppercase snake case equivalent of the JVM System Property
in lowercase, dot-delimited. For instance, the OS Environment Variable `ENTANDO_CA_CERT_PATHS` would override the
JVM System Property `entando.ca.cert.paths`
 
## How it resolves Docker images

In order to support development of both Controller images and supporting images such as sidecars, we have established
a very flexible Docker image resolution process. The eventual image URI that we resolve has 4 segments to it: 
 * a Docker registry
 * a Docker (or openshift) registry namespace
 * the image name
 * a version suffix.
Entando looks for default and overriding configuration settings in both the OS Environment Variables and JVM System Properties
to resolve the full image URI. It also inspects a Kubernetes ConfigMap named `entando-image-versions`, which stores 
a JSON formatted image configuration against known image names. This ConfigMap can be created in the Operator's namespace.
For images in the `entando` namespace, the registry, namespace and version segments of the image URI can be overridden 
following a similar pattern. (The namespace used to resolve this ConfigMap can be overridden by using the `ENTANDO_K8S_OPERATOR_CONFIGMAP_NAMESPACE` environment variable/system property)

The Docker registry segment will be resolved as follows:
 * If a global override, e.g. ENTANDO_DOCKER_REGISTRY_OVERRIDE=docker.io., was configured, it will always be used
 * An entry in the standard ImageVersionsConfigMap against the image name, e.g. my-image:{docker-registry:docker.io}, will be used when the aforementioned override  is absent
 * A default, e.g. ENTANDO_DOCKER_REGISTRY_DEFAULT=docker.io, will be used if nothing else was specified.

The Docker namespace segment will be resolved as follows:
 * If a global override, e.g. ENTANDO_DOCKER_IMAGE_NAMESPACE_OVERRIDE=test-namespace, was configured, it will always be used
 * An entry in the standard ImageVersionsConfigMap against the image name, e.g. my-image:{image-namespace:test-namespace}, will be used when the aforementioned override  is absent
 * A default, e.g. ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT=test-namespace, will be used if nothing else was specified.

The image version segment will be resolved as follows:
 * If a global override, e.g. ENTANDO_DOCKER_IMAGE_VERSION_OVERRIDE=6.0.14, was configured, it will always be used
 * An entry in the standard ImageVersionsConfigMap against the image name, e.g. my-image:{version:6.0.14}, will be used when the aforementioned override  is absent
 * A default, e.g. ENTANDO_DOCKER_IMAGE_NAMESPACE_DEFAULT=6.0.14, will be used if nothing else was specified.

## Configuring TLS settings

TBD
  
## Examples

Some examples are available in the test package at the location `org.entando.kubernetes.controller.common.examples`