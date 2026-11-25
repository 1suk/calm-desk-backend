package com.code808.calmdesk.domain.dashboard.repository.admin;

import com.code808.calmdesk.domain.attendance.entity.Attendance;
import com.code808.calmdesk.domain.attendance.entity.StressSummary;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DashboardRepository extends JpaRepository<StressSummary, Long> {

    // 멤버 단위 하루 평균을 emotion_checkin에서 직접 집계 (회사/부서/고위험군은 앱에서 롤업)
    @Query("SELECT a.member.memberId as memberId, " +
            "a.member.department.departmentId as departmentId, " +
            "a.member.department.departmentName as departmentName, " +
            "a.member.name as memberName, " +
            "AVG(e.stressLevel) as avgStress " +
            "FROM EMOTION_CHECKIN e JOIN e.attendance a " +
            "WHERE a.member.company.companyId = :companyId AND a.workDate = :date " +
            "GROUP BY a.member.memberId, a.member.department.departmentId, a.member.department.departmentName, a.member.name")
    List<MemberDailyStressProjection> findMemberDailyStress(@Param("companyId") Long companyId, @Param("date") LocalDate date);

    @Query("SELECT d.departmentId as departmentId, COUNT(c) as cooldownCount FROM COOLDOWN c JOIN c.member m JOIN m.department d WHERE d.company.companyId = :companyId AND c.createdDate BETWEEN :startDate AND :endDate GROUP BY d.departmentId")
    List<DepartmentCooldownProjection> findCooldownCountsByCompany(@Param("companyId") Long companyId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT " + "SUM(CASE WHEN a.attendanceStatus IN :statuses THEN 1 ELSE 0 END) as attendCount, " + "(SELECT COUNT(m2.memberId) FROM MEMBER m2 WHERE m2.company.companyId = :companyId) as totalMemberCount " + "FROM MEMBER m " + "LEFT JOIN Attendance a ON a.member.memberId = m.memberId AND a.workDate = :date " + "WHERE m.company.companyId = :companyId")
    AttendanceRateProjection findAttendanceRate(@Param("companyId") Long companyId, @Param("date") LocalDate date, @Param("statuses") List<Attendance.AttendanceStatus> statuses);

    @Query("SELECT COUNT(c.counselionId) " + "FROM Consultation c " + "WHERE c.member.company.companyId = :companyId " + "AND c.status = 'WAITING'")
    Long countWaitingConsultations(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(v.vacationId) " + "FROM Vacation v " + "WHERE v.requestMember.company.companyId = :companyId " + "AND v.status = 'N'")
    Long countPendingVacations(@Param("companyId") Long companyId);
}

