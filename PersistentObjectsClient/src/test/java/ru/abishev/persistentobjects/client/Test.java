package ru.abishev.persistentobjects.client;

public class Test {
    public static interface ITestObject {
        String sayHello(String suffix);
    }

    public static class TestObject implements ITestObject {
        private final String helloPrefix;

        public TestObject(String helloPrefix) {
            this.helloPrefix = helloPrefix;
        }

        public String sayHello(String suffix) {
            return helloPrefix + "@" + suffix;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        boolean isLocal = false;
        PersistentObjects.setRemotePort(5678);

        ITestObject obj = PersistentObjects.create(ITestObject.class, TestObject.class, isLocal, new String[]{"Hello world"});
        System.out.println(obj.sayHello(isLocal ? "local" : "remote"));

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            obj.sayHello("suffix");
        }
        System.out.println("Time for 1000 requests " + (System.currentTimeMillis() - startTime) / 1000.0);

        PersistentObjects.shutdown();
    }
}
