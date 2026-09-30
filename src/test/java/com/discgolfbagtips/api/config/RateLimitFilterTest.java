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
        BagTipsProperties base = TestProperties.defaults();
        return new BagTipsProperties(base.discit(), base.embedding(), base.retrieval(), base.generation(),
                new BagTipsProperties.RateLimit(true, generalCapacity, generalCapacity, Duration.ofMinutes(1),
                        recommendationCapacity, recommendationCapacity, Duration.ofMinutes(1)),
                new BagTipsProperties.Cors(List.of("http://localhost:5173")), base.cache(),
                "test-admin-token");
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
        RateLimitFilter filter = new RateLimitFilter(new RateLimitService(withLimits(2, 10)));

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
        RateLimitFilter filter = new RateLimitFilter(new RateLimitService(withLimits(1, 10)));

        assertThat(call(filter, "GET", "/api/v1/discs/search", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/api/v1/discs/search", "1.1.1.1").getStatus()).isEqualTo(429);
        // A different caller still has a full bucket.
        assertThat(call(filter, "GET", "/api/v1/discs/search", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void theRecommendationEndpointHasItsOwnTighterBudget() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitService(withLimits(50, 1)));

        assertThat(call(filter, "POST", "/api/v1/recommendations", "3.3.3.3").getStatus()).isEqualTo(200);
        assertThat(call(filter, "POST", "/api/v1/recommendations", "3.3.3.3").getStatus()).isEqualTo(429);
        // ...and the tight budget does not spill over onto catalog search.
        assertThat(call(filter, "GET", "/api/v1/discs/search", "3.3.3.3").getStatus()).isEqualTo(200);
    }

    @Test
    void publishesTheRemainingBudgetOnEveryResponse() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitService(withLimits(5, 10)));

        MockHttpServletResponse response = call(filter, "GET", "/api/v1/discs/search", "4.4.4.4");

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
    }

    @Test
    void leavesNonApiTrafficAlone() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitService(withLimits(1, 1)));

        assertThat(call(filter, "GET", "/actuator/health", "5.5.5.5").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/actuator/health", "5.5.5.5").getStatus()).isEqualTo(200);
        assertThat(call(filter, "GET", "/docs", "5.5.5.5").getStatus()).isEqualTo(200);
    }

    @Test
    void prefersTheForwardedClientAddressBehindAProxy() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(new RateLimitService(withLimits(1, 10)));

        MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/v1/discs/search");
        first.setRemoteAddr("10.0.0.1");
        first.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());
        assertThat(firstResponse.getStatus()).isEqualTo(200);

        // Same proxy, same real client: must be refused even though the socket address is shared.
        MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/v1/discs/search");
        second.setRemoteAddr("10.0.0.1");
        second.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());
        assertThat(secondResponse.getStatus()).isEqualTo(429);

        // A different real client behind the same proxy is unaffected.
        MockHttpServletRequest third = new MockHttpServletRequest("GET", "/api/v1/discs/search");
        third.setRemoteAddr("10.0.0.1");
        third.addHeader("X-Forwarded-For", "198.51.100.4, 10.0.0.1");
        MockHttpServletResponse thirdResponse = new MockHttpServletResponse();
        filter.doFilter(third, thirdResponse, new MockFilterChain());
        assertThat(thirdResponse.getStatus()).isEqualTo(200);
    }
}
