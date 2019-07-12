package impl;

public interface ResourcePool<R> {
    void open();

    boolean isOpen();

    void close();

    void closeNow();

    R acquire() throws InterruptedException;

    R acquire(long timeout, java.util.concurrent.TimeUnit timeUnit) throws InterruptedException;

    void release(R resource);

    boolean add(R resource);

    boolean remove(R resource);

    boolean removeNow(R resource);
}
