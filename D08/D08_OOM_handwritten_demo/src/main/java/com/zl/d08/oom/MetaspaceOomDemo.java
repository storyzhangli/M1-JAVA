package com.zl.d08.oom;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 场景五：元空间溢出（{@code java.lang.OutOfMemoryError: Metaspace}）。
 *
 * <p>原理：JDK 8 起，类的元数据（Class 元信息）存储在本地内存的元空间（Metaspace），
 * 由 {@code -XX:MaxMetaspaceSize} 限制。当大量类被加载且其类加载器保持存活（无法卸载）时，
 * 元空间被占满并抛出该错误。常见根因：动态生成类、类加载器泄漏。</p>
 *
 * <p>运行：{@code java -XX:MaxMetaspaceSize=20m MetaspaceOomDemo bad} 触发；
 * {@code java MetaspaceOomDemo good} 演示正确写法。</p>
 */
public class MetaspaceOomDemo {

    /** 被反复加载的目标类全限定名。 */
    private static final String TARGET_CLASS_NAME = "com.zl.d08.oom.MetaspaceSampleClass";

    /**
     * 自定义类加载器：每次实例化都会把目标类的字节码定义为一份“新的 Class”。
     */
    private static class LocalClassLoader extends ClassLoader {
        /** 目标类的字节码由外部传入，避免重复读取。 */
        private final byte[] classBytes;

        LocalClassLoader(byte[] classBytes) {
            // 以系统类加载器为父加载器
            super(MetaspaceOomDemo.class.getClassLoader());
            this.classBytes = classBytes;
        }

        /**
         * 仅对目标类自行定义，其余类委派给父加载器，避免破坏类加载体系。
         */
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (TARGET_CLASS_NAME.equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> clazz = findLoadedClass(name);
                    if (clazz == null) {
                        clazz = defineClass(name, classBytes, 0, classBytes.length);
                    }
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    /**
     * 【错误写法】不断创建新的类加载器并加载目标类，同时保留类加载器引用，
     * 导致每份 Class 元数据都无法卸载，元空间被占满。
     */
    public void triggerOom() throws IOException, ClassNotFoundException {
        byte[] bytes = readTargetClassBytes();
        List<ClassLoader> leak = new ArrayList<>();
        int count = 0;
        while (true) {
            LocalClassLoader loader = new LocalClassLoader(bytes);
            loader.loadClass(TARGET_CLASS_NAME);
            // 保留类加载器引用：类无法被卸载，元空间持续增长
            leak.add(loader);
            if (++count % 200 == 0) {
                System.out.println("已加载类副本：" + count);
            }
        }
    }

    /**
     * 【正确写法】复用同一个类加载器，目标类只加载一次；不长期保留多余的加载器引用。
     *
     * <p>要点：避免类加载器泄漏，动态生成类要保证加载器可被回收，复用已加载的 Class。</p>
     */
    public void safePractice() throws Exception {
        byte[] bytes = readTargetClassBytes();
        LocalClassLoader loader = new LocalClassLoader(bytes);
        for (int i = 0; i < 5; i++) {
            // 复用同一个 Class 对象，不会重复占用元空间
            Class<?> clazz = loader.loadClass(TARGET_CLASS_NAME);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            Object result = clazz.getMethod("hello").invoke(instance);
            System.out.println("第 " + (i + 1) + " 次复用同一 Class：" + result);
        }
        System.out.println("安全写法执行完毕，目标类仅加载一次。");
    }

    /**
     * 从类路径读取目标类的字节码（.class 资源）。
     */
    private byte[] readTargetClassBytes() throws IOException {
        String resourcePath = TARGET_CLASS_NAME.replace('.', '/') + ".class";
        try (InputStream in = MetaspaceOomDemo.class.getClassLoader().getResourceAsStream(resourcePath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("找不到目标类资源：" + resourcePath);
            }
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    public static void main(String[] args) throws Exception {
        MetaspaceOomDemo demo = new MetaspaceOomDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            demo.safePractice();
        } else {
            demo.triggerOom();
        }
    }
}
