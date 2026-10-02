package com.discgolfbagtips.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestProperties;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private BagTipsProperties withLimits(int generalCapacity, int recommendationCapacity) {
        return withLimits(generalCapacity, recommendationCapacity, 1);
    }

    private BagTipsProperties withLimits(int generalCapacity, int recommendationCapacity, int hops) {
        BagTipsProperties base = TestProperties.defaults();
        return new BagTipsProperties(base.discit(), base.embedding(), base.retrieval(), base.generation(),
                new BagTipsProperties.RateLimit(true, generalCapacity, generalCapacity, Duration.ofMinutes(1),
                        recommendationCapacity, recommendationCapacity, Duration.ofMinutes(1), hops),
                new BagTipsProperties.Cors(List.of("http://localhost:5173")), base.cache(),
                "test-admin-token");
    }

    private RateLimitFilter filterFor(BagTipsProperties properties) {
        return new RateLimitFilter(new RateLimitService(properties), properties);
    }

    private MockHttpServletResponse call(RateLimitFilter filter, String method, String uri, String ip)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void refusesOnceTheBucketIsEmptyAndSaysWhenToComeBack() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(2, 10));

        assertThat(call(filter, "GET", "/api/v1/discs/search", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/api/v1/discs/search", "1.1.1.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse refused = call(filter, "GET", "/api/v1/discs/search", "1.1.1.1");
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).isNotNull();
        assertThat(refused.getContentType()).isEqualTo("application/problem+json");
        assertThat(refused.getContentAsString()).contains("rate-limited");
    }

    @Test
    void countsEachClientSeparately() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(1, 10));

        assertThat(call(filter, "GET", "/api/v1/discs/search", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/api/v1/discs/search", "1.1.1.1").getStatus()).isEqualTo(429);
        // A different caller still has a full bucket.
        assertThat(call(filter, "GET", "/api/v1/discs/search", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void theRecommendationEndpointHasItsOwnTighterBudget() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(50, 1));

        assertThat(call(filter, "POST", "/api/v1/recommendations", "3.3.3.3").getStatus()).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/v1/recommendations", "3.3.3.3").getStatus()).isEqualTo(429);
        // ...and the tight budget does not spill over onto catalog search.
        assertThat(call(filter, "GET", "/api/v1/discs/search", "3.3.3.3").getStatus()).isEqualTo(200);
    }

    @Test
    void publishesTheRemainingBudgetOnEveryResponse() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(5, 10));

        MockHttpServletResponse response = call(filter, "GET", "/api/v1/discs/search", "4.4.4.4");

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
    }

    @Test
    void leavesNonApiTrafficAlone() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(1, 1));

        assertThat(call(filter, "GET", "/actuator/health", "5.5.5.5").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/actuator/health", "5.5.5.5").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/docs", "5.5.5.5").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse callWithForwarded(RateLimitFilter filter, String forwarded) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/discs/search");
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", forwarded);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    /** The proxy appends the address it saw, so the real client is the rightmost entry. */
    @Test
    void readsTheClientFromTheRightOfForwardedFor() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(1, 10, 1));

        assertThat(callWithForwarded(filter, "203.0.113.7").getStatus()).isEqualTo(200);
        assertThat(callWithForwarded(filter, "203.0.113.7").getStatus()).isEqualTo(429);
        // A different real client behind the same proxy is unaffected.
        assertThat(callWithForwarded(filter, "198.51.100.4").getStatus()).isEqualTo(200);
    }

    /**
     * The attack this exists to stop: a caller writes a fresh fake address on the left of every
     * request. The proxy still appends their true one on the right, so they remain one client.
     */
    @Test
    void aSpoofedLeadingAddressDoesNotBuyAFreshBucket() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(1, 10, 1));

        assertThat(callWithForwarded(filter, "1.1.1.1, 203.0.113.7").getStatus()).isEqualTo(200);
        assertThat(callWithForwarded(filter, "2.2.2.2, 203.0.113.7").getStatus()).isEqualTo(429);
        assertThat(callWithForwarded(filter, "3.3.3.3, 4.4.4.4, 203.0.113.7").getStatus()).isEqualTo(429);
    }

    /** Two proxies in a chain: the client sits one place further in. */
    @Test
    void countsInFurtherWhenMoreProxiesAreTrusted() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(1, 10, 2));

        assertThat(callWithForwarded(filter, "spoof-a, 203.0.113.7, 172.16.0.9").getStatus()).isEqualTo(200);
        assertThat(callWithForwarded(filter, "spoof-b, 203.0.113.7, 172.16.0.9").getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresTheHeaderEntirelyWhenNoProxyIsTrusted() throws Exception {
        RateLimitFilter filter = filterFor(withLimits(1, 10, 0));

        // Everything arrives from the same socket address, so a forged header buys nothing.
        assertThat(callWithForwarded(filter, "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(callWithForwarded(filter, "2.2.2.2").getStatus()).isEqualTo(429);
    }
}
