package com.code808.calmdesk.domain.attendance.event;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class DashboardEvent {
    private final Long companyId;

    public DashboardEvent(Long companyId) {
        this.companyId = companyId;
    }
}
