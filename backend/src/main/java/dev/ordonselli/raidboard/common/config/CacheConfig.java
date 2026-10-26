package dev.ordonselli.raidboard.common.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/** Turns on @Cacheable. Caches are Caffeine (in-memory), configured under spring.cache in application.yml. */
@Configuration
@EnableCaching
public class CacheConfig {
}
