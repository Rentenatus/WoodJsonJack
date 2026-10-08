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
 * Schneller In-Memory-Log (Ringpuffer) fuer kurze Systemmeldungen. Systeme
 * registrieren ihre Typen ueber {@link #registerType(FastLogType)}; nur Typen
 * mit {@code writable = true} nehmen Schreibzugriffe ueber
 * {@link #write(String, String)} an. Ab der Obergrenze {@code maxEntries} werden die
 * aeltesten Eintraege verdraengt, die laufende Nummer laeuft trotzdem weiter.
 * <p>
 * Der Log ist thread-safe; Systeme duerfen von beliebigen Threads schreiben.
 * Listener werden auf dem schreibenden Thread benachrichtigt.
 * </p>
 *
 * @author Janusch Rentenatus
 */
public class FastLog {

    /**
     * Default-Obergrenze des Ringpuffers.
     */
    public static final int DEFAULT_MAX_ENTRIES = 2000;

    private final Object lock = new Object();
    private final Map<String, FastLogType> types = new LinkedHashMap<>();
    private final ArrayDeque<FastLogEntry> entries = new ArrayDeque<>();
    private final List<FastLogListener> listeners = new CopyOnWriteArrayList<>();
    private int maxEntries;
    private long nextNr = 1;

    /**
     * Erzeugt einen FastLog mit der Default-Obergrenze.
     */
    public FastLog() {
        this(DEFAULT_MAX_ENTRIES);
    }

    /**
     * Erzeugt einen FastLog mit der angegebenen Obergrenze.
     *
     * @param maxEntries maximale Anzahl gehaltener Eintraege, mindestens 1
     */
    public FastLog(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries muss mindestens 1 sein: " + maxEntries);
        }
        this.maxEntries = maxEntries;
    }

    /**
     * Registriert einen Fast-Log-Typ. Doppelte IDs werden abgelehnt.
     *
     * @param type der zu registrierende Typ
     */
    public void registerType(FastLogType type) {
        if (type == null) {
            throw new IllegalArgumentException("type darf nicht null sein");
        }
        synchronized (lock) {
            if (types.containsKey(type.getId())) {
                throw new IllegalArgumentException("Fast-Log-Typ doppelt registriert: " + type.getId());
            }
            types.put(type.getId(), type);
        }
    }

    /**
     * Returns all registered types in registration order.
     *
     * @return Liste der registrierten Typen
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
     * @return die Obergrenze des Ringpuffers
     */
    public int getMaxEntries() {
        synchronized (lock) {
            return maxEntries;
        }
    }

    /**
     * Setzt die Obergrenze des Ringpuffers. Beim Verkleinern werden sofort die
     * aeltesten Eintraege verdraengt, bis die Grenze eingehalten wird.
     *
     * @param maxEntries neue Obergrenze, mindestens 1
     */
    public void setMaxEntries(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries muss mindestens 1 sein: " + maxEntries);
        }
        synchronized (lock) {
            this.maxEntries = maxEntries;
            while (entries.size() > maxEntries) {
                entries.pollFirst();
            }
        }
    }

    /**
     * @return die aktuelle Anzahl gehaltener Eintraege
     */
    public int getEntryCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    /**
     * Schreibt einen Eintrag in den Log. Wirft eine {@link FastLogWriteException},
     * wenn der Typ nicht registriert oder nicht beschreibbar ist.
     *
     * @param typeId ID des Fast-Log-Typs
     * @param message Meldungstext, null wird als leerer Text gespeichert
     * @return der erzeugte Eintrag
     * @throws FastLogWriteException wenn der Typ unbekannt oder nicht beschreibbar ist
     */
    public FastLogEntry write(String typeId, String message) throws FastLogWriteException {
        final FastLogEntry entry;
        synchronized (lock) {
            FastLogType type = types.get(typeId);
            if (type == null) {
                throw new FastLogWriteException("Unbekannter Fast-Log-Typ: " + typeId);
            }
            if (!type.isWritable()) {
                throw new FastLogWriteException("Fast-Log-Typ '" + typeId + "' ist nicht beschreibbar");
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
     * Schreibt einen Eintrag, sofern erlaubt, und wirft keine Exception.
     *
     * @param typeId ID des Fast-Log-Typs
     * @param message Meldungstext
     * @return true, wenn der Eintrag geschrieben wurde
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
     * @param typeId ID des Fast-Log-Typs oder null fuer alle Typen
     * @return Liste der Eintraege
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
     * Loescht Eintraege eines Typs oder alle Eintraege.
     *
     * @param typeId ID des Fast-Log-Typs oder null fuer alle Typen
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
     * Registriert einen Listener.
     *
     * @param l der Listener
     */
    public void addFastLogListener(FastLogListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    /**
     * Entfernt einen Listener.
     *
     * @param l der Listener
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
