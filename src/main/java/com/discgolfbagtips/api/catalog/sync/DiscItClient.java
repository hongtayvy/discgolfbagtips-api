package com.discgolfbagtips.api.catalog.sync;

import com.discgolfbagtips.api.common.UpstreamServiceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The only place that talks to DiscIt. Called from the scheduled sync, never from a user request —
 * user traffic is always served from our own copy of the catalog.
 */
@Component
public class DiscItClient {

    private static final Logger log = LoggerFactory.getLogger(DiscItClient.class);
    private static final ParameterizedTypeReference<List<DiscItDisc>> DISC_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public DiscItClient(RestClient discItRestClient) {
        this.restClient = discItRestClient;
    }

    @CircuitBreaker(name = "discit", fallbackMethod = "fetchAllFallback")
    @Retry(name = "discit")
    public List<DiscItDisc> fetchAll() {
        log.info("Fetching full disc catalog from DiscIt");
        List<DiscItDisc> discs = restClient.get().uri("/disc").retrieve().body(DISC_LIST);
        if (discs == null || discs.isEmpty()) {
            throw new UpstreamServiceException("discit", "DiscIt returned an empty catalog");
        }
        log.info("DiscIt returned {} discs", discs.size());
        return discs;
    }

    @SuppressWarnings("unused")
    private List<DiscItDisc> fetchAllFallback(Throwable cause) {
        if (cause instanceof UpstreamServiceException upstream) {
            throw upstream;
        }
        String reason = cause instanceof RestClientException ? cause.getMessage() : cause.toString();
        throw new UpstreamServiceException("discit", "Could not reach the DiscIt catalog: " + reason, cause);
    }
}
