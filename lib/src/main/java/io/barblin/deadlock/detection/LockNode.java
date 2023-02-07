package io.barblin.deadlock.detection;

import java.util.concurrent.locks.Lock;

record LockNode(Lock l) implements Node {

    @Override
    public void addNode() {

    }
}
