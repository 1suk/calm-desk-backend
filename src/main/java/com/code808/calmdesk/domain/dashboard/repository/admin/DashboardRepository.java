package com.code808.calmdesk.domain.dashboard.repository.admin;

import com.code808.calmdesk.domain.attendance.entity.Attendance;
import com.code808.calmdesk.domain.attendance.entity.StressSummary;
import com.code808.calmdesk.domain.dashboard.repository.admin.projection.*;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DashboardRepository extends JpaRepository<StressSummary, Long> {

    @Query("SELECT d.departmentId as departmentId, " + "d.departmentName as departmentName, " + "AVG(s.avgStressLevel) as avgStressLevel, " + "COUNT(s) as memberCount, " + "COALESCE((SELECT COUNT(c) FROM COOLDOWN c " + " WHERE c.member.department = d " + " AND c.createdDate BETWEEN :startDate AND :endDate), 0) as cooldownCount " + "FROM StressSummary s " + "JOIN s.department d " + "WHERE d.company.companyId = :companyId " + "AND s.summaryDate = :date " + "GROUP BY d.departmentId, d.departmentName")
    List<DepartmentStatsProjection> findDepartmentStats(@Param("companyId") Long companyId, @Param("date") LocalDate date, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT " + "m.memberId as memberId, " + "m.name as memberName, " + "d.departmentName as departmentName, " + "s.avgStressLevel as avgStressLevel " + "FROM StressSummary s " + "JOIN s.member m " + "JOIN s.department d " + "WHERE m.company.companyId = :companyId " + "AND s.summaryDate = :date " + "AND s.avgStressLevel >= :threshold " + "ORDER BY s.avgStressLevel DESC")
    List<HighRiskMemberProjection> findHighRiskMembers(@Param("companyId") Long companyId, @Param("date") LocalDate date, @Param("threshold") Integer threshold, Pageable pageable);

    @Query("SELECT AVG(s.avgStressLevel) as avgStressLevel, " + "COUNT(s.summaryId) as totalMembers, " + "SUM(CASE WHEN s.avgStressLevel >= :threshold THEN 1 ELSE 0 END) as highRiskCount " + "FROM StressSummary s " + "WHERE s.member.company.companyId = :companyId " + "AND s.summaryDate = :date")
    CompanyStatsProjection findCompanyStats(@Param("companyId") Long companyId, @Param("date") LocalDate date, @Param("threshold") Integer threshold);

    @Query("SELECT " + "SUM(CASE WHEN a.attendanceStatus IN :statuses THEN 1 ELSE 0 END) as attendCount, " + "(SELECT COUNT(m2.memberId) FROM MEMBER m2 WHERE m2.company.companyId = :companyId) as totalMemberCount " + "FROM MEMBER m " + "LEFT JOIN Attendance a ON a.member.memberId = m.memberId AND a.workDate = :date " + "WHERE m.company.companyId = :companyId")
    AttendanceRateProjection findAttendanceRate(@Param("companyId") Long companyId, @Param("date") LocalDate date, @Param("statuses") List<Attendance.AttendanceStatus> statuses);

    @Query("SELECT COUNT(c.counselionId) " + "FROM Consultation c " + "WHERE c.member.company.companyId = :companyId " + "AND c.status = 'WAITING'")
    Long countWaitingConsultations(@Param("companyId") Long companyId);

    @Query("SELECT COUNT(v.vacationId) " + "FROM Vacation v " + "WHERE v.requestMember.company.companyId = :companyId " + "AND v.status = 'N'")
    Long countPendingVacations(@Param("companyId") Long companyId);

    @Query("SELECT " + "m.memberId as memberId, " + "m.name as memberName, " + "d.departmentName as departmentName " + "FROM MEMBER m " + "JOIN m.department d " + "WHERE m.memberId IN :memberIds")
    List<MemberProjection> findMemberBasicInfoByIds(@Param("memberIds") List<Long> memberIds);

    @Query("SELECT " + "d.departmentId as departmentId, " + "COUNT(c) as cooldownCount " + "FROM COOLDOWN c " + "JOIN c.member m " + "JOIN m.department d " + "WHERE d.company.companyId = :companyId " + "AND c.createdDate BETWEEN :startDate AND :endDate " + "GROUP BY d.departmentId")
    List<DepartmentCooldownProjection> findCooldownCountsByCompany(@Param("companyId") Long companyId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}

