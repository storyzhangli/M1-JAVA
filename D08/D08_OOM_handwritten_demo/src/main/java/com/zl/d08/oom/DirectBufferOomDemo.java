package com.zl.d08.oom;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 场景三：直接内存溢出（{@code java.lang.OutOfMemoryError: Direct buffer memory}）。
 *
 * <p>原理：直接字节缓冲区（Direct ByteBuffer）使用堆外本地内存（Native Memory），
 * 由 {@code -XX:MaxDirectMemorySize} 限制上限。持续申请且不释放（或引用未释放）时抛出该错误。
 * 直接内存分配成本高、不受堆 GC 直接管理，适合复用。</p>
 *
 * <p>运行：{@code java DirectBufferOomDemo bad} 触发；{@code java DirectBufferOomDemo good} 演示正确写法。</p>
 */
public class DirectBufferOomDemo {

    /** 演示用：每块 1MB。 */
    private static final int ONE_MB = 1024 * 1024;

    /**
     * 【错误写法】循环申请直接内存并保持引用，堆外内存被占满后抛出 Direct buffer memory。
     */
    public void triggerOom() {
        List<ByteBuffer> holder = new ArrayList<>();
        int index = 0;
        while (true) {
            // allocateDirect 分配堆外内存
            holder.add(ByteBuffer.allocateDirect(ONE_MB));
            System.out.println("已申请直接内存 " + (++index) + " MB");
        }
    }

    /**
     * 【正确写法】复用同一块直接缓冲区，避免重复申请；按需使用堆内缓冲区。
     *
     * <p>要点：直接内存应“少量、复用”，用完即弃并保持引用可被回收，必要时显式释放
     * （通过反射调用 {@code Cleaner} 属于内部 API，不推荐；规范做法是控制分配与复用）。</p>
     */
    public void safePractice() {
        // 只申请一块直接缓冲区，多次复用进行读写
        ByteBuffer reusable = ByteBuffer.allocateDirect(ONE_MB);
        for (int round = 1; round <= 3; round++) {
            reusable.clear();
            reusable.put(("round-" + round).getBytes());
            reusable.flip();
            byte[] dst = new byte[reusable.remaining()];
            reusable.get(dst);
            System.out.println("第 " + round + " 次复用，内容：" + new String(dst));
        }
        System.out.println("安全写法执行完毕，直接内存仅申请一次。");
    }

    public static void main(String[] args) {
        DirectBufferOomDemo demo = new DirectBufferOomDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            demo.safePractice();
        } else {
            demo.triggerOom();
        }
    }
}
