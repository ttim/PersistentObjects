package ru.abishev.persistentobjects.client;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.http.Http;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;

class Client {
    private final int port;
    private final Service<HttpRequest, HttpResponse> httpClient;

    Client(int port) {
        this.port = port;

        httpClient = ClientBuilder.safeBuild(
                ClientBuilder.get()
                        .codec(Http.get())
                        .hosts(new InetSocketAddress(port))
                        .hostConnectionLimit(1));
    }

    int getToken(Class implClazz, Object... args) throws IOException {
        URLClassLoader loader = (URLClassLoader) implClazz.getClassLoader();

        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(data);

        outputStream.writeByte(1);
        outputStream.writeUTF(implClazz.getCanonicalName());
        outputStream.writeObject(loader.getURLs());
        outputStream.writeObject(args);

        outputStream.close();

        return (Integer) sendRequest(data.toByteArray());
    }

    Object invokeByToken(int token, String methodName, Object... args) throws InvalidTokenException, IOException {
        // todo: InvalidToken?
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ObjectOutputStream outputStream = new ObjectOutputStream(data);
        outputStream.writeByte(2);
        outputStream.writeInt(token);
        outputStream.writeUTF(methodName);
        outputStream.writeObject(args);
        outputStream.close();

        return sendRequest(data.toByteArray());
    }

    private Object sendRequest(byte[] request) throws IOException {
        HttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/");
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(request);
        httpRequest.setContent(buffer);
        httpRequest.addHeader(HttpHeaders.Names.CONTENT_LENGTH, buffer.readableBytes());
        HttpResponse response = httpClient.apply(httpRequest).get();
        if (response.getStatus().getCode() == 200) {
            ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(response.getContent().array()));
            Object result;
            try {
                result = inputStream.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            inputStream.close();

            return result;
        } else {
            throw new IOException();
        }
    }

    int getPort() {
        return port;
    }

    void release() {
        httpClient.release();
    }

    static class InvalidTokenException extends Exception {
    }
}
