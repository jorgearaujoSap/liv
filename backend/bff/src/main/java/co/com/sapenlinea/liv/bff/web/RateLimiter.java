package co.com.sapenlinea.liv.bff.web;

import co.com.sapenlinea.liv.bff.web.RateLimitProperties.Limit;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Token buckets per key (bucket4j): each key (a person, an address) gets as many requests as its
 * limit, and they come back little by little. The buckets live in the memory of each replica: with
 * several, a key may make as many requests per replica, which is enough to stop an abuse. A full
 * bucket is the same as a new one, so the full ones are forgotten ({@link #forgetTheFull}), and the
 * memory follows the keys in use; past {@link #MAX_KEYS} every bucket is forgotten.
 */
final class RateLimiter {

  /** The most keys kept; past it the buckets start again, rather than filling the memory. */
  static final int MAX_KEYS = 100_000;

  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  /**
   * Takes one request from the bucket of the key.
   *
   * @return 0 if it fits; otherwise the whole seconds to wait, at least 1
   */
  long take(String key, Limit limit) {
    if (buckets.size() >= MAX_KEYS) {
      buckets.clear();
    }
    ConsumptionProbe probe =
        buckets.computeIfAbsent(key, k -> bucket(limit)).tryConsumeAndReturnRemaining(1);
    if (probe.isConsumed()) {
      return 0;
    }
    return Math.max(
        1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill() + 999_999_999));
  }

  /** Forgets the buckets that are full again: the next request of their key starts a new one. */
  void forgetTheFull(Map<String, Limit> limitsByPrefix) {
    buckets
        .entrySet()
        .removeIf(entry -> {
          Limit limit = limitOf(entry.getKey(), limitsByPrefix);
          return limit != null && entry.getValue().getAvailableTokens() >= limit.requests();
        });
  }

  int size() {
    return buckets.size();
  }

  private static Limit limitOf(String key, Map<String, Limit> limitsByPrefix) {
    for (Map.Entry<String, Limit> prefix : limitsByPrefix.entrySet()) {
      if (key.startsWith(prefix.getKey())) {
        return prefix.getValue();
      }
    }
    return null;
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
