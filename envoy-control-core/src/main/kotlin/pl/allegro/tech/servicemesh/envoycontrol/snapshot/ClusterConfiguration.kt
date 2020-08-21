package pl.allegro.tech.servicemesh.envoycontrol.snapshot

data class ClusterConfiguration(
    val serviceName: String,
    val http2Enabled: Boolean
)