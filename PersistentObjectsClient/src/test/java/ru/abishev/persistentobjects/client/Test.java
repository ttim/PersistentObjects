package ru.abishev.persistentobjects.client;

public class Test {
    public static void main(String[] args) {
        boolean isLocal = false;
        PersistentObjects.setRemotePort(5678);
        ITestObject obj = PersistentObjects.create(ITestObject.class, TestObject.class, isLocal, new String[]{"Hello world"});
        System.out.println(obj.sayHello(isLocal ? "local" : "remote"));

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            obj.sayHello("suffix");
        }
        System.out.println("Time for 1000requests " + (System.currentTimeMillis() - startTime) / 1000.0);

        PersistentObjects.shutdown();
    }
}
