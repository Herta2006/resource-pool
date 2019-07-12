package impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResourcePoolImpl<R> implements ResourcePool<R> {
    private final AtomicBoolean open = new AtomicBoolean();
    private final BlockingQueue<R> availableResources = new LinkedBlockingQueue<>();
    private final Map<R, AtomicBoolean> resourceToLock = new ConcurrentHashMap<>();

    public ResourcePoolImpl(Set<R> resources) {
        resources.forEach(r -> resourceToLock.put(r, new AtomicBoolean()));
        availableResources.addAll(resources);
    }

    @Override
    public void open() {
        open.set(true);
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        while (resourceToLock.values().stream().anyMatch(AtomicBoolean::get)) {
            // busy wait
        }
        open.set(false);
    }

    @Override
    public void closeNow() {
        open.set(false);
    }

    @Override
    public R acquire() throws InterruptedException {
        if (!isOpen()) return null;

        R resource = availableResources.take();
        resourceToLock.get(resource).set(true);

        return resource;
    }

    @Override
    public R acquire(long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (!isOpen()) return null;

        R resource = availableResources.poll(timeout, timeUnit);
        resourceToLock.get(resource).set(true);

        return resource;
    }

    @Override
    public void release(R resource) {
        AtomicBoolean lock = resourceToLock.get(resource);
        synchronized (resourceToLock) {
            availableResources.offer(resource);
            lock.set(false);
        }
    }

    @Override
    public boolean add(R resource) {
        resourceToLock.put(resource, new AtomicBoolean());
        return availableResources.offer(resource);
    }

    @Override
    public boolean remove(R resource) {
        while (resourceToLock.get(resource).get()) {
            // busy wait
        }
        resourceToLock.remove(resource);
        return availableResources.remove(resource);
    }

    @Override
    public boolean removeNow(R resource) {
        resourceToLock.remove(resource);
        return availableResources.remove(resource);
    }
}
