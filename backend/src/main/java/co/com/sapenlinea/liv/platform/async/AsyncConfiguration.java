package co.com.sapenlinea.liv.platform.async;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Asynchronous listeners (the audit of T15): they run after the commit in the executor of Spring
 * Boot, on virtual threads ({@code spring.threads.virtual.enabled}). A listener has no request: it
 * opens the administration of its event before any transaction (plan step 7).
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class AsyncConfiguration {}
