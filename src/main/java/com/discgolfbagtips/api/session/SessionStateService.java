package com.discgolfbagtips.api.session;

import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.RecommendationResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Session-scoped state instead of user accounts. Everything a visitor does lives under their
 * session cookie; return-visit support would be a matter of promoting this to a real user record.
 *
 * <p>State is held as JSON rather than as serialized objects. Spring Session JDBC persists
 * attributes with Java serialization, and tying the on-disk shape of a live session to the exact
 * class layout of a response DTO makes every DTO change a breaking one.
 */
@Service
public class SessionStateService {

    static final String REQUEST_ATTRIBUTE = "bagtips.session.request";
    static final String RECOMMENDATION_ATTRIBUTE = "bagtips.session.recommendation";
    static final String UPDATED_AT_ATTRIBUTE = "bagtips.session.updatedAt";

    private static final Logger log = LoggerFactory.getLogger(SessionStateService.class);

    private final ObjectMapper objectMapper;

    public SessionStateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String sessionId(HttpSession session) {
        return session.getId();
    }

    public SessionState current(HttpSession session) {
        return new SessionState(
                read(session, REQUEST_ATTRIBUTE, BagAnalysisRequest.class),
                read(session, RECOMMENDATION_ATTRIBUTE, RecommendationResponse.class),
                readInstant(session));
    }

    public void recordRequest(HttpSession session, BagAnalysisRequest request) {
        write(session, REQUEST_ATTRIBUTE, request);
        session.setAttribute(UPDATED_AT_ATTRIBUTE, Instant.now().toString());
    }

    public void recordRecommendation(HttpSession session, BagAnalysisRequest request,
            RecommendationResponse response) {
        write(session, REQUEST_ATTRIBUTE, request);
        write(session, RECOMMENDATION_ATTRIBUTE, response);
        session.setAttribute(UPDATED_AT_ATTRIBUTE, Instant.now().toString());
    }

    public void clear(HttpSession session) {
        session.removeAttribute(REQUEST_ATTRIBUTE);
        session.removeAttribute(RECOMMENDATION_ATTRIBUTE);
        session.removeAttribute(UPDATED_AT_ATTRIBUTE);
    }

    private void write(HttpSession session, String attribute, Object value) {
        session.setAttribute(attribute, objectMapper.writeValueAsString(value));
    }

    private <T> T read(HttpSession session, String attribute, Class<T> type) {
        Object stored = session.getAttribute(attribute);
        if (!(stored instanceof String json) || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException ex) {
            // A DTO changed shape since this session was written; drop the stale value rather than fail.
            log.debug("Discarding unreadable session attribute {}: {}", attribute, ex.getOriginalMessage());
            session.removeAttribute(attribute);
            return null;
        }
    }

    private Instant readInstant(HttpSession session) {
        Object stored = session.getAttribute(UPDATED_AT_ATTRIBUTE);
        if (!(stored instanceof String value) || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
