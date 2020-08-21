package pl.allegro.tech.servicemesh.envoycontrol.snapshot

import pl.allegro.tech.servicemesh.envoycontrol.groups.DependencySettings

class RouteSpecification(
    val clusterName: String,
    val routeDomain: String,
    val settings: DependencySettings
)