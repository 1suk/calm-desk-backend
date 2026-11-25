package com.code808.calmdesk.domain.dashboard.service.admin;

import com.code808.calmdesk.domain.attendance.entity.Attendance;
import com.code808.calmdesk.domain.attendance.repository.AttendanceRepository;
import com.code808.calmdesk.domain.dashboard.dto.admin.DashboardDto;
import com.code808.calmdesk.domain.dashboard.repository.admin.DashboardRepository;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.AttendanceRateProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.DepartmentCooldownProjection;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.MemberDailyStressProjection;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final DashboardRepository dashboardRepository;
    private final AttendanceRepository attendanceRepository;

    private static final int TOP_HIGH_RISK_LIMIT = 5;

    private static final List<Attendance.AttendanceStatus> attendanceStatuses = Arrays.asList(Attendance.AttendanceStatus.ATTEND, Attendance.AttendanceStatus.LATE);

    private LocalDate getBaseDate(DashboardDto.DashboardRequest request) {
        return Optional.ofNullable(request.getDate()).orElseGet(LocalDate::now);
    }

    private List<MemberDailyStressProjection> memberStress(Long companyId, LocalDate date) {
        return dashboardRepository.findMemberDailyStress(companyId, date);
    }

    // ---- 앱에서 롤업 (멤버 단위 결과 하나로 회사/부서/고위험군 계산) ----

    private DashboardDto.CompanyStats companyStats(DashboardDto.DashboardRequest request, LocalDate date, List<MemberDailyStressProjection> members) {
        Long companyId = request.getCompanyId();
        int threshold = request.getThreshold();

        long totalMembers = members.size();
        Double avgStress = members.isEmpty() ? null
                : members.stream().mapToDouble(MemberDailyStressProjection::getAvgStress).average().orElse(0.0);
        long highRiskCount = members.stream().filter(m -> m.getAvgStress() >= threshold).count();

        AttendanceRateProjection attendance = dashboardRepository.findAttendanceRate(companyId, date, attendanceStatuses);
        Long consultationCount = dashboardRepository.countWaitingConsultations(companyId);
        Long vacationCount = dashboardRepository.countPendingVacations(companyId);

        return DashboardDto.CompanyStats.of(avgStress, totalMembers, highRiskCount,
                (attendance != null) ? attendance.getAttendanceRate() : 0.0, consultationCount, vacationCount);
    }

    private List<DashboardDto.DepartmentStats> departmentStats(Long companyId, LocalDateTime startOfDay, LocalDateTime endOfDay, List<MemberDailyStressProjection> members) {
        Map<Long, Long> cooldownByDept = dashboardRepository.findCooldownCountsByCompany(companyId, startOfDay, endOfDay).stream()
                .collect(Collectors.toMap(DepartmentCooldownProjection::getDepartmentId, DepartmentCooldownProjection::getCooldownCount));

        Map<Long, List<MemberDailyStressProjection>> byDept = members.stream()
                .collect(Collectors.groupingBy(MemberDailyStressProjection::getDepartmentId));

        List<DashboardDto.DepartmentStats> result = new ArrayList<>();
        for (Map.Entry<Long, List<MemberDailyStressProjection>> entry : byDept.entrySet()) {
            List<MemberDailyStressProjection> deptMembers = entry.getValue();
            double avg = deptMembers.stream().mapToDouble(MemberDailyStressProjection::getAvgStress).average().orElse(0.0);

            result.add(DashboardDto.DepartmentStats.builder()
                    .departmentId(entry.getKey())
                    .departmentName(deptMembers.get(0).getDepartmentName())
                    .avgStressLevel(avg)
                    .avgStressPercentage((double) DashboardDto.convertScore(avg))
                    .memberCount((long) deptMembers.size())
                    .cooldownCount(cooldownByDept.getOrDefault(entry.getKey(), 0L))
                    .build());
        }
        return result;
    }

    private List<DashboardDto.HighRiskMember> highRiskMembers(List<MemberDailyStressProjection> members, int threshold) {
        return members.stream()
                .filter(m -> m.getAvgStress() >= threshold)
                .sorted(Comparator.comparingDouble(MemberDailyStressProjection::getAvgStress).reversed())
                .limit(TOP_HIGH_RISK_LIMIT)
                .map(m -> DashboardDto.HighRiskMember.builder()
                        .memberId(m.getMemberId())
                        .memberName(m.getMemberName())
                        .departmentName(m.getDepartmentName())
                        .stressLevel(m.getAvgStress())
                        .stressPercentage((double) DashboardDto.convertScore(m.getAvgStress()))
                        .build())
                .toList();
    }

    // ---- 인터페이스 메서드 (각자 멤버 집계를 한 번씩 조회) ----

    @Override
    public DashboardDto.CompanyStats getCompanyStats(DashboardDto.DashboardRequest request, LocalDate baseDate) {
        return companyStats(request, baseDate, memberStress(request.getCompanyId(), baseDate));
    }

    @Override
    public List<DashboardDto.DepartmentStats> getDepartmentStats(DashboardDto.DashboardRequest request, LocalDate baseDate, LocalDateTime startOfDay, LocalDateTime endOfDay) {
        return departmentStats(request.getCompanyId(), startOfDay, endOfDay, memberStress(request.getCompanyId(), baseDate));
    }

    @Override
    public List<DashboardDto.HighRiskMember> getHighRiskMembers(DashboardDto.DashboardRequest request, LocalDate baseDate) {
        return highRiskMembers(memberStress(request.getCompanyId(), baseDate), request.getThreshold());
    }

    @Override
    public DashboardDto.DashboardResponse getRealtimeStats(DashboardDto.DashboardRequest request) {
        LocalDate baseDate = getBaseDate(request);
        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);

        List<MemberDailyStressProjection> members = memberStress(request.getCompanyId(), baseDate);

        return DashboardDto.DashboardResponse.of(baseDate,
                companyStats(request, baseDate, members),
                departmentStats(request.getCompanyId(), startOfDay, endOfDay, members),
                highRiskMembers(members, request.getThreshold()));
    }

    @Override
    public DashboardDto.DashboardResponse getAllStats(DashboardDto.DashboardRequest request) {
        Long companyId = request.getCompanyId();

        LocalDate baseDate = getBaseDate(request);
        LocalDateTime startOfDay = baseDate.atStartOfDay();
        LocalDateTime endOfDay = baseDate.atTime(23, 59, 59);

        LocalDate lastDate = attendanceRepository
                .findLatestWorkDateBefore(companyId, baseDate)
                .orElse(baseDate.minusDays(1));

        LocalDateTime startOfLastDay = lastDate.atStartOfDay();
        LocalDateTime endOfLastDay = lastDate.atTime(23, 59, 59);

        List<MemberDailyStressProjection> today = memberStress(companyId, baseDate);
        List<MemberDailyStressProjection> last = memberStress(companyId, lastDate);

        return DashboardDto.DashboardResponse.of(
                baseDate,
                companyStats(request, baseDate, today),
                departmentStats(companyId, startOfDay, endOfDay, today),
                highRiskMembers(today, request.getThreshold()),
                lastDate,
                companyStats(request, lastDate, last),
                departmentStats(companyId, startOfLastDay, endOfLastDay, last));
    }
}
