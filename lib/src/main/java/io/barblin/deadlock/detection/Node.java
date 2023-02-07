package io.barblin.deadlock.detection;

public sealed interface Node permits LockNode, ThreadNode {

    void addNode();
}