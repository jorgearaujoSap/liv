package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.web.RateLimitProperties.Limit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token buckets per key (bucket4j): each key (a person, an address) gets as many requests as its
 * limit, and they come back little by little. The buckets live in the memory of each replica, so
 * each replica gets its share of every limit ({@link RateLimitProperties#ofOneReplica}). A full
 * bucket is the same as a new one, so the full ones are forgotten ({@link #forgetTheFull}), and the
 * memory follows the keys in use.
 *
 * <p>The keys of addresses can be many (whoever controls a range of addresses), so they are capped:
 * past {@link #MAX_KEYS}, first the full buckets go and then some of the others, never the buckets
 * of the people, whose number is bounded by who can sign in.
 */
final class RateLimiter {

  private static final Logger LOG = LoggerFactory.getLogger(RateLimiter.class);

  /** The most keys of addresses kept. */
  static final int MAX_KEYS = 100_000;

  private final Map<String, Kept> people = new ConcurrentHashMap<>();
  private final Map<String, Kept> addresses = new ConcurrentHashMap<>();

  /** A bucket and the capacity it was made with, to know when it is full again. */
  private record Kept(Bucket bucket, int capacity) {

    boolean isFull() {
      return bucket.getAvailableTokens() >= capacity;
    }
  }

  /**
   * Takes one request from the bucket of a person.
   *
   * @return 0 if it fits; otherwise the whole seconds to wait, at least 1
   */
  long takeForAPerson(String key, Limit limit) {
    return take(people, key, limit);
  }

  /** The same for a key of an address: the address, or the sign ins from it. */
  long takeForAnAddress(String key, Limit limit) {
    if (addresses.size() >= MAX_KEYS && !addresses.containsKey(key)) {
      makeRoom();
    }
    return take(addresses, key, limit);
  }

  /** Forgets the buckets that are full again: the next request of their key starts a new one. */
  void forgetTheFull() {
    people.values().removeIf(Kept::isFull);
    addresses.values().removeIf(Kept::isFull);
  }

  int addressKeys() {
    return addresses.size();
  }

  int peopleKeys() {
    return people.size();
  }

  private static long take(Map<String, Kept> buckets, String key, Limit limit) {
    ConsumptionProbe probe =
        buckets
            .computeIfAbsent(key, k -> new Kept(bucket(limit), limit.requests()))
            .bucket()
            .tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return 0;
    }
    return Math.max(
        1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999));
  }

  /** Past the cap: a tenth of the addresses go, the full ones first. */
  private void makeRoom() {
    LOG.warn("El límite de peticiones llegó a {} direcciones: se olvidan algunas", MAX_KEYS);
    int toRemove = MAX_KEYS / 10;
    Iterator<Kept> kept = addresses.values().iterator();
    while (kept.hasNext() && toRemove > 0) {
      if (kept.next().isFull()) {
        kept.remove();
        toRemove--;
      }
    }
    kept = addresses.values().iterator();
    while (kept.hasNext() && toRemove > 0) {
      kept.next();
      kept.remove();
      toRemove--;
    }
  }

  private static Bucket bucket(Limit limit) {
    return Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(limit.requests())
                .refillGreedy(limit.requests(), limit.per())
                .build())
        .build();
  }
}
