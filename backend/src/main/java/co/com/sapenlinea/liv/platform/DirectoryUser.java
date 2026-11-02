package co.com.sapenlinea.liv.platform;

import java.util.UUID;

/** A registered user as the directory sees it: id, registered administration and state. */
public record DirectoryUser(UUID id, UUID administrationId, boolean active) {}
