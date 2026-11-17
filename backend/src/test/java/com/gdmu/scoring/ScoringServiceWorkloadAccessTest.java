package com.gdmu.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.apache.ibatis.annotations.Select;
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

class ScoringServiceWorkloadAccessTest {
    private final ScoringMapper mapper = mock(ScoringMapper.class);
    private final ScoringService service = new ScoringService(
            mapper, new PaperScoringEngine(), mock(ModelClient.class), new ObjectMapper(), mock(AuditService.class));
    private final UserPrincipal leader = new UserPrincipal(3, "leader", "组长", Role.STUDENT,
            "hash", true, false, 0);

    @Test
    void groupLeaderPassesThePermissionCheck() {
        when(mapper.studentLeadsGroup(10, 7, 3)).thenReturn(1);
        when(mapper.lockGroupForWorkload(10, 7)).thenReturn(10L);
        when(mapper.parts(7)).thenReturn(List.of());
        when(mapper.members(10)).thenReturn(List.of());

        assertThatThrownBy(() -> service.proposeWorkload(leader, 7, 10,
                new WorkloadAllocationRequest(List.of())))
                .hasMessageContaining("评价分支或团队成员为空");
    }

    @Test
    void nonLeaderIsRejectedBeforeAnyWrite() {
        when(mapper.studentLeadsGroup(10, 7, 3)).thenReturn(0);

        assertThatThrownBy(() -> service.proposeWorkload(leader, 7, 10,
                new WorkloadAllocationRequest(List.of())))
                .hasMessageContaining("只有该团队组长可以提交工作量分配方案");
    }

    @Test
    void leaderLookupAcceptsStudentCreatedFormingGroups() throws Exception {
        Method method = ScoringMapper.class.getMethod("studentLeadsGroup", long.class, long.class, long.class);
        Select select = method.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        String sql = String.join(" ", select.value());
        assertThat(sql).contains("leader_student_id=#{studentId}");
        assertThat(sql).doesNotContain("team_status IN ('CONFIRMED','COMPLETED')");
        assertThat(sql).contains("team_status NOT IN ('DISSOLVED','ARCHIVED')");
    }

    @Test
    void confirmationIsFrozenAfterAnyMemberObjects() {
        when(mapper.lockWorkloadSetForConfirmation(10, 3)).thenReturn(10L);
        when(mapper.objectedCount(10)).thenReturn(1);

        assertThatThrownBy(() -> service.confirmWorkload(leader, 10,
                new WorkloadConfirmationRequest("CONFIRMED", null)))
                .hasMessageContaining("已有成员提出异议");
        verify(mapper, never()).confirmWorkload(10, 3, "CONFIRMED", null);
        verify(mapper, never()).lockWorkload(10);
    }

    @Test
    void objectionIsRecordedWithoutLockingTheVersion() {
        when(mapper.lockWorkloadSetForConfirmation(10, 3)).thenReturn(10L);
        when(mapper.confirmWorkload(10, 3, "OBJECTED", "份额不符")).thenReturn(1);

        service.confirmWorkload(leader, 10, new WorkloadConfirmationRequest("OBJECTED", "份额不符"));

        verify(mapper).confirmWorkload(10, 3, "OBJECTED", "份额不符");
        verify(mapper, never()).lockWorkload(10);
    }

    @Test
    void studentWorkloadExposesObjectionReasons() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", 10L);
        summary.put("status", "PENDING_CONFIRMATION");
        List<Map<String, Object>> objections = List.of(
                Map.of("studentId", 2L, "studentName", "陆盈", "comment", "份额不符"));
        when(mapper.studentWorkload(7, 3)).thenReturn(summary);
        when(mapper.workloadItems(10)).thenReturn(List.of());
        when(mapper.workloadObjections(10)).thenReturn(objections);

        Map<String, Object> result = service.studentWorkload(leader, 7);

        assertThat(result.get("objections")).isEqualTo(objections);
    }
}
