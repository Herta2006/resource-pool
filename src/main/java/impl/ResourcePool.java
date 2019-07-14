package impl;

import java.util.concurrent.TimeUnit;

public interface ResourcePool<R> {
    void open();

    boolean isOpen();

    void close();

    void closeNow();

    R acquire() throws InterruptedException;

    R acquire(final long timeout, final TimeUnit timeUnit) throws InterruptedException;

    void release(final R resource);

    boolean add(final R resource);

    boolean remove(final R resource);

    boolean removeNow(final R resource);
}
