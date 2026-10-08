package com.sywater.ms_iam.application.port.in;

import com.sywater.ms_iam.application.dto.PlatformMetricsView;

import java.util.UUID;

/** HU-062 */
public interface PlatformMetricsUseCase {

    /**
     * Totals and active/inactive counts of users, places and devices, calculated when asked.
     * If ms-places or ms-device does not answer, the rest still comes and the section is reported as unavailable.
     *
     * @throws com.sywater.ms_iam.domain.exception.NotAdministratorException the caller is not an ADMIN
     */
    PlatformMetricsView get(UUID administratorId);
}
