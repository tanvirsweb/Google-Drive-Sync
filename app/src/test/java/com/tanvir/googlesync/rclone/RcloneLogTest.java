package com.tanvir.googlesync.rclone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RcloneLogTest {
    @Test public void parsesStats() {
        RcloneLog.Entry e = RcloneLog.parse("{\"level\":\"info\",\"msg\":\"\\nTransferred: ...\",\"stats\":"
                + "{\"bytes\":512,\"totalBytes\":1024,\"transfers\":1,\"totalTransfers\":4,\"speed\":2048.5,\"eta\":3}}");
        assertNotNull(e);
        assertNotNull(e.stats);
        assertEquals(50, e.stats.percent());
        assertEquals(4, e.stats.totalTransfers);
    }

    @Test public void humanizesObjectMessages() {
        assertEquals("a.jpg: Copied (new)",
                RcloneLog.humanize("{\"level\":\"info\",\"msg\":\"Copied (new)\",\"object\":\"a.jpg\"}"));
        assertEquals("plain text", RcloneLog.humanize("plain text"));
        assertNull(RcloneLog.parse("not json"));
    }

    @Test public void detectsNetworkErrors() {
        assertTrue(RcloneLog.looksLikeNetworkError("Get \"https://www.googleapis.com\": dial tcp: lookup www.googleapis.com: no such host"));
        assertFalse(RcloneLog.looksLikeNetworkError("directory not found"));
    }
}
