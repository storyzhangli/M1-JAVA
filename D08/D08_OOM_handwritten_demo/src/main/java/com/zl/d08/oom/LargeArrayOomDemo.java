package com.zl.d08.oom;

/**
 * 场景七：超大数组分配（{@code java.lang.OutOfMemoryError: Requested array size exceeds VM limit}）。
 *
 * <p>原理：申请的数组长度超过了 JVM 对单个数组的大小限制（通常接近 {@code Integer.MAX_VALUE - 1}，
 * 且受剩余内存约束）。即使堆未满，也会因数组长度非法而抛出该错误。</p>
 *
 * <p>运行：{@code java LargeArrayOomDemo bad} 触发；{@code java LargeArrayOomDemo good} 演示正确写法。</p>
 */
public class LargeArrayOomDemo {

    /**
     * 【错误写法】申请一个远超可用内存与 VM 限制的数组。
     */
    public int[] triggerOom() {
        // 长度约 21 亿，直接超过 VM 数组限制
        return new int[Integer.MAX_VALUE];
    }

    /**
     * 【正确写法】分配前校验长度，采用合理上限或分块（分页）处理，避免一次性申请超大数组。
     *
     * <p>这里演示“分块”处理一个逻辑上的大数据集，每次只分配一块。</p>
     */
    public void safePractice(long logicalTotal) {
        // 单块上限，受业务与内存约束
        final int chunkSize = 100_000;
        long remaining = logicalTotal;
        int chunkIndex = 0;
        while (remaining > 0) {
            int currentSize = (int) Math.min(chunkSize, remaining);
            int[] chunk = new int[currentSize];
            // 仅在当前块内处理，块结束后引用可被回收
            for (int i = 0; i < currentSize; i++) {
                chunk[i] = chunkIndex * chunkSize + i;
            }
            System.out.println("第 " + (++chunkIndex) + " 块，长度 " + currentSize);
            remaining -= currentSize;
        }
        System.out.println("安全写法执行完毕，逻辑总量 " + logicalTotal + " 已分块处理。");
    }

    public static void main(String[] args) {
        LargeArrayOomDemo demo = new LargeArrayOomDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            demo.safePractice(250_000L);
        } else {
            try {
                demo.triggerOom();
            } catch (OutOfMemoryError e) {
                System.out.println("触发 OOM：" + e.getMessage());
            }
        }
    }
}
