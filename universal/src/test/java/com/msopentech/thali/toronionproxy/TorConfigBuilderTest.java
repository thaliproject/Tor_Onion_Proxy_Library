package com.msopentech.thali.toronionproxy;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TorConfigBuilderTest {

    @Test
    public void testUpdateTorConfigNoExceptionsThrown() throws Exception {
        OnionProxyContext context = new OnionProxyContext(new File(".")) {
            @Override
            public String getProcessId() {
                return null;
            }

            @Override
            public WriteObserver generateWriteObserver(File file) throws IOException {
                return null;
            }

            @Override
            public TorInstaller getInstaller() {
                return null;
            }
        };

        TorConfigBuilder builder = new TorConfigBuilder(context);
    }
}
