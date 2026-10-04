package com.sywater.ms_iam.infrastructure.integration;

import com.sywater.ms_iam.application.port.out.DeviceCleanup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Temporary adapter for HU-008 until ms-devices (E3) is running: it only leaves a trace.
 * Replacing it by an HTTP (or event) adapter is a change in this package only.
 */
@Component
public class NoDeviceCleanup implements DeviceCleanup {

    private static final Logger log = LoggerFactory.getLogger(NoDeviceCleanup.class);

    @Override
    public void unlinkAllDevicesOf(UUID userId) {
        log.info("Account {} deleted: device unlinking is pending the ms-devices integration (E3).", userId);
    }
}
