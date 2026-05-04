/*
 * Copyright (c) 2025, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.jsoncasted.editor.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Thread-safe event bus for distributing events to registered listeners.
 * This is a type-safe event dispatching mechanism that replaces the Orator class.
 * 
 * @param <T> the type of events this bus handles
 */
public class EventBus {

    private final Map<Class<?>, List<Consumer<Object>>> listeners;

    /**
     * Creates a new EventBus instance.
     */
    public EventBus() {
        this.listeners = new HashMap<>();
    }

    /**
     * Adds a listener for a specific event type.
     * The listener will be notified whenever an event of the specified type is fired.
     * 
     * @param <T> the event type
     * @param eventType the class of events to listen for
     * @param listener the consumer to be called when an event is fired
     * @throws IllegalArgumentException if eventType or listener is null
     */
    public <T> void addListener(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null) {
            throw new IllegalArgumentException("Event type cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        
        synchronized (listeners) {
            listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                    .add(wrapConsumer(listener));
        }
    }

    /**
     * Removes a listener for a specific event type.
     * 
     * @param <T> the event type
     * @param eventType the class of events
     * @param listener the consumer to remove
     * @return true if the listener was removed
     */
    public <T> boolean removeListener(Class<T> eventType, Consumer<T> listener) {
        if (eventType == null || listener == null) {
            return false;
        }
        
        synchronized (listeners) {
            List<Consumer<Object>> eventListeners = listeners.get(eventType);
            if (eventListeners == null) {
                return false;
            }
            return eventListeners.removeIf(c -> c.equals(wrapConsumer(listener)));
        }
    }

    /**
     * Fires an event to all registered listeners for its type.
     * Also fires to listeners of supertypes and interfaces.
     * 
     * @param <T> the event type
     * @param event the event to fire
     */
    public <T> void fireEvent(T event) {
        if (event == null) {
            return;
        }

        List<Consumer<Object>> eventListeners;
        
        synchronized (listeners) {
            eventListeners = new ArrayList<>(listeners.getOrDefault(
                event.getClass(), Collections.emptyList()));
        }

        // Notify listeners
        for (Consumer<Object> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                // Log error but don't propagate
                System.err.println("Error in event listener: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Fires an event to all registered listeners, including supertype listeners.
     * This method checks all superclasses and interfaces for listeners.
     * 
     * @param <T> the event type
     * @param event the event to fire
     */
    public <T> void fireEventHierarchical(T event) {
        if (event == null) {
            return;
        }

        Class<?> eventClass = event.getClass();
        List<Class<?>> types = getAllTypes(eventClass);
        
        synchronized (listeners) {
            for (Class<?> type : types) {
                List<Consumer<Object>> typeListeners = listeners.get(type);
                if (typeListeners != null) {
                    // Make a copy to avoid concurrent modification
                    for (Consumer<Object> listener : new ArrayList<>(typeListeners)) {
                        try {
                            listener.accept(event);
                        } catch (Exception e) {
                            System.err.println("Error in event listener for " + type.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns all superclasses and interfaces of the given class.
     * 
     * @param clazz the class to inspect
     * @return list of all types in the hierarchy
     */
    private List<Class<?>> getAllTypes(Class<?> clazz) {
        List<Class<?>> types = new ArrayList<>();
        collectTypes(clazz, types);
        return types;
    }

    /**
     * Recursively collects all superclasses and interfaces.
     * 
     * @param clazz the class to process
     * @param types the list to add types to
     */
    private void collectTypes(Class<?> clazz, List<Class<?>> types) {
        if (clazz == null || types.contains(clazz)) {
            return;
        }
        types.add(clazz);
        
        // Add superclass
        collectTypes(clazz.getSuperclass(), types);
        
        // Add interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            collectTypes(iface, types);
        }
    }

    /**
     * Wraps a typed consumer as a Consumer<Object> for internal storage.
     * 
     * @param <T> the event type
     * @param consumer the typed consumer
     * @return a wrapped consumer
     */
    @SuppressWarnings("unchecked")
    private <T> Consumer<Object> wrapConsumer(Consumer<T> consumer) {
        return (Consumer<Object>) consumer;
    }

    /**
     * Removes all listeners from this event bus.
     */
    public void clear() {
        synchronized (listeners) {
            listeners.clear();
        }
    }

    /**
     * Returns the number of listener registrations.
     * 
     * @return the total number of listeners
     */
    public int getListenerCount() {
        synchronized (listeners) {
            int count = 0;
            for (List<Consumer<Object>> list : listeners.values()) {
                count += list.size();
            }
            return count;
        }
    }

    /**
     * Returns whether there are any listeners registered for a specific event type.
     * 
     * @param eventType the event type to check
     * @return true if there are listeners
     */
    public boolean hasListeners(Class<?> eventType) {
        synchronized (listeners) {
            return listeners.containsKey(eventType);
        }
    }

    @Override
    public String toString() {
        synchronized (listeners) {
            return "EventBus[listeners=" + getListenerCount() + ", types=" + listeners.size() + "]";
        }
    }
}
