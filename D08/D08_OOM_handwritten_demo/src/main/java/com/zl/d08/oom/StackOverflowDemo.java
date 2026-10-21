package com.zl.d08.oom;

/**
 * 场景六：栈溢出（{@code java.lang.StackOverflowError}）。
 *
 * <p>原理：每个线程拥有独立的虚拟机栈（JVM Stack），每次方法调用都会创建一个栈帧。
 * 无限递归（或调用层级过深）会使栈帧数量超过栈容量（由 {@code -Xss} 限制），抛出该错误。
 * 它属于 Error，与堆 OOM 是不同区域，但常被一起讨论。</p>
 *
 * <p>运行：{@code java StackOverflowDemo bad} 触发；{@code java StackOverflowDemo good} 演示正确写法。</p>
 */
public class StackOverflowDemo {

    /**
     * 【错误写法】递归没有正确的终止条件，栈帧无限增长，最终抛出 StackOverflowError。
     */
    public void triggerError(int depth) {
        // 缺少有效终止条件的递归
        System.out.println("递归深度：" + depth);
        triggerError(depth + 1);
    }

    /**
     * 【正确写法】用迭代替代递归，或确保递归有明确的基准条件（base case）且深度可控。
     *
     * <p>这里以“累加 1..n”为例，分别给出迭代与安全递归。</p>
     */
    public int safePractice(int n) {
        // 迭代写法：栈深度恒定，不会随 n 增长
        int sum = 0;
        for (int i = 1; i <= n; i++) {
            sum += i;
        }
        return sum;
    }

    /**
     * 安全递归示例：有明确的基准条件，且深度在可控范围内。
     */
    public int safeRecursion(int n) {
        // 基准条件：n <= 0 时终止递归
        if (n <= 0) {
            return 0;
        }
        return n + safeRecursion(n - 1);
    }

    public static void main(String[] args) {
        StackOverflowDemo demo = new StackOverflowDemo();
        if (args.length > 0 && "good".equalsIgnoreCase(args[0])) {
            int n = 100;
            System.out.println("迭代累加 1.." + n + " = " + demo.safePractice(n));
            System.out.println("安全递归累加 1.." + n + " = " + demo.safeRecursion(n));
        } else {
            try {
                demo.triggerError(1);
            } catch (StackOverflowError e) {
                System.out.println("触发 StackOverflowError（递归过深）。");
            }
        }
    }
}
