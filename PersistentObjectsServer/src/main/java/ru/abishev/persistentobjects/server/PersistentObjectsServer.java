package ru.abishev.persistentobjects.server;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.Server;
import com.twitter.finagle.builder.ServerBuilder;
import com.twitter.finagle.http.Http;
import com.twitter.util.Future;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class PersistentObjectsServer extends Service<HttpRequest, HttpResponse> {
    private Map<List<Byte>, Integer> requestToToken = new HashMap<List<Byte>, Integer>();
    private Map<Integer, Object> tokenToObject = new HashMap<Integer, Object>();
    private int tokenNum = 0;

    private PersistentObjectsServer() {
    }

    private int createObjectAndGetToken(String className, URL[] classLoaderUrls, Object[] args) {
        ClassLoader loader = new URLClassLoader(classLoaderUrls);
        Class[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        tokenNum++;
        try {
            tokenToObject.put(tokenNum, loader.loadClass(className).getConstructor(argTypes).newInstance(args));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return tokenNum;
    }

    private Object invokeRequest(byte[] request) throws IOException {
        ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(request));
        byte type = input.readByte();
        if (type == 1) {
            List<Byte> requestList = new ArrayList<Byte>();
            for (byte b : request) {
                requestList.add(b);
            }
            if (requestToToken.containsKey(requestList)) {
                return requestToToken.get(requestList);
            } else {
                try {
                    int token = createObjectAndGetToken(input.readUTF(), (URL[]) input.readObject(), (Object[]) input.readObject());
                    requestToToken.put(requestList, token);
                    return token;
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException();
                }
            }
        } else if (type == 2) {
            try {
                int token = input.readInt();
                String method = input.readUTF();
                Object[] args = (Object[]) input.readObject();

                if (!tokenToObject.containsKey(token)) {
                    throw new RuntimeException();
                }

                // invoke method, todo: maybe optimize?
                Class[] argTypes = new Class[args.length];
                for (int i = 0; i < args.length; i++) {
                    argTypes[i] = args[i].getClass();
                }
                Object obj = tokenToObject.get(token);

                try {
                    return obj.getClass().getMethod(method, argTypes).invoke(obj, args);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException();
                } catch (InvocationTargetException e) {
                    throw new RuntimeException();
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException();
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException();
            }
        } else {
            throw new IOException();
        }
    }

    private byte[] objectToBytes(Object o) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        ObjectOutputStream output = new ObjectOutputStream(data);
        output.writeObject(o);
        output.close();
        return data.toByteArray();
    }

    public Future<HttpResponse> apply(HttpRequest request) {
        byte[] r = request.getContent().array();
        Object result = null;
        try {
            result = invokeRequest(r);
        } catch (IOException e) {
            e.printStackTrace();
        }
        HttpResponse response =
                new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        try {
            response.setContent(ChannelBuffers.wrappedBuffer(objectToBytes(result)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Future.value(response);
    }

    public static Server create(int port) {
        return ServerBuilder.safeBuild(new PersistentObjectsServer(),
                ServerBuilder.get()
                        .codec(Http.get())
                        .name("PersistentObjects server")
                        .bindTo(new InetSocketAddress(port)));
    }

    public static void main(String[] args) {
        Server server = create(Integer.parseInt(args[0]));
        System.out.println("Server is running at port " + args[0]);
    }
}
