package dev.cerez.tahp.utils;

import org.jetbrains.annotations.NotNull;

import java.sql.Time;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WaitableSet<T> {

    private final Set<T> elements = new HashSet<>();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition emptyCondition = lock.newCondition();

    public void add(T element) {
        lock.lock();
        try {
            elements.add(element);
        } finally {
            lock.unlock();
        }
    }

    public void addAll(Iterable<T> elements) {
        lock.lock();
        try {
            for (T element : elements) {
                this.elements.add(element);
            }
        } finally {
            lock.unlock();
        }
    }
    public void addAll(Collection<T> elements) {
        lock.lock();
        try {
            this.elements.addAll(elements);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(T element) {
        boolean result;
        lock.lock();
        try {
            boolean removed = elements.remove(element);
            result = removed;
            if (removed && elements.isEmpty()) {
                emptyCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return elements.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void awaitEmpty() throws InterruptedException {
        lock.lock();
        try {
            while (!elements.isEmpty()) {
                emptyCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean awaitEmpty(long timeout, @NotNull TimeUnit timeUnite) throws InterruptedException {
        lock.lock();
        try {
            long remaining = timeUnite.toNanos(timeout);

            while (!elements.isEmpty() && remaining > 0) {
                remaining = emptyCondition.awaitNanos(remaining);
            }
            boolean result = elements.isEmpty();
            elements.clear();
            return result;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return elements.size();
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            elements.clear();
            emptyCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}