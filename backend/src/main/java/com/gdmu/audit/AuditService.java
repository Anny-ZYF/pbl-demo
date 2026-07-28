package com.gdmu.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.security.UserPrincipal;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Service
public class AuditService {
    private final AuditMapper mapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void record(UserPrincipal actor, String eventType, String aggregateType, Object aggregateId,
                       Map<String, ?> detail) {
        try {
            String ipAddress = null;
            String userAgent = null;
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                var request = attributes.getRequest();
                // Do not trust client-supplied forwarding headers here. A trusted reverse proxy can
                // rewrite the connection address before it reaches the application if required.
                ipAddress = request.getRemoteAddr();
                if (ipAddress != null && ipAddress.length() > 45) ipAddress = ipAddress.substring(0, 45);
                userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 512) userAgent = userAgent.substring(0, 512);
            }
            mapper.insert(eventType, actor.role().name(), actor.id(), aggregateType,
                    String.valueOf(aggregateId), MDC.get("requestId"), objectMapper.writeValueAsString(detail),
                    ipAddress, userAgent);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("审计详情无法序列化", ex);
        }
    }
}
