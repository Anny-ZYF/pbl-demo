package com.gdmu.teaching;

import com.gdmu.audit.AuditService;
import com.gdmu.common.BusinessException;
import com.gdmu.security.Role;
import com.gdmu.security.UserPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeachingServiceOwnershipTest {
    @Test
    void teacherCannotReadAnotherTeachersAssignments() {
        TeachingMapper mapper = mock(TeachingMapper.class);
        TeachingService service = new TeachingService(mapper, mock(AuditService.class));
        UserPrincipal teacher = new UserPrincipal(7, "T7", "教师7", Role.TEACHER, "", true, false, 0);
        when(mapper.ownsCourse(99, 7)).thenReturn(0);

        assertThatThrownBy(() -> service.teacherAssignments(teacher, 99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前教师");
    }

    @Test
    void studentCannotReadMembersOfAnotherGroup() {
        TeachingMapper mapper = mock(TeachingMapper.class);
        TeachingService service = new TeachingService(mapper, mock(AuditService.class));
        UserPrincipal student = new UserPrincipal(9, "S9", "学生9", Role.STUDENT, "", true, false, 0);
        when(mapper.studentOwnsGroup(42, 9)).thenReturn(0);

        assertThatThrownBy(() -> service.studentGroupMembers(student, 42))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是有效成员");
    }
}
