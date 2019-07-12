package impl;

import org.junit.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.time.LocalDateTime.now;
import static java.util.Arrays.asList;
import static java.util.Objects.nonNull;

public class ResourcePoolImplTest {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SLEEP_MAX_MS = 100;

    private static final class PoolActions implements Runnable {
        private final ResourcePool<Resource> pool;
        private final boolean debugger;
        private final Set<Resource> added = new HashSet<>();
        private final Set<Resource> acquired = new HashSet<>();
        private final Set<PoolActions> otherActions;

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
                    if (0.9995 < randomValue && !pool.isOpen()) {
                        System.err.println(format("[%s][%s] OPENING THE POOL...", now(), threadName));
                        pool.open();
                        System.err.println(format("[%s][%s] THE POOL IS OPENED", now(), threadName));
                    }
                    if (0.9990 <= randomValue && randomValue < 0.9995) {
                        Resource resource = new Resource();
                        System.err.println(format("[%s][%s]\tADDING the resource with id %d", now(), threadName, resource.getId()));
                        if (pool.add(resource)) added.add(resource);
                        System.err.println(format("[%s][%s]\tADDED the resource with id %d", now(), threadName, resource.getId()));
                    }
                    if (0.8 <= randomValue && randomValue < 0.9 && !debugger) {
                        System.err.println(format("[%s][%s]\tAcquiring a resource...", now(), threadName));
                        Resource resource = pool.acquire();
                        if(nonNull(resource)) {
                            System.err.println(format("[%s][%s]\tAcquired the resource with id %d", now(), threadName, resource.getId()));
                            acquired.add(resource);
                        }
                    }
                    if (0.7 <= randomValue && randomValue < 0.8 && acquired.iterator().hasNext()) {
                        Resource resource = acquired.iterator().next();
                        System.err.println(format("[%s][%s]\t\tReleasing the resource with id %d", now(), threadName, resource.getId()));
                        pool.release(resource);
                        System.err.println(format("[%s][%s]\t\tReleased the resource with id %d", now(), threadName, resource.getId()));
                    }
                    if (0.0005 <= randomValue && randomValue < 0.001 && added.iterator().hasNext()) {
                        Resource resource = added.iterator().next();
                        System.err.println(format("[%s][%s]\tRemoving the resource with id %d", now(), threadName, resource.getId()));
                        pool.remove(resource);
                        System.err.println(format("[%s][%s]\tRemoved the resource with id %d", now(), threadName, resource.getId()));
                    }
                    if (0 <= randomValue && randomValue < 0.0005 && pool.isOpen()) {
                        System.err.println(format("[%s][%s] CLOSING THE POOL...", now(), threadName));
                        pool.close();
                        System.err.println(format("[%s][%s] THE POOL IS CLOSED", now(), threadName));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public Set<PoolActions> getOtherActions() {
            return otherActions;
        }
    }

    @Test
    public void testConcurrency() throws InterruptedException {
        final ResourcePool<Resource> pool = new ResourcePoolImpl<>(new HashSet<>(asList(
                new Resource(), new Resource(), new Resource(), new Resource(), new Resource())));

        Set<PoolActions> workers = Stream
                .iterate(0, i -> i + 1)
                .limit(10)
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
        System.err.println(format("[%s][%s] The pool is force closed", now(), mainThreadName));
    }
}