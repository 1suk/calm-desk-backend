package com.code808.calmdesk.domain.attendance.event;

import lombok.*;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class StressDeltaEvent {
    private final Long companyId;
    private final Long departmentId;
    private final Long memberId;
    private final LocalDate summaryDate;
    private final Double oldAvg;
    private final Double newAvg;


    public boolean isNewMember(){
        return oldAvg == null;
    }
}
