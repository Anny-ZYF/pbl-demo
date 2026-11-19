package com.gdmu.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** At the deadline, an unconfirmed workload version is locked even without full confirmation. */
class ScoringServiceDeadlineAutoLockTest {
    private final ScoringMapper mapper = mock(ScoringMapper.class);
    private final ScoringService service = new ScoringService(
            mapper, new PaperScoringEngine(), mock(ModelClient.class), new ObjectMapper(), mock(AuditService.class));
    private final UserPrincipal leader = new UserPrincipal(3, "leader", "组长", Role.STUDENT,
            "hash", true, false, 0);
    private final UserPrincipal teacher = new UserPrincipal(1, "teacher01", "教师", Role.TEACHER,
            "hash", true, false, 0);

    @Test
    void nothingIsLockedBeforeTheDeadline() {
        when(mapper.expiredAssignmentCount(7)).thenReturn(0);

        assertThat(service.autoLockExpired(7)).isZero();
        verify(mapper, never()).expiredOpenWorkloadSetIds(7);
        verify(mapper, never()).autoLockWorkloadSet(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void everyGroupsLatestOpenVersionIsLockedAtTheDeadline() {
        when(mapper.expiredAssignmentCount(7)).thenReturn(1);
        when(mapper.expiredOpenWorkloadSetIds(7)).thenReturn(List.of(10L, 20L));
        when(mapper.autoLockWorkloadSet(10L)).thenReturn(1);
        when(mapper.autoLockWorkloadSet(20L)).thenReturn(1);

        assertThat(service.autoLockExpired(7)).isEqualTo(2);

        verify(mapper).supersedeOtherWorkloadSets(10L);
        verify(mapper).autoLockWorkloadSet(10L);
        verify(mapper).supersedeOtherWorkloadSets(20L);
        verify(mapper).autoLockWorkloadSet(20L);
    }

    @Test
    void sweeperLocksAllExpiredAssignments() {
        when(mapper.assignmentsWithExpiredOpenWorkloads()).thenReturn(List.of(7L, 8L));
        when(mapper.expiredAssignmentCount(7L)).thenReturn(1);
        when(mapper.expiredAssignmentCount(8L)).thenReturn(1);
        when(mapper.expiredOpenWorkloadSetIds(7L)).thenReturn(List.of(10L));
        when(mapper.expiredOpenWorkloadSetIds(8L)).thenReturn(List.of(11L, 12L));
        when(mapper.autoLockWorkloadSet(10L)).thenReturn(1);
        when(mapper.autoLockWorkloadSet(11L)).thenReturn(1);
        when(mapper.autoLockWorkloadSet(12L)).thenReturn(1);

        assertThat(service.autoLockAllExpired()).isEqualTo(3);
        verify(mapper).autoLockWorkloadSet(12L);
    }

    @Test
    void aVersionSupersededByARosterChangeDoesNotSupersedeTheReplacement() {
        when(mapper.expiredAssignmentCount(7)).thenReturn(1);
        when(mapper.expiredOpenWorkloadSetIds(7)).thenReturn(List.of(10L));
        when(mapper.autoLockWorkloadSet(10L)).thenReturn(0);

        assertThat(service.autoLockExpired(7)).isZero();
        verify(mapper, never()).supersedeOtherWorkloadSets(10L);
    }

    @Test
    void leaderCannotProposeANewVersionAfterTheDeadline() {
        when(mapper.studentLeadsGroup(10, 7, 3)).thenReturn(1);
        when(mapper.expiredAssignmentCount(7)).thenReturn(1);

        assertThatThrownBy(() -> service.proposeWorkload(leader, 7, 10,
                new WorkloadAllocationRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已截止");
        verify(mapper, never()).lockGroupForWorkload(10, 7);
    }

    @Test
    void teacherCanBackfillAWorkloadVersionForAGroupThatNeverSubmittedOne() {
        when(mapper.teacherOwnsGroup(10, 7, 1)).thenReturn(1);
        when(mapper.expiredAssignmentCount(7)).thenReturn(1);
        when(mapper.lockGroupForWorkload(10, 7)).thenReturn(10L);
        when(mapper.parts(7)).thenReturn(List.of());
        when(mapper.members(10)).thenReturn(List.of());

        // Reaching the "empty parts/members" validation proves the deadline guard did not
        // reject the teacher. Students stay frozen so a missing version can still be scored.
        assertThatThrownBy(() -> service.createWorkload(teacher, 7, 10,
                new WorkloadAllocationRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("评价分支或团队成员为空");
    }

    @Test
    void confirmationIsRefusedAfterTheDeadline() {
        when(mapper.expiredWorkloadSetCount(10)).thenReturn(1);

        assertThatThrownBy(() -> service.confirmWorkload(leader, 10,
                new WorkloadConfirmationRequest("CONFIRMED", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("作业已截止");
        verify(mapper, never()).confirmWorkload(10, 3, "CONFIRMED", null);
    }

    @Test
    void readingTheWorkloadTriggersTheLazyAutoLock() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", 10L);
        summary.put("status", "LOCKED");
        summary.put("autoLocked", true);
        when(mapper.expiredAssignmentCount(7)).thenReturn(1);
        when(mapper.expiredOpenWorkloadSetIds(7)).thenReturn(List.of(10L));
        when(mapper.studentWorkload(7, 3)).thenReturn(summary);

        Map<String, Object> result = service.studentWorkload(leader, 7);

        verify(mapper).autoLockWorkloadSet(10L);
        assertThat(result.get("autoLocked")).isEqualTo(true);
    }

    @Test
    void scoringAlsoTriggersTheLazyAutoLock() {
        when(mapper.submissionContext(99, 3)).thenReturn(
                new SubmissionContext(99, 7, 10, 3, 4, 90.0, 1L));
        when(mapper.expiredAssignmentCount(7)).thenReturn(1);
        when(mapper.expiredOpenWorkloadSetIds(7)).thenReturn(List.of(10L));
        when(mapper.lockedWorkloadSet(7, 10)).thenReturn(null);

        assertThatThrownBy(() -> service.runScoring(leader, 99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作量分配尚未得到全员确认并锁定");
        verify(mapper).autoLockWorkloadSet(10L);
    }

    @Test
    void autoLockSqlOnlyTouchesTheNewestOpenVersionPerGroup() throws Exception {
        Method method = ScoringMapper.class.getMethod("expiredOpenWorkloadSetIds", long.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("a.deadline <= CURRENT_TIMESTAMP");
        assertThat(sql).contains("MAX(w2.version_no)");
        assertThat(sql).contains("ws.status IN ('DRAFT','PENDING_CONFIRMATION','CONFIRMED')");

        Method lock = ScoringMapper.class.getMethod("autoLockWorkloadSet", long.class);
        assertThat(String.join(" ", lock.getAnnotation(Update.class).value()))
                .contains("status='LOCKED'");
    }

    @Test
    void workloadReadsExposeWhetherTheVersionWasAutoLocked() throws Exception {
        Method student = ScoringMapper.class.getMethod("studentWorkload", long.class, long.class);
        Method teacher = ScoringMapper.class.getMethod("teacherWorkload", long.class, long.class, long.class);

        assertThat(String.join(" ", student.getAnnotation(Select.class).value()))
                .contains("AS autoLocked");
        assertThat(String.join(" ", teacher.getAnnotation(Select.class).value()))
                .contains("AS autoLocked");
    }
}
