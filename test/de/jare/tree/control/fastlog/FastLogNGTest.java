/*
 * Copyright (c) 2026, Janusch Rentenatus. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 */
package de.jare.tree.control.fastlog;

import de.jare.jsoncasted.editor.core.ParseMode;
import de.jare.tree.control.JackMasterControl;
import java.util.ArrayList;
import java.util.List;
import javax.swing.tree.DefaultMutableTreeNode;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test class for the FastLog core: type registration, write permission,
 * ring buffer eviction, filtering, clearing and listener notification, plus
 * the JackMasterControl integration (default types and parser hooks).
 *
 * @author Mistral Vibe
 * @author Janusch Rentenatus
 */
public class FastLogNGTest {

    private FastLog instance;

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new FastLog();
        instance.registerType(new FastLogType("parser", "Parser", true));
        instance.registerType(new FastLogType("io", "Datei I/O", true));
        instance.registerType(new FastLogType("system", "System", false));
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        instance = null;
    }

    // ========================================================================
    // TYPE REGISTRATION TESTS
    // ========================================================================
    @Test
    public void testRegisterTypeAndGetTypes() {
        System.out.println("testRegisterTypeAndGetTypes");

        List<FastLogType> types = instance.getTypes();
        assertEquals(types.size(), 3);
        assertEquals(types.get(0).getId(), "parser");
        assertEquals(types.get(1).getId(), "io");
        assertEquals(types.get(2).getId(), "system");
        assertEquals(types.get(0).getLabel(), "Parser");
        assertTrue(types.get(0).isWritable());
        assertFalse(types.get(2).isWritable());
    }

    @Test
    public void testGetType() {
        System.out.println("testGetType");

        FastLogType type = instance.getType("io");
        assertNotNull(type);
        assertEquals(type.getLabel(), "Datei I/O");
        assertNull(instance.getType("ghost"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRegisterDuplicateTypeThrows() {
        System.out.println("testRegisterDuplicateTypeThrows");

        instance.registerType(new FastLogType("parser", "Parser 2", true));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRegisterNullTypeThrows() {
        System.out.println("testRegisterNullTypeThrows");

        instance.registerType(null);
    }

    // ========================================================================
    // WRITE PERMISSION TESTS
    // ========================================================================
    @Test
    public void testWriteToWritableType() throws Exception {
        System.out.println("testWriteToWritableType");

        FastLogEntry entry = instance.write("parser", "hello world");
        assertNotNull(entry);
        assertEquals(entry.getNr(), 1L);
        assertEquals(entry.getTypeId(), "parser");
        assertEquals(entry.getMessage(), "hello world");
        assertTrue(entry.getTimestamp() > 0);
        assertEquals(instance.getEntryCount(), 1);
    }

    @Test(expectedExceptions = FastLogWriteException.class)
    public void testWriteToNonWritableTypeThrows() throws Exception {
        System.out.println("testWriteToNonWritableTypeThrows");

        instance.write("system", "not allowed");
    }

    @Test(expectedExceptions = FastLogWriteException.class)
    public void testWriteToUnknownTypeThrows() throws Exception {
        System.out.println("testWriteToUnknownTypeThrows");

        instance.write("ghost", "not registered");
    }

    @Test
    public void testTryWrite() throws Exception {
        System.out.println("testTryWrite");

        assertTrue(instance.tryWrite("parser", "ok"));
        assertFalse(instance.tryWrite("system", "denied"));
        assertFalse(instance.tryWrite("ghost", "denied"));
        assertEquals(instance.getEntryCount(), 1);
    }

    @Test
    public void testWriteNullMessageBecomesEmpty() throws Exception {
        System.out.println("testWriteNullMessageBecomesEmpty");

        FastLogEntry entry = instance.write("parser", null);
        assertEquals(entry.getMessage(), "");
    }

    @Test
    public void testNrIsMonotonicAcrossTypes() throws Exception {
        System.out.println("testNrIsMonotonicAcrossTypes");

        instance.write("parser", "a");
        instance.write("io", "b");
        instance.write("parser", "c");
        List<FastLogEntry> entries = instance.getEntries(null);
        assertEquals(entries.get(0).getNr(), 1L);
        assertEquals(entries.get(1).getNr(), 2L);
        assertEquals(entries.get(2).getNr(), 3L);
    }

    // ========================================================================
    // RING BUFFER TESTS
    // ========================================================================
    @Test
    public void testRingBufferEvictsOldest() throws Exception {
        System.out.println("testRingBufferEvictsOldest");

        FastLog small = new FastLog(3);
        small.registerType(new FastLogType("parser", "Parser", true));
        for (int i = 1; i <= 5; i++) {
            small.write("parser", "msg " + i);
        }
        assertEquals(small.getEntryCount(), 3);
        List<FastLogEntry> entries = small.getEntries(null);
        assertEquals(entries.get(0).getNr(), 3L);
        assertEquals(entries.get(0).getMessage(), "msg 3");
        assertEquals(entries.get(2).getNr(), 5L);
        assertEquals(entries.get(2).getMessage(), "msg 5");
    }

    @Test
    public void testNrContinuesAfterEviction() throws Exception {
        System.out.println("testNrContinuesAfterEviction");

        FastLog small = new FastLog(2);
        small.registerType(new FastLogType("parser", "Parser", true));
        small.write("parser", "a");
        small.write("parser", "b");
        small.write("parser", "c");
        FastLogEntry entry = small.write("parser", "d");
        assertEquals(entry.getNr(), 4L);
        List<FastLogEntry> entries = small.getEntries(null);
        assertEquals(entries.size(), 2);
        assertEquals(entries.get(0).getNr(), 3L);
        assertEquals(entries.get(1).getNr(), 4L);
    }

    @Test
    public void testDefaultMaxEntries() {
        System.out.println("testDefaultMaxEntries");

        assertEquals(new FastLog().getMaxEntries(), FastLog.DEFAULT_MAX_ENTRIES);
        assertEquals(instance.getMaxEntries(), FastLog.DEFAULT_MAX_ENTRIES);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testConstructorInvalidMaxEntriesThrows() {
        System.out.println("testConstructorInvalidMaxEntriesThrows");

        new FastLog(0);
    }

    @Test
    public void testSetMaxEntriesTrimsOldest() throws Exception {
        System.out.println("testSetMaxEntriesTrimsOldest");

        for (int i = 1; i <= 5; i++) {
            instance.write("parser", "msg " + i);
        }
        instance.setMaxEntries(3);
        assertEquals(instance.getMaxEntries(), 3);
        List<FastLogEntry> entries = instance.getEntries(null);
        assertEquals(entries.size(), 3);
        assertEquals(entries.get(0).getNr(), 3L);
        assertEquals(entries.get(0).getMessage(), "msg 3");
        assertEquals(entries.get(2).getNr(), 5L);
    }

    @Test
    public void testSetMaxEntriesGrowAllowsMoreEntries() throws Exception {
        System.out.println("testSetMaxEntriesGrowAllowsMoreEntries");

        FastLog small = new FastLog(2);
        small.registerType(new FastLogType("parser", "Parser", true));
        small.write("parser", "a");
        small.write("parser", "b");
        small.setMaxEntries(5);
        small.write("parser", "c");
        small.write("parser", "d");
        small.write("parser", "e");
        assertEquals(small.getEntryCount(), 5);
        assertEquals(small.getEntries(null).get(0).getMessage(), "a");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testSetMaxEntriesInvalidThrows() {
        System.out.println("testSetMaxEntriesInvalidThrows");

        instance.setMaxEntries(0);
    }

    // ========================================================================
    // FILTER AND SNAPSHOT TESTS
    // ========================================================================
    @Test
    public void testGetEntriesFilter() throws Exception {
        System.out.println("testGetEntriesFilter");

        instance.write("parser", "p1");
        instance.write("io", "i1");
        instance.write("parser", "p2");

        assertEquals(instance.getEntries(null).size(), 3);
        assertEquals(instance.getEntries("parser").size(), 2);
        assertEquals(instance.getEntries("io").size(), 1);
        assertEquals(instance.getEntries("system").size(), 0);
        assertEquals(instance.getEntries("parser").get(0).getMessage(), "p1");
        assertEquals(instance.getEntries("parser").get(1).getMessage(), "p2");
    }

    @Test
    public void testGetEntriesReturnsSnapshot() throws Exception {
        System.out.println("testGetEntriesReturnsSnapshot");

        instance.write("parser", "p1");
        List<FastLogEntry> snapshot = instance.getEntries(null);
        instance.write("parser", "p2");
        assertEquals(snapshot.size(), 1);
        assertEquals(instance.getEntries(null).size(), 2);
    }

    // ========================================================================
    // CLEAR TESTS
    // ========================================================================
    @Test
    public void testClearType() throws Exception {
        System.out.println("testClearType");

        instance.write("parser", "p1");
        instance.write("io", "i1");
        instance.write("parser", "p2");
        instance.clear("parser");
        assertEquals(instance.getEntries("parser").size(), 0);
        assertEquals(instance.getEntries("io").size(), 1);
        assertEquals(instance.getEntries(null).size(), 1);
    }

    @Test
    public void testClearAll() throws Exception {
        System.out.println("testClearAll");

        instance.write("parser", "p1");
        instance.write("io", "i1");
        instance.clear(null);
        assertEquals(instance.getEntries(null).size(), 0);
        assertEquals(instance.getEntryCount(), 0);
    }

    @Test
    public void testWriteAfterClearContinuesNr() throws Exception {
        System.out.println("testWriteAfterClearContinuesNr");

        instance.write("parser", "p1");
        instance.clear(null);
        FastLogEntry entry = instance.write("parser", "p2");
        assertEquals(entry.getNr(), 2L);
    }

    // ========================================================================
    // LISTENER TESTS
    // ========================================================================
    @Test
    public void testListenerNotifiedOnAddAndClear() throws Exception {
        System.out.println("testListenerNotifiedOnAddAndClear");

        TestFastLogListener listener = new TestFastLogListener();
        instance.addFastLogListener(listener);
        instance.write("parser", "p1");
        instance.write("io", "i1");
        assertEquals(listener.addedCount, 2);
        assertEquals(listener.lastEntry.getMessage(), "i1");
        instance.clear("parser");
        assertEquals(listener.clearedCount, 1);
        assertEquals(listener.lastClearedType, "parser");
        instance.clear(null);
        assertEquals(listener.clearedCount, 2);
        assertNull(listener.lastClearedType);
    }

    @Test
    public void testRemoveListener() throws Exception {
        System.out.println("testRemoveListener");

        TestFastLogListener listener = new TestFastLogListener();
        instance.addFastLogListener(listener);
        instance.removeFastLogListener(listener);
        instance.write("parser", "p1");
        assertEquals(listener.addedCount, 0);
    }

    @Test
    public void testAddListenerTwiceRegisteredOnce() throws Exception {
        System.out.println("testAddListenerTwiceRegisteredOnce");

        TestFastLogListener listener = new TestFastLogListener();
        instance.addFastLogListener(listener);
        instance.addFastLogListener(listener);
        instance.write("parser", "p1");
        assertEquals(listener.addedCount, 1);
    }

    // ========================================================================
    // JACK MASTER CONTROL INTEGRATION TESTS
    // ========================================================================
    @Test
    public void testMasterControlDefaultTypes() {
        System.out.println("testMasterControlDefaultTypes");

        JackMasterControl master = new JackMasterControl();
        FastLog fastLog = master.getFastLog();
        assertNotNull(fastLog);
        List<FastLogType> types = fastLog.getTypes();
        assertEquals(types.size(), 5);
        assertTrue(fastLog.getType("parser").isWritable());
        assertTrue(fastLog.getType("editor").isWritable());
        assertTrue(fastLog.getType("io").isWritable());
        assertTrue(fastLog.getType("ui").isWritable());
        assertFalse(fastLog.getType("system").isWritable());
    }

    @Test
    public void testMasterControlFastLogLimitEvicts() throws Exception {
        System.out.println("testMasterControlFastLogLimitEvicts");

        JackMasterControl master = new JackMasterControl();
        assertEquals(master.getFastLog().getMaxEntries(), JackMasterControl.MAX_FAST_LOG_ENTRIES);
        assertEquals(master.getFastLog().getMaxEntries(), 42);
        for (int i = 0; i < 50; i++) {
            master.getFastLog().write("parser", "msg " + i);
        }
        List<FastLogEntry> entries = master.getFastLog().getEntries(null);
        assertEquals(entries.size(), 42);
        assertEquals(entries.get(0).getNr(), 9L);
        assertEquals(entries.get(41).getNr(), 50L);
    }

    @Test
    public void testMasterControlParseModeChangeWritesEntry() {
        System.out.println("testMasterControlParseModeChangeWritesEntry");

        JackMasterControl master = new JackMasterControl();
        master.fireParseModeChanged(null, ParseMode.SOFT_PARSE);
        List<FastLogEntry> entries = master.getFastLog().getEntries("parser");
        assertEquals(entries.size(), 1);
        assertTrue(entries.get(0).getMessage().contains("soft parse"));
    }

    @Test
    public void testMasterControlParseProblemsWritesEntry() {
        System.out.println("testMasterControlParseProblemsWritesEntry");

        JackMasterControl master = new JackMasterControl();
        List<DefaultMutableTreeNode> nodes = new ArrayList<>();
        nodes.add(new DefaultMutableTreeNode("broken"));
        nodes.add(new DefaultMutableTreeNode("also broken"));
        master.fireParseProblems(null, nodes);
        List<FastLogEntry> entries = master.getFastLog().getEntries("parser");
        assertEquals(entries.size(), 1);
        assertTrue(entries.get(0).getMessage().contains("2"));
    }

    // ========================================================================
    // TEST LISTENER
    // ========================================================================
    private static class TestFastLogListener implements FastLogListener {

        public int addedCount = 0;
        public int clearedCount = 0;
        public FastLogEntry lastEntry;
        public String lastClearedType;

        @Override
        public void onEntryAdded(FastLogEntry entry) {
            addedCount++;
            lastEntry = entry;
        }

        @Override
        public void onCleared(String typeId) {
            clearedCount++;
            lastClearedType = typeId;
        }
    }

}
