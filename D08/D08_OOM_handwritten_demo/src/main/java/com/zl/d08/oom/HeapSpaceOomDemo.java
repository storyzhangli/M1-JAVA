package com.zl.d08.oom;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景一：堆内存溢出（{@code java.lang.OutOfMemoryError: Java heap space}）。
 *
 * <p>原理：堆（Heap）是对象实例的主要存储区域，通过 {@code -Xmx} 限制最大值。
 * 当程序持续创建对象并保持强引用（GC 无法回收），对象总占用超过堆上限时抛出该错误。</p>
 *
 * <p>运行：{@code java HeapSpaceOomDemo bad} 触发 OOM；{@code java HeapSpaceOomDemo good} 演示正确写法。</p>
 */
public class HeapSpaceOomDemo {

    /** 演示用：每块 1MB。 */
    private static final int ONE_MB = 1024 * 1024;

    /**
     * 【错误写法】无限向集合中追加对象并保持引用，堆被占满后抛出 OOM。
     *
     * <p>典型根因：无界缓存、一次性查询全表、集合只增不清。</p>
     */
    public void triggerOom() {
        List<byte[]> holder = new ArrayList<>();
        int index = 0;
        while (true) {
            // 每次分配 1MB 并加入集合，强引用导致无法回收
            holder.add(new byte[ONE_MB]);
            System.out.println("已分配 " + (++index) + " MB，当前堆剩余："
                    + Runtime.getRuntime().freeMemory() / ONE_MB + " MB");
        }
    }

    /**
     * 【正确写法】分批处理 + 有界集合 + 及时释放引用，避免内存无界增长。
     *
     * <p>要点：预估容量、控制单批数据量、循环内用完即弃（让对象可被 GC 回收）。</p>
     */
    public void safePractice() {
        // 单批最多处理 2MB，处理完即丢弃引用
        int batchSize = 2;
        for (int batch = 1; batch <= 3; batch++) {
            List<byte[]> batchData = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                batchData.add(new byte[ONE_MB]);
            }
            System.out.println("第 " + batch + " 批处理 " + batchData.size() + " MB");
            // 批次结束：清空引用，帮助 GC；batchData 出作用域后也会变为不可达
            batchData.clear();
        }
        System.out.println("安全写法执行完毕，未发生 OOM。");
    }

    public static void main(String[] args) {
        HeapSpaceOomDemo demo = new HeapSpaceOomDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            demo.safePractice();
        } else {
            demo.triggerOom();
        }
    }
}
