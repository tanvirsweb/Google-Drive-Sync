package com.tanvir.googlesync.rclone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RcloneAuthorizerTest {
    @Test public void extractsToken() {
        String out = "Paste the following into your remote machine --->\n"
                + "{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"refresh_token\":\"r\",\"expiry\":\"2026-11-09T10:00:00Z\"}\n"
                + "<---End paste\n";
        assertEquals("{\"access_token\":\"a\",\"token_type\":\"Bearer\",\"refresh_token\":\"r\",\"expiry\":\"2026-11-09T10:00:00Z\"}",
                RcloneAuthorizer.extractToken(out));
    }

    @Test public void noTokenWithoutMarkers() {
        assertNull(RcloneAuthorizer.extractToken("something went wrong"));
    }
}
