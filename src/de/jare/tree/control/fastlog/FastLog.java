/* <copyright>
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control.fastlog;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fast in-memory log (ring buffer) for short system messages. Systems
 * register their types via {@link #registerType(FastLogType)}; only types
 * with {@code writable = true} accept writes via
 * {@link #write(String, String)}. Beyond the limit {@code maxEntries} the
 * oldest entries are evicted, the running number still keeps counting up.
 * <p>
 * The log is thread-safe; systems may write from any thread.
 * Listeners are notified on the writing thread.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class FastLog {

    /**
     * Default upper limit of the ring buffer.
     */
    public static final int DEFAULT_MAX_ENTRIES = 2000;

    private final Object lock = new Object();
    private final Map<String, FastLogType> types = new LinkedHashMap<>();
    private final ArrayDeque<FastLogEntry> entries = new ArrayDeque<>();
    private final List<FastLogListener> listeners = new CopyOnWriteArrayList<>();
    private int maxEntries;
    private long nextNr = 1;

    /**
     * Creates a FastLog with the default upper limit.
     */
    public FastLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates a FastLog with the given upper limit.
     *
     * @param maxEntries maximum number of held entries, at least 1
     */
    public FastLog(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be at least 1: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    /**
     * Registers a fast log type. Duplicate ids are rejected.
     *
     * @param type the type to register
     */
    public void registerType(FastLogType type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        synchronized (lock) {
            if (types.containsKey(type.getId())) {
                throw new IllegalArgumentException("fast log type registered twice: " + type.getId());
            }
            types.put(type.getId(), type);
        }
    }

    /**
     * Returns all registered types in registration order.
     *
     * @return list of registered types
     */
    public List<FastLogType> getTypes() {
        synchronized (lock) {
            return new ArrayList<>(types.values());
        }
    }

    /**
     * Returns the registered type with the given id.
     *
     * @param typeId the type id
     * @return the type or null when no type with this id is registered
     */
    public FastLogType getType(String typeId) {
        synchronized (lock) {
            return types.get(typeId);
        }
    }

    /**
     * @return the upper limit of the ring buffer
     */
    public int getMaxEntries() {
        synchronized (lock) {
            return maxEntries;
        }
    }

    /**
     * Sets the upper limit of the ring buffer. When shrinking, the oldest
     * entries are evicted immediately until the limit is met.
     *
     * @param maxEntries new upper limit, at least 1
     */
    public void setMaxEntries(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be at least 1: " + maxEntries);
        }
        synchronized (lock) {
            this.maxEntries = maxEntries;
            while (entries.size() > maxEntries) {
                entries.pollFirst();
            }
        }
    }

    /**
     * @return the current number of held entries
     */
    public int getEntryCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /**
     * Writes an entry to the log. Throws a {@link FastLogWriteException}
     * when the type is not registered or not writable.
     *
     * @param typeId id of the fast log type
     * @param message message text, null is stored as empty text
     * @return the created entry
     * @throws FastLogWriteException when the type is unknown or not writable
     */
    public FastLogEntry write(String typeId, String message) throws FastLogWriteException {
        final FastLogEntry entry;
        synchronized (lock) {
            FastLogType type = types.get(typeId);
            if (type == null) {
                throw new FastLogWriteException("Unknown fast log type: " + typeId);
            }
            if (!type.isWritable()) {
                throw new FastLogWriteException("Fast log type '" + typeId + "' is not writable");
            }
            entry = new FastLogEntry(nextNr++, typeId, message == null ? "" : message, System.currentTimeMillis());
            entries.addLast(entry);
            while (entries.size() > maxEntries) {
                entries.pollFirst();
            }
        }
        fireEntryAdded(entry);
        return entry;
    }

    /**
     * Writes an entry when allowed and throws no exception.
     *
     * @param typeId id of the fast log type
     * @param message message text
     * @return true when the entry was written
     */
    public boolean tryWrite(String typeId, String message) {
        try {
            write(typeId, message);
            return true;
        } catch (FastLogWriteException ex) {
            return false;
        }
    }

    /**
     * Returns a snapshot of the entries, oldest first. Without a type id all
     * entries are returned, otherwise only entries of the given type.
     *
     * @param typeId id of the fast log type, or null for all types
     * @return list of entries
     */
    public List<FastLogEntry> getEntries(String typeId) {
        synchronized (lock) {
            List<FastLogEntry> result = new ArrayList<>(entries.size());
            for (FastLogEntry entry : entries) {
                if (typeId == null || typeId.equals(entry.getTypeId())) {
                    result.add(entry);
                }
            }
            return result;
        }
    }

    /**
     * Deletes the entries of one type or all entries.
     *
     * @param typeId id of the fast log type, or null for all types
     */
    public void clear(String typeId) {
        synchronized (lock) {
            if (typeId == null) {
                entries.clear();
            } else {
                entries.removeIf(entry -> typeId.equals(entry.getTypeId()));
            }
        }
        fireCleared(typeId);
    }

    /**
     * Registers a listener.
     *
     * @param l the listener
     */
    public void addFastLogListener(FastLogListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    /**
     * Removes a listener.
     *
     * @param l the listener
     */
    public void removeFastLogListener(FastLogListener l) {
        listeners.remove(l);
    }

    private void fireEntryAdded(FastLogEntry entry) {
        for (FastLogListener l : listeners) {
            l.onEntryAdded(entry);
        }
    }

    private void fireCleared(String typeId) {
        for (FastLogListener l : listeners) {
            l.onCleared(typeId);
        }
    }

}
