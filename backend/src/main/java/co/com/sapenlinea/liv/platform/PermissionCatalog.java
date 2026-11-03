package co.com.sapenlinea.liv.platform;

import java.util.Set;

/**
 * The roles and permissions of LIV (doc. 02 §4, plan: permissions are resolved in LIV). The token
 * says which roles the user has; LIV keeps only those of its catalog and gives them their
 * permissions ({@code <module>.<resource>.<action>}).
 */
public interface PermissionCatalog {

  /** The roles of the catalog among {@code roles}; the rest of the provider's roles are ignored. */
  Set<String> knownRoles(Set<String> roles);

  /** The union of the permissions of {@code roles}. */
  Set<String> permissionsOf(Set<String> roles);
}
