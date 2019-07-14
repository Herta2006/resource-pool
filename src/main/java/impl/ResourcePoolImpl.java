package impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// final variables/parameters occurred here because it helps to compiler make possible improvements
// and increase a bit performance. In general good practice
public class ResourcePoolImpl<R> implements ResourcePool<R> {
    // AtomicBoolean solves thread safe problem here for simultaneous write/read
    private final AtomicBoolean open = new AtomicBoolean();

    // BlockingQueue is enough to handle available resources and block if no one is presented
    private final BlockingQueue<R> availableResources = new LinkedBlockingQueue<>();

    // ConcurrentHashMap good here `cause it allows to keep simple pair R to Lock (AtomicBoolean same to open indicator)
    // find particular resource to check is it acquired and handle resource availability via availableResources field
    private final Map<R, AtomicBoolean> resourceToLock = new ConcurrentHashMap<>();

    public ResourcePoolImpl(final Set<R> resources) {
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

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public void close() {
        // check is there any acquired resource presented
        // solves a requirement to wait until all resources be available
        // in practice/tests there is a low probability to close the pool with help of this method,
        // therefore closeNow often force closes the pool
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

        // take method is ok to solve a requirement as it retrieves and removes the head of availableResources queue,
        // waiting if necessary until an element becomes available.
        final R resource = availableResources.take();

        // set is synchronized due to resourceToLock is thread safe and lock is AtomicBoolean
        resourceToLock.get(resource).set(true);

        return resource;
    }

    @Override
    public R acquire(final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        if (!isOpen()) return null;

        // poll method is ok to solve a requirement as it retrieves and removes the head of availableResources queue,
        // waiting up to the specified wait time if necessary for an element to become available.
        final R resource = availableResources.poll(timeout, timeUnit);

        // set is synchronized due to resourceToLock is thread safe and lock is AtomicBoolean
        resourceToLock.get(resource).set(true);

        return resource;
    }

    @Override
    public void release(final R resource) {
        final AtomicBoolean lock = resourceToLock.get(resource);

        // need to synchronize this place because from offer to set possible race condition
        // but synchronized block makes release atomic and not one thread will not acquire just offered resource
        // in availableResources "storage"
        synchronized (resourceToLock) {
            availableResources.offer(resource);
            lock.set(false);
        }
    }

    @Override
    public boolean add(final R resource) {
        //no need synchronized block because no one thread will set lock to true
        // till it be added to availableResources "storage"
        resourceToLock.put(resource, new AtomicBoolean());

        return availableResources.offer(resource);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean remove(final R resource) {
        // solves a requirement to wait until a resource be available
        while (resourceToLock.get(resource).get()) {
            // busy wait
        }

        // at first need to prevent the resource availability
        boolean removed = availableResources.remove(resource);

        // and then remove the resource lock
        resourceToLock.remove(resource);

        return removed;
    }

    @Override
    public boolean removeNow(final R resource) {
        // at first need to prevent the resource availability
        boolean removed = availableResources.remove(resource);

        // and then remove the resource lock
        resourceToLock.remove(resource);

        return removed;
    }
}