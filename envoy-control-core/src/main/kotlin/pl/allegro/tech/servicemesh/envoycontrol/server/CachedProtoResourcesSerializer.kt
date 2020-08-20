package pl.allegro.tech.servicemesh.envoycontrol.server

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.protobuf.Any
import com.google.protobuf.Message
import io.envoyproxy.controlplane.cache.Resources
import io.envoyproxy.controlplane.server.serializer.ProtoResourcesSerializer
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.GuavaCacheMetrics
import pl.allegro.tech.servicemesh.envoycontrol.utils.noopTimer
import java.util.function.Supplier

import io.envoyproxy.controlplane.cache.Resources.ApiVersion.V2
import io.envoyproxy.controlplane.cache.Resources.ApiVersion.V3
import io.micrometer.core.instrument.Timer

internal class CachedProtoResourcesSerializer(
    private val meterRegistry: MeterRegistry,
    private val reportMetrics: Boolean
) : ProtoResourcesSerializer {

    private fun createTimer(reportMetrics: Boolean, meterRegistry: MeterRegistry, timerName: String): Timer {
        return if (reportMetrics) {
            meterRegistry.timer(timerName)
        } else {
            noopTimer
        }
    }

    private val cacheV2: Cache<Collection<Message>, MutableCollection<Any>> = createCache(reportMetrics, meterRegistry, "protobuf-cache-v2")
    private val cacheV3: Cache<Collection<Message>, MutableCollection<Any>> = createCache(reportMetrics, meterRegistry, "protobuf-cache-v3")

    private fun createCache(reportMetrics: Boolean, meterRegistry: MeterRegistry, cacheName: String): Cache<Collection<Message>, MutableCollection<Any>> {
        return if (reportMetrics) {
            GuavaCacheMetrics
                    .monitor(
                            meterRegistry,
                            CacheBuilder.newBuilder()
                                    .recordStats()
                                    .weakValues()
                                    .build<Collection<Message>, MutableCollection<Any>>(),
                            cacheName
                    )
        } else {
            CacheBuilder.newBuilder()
                    .weakValues()
                    .build<Collection<Message>, MutableCollection<Any>>()
        }
    }


    override fun serialize(resources: MutableCollection<out Message>, apiVersion: Resources.ApiVersion): MutableCollection<Any> {
        return if (apiVersion == V3) {
            createTimer(reportMetrics, meterRegistry, "protobuf-cache.serialize.time").record(Supplier { getResources(resources, apiVersion) })
        } else {
            createTimer(reportMetrics, meterRegistry, "protobuf-cache.serialize.time").record(Supplier { getResources(resources, apiVersion) })
        }
    }

    private fun getResources(resources: MutableCollection<out Message>, apiVersion: Resources.ApiVersion): MutableCollection<Any> {
        val cache = when(apiVersion) {
            V2 -> cacheV2
            V3 -> cacheV3
        }

        return cache.get(resources) {
            resources.asSequence()
                .map { Any.pack(it) }
                .toMutableList()
        }
    }

    @Suppress("NotImplementedDeclaration")
    override fun serialize(resource: Message?, apiVersion: Resources.ApiVersion?): Any {
        throw NotImplementedError("Serializing single messages is not supported")
    }
}
