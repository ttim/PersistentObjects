package ru.abishev.persistentobjects.client;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PersistentObjects {
    private static Client server;

    public static void setRemotePort(int port) {
        if (server != null) {
            server.release();
        }
        server = new Client(port);
    }

    public static void shutdown() {
        if (server != null) {
            server.release();
        }
    }

    public static <T1, T2 extends T1> T1 create(Class<T1> interfaceClass, Class<T2> implClass, Object... args) {
        String isLocal = System.getProperty("persistent-objects-is-local"); // local by default
        return create(interfaceClass, implClass, !("false".equals(isLocal)), args);
    }

    public static <T1, T2 extends T1> T1 create(Class<T1> interfaceClass, Class<T2> implClass, boolean isLocal, Object... args) {
        return isLocal ? createLocal(interfaceClass, implClass, args) : createRemote(interfaceClass, implClass, args);
    }

    public static <T1, T2 extends T1> T1 createLocal(Class<T1> interfaceClass, Class<T2> implClass, Object... args) {
        Class[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }
        try {
            return implClass.getConstructor(argTypes).newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void checkArgs(Object... args) {
        // check on default class loader for all args
        for (Object arg : args) {
            if (arg.getClass().getClassLoader() != null || !(arg instanceof Serializable)) {
                throw new IllegalArgumentException("All initial args should be from default class loader and serializable");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T1, T2 extends T1> T1 createRemote(Class<T1> interfaceClass, final Class<T2> implClass, final Object... contstructorArgs) {
        checkArgs(contstructorArgs);

        if (server == null) {
            throw new IllegalStateException("You should setup the port before using remote creation");
        }

        return (T1) Proxy.newProxyInstance(ClassLoader.getSystemClassLoader(), new Class[]{interfaceClass}, new InvocationHandler() {
            Integer token = null;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                checkArgs(args);

                if (token == null) {
                    token = server.getToken(implClass, contstructorArgs);
                }

                while (true) {
                    try {
                        return server.invokeByToken(token, method.getName(), args);
                    } catch (Client.InvalidTokenException e) {
                        token = server.getToken(implClass, args);
                    }
                }
            }
        });
    }
}
