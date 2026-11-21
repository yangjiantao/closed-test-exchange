package com.shanmei.tools.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.Calendar;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TestStoreTest {
    private static final String APP = "example.checkin";
    private TestStore store;
    private Calendar time;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        store = new TestStore(context, null);
        time = Calendar.getInstance();
        time.clear();
        time.set(2026, Calendar.MARCH, 7, 12, 0);
        store.setManaged(APP, true);
    }

    @After
    public void tearDown() {
        store.close();
    }

    @Test
    public void sameDayCountsOnceAndNextDayIncrements() {
        assertFalse(store.entries().get(0).openedOn(time.getTimeInMillis()));
        recordOpen();
        time.add(Calendar.HOUR_OF_DAY, 1);
        recordOpen();
        assertEquals(1, store.entries().get(0).days());
        assertEquals(time.getTimeInMillis(), store.entries().get(0).lastOpened());
        assertTrue(store.entries().get(0).openedOn(time.getTimeInMillis()));

        time.add(Calendar.DAY_OF_MONTH, 1);
        assertFalse(store.entries().get(0).openedOn(time.getTimeInMillis()));
        recordOpen();
        assertEquals(2, store.entries().get(0).days());
    }

    @Test
    public void removeAndReAddPreservesPriorCheckIn() {
        long firstOpen = time.getTimeInMillis();
        recordOpen();
        store.setManaged(APP, false);
        assertTrue(store.entries().isEmpty());

        time.add(Calendar.DAY_OF_MONTH, 1);
        recordOpen();
        store.setManaged(APP, true);
        assertEquals(1, store.entries().get(0).days());
        assertEquals(firstOpen, store.entries().get(0).lastOpened());
        recordOpen();
        assertEquals(2, store.entries().get(0).days());
    }

    private void recordOpen() {
        store.recordOpen(APP, time.getTimeInMillis());
    }
}
