package pl.allegro.tech.servicemesh.envoycontrol.snapshot

import io.envoyproxy.controlplane.cache.SnapshotResources
import io.envoyproxy.envoy.api.v2.Cluster
import io.envoyproxy.envoy.api.v2.ClusterLoadAssignment
import io.envoyproxy.envoy.config.cluster.v3.Cluster as ClusterV3
import io.envoyproxy.envoy.config.endpoint.v3.ClusterLoadAssignment as ClusterLoadAssignmentV3

data class GlobalSnapshot(
    val clusters: SnapshotResources<Cluster>,
    val clustersV3: SnapshotResources<ClusterV3>,
    val allServicesNames: Set<String>,
    val endpoints: SnapshotResources<ClusterLoadAssignment>,
    val endpointsV3: SnapshotResources<ClusterLoadAssignmentV3>,
    val clusterConfigurations: Map<String, ClusterConfiguration>,
    val securedClusters: SnapshotResources<Cluster>,
    val securedClustersV3: SnapshotResources<ClusterV3>
)

internal fun globalSnapshot(
    clusters: Iterable<Cluster>,
    clustersV3: Iterable<ClusterV3>,
    endpoints: Iterable<ClusterLoadAssignment>,
    endpointsV3: Iterable<ClusterLoadAssignmentV3>,
    properties: OutgoingPermissionsProperties = OutgoingPermissionsProperties(),
    clusterConfigurations: Map<String, ClusterConfiguration>,
    securedClusters: List<Cluster>,
    securedClustersV3: List<ClusterV3>
): GlobalSnapshot {
    val clusters = SnapshotResources.create(clusters, "")
    val clustersV3 = SnapshotResources.create(clustersV3, "")
    val securedClusters = SnapshotResources.create(securedClusters, "")
    val securedClustersV3 = SnapshotResources.create(securedClustersV3, "")
    val allServicesNames = getClustersForAllServicesGroups(clusters.resources(), clustersV3.resources(), properties)
    val endpoints = SnapshotResources.create(endpoints, "")
    val endpointsV3 = SnapshotResources.create(endpointsV3, "")
    return GlobalSnapshot(
        clusters = clusters,
        clustersV3 = clustersV3,
        securedClusters = securedClusters,
        securedClustersV3 = securedClustersV3,
        endpoints = endpoints,
        endpointsV3 = endpointsV3,
        allServicesNames = allServicesNames,
        clusterConfigurations = clusterConfigurations
    )
}

private fun getClustersForAllServicesGroups(
        clusters: Map<String, Cluster>,
        clustersV3: MutableMap<String, ClusterV3>,
        properties: OutgoingPermissionsProperties
): Set<String> {
    val blacklist = properties.allServicesDependencies.notIncludedByPrefix
    return if (blacklist.isEmpty()) {
        clusters.keys
    } else {
        clusters.filter { (serviceName) -> blacklist.none { serviceName.startsWith(it) } }.keys +
                clustersV3.filter { (serviceName) -> blacklist.none { serviceName.startsWith(it) } }.keys
    }
}
