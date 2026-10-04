package com.sywater.ms_iam.application.port.out;

import java.util.UUID;

public interface DeviceCleanup {

    void unlinkAllDevicesOf(UUID userId);
}
