package com.gdmu.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditMapper {
    @Insert("""
            INSERT INTO audit_event(event_type, actor_role, actor_id, aggregate_type, aggregate_id,
                                    request_id, detail_json, ip_address, user_agent, created_at)
            VALUES(#{eventType}, #{actorRole}, #{actorId}, #{aggregateType}, #{aggregateId},
                   #{requestId}, #{detailJson}, #{ipAddress}, #{userAgent}, CURRENT_TIMESTAMP)
            """)
    int insert(@Param("eventType") String eventType, @Param("actorRole") String actorRole,
               @Param("actorId") long actorId, @Param("aggregateType") String aggregateType,
               @Param("aggregateId") String aggregateId, @Param("requestId") String requestId,
               @Param("detailJson") String detailJson, @Param("ipAddress") String ipAddress,
               @Param("userAgent") String userAgent);
}
