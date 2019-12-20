package pl.allegro.tech.servicemesh.envoycontrol.synchronization

import io.micrometer.core.instrument.MeterRegistry
import pl.allegro.tech.servicemesh.envoycontrol.logger
import pl.allegro.tech.servicemesh.envoycontrol.services.Locality
import pl.allegro.tech.servicemesh.envoycontrol.services.LocalityAwareServicesState
import pl.allegro.tech.servicemesh.envoycontrol.services.ServicesState
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

class CrossDcServices(
    private val controlPlaneClient: AsyncControlPlaneClient,
    private val meterRegistry: MeterRegistry,
    private val controlPlaneInstanceFetcher: ControlPlaneInstanceFetcher,
    private val remoteDcs: List<String>
) {
    private val logger by logger()
    private val dcServicesCache = ConcurrentHashMap<String, LocalityAwareServicesState>()

    fun getChanges(interval: Long): Flux<Set<LocalityAwareServicesState>> {
        return Flux
            .interval(Duration.ofSeconds(0), Duration.ofSeconds(interval))
            // Cross DC sync is not a backpressure compatible stream. If running cross dc sync is slower than interval
            // we have to drop interval events and run another cross dc on another interval tick.
            .onBackpressureDrop()
            .flatMap {
                Flux.fromIterable(remoteDcs)
                    .map { dc -> dcWithControlPlaneInstances(dc) }
                    .filter { (_, instances) -> instances.isNotEmpty() }
                    .flatMap { (dc, instances) -> servicesStateFromDc(dc, instances) }
                    .collect(Collectors.toSet())
            }
            .filter {
                it.isNotEmpty()
            }
            .doOnCancel {
                meterRegistry.counter("cross-dc-synchronization.cancelled").increment()
                logger.warn("Cancelling cross dc sync")
            }
    }

    private fun dcWithControlPlaneInstances(dc: String): Pair<String, List<URI>> {
        return try {
            val instances = controlPlaneInstanceFetcher.instances(dc)
            dc to instances
        } catch (e: Exception) {
            meterRegistry.counter("cross-dc-synchronization.$dc.instance-fetcher.errors").increment()
            logger.warn("Failed fetching instances from $dc", e)
            dc to emptyList()
        }
    }

    private fun servicesStateFromDc(
        dc: String,
        instances: List<URI>
    ): Mono<LocalityAwareServicesState> {
        val instance = chooseInstance(instances)

        lateinit var state: Mono<ServicesState>
        lateinit var protobufState: Mono<ServicesState>
        lateinit var gzipState: Mono<ServicesState>
        lateinit var gzipProtobufState: Mono<ServicesState>

        listOf(
            { state = controlPlaneClient.getState(instance) },
            { protobufState = logErrorAndContinue(controlPlaneClient.getV2State(instance), "protobufState") },
            { gzipState = logErrorAndContinue(controlPlaneClient.getStateGzip(instance).onErrorReturn(ServicesState()), "gzipState") },
            { gzipProtobufState = logErrorAndContinue(controlPlaneClient.getV2StateGzip(instance).onErrorReturn(ServicesState()), "gzipProtobufState") }
        ).shuffled()
            .forEach { it() }


        return Mono.zip(state, protobufState, gzipState, gzipProtobufState)
            .map { t -> t.t1 }
            .map {
                LocalityAwareServicesState(it, Locality.REMOTE, dc)
            }
            .doOnSuccess {
                dcServicesCache += dc to it
            }
            .onErrorResume { exception ->
                meterRegistry.counter("cross-dc-synchronization.$dc.state-fetcher.errors").increment()
                logger.warn("Error synchronizing instances ${exception.message}", exception)
                Mono.justOrEmpty(dcServicesCache[dc])
            }
    }

    private fun chooseInstance(serviceInstances: List<URI>): URI = serviceInstances.random()

    private fun logErrorAndContinue(state: Mono<ServicesState>, name: String): Mono<ServicesState> {
        return state.onErrorResume { e ->
            logger.error("LOADING SERVICE STATE FAILED FOR VERSION: ${name}", e)
            Mono.just(ServicesState())
        }
    }
}
