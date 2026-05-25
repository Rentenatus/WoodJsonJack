package de.jare.jsoncasted.editor.core;

import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Janusch Rentenatus
 */
public class EditTimes {

    private final AtomicLong counter = new AtomicLong(Long.MIN_VALUE + 1);

    public long update() {
        return counter.incrementAndGet();
    }

    public long getTimes() {
        return counter.get();
    }

}
