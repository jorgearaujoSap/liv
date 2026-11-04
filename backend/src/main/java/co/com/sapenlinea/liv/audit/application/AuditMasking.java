package co.com.sapenlinea.liv.audit.application;

import java.util.Map;

/** What the audit keeps of personal data (001-18). Pending: T15. */
public final class AuditMasking {

  public AuditMasking(String key) {
    throw new UnsupportedOperationException("T15");
  }

  public static String truncateIp(String ip) {
    throw new UnsupportedOperationException("T15");
  }

  public Map<String, Object> maskUsername(String typed) {
    throw new UnsupportedOperationException("T15");
  }
}
