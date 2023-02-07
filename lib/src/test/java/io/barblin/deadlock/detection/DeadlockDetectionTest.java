package io.barblin.deadlock.detection;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadlockDetectionTest {

    @Test
    void shouldReturnTrueOnSuccessfulLocks() throws InterruptedException {
        DeadlockDetection detection = new DeadlockDetection();
        Lock lock1 = new ReentrantLock();
        Lock lock2 = new ReentrantLock();

        AtomicBoolean lock1Locked = new AtomicBoolean(false);
        AtomicBoolean lock2Locked = new AtomicBoolean(false);

        Thread thread1 = new Thread(() -> lock1Locked.set(detection.tryLock(lock1)));
        Thread thread2 = new Thread(() -> lock2Locked.set(detection.tryLock(lock2)));

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        assertTrue(lock1Locked.get());
        assertTrue(lock2Locked.get());
    }

    @Test
    void shouldReturnTrueOnSuccessfulLocksWithWaiting() throws InterruptedException {
        DeadlockDetection detection = new DeadlockDetection();
        Lock lock1 = new ReentrantLock();


        Thread thread1 = new Thread(() -> detection.tryLock(lock1));
        thread1.start();

        Thread thread2 = new Thread(() -> detection.tryLock(lock1));
        thread2.start();

        thread1.join();
        thread2.join(500);

        assertTrue(detection.getWaitingFor(thread2.getId()).contains(lock1));

        thread1.interrupt();
        thread2.interrupt();
    }

    @Test
    void shouldReturnTrueOnSuccessfulLocksWaitingInLine() throws InterruptedException {
        DeadlockDetection detection = new DeadlockDetection();
        Lock lock1 = new ReentrantLock();
        Lock lock2 = new ReentrantLock();
        Lock lock3 = new ReentrantLock();

        AtomicBoolean lock1Locked = new AtomicBoolean(false);
        AtomicBoolean lock2Locked = new AtomicBoolean(false);
        AtomicBoolean lock3Locked = new AtomicBoolean(false);

        Thread thread1 = new Thread(() -> {
            lock1Locked.set(detection.tryLock(lock1));
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            detection.tryLock(lock2);
        });

        Thread thread2 = new Thread(() -> {
            lock2Locked.set(detection.tryLock(lock2));
            try {
                sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            detection.tryLock(lock3);
        });

        Thread thread3 = new Thread(() -> lock3Locked.set(detection.tryLock(lock3)));

        thread1.start();
        thread2.start();
        thread3.start();


        thread1.join(500);
        thread2.join(500);
        thread3.join();

        assertTrue(detection.getWaitingFor(thread1.getId()).contains(lock2));
        assertTrue(detection.getWaitingFor(thread2.getId()).contains(lock3));
        assertTrue(detection.getWaitingFor(thread3.getId()).isEmpty());

        assertTrue(lock1Locked.get());
        assertTrue(lock2Locked.get());
        assertTrue(lock3Locked.get());

        thread1.interrupt();
        thread2.interrupt();
        thread3.interrupt();
    }

    @Test
    void shouldReturnFalseOnCircularWait() throws InterruptedException {
        DeadlockDetection detection = new DeadlockDetection();
        Lock lock1 = new ReentrantLock();
        Lock lock2 = new ReentrantLock();
        Lock lock3 = new ReentrantLock();

        AtomicBoolean lock1Locked = new AtomicBoolean(false);
        AtomicBoolean lock2Locked = new AtomicBoolean(false);
        AtomicBoolean lock3Locked = new AtomicBoolean(false);
        AtomicBoolean circularLockSuccessful = new AtomicBoolean(true);

        Thread thread1 = new Thread(() -> {
            lock1Locked.set(detection.tryLock(lock1));
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            detection.tryLock(lock2);
        });

        Thread thread2 = new Thread(() -> {
            lock2Locked.set(detection.tryLock(lock2));
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            detection.tryLock(lock3);
        });

        Thread thread3 = new Thread(() -> {
            lock3Locked.set(detection.tryLock(lock3));
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            circularLockSuccessful.set(detection.tryLock(lock1));
        });

        thread1.start();
        thread2.start();
        thread3.start();


        thread1.join(1000);
        thread2.join(1000);
        thread3.join(1000);

        assertTrue(detection.getWaitingFor(thread1.getId()).contains(lock2));
        assertTrue(detection.getWaitingFor(thread2.getId()).contains(lock3));
        assertTrue(detection.getWaitingFor(thread3.getId()).isEmpty());

        assertTrue(lock1Locked.get());
        assertTrue(lock2Locked.get());
        assertTrue(lock3Locked.get());
        assertFalse(circularLockSuccessful.get());

        thread1.interrupt();
        thread2.interrupt();
        thread3.interrupt();
    }
}