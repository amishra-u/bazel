package com.google.devtools.build.lib.remote.circuitbreaker;

import build.bazel.remote.execution.v2.Digest;
import com.google.devtools.build.lib.remote.Retrier.CircuitBreaker.State;
import com.google.devtools.build.lib.remote.common.CacheNotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class FailureCircuitBreakerTest {

    @Test
    public void testRecordFailure_WithIgnoredErrors() throws InterruptedException {
        final int failureRateThreshold = 10;
        final int windowInterval = 100;
        FailureCircuitBreaker failureCircuitBreaker =
            new FailureCircuitBreaker(failureRateThreshold, windowInterval);

        List<Exception> listOfExceptionThrownOnFailure = new ArrayList<>();
        for (int index = 0; index < failureRateThreshold; index++) {
            listOfExceptionThrownOnFailure.add(new Exception());
        }
        for (int index = 0; index < failureRateThreshold * 9; index++) {
            listOfExceptionThrownOnFailure.add(new CacheNotFoundException(Digest.newBuilder().build()));
        }

        Collections.shuffle(listOfExceptionThrownOnFailure);

        // make calls equals to threshold number of not ignored failure calls in parallel.
        listOfExceptionThrownOnFailure.stream().parallel().forEach(failureCircuitBreaker::recordFailure);
        assertThat(failureCircuitBreaker.state()).isEqualTo(State.ACCEPT_CALLS);

        // Sleep for windowInterval + 1ms.
        Thread.sleep(windowInterval + 1 /*to compensate any delay*/);

        // make calls equals to threshold number of not ignored failure calls in parallel.
        listOfExceptionThrownOnFailure.stream().parallel().forEach(failureCircuitBreaker::recordFailure);
        assertThat(failureCircuitBreaker.state()).isEqualTo(State.ACCEPT_CALLS);

        // Sleep for less than windowInterval.
        Thread.sleep(windowInterval - 5);
        failureCircuitBreaker.recordFailure(new Exception());
        assertThat(failureCircuitBreaker.state()).isEqualTo(State.REJECT_CALLS);
    }

    @Test
    public void testRecordFailure_MinCallCriteriaNotMet() throws InterruptedException {
        final int failureRateThreshold = 10;
        final int windowInterval = 100;
        final int minCallToComputeFailure = CircuitBreakerFactory.DEFAULT_MIN_CALL_COUNT_TO_COMPUTE_FAILURE_RATE;
        FailureCircuitBreaker failureCircuitBreaker =
            new FailureCircuitBreaker(failureRateThreshold, windowInterval);

        // make half failure call, half success call and number of total call less than minCallToComputeFailure.
        IntStream.range(0, minCallToComputeFailure >> 1).parallel()
            .forEach(i -> failureCircuitBreaker.recordFailure(new Exception()));
        IntStream.range(0, minCallToComputeFailure >> 1).parallel().forEach(i -> failureCircuitBreaker.recordSuccess());
        assertThat(failureCircuitBreaker.state()).isEqualTo(State.ACCEPT_CALLS);

        // Sleep for less than windowInterval.
        Thread.sleep(windowInterval - 5);
        failureCircuitBreaker.recordFailure(new Exception());
        assertThat(failureCircuitBreaker.state()).isEqualTo(State.REJECT_CALLS);
    }
}
