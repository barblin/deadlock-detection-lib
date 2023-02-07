package io.barblin.deadlock.detection;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static java.lang.Thread.currentThread;

public final class DeadlockGraph {

    private final Map<Lock, Long> lockedBy;
    private final Map<Long, Set<Lock>> waitingFor;
    private final AtomicBoolean lockInProgress;
    private final AtomicBoolean unlockInProgress;

    public DeadlockGraph() {
        lockedBy = new HashMap<>();
        waitingFor = new HashMap<>();
        lockInProgress = new AtomicBoolean(false);
        unlockInProgress = new AtomicBoolean(false);
    }

    public boolean tryLock(Lock lock) {
        validateLock(lock);

        try {
            while (!lockInProgress.compareAndSet(false, true)) {
            }

            Long threadId = currentThread().getId();

            if (threadId.equals(lockedBy.get(lock))) {
                return true;
            }

            if (isDeadlock(threadId, lock)) {
                return false;
            }

            addWaitingFor(threadId, lock);
            lockInProgress.compareAndSet(true, false);

            boolean success = lock.tryLock();
            removeWaitingFor(threadId, lock);

            return success;

        } catch (Exception ex) {
            lockInProgress.compareAndSet(true, false);
            return false;
        }
    }

    public void unlock(Lock lock) {
        validateLock(lock);

        try {
            while (!unlockInProgress.compareAndSet(false, true)) {
            }

            lock.unlock();
            lockedBy.remove(lock);
        } finally {
            unlockInProgress.set(false);
        }
    }

    private boolean isDeadlock(Long threadId, Lock lock) {
        if (lockedBy.containsKey(lock)) {
            if (waitingFor.containsKey(threadId)) {
                return containsCircle(lock, threadId);
            }
        }

        return false;
    }

    private void addWaitingFor(Long threadId, Lock lock) {
        Set<Lock> waitingForLocks = waitingFor.get(threadId);
        waitingForLocks.add(lock);
        waitingFor.put(threadId, waitingForLocks);
    }

    private void removeWaitingFor(Long threadId, Lock lock) {
        Set<Lock> waitForLocks = waitingFor.get(threadId);
        waitForLocks.remove(lock);
        waitingFor.put(threadId, waitForLocks);
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
            }
        }

        return false;
    }

    private void validateLock(Lock lock) {
        if (Objects.isNull(lock)) {
            throw new IllegalArgumentException("A lock may not be null.");
        }
    }
}
