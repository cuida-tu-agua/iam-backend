package com.sywater.ms_iam.application.port.out;

import com.sywater.ms_iam.application.dto.ServiceTotals;

/** HU-062: numbers that live in other services. Both throw ExternalServiceUnavailableException when they cannot answer. */
public interface ServiceMetricsReader {

    ServiceTotals.Places places();

    ServiceTotals.Devices devices();
}
