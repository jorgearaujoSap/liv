package co.com.sapenlinea.liv.core.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sessions of LIV.
 *
 * @param autoProvision whether an unknown (issuer, subject) is registered on its first login
 *     (001-21). Turned off, for example while the identity provider changes, unknown users are
 *     refused (403, 001-17) and are linked explicitly (decision of the project lead, T13).
 */
@ConfigurationProperties("liv.sessions")
public record SessionProperties(@DefaultValue("true") boolean autoProvision) {}
