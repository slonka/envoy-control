package pl.allegro.tech.servicemesh.envoycontrol.snapshot.resource.listeners.filters.v2

import pl.allegro.tech.servicemesh.envoycontrol.snapshot.SnapshotProperties
import pl.allegro.tech.servicemesh.envoycontrol.snapshot.resource.listeners.v2.HttpFilterFactory

class EnvoyHttpFilters(
    val ingressFilters: List<HttpFilterFactory>,
    val egressFilters: List<HttpFilterFactory>
) {
    companion object {
        val emptyFilters = EnvoyHttpFilters(listOf(), listOf())

        fun defaultFilters(snapshotProperties: SnapshotProperties): EnvoyHttpFilters {
            val defaultFilters = EnvoyDefaultFilters(snapshotProperties)
            return EnvoyHttpFilters(defaultFilters.defaultIngressFilters, defaultFilters.defaultEgressFilters)
        }
    }
}
