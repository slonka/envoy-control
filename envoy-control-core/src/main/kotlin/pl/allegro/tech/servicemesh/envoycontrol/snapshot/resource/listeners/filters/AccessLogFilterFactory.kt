package pl.allegro.tech.servicemesh.envoycontrol.snapshot.resource.listeners.filters

import com.google.re2j.Pattern
import io.envoyproxy.envoy.config.accesslog.v3.ComparisonFilter as ComparisonFilterV3
import io.envoyproxy.envoy.config.filter.accesslog.v2.ComparisonFilter
import pl.allegro.tech.servicemesh.envoycontrol.groups.AccessLogFilterSettings
import pl.allegro.tech.servicemesh.envoycontrol.groups.NodeMetadataValidationException

class AccessLogFilterFactory {
    private val operators: Array<ComparisonFilter.Op> = arrayOf(
        ComparisonFilter.Op.LE, ComparisonFilter.Op.GE, ComparisonFilter.Op.EQ
    )
    private val delimiter: Char = ':'
    private val statusCodeFilterPattern: Pattern = Pattern.compile(
        """^(${operators.joinToString("|")})$delimiter(\d{3})$"""
    )

    fun parseStatusCodeFilter(value: String): AccessLogFilterSettings.StatusCodeFilterSettings {
        validateValue(value)
        val split = value.split(delimiter)
        return AccessLogFilterSettings.StatusCodeFilterSettings(
                comparisonOperator = ComparisonFilter.Op.valueOf(split[0]),
                comparisonCode = split[1].toInt()
        )
    }

    fun parseStatusCodeFilterV3(value: String): AccessLogFilterSettings.StatusCodeFilterSettingsV3 {
        validateValue(value)
        val split = value.split(delimiter)
        return AccessLogFilterSettings.StatusCodeFilterSettingsV3(
                comparisonOperator = ComparisonFilterV3.Op.valueOf(split[0]),
                comparisonCode = split[1].toInt()
        )
    }

    private fun validateValue(value: String) {
        if (!statusCodeFilterPattern.matches(value)) {
            throw NodeMetadataValidationException(
                    "Invalid access log status code filter. Expected OPERATOR:STATUS_CODE"
            )
        }
    }
}
