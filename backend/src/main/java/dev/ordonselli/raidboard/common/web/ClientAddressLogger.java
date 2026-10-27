package dev.ordonselli.raidboard.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Logs the client address the rate limiter will see, to check a deployment behind a proxy.
 * Off by default: turn it on with
 * {@code LOGGING_LEVEL_DEV_ORDONSELLI_RAIDBOARD_COMMON_WEB=DEBUG}, call the API once and
 * compare the logged address with your public IP (see infra/README.md, "After deploying"). The package, not the
 * class: Spring Boot lowercases environment variable names, so they can't name a class logger.
 * <p>
 * By the time a filter runs, Tomcat has already applied X-Forwarded-For ({@code server.forward-headers-strategy}),
 * so {@code getRemoteAddr()} is the resolved address. The header that is left over holds what Tomcat did not
 * consume: empty when every hop was a trusted proxy, otherwise the addresses to the left of the client.
 * If the logged address is a proxy's, the proxies are not matched by {@code server.tomcat.remoteip.internal-proxies}.
 */
@Component
public class ClientAddressLogger extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ClientAddressLogger.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only the API: the health probes would fill the log with the platform's own addresses
        return !log.isDebugEnabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        log.debug("{} {} from {} (unconsumed X-Forwarded-For: {})", request.getMethod(), request.getRequestURI(),
                request.getRemoteAddr(), request.getHeader("X-Forwarded-For"));
        chain.doFilter(request, response);
    }
}
