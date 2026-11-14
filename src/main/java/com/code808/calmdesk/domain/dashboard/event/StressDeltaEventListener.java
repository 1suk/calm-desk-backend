package com.code808.calmdesk.domain.dashboard.event;

import com.code808.calmdesk.domain.attendance.event.StressDeltaEvent;
import com.code808.calmdesk.domain.dashboard.repository.admin.cache.DashboardRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class StressDeltaEventListener {

    private final DashboardRedisRepository dashboardRedisRepository;


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStressDelta(StressDeltaEvent event) {
        try {
            dashboardRedisRepository.applyCheckinDelta(
                    event.getDepartmentId(),
                    event.getCompanyId(),
                    event.getSummaryDate(),
                    event.getMemberId(),
                    event.getOldAvg(),
                    event.getNewAvg()
            );
        } catch (Exception e) {
            log.error("대시보드 Redis 갱신 실패 - companyId: {}, departmentId: {}, memberId: {}",
                    event.getCompanyId(), event.getDepartmentId(), event.getMemberId(), e);
        }
    }
}