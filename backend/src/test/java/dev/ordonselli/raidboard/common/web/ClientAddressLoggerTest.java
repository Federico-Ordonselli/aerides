package dev.ordonselli.raidboard.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ClientAddressLoggerTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ClientAddressLogger.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final ClientAddressLogger filter = new ClientAddressLogger();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogger() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    void logsTheResolvedAddressOfApiRequestsAtDebug() throws Exception {
        logger.setLevel(Level.DEBUG);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/auth/login"), new MockHttpServletResponse(), chain);

        assertThat(appender.list).singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .isEqualTo("POST /api/auth/login from 203.0.113.7 (unconsumed X-Forwarded-For: 198.51.100.1)");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void staysQuietWhenDebugIsOff() throws Exception {
        logger.setLevel(Level.INFO);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/api/auth/login"), new MockHttpServletResponse(), chain);

        assertThat(appender.list).isEmpty();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void skipsRequestsOutsideTheApi() throws Exception {
        logger.setLevel(Level.DEBUG);

        filter.doFilter(request("/actuator/health/liveness"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(appender.list).isEmpty();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        return request;
    }
}
