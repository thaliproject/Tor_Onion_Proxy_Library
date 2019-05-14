package com.msopentech.thali.toronionproxy;

import java.io.*;
import java.net.Socket;
import java.util.Collections;

public final class TorControlConnection extends net.freehaven.tor.control.TorControlConnection {
    public TorControlConnection(Socket socket) throws IOException {
        super(socket);
    }

    public TorControlConnection(InputStream inputStream, OutputStream outputStream) {
        super(inputStream, outputStream);
    }

    public TorControlConnection(Reader reader, Writer writer) {
        super(reader, writer);
    }
    
    public void takeownership() throws IOException {
        sendAndWaitForResponse("TAKEOWNERSHIP\r\n", null);
    }

    public void resetOwningControllerProcess() throws IOException {
        resetConf(Collections.singleton("__OwningControllerProcess"));
    }

    public void reloadConf() throws IOException {
        signal("HUP");
    }
}
