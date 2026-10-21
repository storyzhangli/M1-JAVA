package com.zl.d08.oom;

import java.util.Scanner;

/**
 * OOM 多场景统一启动器。
 *
 * <p>提供一个交互式菜单，选择要演示的场景与“错误写法 / 正确写法”，
 * 便于在 IDEA 中统一运行，无需分别配置多个 main 类。</p>
 *
 * <p>用法：</p>
 * <ul>
 *     <li>无参数：进入交互式菜单；</li>
 *     <li>参数：{@code java OomLauncher <场景编号 1-7> <bad|good>}。</li>
 * </ul>
 */
public class OomLauncher {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2) {
            runScenario(Integer.parseInt(args[0]), args[1]);
            return;
        }
        printMenu();
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("请选择场景编号（1-7）：");
            int scenario = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("请选择写法（bad 触发 OOM / good 正确写法）：");
            String mode = scanner.nextLine().trim();
            runScenario(scenario, mode);
        }
    }

    /**
     * 根据场景编号和模式运行对应演示。
     */
    private static void runScenario(int scenario, String mode) throws Exception {
        boolean good = "good".equalsIgnoreCase(mode);
        System.out.println("===== 场景 " + scenario + "，模式：" + (good ? "正确写法" : "错误写法（可能触发 OOM）") + " =====");
        switch (scenario) {
            case 1 -> {
                HeapSpaceOomDemo d = new HeapSpaceOomDemo();
                if (good) d.safePractice(); else d.triggerOom();
            }
            case 2 -> {
                GcOverheadOomDemo d = new GcOverheadOomDemo();
                if (good) d.safePractice(); else d.triggerOom();
            }
            case 3 -> {
                DirectBufferOomDemo d = new DirectBufferOomDemo();
                if (good) d.safePractice(); else d.triggerOom();
            }
            case 4 -> {
                NativeThreadOomDemo d = new NativeThreadOomDemo();
                if (good) d.safePractice(); else d.triggerOom();
            }
            case 5 -> {
                MetaspaceOomDemo d = new MetaspaceOomDemo();
                if (good) d.safePractice(); else d.triggerOom();
            }
            case 6 -> {
                StackOverflowDemo d = new StackOverflowDemo();
                if (good) {
                    System.out.println("累加 1..100 = " + d.safePractice(100));
                } else {
                    d.triggerError(1);
                }
            }
            case 7 -> {
                LargeArrayOomDemo d = new LargeArrayOomDemo();
                if (good) d.safePractice(250_000L);
                else {
                    try {
                        d.triggerOom();
                    } catch (OutOfMemoryError e) {
                        System.out.println("触发 OOM：" + e.getMessage());
                    }
                }
            }
            default -> System.out.println("无效场景编号：" + scenario);
        }
    }

    /**
     * 打印场景菜单。
     */
    private static void printMenu() {
        System.out.println("========== OOM 多场景演示菜单 ==========");
        System.out.println("1. Java heap space（堆内存溢出）");
        System.out.println("2. GC overhead limit exceeded（GC 开销超限）");
        System.out.println("3. Direct buffer memory（直接内存溢出）");
        System.out.println("4. unable to create native thread（无法创建本地线程）");
        System.out.println("5. Metaspace（元空间溢出）");
        System.out.println("6. StackOverflowError（栈溢出）");
        System.out.println("7. Requested array size exceeds VM limit（超大数组）");
        System.out.println("========================================");
    }
}
