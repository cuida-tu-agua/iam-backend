package com.sywater.ms_iam.application.service;

import com.sywater.ms_iam.application.dto.PlatformMetricsView;
import com.sywater.ms_iam.application.dto.ServiceTotals;
import com.sywater.ms_iam.application.port.in.PlatformMetricsUseCase;
import com.sywater.ms_iam.application.port.out.ServiceMetricsReader;
import com.sywater.ms_iam.application.port.out.UserRepository;
import com.sywater.ms_iam.domain.exception.ExternalServiceUnavailableException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PlatformMetricsService implements PlatformMetricsUseCase {

    private final UserRepository users;
    private final AdminAccess admins;
    private final ServiceMetricsReader services;
    private final Clock clock;

    public PlatformMetricsService(UserRepository users, AdminAccess admins, ServiceMetricsReader services, Clock clock) {
        this.users = users;
        this.admins = admins;
        this.services = services;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformMetricsView get(UUID administratorId) {
        admins.require(administratorId);
        PlatformMetricsView.Users userMetrics = PlatformMetricsView.Users.of(users.countByStatus());

        List<String> unavailable = new ArrayList<>();
        ServiceTotals.Places placeTotals = ask(unavailable, "places", services::places);
        ServiceTotals.Devices deviceTotals = ask(unavailable, "devices", services::devices);

        PlatformMetricsView.Devices devices = deviceTotals == null ? null : new PlatformMetricsView.Devices(
                deviceTotals.total(), deviceTotals.connected(), deviceTotals.total() - deviceTotals.connected(),
                deviceTotals.linked());

        // A place is active when it has a device and only ms-device knows that: without both there is no honest split
        PlatformMetricsView.Places places = null;
        if (placeTotals != null && deviceTotals != null) {
            long active = Math.min(deviceTotals.linked(), placeTotals.total());
            places = new PlatformMetricsView.Places(placeTotals.total(), active, placeTotals.total() - active);
        } else if (placeTotals != null) {
            unavailable.add("places");
        }
        return new PlatformMetricsView(clock.instant(), userMetrics, places, devices, List.copyOf(unavailable));
    }

    private static <T> T ask(List<String> unavailable, String name, Supplier<T> call) {
        try {
            return call.get();
        } catch (ExternalServiceUnavailableException e) {
            unavailable.add(name);
            return null;
        }
    }
}
