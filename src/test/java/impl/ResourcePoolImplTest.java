package impl;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.CompletableFuture.supplyAsync;
import static java.util.concurrent.TimeUnit.SECONDS;


//System.err used to for more correct stack trace printing due to it is not buffered like System.out
public class ResourcePoolImplTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SLEEP_MAX_MS = 100;
    private static final int THREADS_AMOUNT = 10;

    @Test
    public void testConcurrency() throws InterruptedException {
        final ResourcePool<Resource> pool = new ResourcePoolImpl<>(new HashSet<>(asList(
                new Resource(), new Resource(), new Resource(), new Resource(), new Resource())));

        Set<PoolActions> workers = Stream
                .iterate(0, i -> i + 1)
                .limit(THREADS_AMOUNT)
                .map(i -> new PoolActions(pool))
                .collect(Collectors.toSet());

        workers.forEach(pa -> new Thread(pa).start());

        PoolActions poolActions = new PoolActions(pool, true, workers);
        Thread debugger = new Thread(poolActions);
        debugger.start();

        Thread.sleep(3600_000);

        String mainThreadName = Thread.currentThread().getName();
        System.err.println(format("[%s][%s] Force closing the pool...", now(), mainThreadName));
        pool.closeNow();
        System.err.println(format("[%s][%s] The pool is force success", now(), mainThreadName));
    }

    @SuppressWarnings("all")
    private static final class PoolActions implements Runnable {
        private final ResourcePool<Resource> pool;
        private final boolean debugger;
        private final Set<Resource> added = new HashSet<>();
        private final List<Resource> acquired = new ArrayList<>();
        private final Set<PoolActions> otherActions; //for debugger

        PoolActions(final ResourcePool<Resource> pool) {
            this(pool, false, null);
        }

        PoolActions(final ResourcePool<Resource> pool, boolean debugger, Set<PoolActions> otherActions) {
            this.pool = pool;
            this.debugger = debugger;
            this.otherActions = otherActions;
        }

        @Override
        public void run() {
            String threadName = Thread.currentThread().getName();
            while (true) {
                try {
                    Thread.sleep(RANDOM.nextInt(SLEEP_MAX_MS));
                    double randomValue = RANDOM.nextDouble();
                    if (0.995 < randomValue && !pool.isOpen()) openPool(threadName);
                    if (0.9990 <= randomValue && randomValue < 0.9995) addResource(threadName);
                    if (0.8 <= randomValue && randomValue < 0.9 && !debugger) acquireResource(threadName);
                    if (0.7 <= randomValue && randomValue < 0.8) releaseResource(threadName);
                    if (0.0005 <= randomValue && randomValue < 0.001 && mayRemove()) removeResource(threadName); //force
                    if (0 <= randomValue && randomValue < 0.0005 && pool.isOpen()) closePool(threadName);        //force
                } catch (InterruptedException e) {
                    //Noop
                }
            }
        }

        private void closePool(String threadName) throws InterruptedException {
            System.err.println(format("[%s][%s] CLOSING THE POOL...", now(), threadName));
            try {
                runAsync(pool::close).get(30, SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                if (pool.isOpen()) {
                    System.err.println(format("[%s][%s] CANNOT CLOSE POOL IN DEFAULT MODE, FORCE CLOSING...", now(), threadName));
                    pool.closeNow();
                }
            }
            System.err.println(format("[%s][%s] THE POOL IS CLOSED", now(), threadName));
        }

        private void removeResource(String threadName) throws InterruptedException {
            Resource resource = added.iterator().next();
            System.err.println(format("[%s][%s]\tRemoving the resource with id %d", now(), threadName, resource.getId()));
            Boolean removed = null;
            try {
                removed = supplyAsync(() -> pool.remove(resource)).get(10, SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                if (!removed) {
                    System.err.println(format("[%s][%s]\tThe resource with id %d is not removed, force removing", now(), threadName, resource.getId()));
                    pool.removeNow(resource);
                }
            }
            System.err.println(format("[%s][%s]\tRemoved the resource with id %d", now(), threadName, resource.getId()));
        }

        private void releaseResource(String threadName) {
            if (acquired.isEmpty()) return;

            int acquiredIndx = RANDOM.nextInt(acquired.size());
            Resource resource = acquired.get(acquiredIndx);
            System.err.println(format("[%s][%s]\t\tReleasing the resource with id %d", now(), threadName, resource.getId()));
            pool.release(resource);
            acquired.remove(acquiredIndx);
            System.err.println(format("[%s][%s]\t\tReleased the resource with id %d", now(), threadName, resource.getId()));
        }

        private void acquireResource(String threadName) throws InterruptedException {
            System.err.println(format("[%s][%s]\tAcquiring a resource...", now(), threadName));
            try {
                Resource resource = pool.acquire(1, SECONDS);
                if (nonNull(resource)) {
                    System.err.println(format("[%s][%s]\tAcquired the resource with id %d", now(), threadName, resource.getId()));
                    acquired.add(resource);
                }
            } catch (Exception e) {
                System.err.println(format("[%s][%s]\tThe resource is not acquired", now(), threadName));
            }
        }

        private void addResource(String threadName) {
            Resource resource = new Resource();
            System.err.println(format("[%s][%s]\tADDING the resource with id %d", now(), threadName, resource.getId()));
            if (pool.add(resource)) added.add(resource);
            System.err.println(format("[%s][%s]\tADDED the resource with id %d", now(), threadName, resource.getId()));
        }

        private void openPool(String threadName) {
            System.err.println(format("[%s][%s] OPENING THE POOL...", now(), threadName));
            pool.open();
            System.err.println(format("[%s][%s] THE POOL IS OPENED", now(), threadName));
        }

        private boolean mayRemove() {
            Iterator<Resource> addedIt = added.iterator();
            return addedIt.hasNext() && !acquired.contains(addedIt.next());
        }
    }
}