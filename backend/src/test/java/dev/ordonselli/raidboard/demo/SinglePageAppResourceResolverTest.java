package dev.ordonselli.raidboard.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import dev.ordonselli.raidboard.demo.SinglePageAppConfig.SinglePageAppResourceResolver;

class SinglePageAppResourceResolverTest {

    /** Stands in for classpath:/static: an index.html and one bundle. */
    private final Resource location = new ClassPathResource("spa-test/");
    private final SinglePageAppResourceResolver resolver = new SinglePageAppResourceResolver();

    @Test
    void anExistingFileIsServedAsItIs() throws Exception {
        assertThat(resolver.getResource("main-ABC123.js", location).getFilename()).isEqualTo("main-ABC123.js");
    }

    @Test
    void theRootAndAngularRoutesGetTheApp() throws Exception {
        assertThat(resolver.getResource("", location).getFilename()).isEqualTo("index.html");
        assertThat(resolver.getResource("groups/1/events/5", location).getFilename()).isEqualTo("index.html");
    }

    @Test
    void aMissingFileIsNotFound() throws Exception {
        assertThat(resolver.getResource("main-OLD.js", location)).isNull();
    }

    @Test
    void anUnknownApiPathIsNotFound() throws Exception {
        assertThat(resolver.getResource("api/nothing-here", location)).isNull();
    }
}
