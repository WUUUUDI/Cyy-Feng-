package com.clf.mianshiren.utils;

/**
 * @author clf
 * @version 1.0
 */
public class ThreadHolder {

    public static ThreadLocal<Object> threadLocal = new ThreadLocal<>();

    public static void set(Object o) {
        threadLocal.set(o);
    }

    public static Object get() {
        return threadLocal.get();
    }

    public static void remove() {
        threadLocal.remove();
    }

}
