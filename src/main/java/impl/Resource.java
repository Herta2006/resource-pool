package impl;

import java.util.concurrent.atomic.AtomicInteger;

public final class Resource {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger();
    private final Integer id;

    public Resource() {
        id = ID_GENERATOR.getAndIncrement();
    }

    public Integer getId() {
        return id;
    }
}
