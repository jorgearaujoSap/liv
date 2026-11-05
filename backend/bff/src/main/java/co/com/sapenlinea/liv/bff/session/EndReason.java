package co.com.sapenlinea.liv.bff.session;

/** Why a session ended, as EndSession (core-internal.yaml) names it. */
public enum EndReason {
  /** The person signs out. */
  LOGOUT,
  /** Fifteen minutes without activity. */
  IDLE,
  /** The maximum duration of eight hours. */
  ABSOLUTE,
  /** The identity provider closed it, or the token could not be renewed. */
  PROVIDER
}
