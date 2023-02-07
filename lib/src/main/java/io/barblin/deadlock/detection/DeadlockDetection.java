package io.barblin.deadlock.detection;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static java.lang.Thread.currentThread;

public final class DeadlockDetection {

    private final Map<Lock, Long> lockedBy;
    private final Map<Long, Set<Lock>> waitingFor;
    private final AtomicBoolean detectionInProgress;
    private final AtomicBoolean lockInProgress;
    private final AtomicBoolean unlockInProgress;

    public DeadlockDetection() {
        lockedBy = new HashMap<>();
        waitingFor = new HashMap<>();
        detectionInProgress = new AtomicBoolean(false);
        lockInProgress = new AtomicBoolean(false);
        unlockInProgress = new AtomicBoolean(false);
    }

    public boolean tryLock(Lock lock) {
        validateLock(lock);
        Long threadId = currentThread().getId();

        if (!lockAttemptPossible(threadId, lock)) {
            return false;
        }

        try {
            while (!lockInProgress.compareAndSet(false, true)) {
            }
            lock.lock();
            lockedBy.put(lock, threadId);
        } catch (Exception ex) {
            return false;
        } finally {
            lockInProgress.compareAndSet(true, false);
        }

        removeWaitingFor(threadId, lock);
        return true;
    }

    public void unlock(Lock lock) {
        validateLock(lock);

        try {
            while (!unlockInProgress.compareAndSet(false, true)) {
            }
            lock.unlock();
            lockedBy.remove(lock);
        } finally {
            unlockInProgress.compareAndSet(true, false);
        }
    }

    public Set<Lock> getWaitingFor(Long threadId) {
        return waitingFor.getOrDefault(threadId, new HashSet<>());
    }

    private boolean lockAttemptPossible(Long threadId, Lock lock) {
        try {
            while (!detectionInProgress.compareAndSet(false, true)) {
            }
            if (isDeadlock(threadId, lock)) {
                return false;
            }
            addWaitingFor(threadId, lock);
        } catch (Exception ex) {
            return false;
        } finally {
            detectionInProgress.compareAndSet(true, false);
        }

        return true;
    }

    private boolean isDeadlock(Long threadId, Lock lock) {
        if (lockedBy.containsKey(lock)) {
            if (waitingFor.containsKey(threadId)) {
                return containsCircle(lock, threadId);
            }
        }

        return false;
    }

    private boolean containsCircle(Lock lock, long threadId) {
        LinkedList<Lock> locks = new LinkedList<>();
        Set<Long> visited = new HashSet<>();

        locks.add(lock);
        visited.add(threadId);

        while (!locks.isEmpty()) {
            Lock currentLock = locks.removeFirst();

            if (lockedBy.containsKey(currentLock)) {
                long currentThreadId = lockedBy.get(lock);

                if (visited.contains(currentThreadId)) {
                    return true;
                }

                locks.addAll(waitingFor.get(currentThreadId));
                visited.add(currentThreadId);
            }
        }

        return false;
    }

    private void addWaitingFor(Long threadId, Lock lock) {
        Set<Lock> waitingForLocks = waitingFor.getOrDefault(threadId, new HashSet<>());
        waitingForLocks.add(lock);
        waitingFor.put(threadId, waitingForLocks);
    }

    private void removeWaitingFor(Long threadId, Lock lock) {
        Set<Lock> waitForLocks = waitingFor.getOrDefault(threadId, new HashSet<>());
        waitForLocks.remove(lock);
        waitingFor.put(threadId, waitForLocks);
    }

    private void validateLock(Lock lock) {
        if (Objects.isNull(lock)) {
            throw new IllegalArgumentException("A lock may not be null.");
        }
    }
}