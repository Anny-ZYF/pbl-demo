package com.gdmu.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.audit.AuditService;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoringServiceConcurrencyTest {
    private final ScoringMapper mapper = mock(ScoringMapper.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ScoringService service = new ScoringService(
            mapper, new PaperScoringEngine(), mock(ModelClient.class), new ObjectMapper(), auditService);

    @Test
    void serializesConfirmationOnTheWorkloadSetBeforeCountingRemainingMembers() {
        UserPrincipal student = new UserPrincipal(3, "student", "学生", Role.STUDENT,
                "hash", true, false, 0);
        when(mapper.lockWorkloadSetForConfirmation(10, 3)).thenReturn(10L);
        when(mapper.confirmWorkload(10, 3, "CONFIRMED", null)).thenReturn(1);
        when(mapper.unconfirmedCount(10)).thenReturn(0);
        when(mapper.lockWorkload(10)).thenReturn(1);

        service.confirmWorkload(student, 10, new WorkloadConfirmationRequest("CONFIRMED", null));

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockWorkloadSetForConfirmation(10, 3);
        order.verify(mapper).confirmWorkload(10, 3, "CONFIRMED", null);
        order.verify(mapper).unconfirmedCount(10);
        order.verify(mapper).supersedePreviousLocked(10);
        order.verify(mapper).lockWorkload(10);
    }

    @Test
    void canonicalSnapshotJsonDoesNotDependOnMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("groupScore", 80);
        first.put("assignmentId", 7);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("assignmentId", 7);
        second.put("groupScore", 80);

        assertThat(service.canonicalJson(first)).isEqualTo(service.canonicalJson(second));
    }
}
