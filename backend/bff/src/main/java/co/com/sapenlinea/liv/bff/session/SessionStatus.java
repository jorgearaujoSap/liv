package co.com.sapenlinea.liv.bff.session;

/**
 * What the frontend is told about the session (contract bff.yaml, {@code SessionStatus}): the
 * seconds until it expires from inactivity and until it reaches its maximum duration.
 */
public record SessionStatus(long idleSecondsLeft, long absoluteSecondsLeft) {

  /** The JSON of the contract, written by hand where there is no message converter. */
  public String toJson() {
    return "{\"idleSecondsLeft\":%d,\"absoluteSecondsLeft\":%d}"
        .formatted(idleSecondsLeft, absoluteSecondsLeft);
  }
}
