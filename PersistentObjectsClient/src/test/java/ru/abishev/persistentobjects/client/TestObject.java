package ru.abishev.persistentobjects.client;

public class TestObject implements ITestObject {
    private final String helloPrefix;

    public TestObject(String helloPrefix) {
        this.helloPrefix = helloPrefix;
    }

    public String sayHello(String suffix) {
        return helloPrefix + "@" + suffix;
    }
}
