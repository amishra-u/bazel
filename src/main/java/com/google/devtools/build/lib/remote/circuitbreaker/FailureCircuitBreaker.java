package com.google.devtools.build.lib.remote.circuitbreaker;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.remote.Retrier;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link FailureCircuitBreaker} implementation of the {@link Retrier.CircuitBreaker} prevents further calls to a remote cache
 * once the failures rate within a given window exceeds a specified threshold for a build.
 * In the context of Bazel, a new instance of {@link Retrier.CircuitBreaker} is created for each build. Therefore, if the circuit
 * breaker trips during a build, the remote cache will be disabled for that build. However, it will be enabled again
 * for the next build as a new instance of {@link Retrier.CircuitBreaker} will be created.
 */
public class FailureCircuitBreaker implements Retrier.CircuitBreaker {

  private State state;
  private final AtomicInteger successes;
  private final AtomicInteger failures;
  private final AtomicInteger ignoredFailures;
  private final int failureRateThreshold;
  private final int slidingWindowSize;
  private final int minCallCountToComputeFailureRate;
  private final ScheduledExecutorService scheduledExecutor;
  private final ImmutableSet<Class<? extends Exception>> ignoredErrors;

  /**
   * Creates a {@link FailureCircuitBreaker}.
   *
   * @param failureRateThreshold  is used to set the min percentage of failure required to trip the circuit breaker in
   *                             given time window.
   * @param slidingWindowSize the size of the sliding window in milliseconds to calculate the number of failures.
   */
  public FailureCircuitBreaker(int failureRateThreshold, int slidingWindowSize) {
    this.failures = new AtomicInteger(0);
    this.successes = new AtomicInteger(0);
    this.ignoredFailures = new AtomicInteger(0);
    this.failureRateThreshold = failureRateThreshold;
    this.slidingWindowSize = slidingWindowSize;
    this.minCallCountToComputeFailureRate = CircuitBreakerFactory.DEFAULT_MIN_CALL_COUNT_TO_COMPUTE_FAILURE_RATE;
    this.state = State.ACCEPT_CALLS;
    this.scheduledExecutor = slidingWindowSize > 0 ? Executors.newSingleThreadScheduledExecutor() : null;
    this.ignoredErrors = CircuitBreakerFactory.DEFAULT_IGNORED_ERRORS;
  }

  @Override
  public State state() {
    return this.state;
  }

  @Override
  public void recordFailure(Exception e) {
    if (!ignoredErrors.contains(e.getClass())) {
      int failureCount = failures.incrementAndGet();
      int totalCallCount = successes.get() + failureCount + ignoredFailures.get();
      if (slidingWindowSize > 0) {
        scheduledExecutor.schedule(failures::decrementAndGet, slidingWindowSize, TimeUnit.MILLISECONDS);
      }

      System.out.println(failureCount);
      System.out.println(totalCallCount);

      if (totalCallCount < minCallCountToComputeFailureRate) {
        // The remote call count is below the threshold required to calculate the failure rate.
        return;
      }
      double failureRate = (failureCount * 100.0) / totalCallCount;

      // Since the state can only be changed to the open state, synchronization is not required.
      if (failureRate > this.failureRateThreshold) {
        this.state = State.REJECT_CALLS;
      }
    } else {
      ignoredFailures.incrementAndGet();
      if (slidingWindowSize > 0) {
        scheduledExecutor.schedule(ignoredFailures::decrementAndGet, slidingWindowSize, TimeUnit.MILLISECONDS);
      }
    }
  }

  @Override
  public void recordSuccess() {
    successes.incrementAndGet();
    if (slidingWindowSize > 0) {
      scheduledExecutor.schedule(successes::decrementAndGet, slidingWindowSize, TimeUnit.MILLISECONDS);
    }
  }
}