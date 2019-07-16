package impl;

import java.util.concurrent.TimeUnit;

public interface ResourcePool<R> {
    void open();

    boolean isOpen();

    void close() throws InterruptedException;

    void closeNow();

    @SuppressWarnings("unused")
    R acquire() throws InterruptedException;

    R acquire(final long timeout, final TimeUnit timeUnit) throws InterruptedException;

    void release(final R resource);

    boolean add(final R resource);

    boolean remove(final R resource) throws InterruptedException;

    boolean removeNow(final R resource);
}
