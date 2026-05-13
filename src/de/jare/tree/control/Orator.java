/* <copyright>
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 * </copyright>
 */
package de.jare.tree.control;

import de.jare.jsoncasted.editor.events.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic listener dispatcher that maintains listeners with priority levels.
 * This is a compatibility layer that can use EventBus internally for type-safe event dispatching.
 * 
 * @param <T> the listener type
 */
public class Orator<T> {

    private final List<ListenerEntry<T>> listeners = new ArrayList<>();
    private EventBus eventBus;

    /**
     * Creates a new Orator without an EventBus.
     */
    public Orator() {
        this(null);
    }

    /**
     * Creates a new Orator with an optional EventBus for type-safe event dispatching.
     * 
     * @param eventBus the event bus to use, may be null
     */
    public Orator(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Adds a listener at the default level (5).
     * 
     * @param listener the listener to add
     */
    public void addListener(T listener) {
        addListener(5, listener);
    }

    /**
     * Adds a listener at the specified priority level.
     * Lower levels are notified first.
     * 
     * @param level the priority level (lower = higher priority)
     * @param listener the listener to add
     */
    public void addListener(int level, T listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        synchronized (listeners) {
            listeners.add(new ListenerEntry<>(level, listener));
            listeners.sort(Comparator.comparingInt(e -> e.level));
        }
    }

    /**
     * Removes a listener.
     * 
     * @param listener the listener to remove
     * @return true if the listener was removed
     */
    public boolean removeListener(T listener) {
        if (listener == null) {
            return false;
        }
        synchronized (listeners) {
            return listeners.removeIf(e -> e.listener.equals(listener));
        }
    }

    /**
     * Calls all registered listeners with the given action.
     * Listeners are called in order of their priority level (lower levels first).
     * 
     * @param action the consumer to apply to each listener
     */
    public void say(Consumer<T> action) {
        if (action == null) {
            return;
        }
        List<ListenerEntry<T>> entries;
        synchronized (listeners) {
            entries = new ArrayList<>(listeners);
        }
        for (ListenerEntry<T> entry : entries) {
            try {
                action.accept(entry.listener);
            } catch (Exception e) {
                System.err.println("Error in listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets the EventBus for type-safe event dispatching.
     * 
     * @param eventBus the event bus to use
     */
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Gets the EventBus used by this Orator.
     * 
     * @return the event bus, may be null
     */
    public EventBus getEventBus() {
        return eventBus;
    }

    /**
     * Returns the number of registered listeners.
     * 
     * @return the listener count
     */
    public int getListenerCount() {
        synchronized (listeners) {
            return listeners.size();
        }
    }

    /**
     * Clears all registered listeners.
     */
    public void clear() {
        synchronized (listeners) {
            listeners.clear();
        }
    }

    /**
     * Returns an unmodifiable list of all registered listeners.
     * 
     * @return the list of listeners
     */
    public List<T> getListeners() {
        synchronized (listeners) {
            List<T> result = new ArrayList<>();
            for (ListenerEntry<T> entry : listeners) {
                result.add(entry.listener);
            }
            return Collections.unmodifiableList(result);
        }
    }

    /**
     * Internal entry class for storing listener with its level.
     */
    private static class ListenerEntry<T> {
        final int level;
        final T listener;

        ListenerEntry(int level, T listener) {
            this.level = level;
            this.listener = listener;
        }
    }
}
