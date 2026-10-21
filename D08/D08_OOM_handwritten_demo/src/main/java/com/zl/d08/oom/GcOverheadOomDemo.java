package com.zl.d08.oom;

import java.util.HashMap;
import java.util.Map;

/**
 * 场景二：GC 开销超限（{@code java.lang.OutOfMemoryError: GC overhead limit exceeded}）。
 *
 * <p>原理：JVM 投入超过 98% 的时间进行垃圾回收，却只能回收不到 2% 的堆空间时抛出该错误，
 * 是一种“早期保护”，避免程序在极低吞吐下空转。可通过 {@code -XX:-UseGCOverheadLimit} 关闭（不建议）。</p>
 *
 * <p>运行：{@code java GcOverheadOomDemo bad} 触发；{@code java GcOverheadOomDemo good} 演示正确写法。</p>
 */
public class GcOverheadOomDemo {

    /**
     * 【错误写法】持续向 Map 中放入大量小对象，GC 频繁执行却几乎回收不到内存，
     * 最终触发 GC overhead limit（部分配置下也可能直接报 Java heap space）。
     */
    public void triggerOom() {
        Map<String, String> cache = new HashMap<>();
        long counter = 0;
        while (true) {
            // 每次加入 100 个小键值对，对象保持强引用
            for (int i = 0; i < 100; i++) {
                counter++;
                cache.put("key-" + counter, "value-" + counter + "-padding-padding-padding");
            }
            if (counter % 10_000 == 0) {
                System.out.println("已缓存条目：" + cache.size());
            }
        }
    }

    /**
     * 【正确写法】分批处理数据，让临时对象在每批结束后变为不可达，避免长期占用。
     *
     * <p>要点：使用局部容器、批量处理、用完即弃，而不是把所有数据缓存到一个全局 Map。</p>
     */
    public void safePractice() {
        int totalBatches = 5;
        int batchItems = 1000;
        for (int batch = 1; batch <= totalBatches; batch++) {
            // 局部 Map，批次结束后引用失效，可被整体回收
            Map<String, String> batchData = new HashMap<>(batchItems);
            for (int i = 0; i < batchItems; i++) {
                batchData.put("batch" + batch + "-key" + i, "value" + i);
            }
            System.out.println("第 " + batch + " 批处理 " + batchData.size() + " 条，结束后即可回收");
        }
        System.out.println("安全写法执行完毕，GC 无需频繁空转。");
    }

    public static void main(String[] args) {
        GcOverheadOomDemo demo = new GcOverheadOomDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            demo.safePractice();
        } else {
            demo.triggerOom();
        }
    }
}
