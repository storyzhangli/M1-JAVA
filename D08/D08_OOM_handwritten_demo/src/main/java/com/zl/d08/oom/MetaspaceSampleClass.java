package com.zl.d08.oom;

/**
 * 元空间演示用的“被加载类”。
 *
 * <p>该类的字节码会被 {@link MetaspaceOomDemo} 中的自定义类加载器反复读取并定义，
 * 每个类加载器都会在元空间产生一份独立的 Class 元数据。类本身无需任何业务逻辑。</p>
 */
public class MetaspaceSampleClass {
    /** 演示方法，用于证明类已被成功加载。 */
    public String hello() {
        return "loaded by custom classloader";
    }
}
