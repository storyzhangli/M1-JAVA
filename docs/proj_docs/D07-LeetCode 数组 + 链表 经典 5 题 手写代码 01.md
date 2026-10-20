# LeetCode 数组+链表 经典5题 手写代码集

>
> 语言：JDK 21 标准语法；题型：Java后端面试高频核心题；每道配套：核心思路+可运行代码+复杂度分析+面试考点
> 说明：所有代码均为 LeetCode 提交级写法，附带本地可运行测试入口；优先给出工业界最优解，标注关键边界处理。

---

## 一、数组篇（3道）

### 1. LeetCode 1. 两数之和

#### 题目描述

给定整数数组 `nums` 和整数目标值 `target`，在数组中找出和为 `target` 的两个整数，返回它们的数组下标。每种输入只对应一个答案，不可重复使用相同元素。

#### 核心思路

暴力双重循环时间复杂度为 O(n²)，**最优解采用哈希表做空间换时间**：一次遍历数组，用 HashMap 存储「数值-下标」映射；遍历到每个元素时，先检查 `target - 当前值` 是否存在于哈希表中，存在则直接返回两个下标，不存在则将当前值和下标存入哈希表。

#### 代码实现

```
import java.util.HashMap;
import java.util.Map;

public class Solution {
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> numIndexMap = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (numIndexMap.containsKey(complement)) {
                return new int[]{numIndexMap.get(complement), i};
            }
            numIndexMap.put(nums[i], i);
        }
        // 题目保证有解，此处仅为编译合规
        throw new IllegalArgumentException("No two sum solution");
    }

    // 本地测试
    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] nums = {2, 7, 11, 15};
        int target = 9;
        int[] result = solution.twoSum(nums, target);
        System.out.println("[" + result[0] + ", " + result[1] + "]"); // 输出 [0, 1]
    }
}
```

#### 复杂度分析

- 时间复杂度：O(n)，仅遍历一次数组，哈希表查询/插入均为 O(1)
- 空间复杂度：O(n)，哈希表最多存储 n 个元素

#### 面试考点 & 避坑

1. 考点：暴力解到最优解的演进思路、哈希表的工程应用
2. 坑点：数组重复值处理（先判断再存入，避免同一元素重复使用）；返回下标的前后顺序
3. 延伸：如果数组有序，优先用双指针法，空间复杂度可降为 O(1)

---

### 2. LeetCode 53. 最大子数组和

#### 题目描述

给定整数数组 `nums`，找出一个具有最大和的连续子数组（子数组最少包含一个元素），返回其最大和。

#### 核心思路

**Kadane 算法（动态规划空间优化版）**：

- 定义 `currentMax`：以当前元素结尾的最大子数组和
- 状态转移：`currentMax = max(当前元素值, currentMax + 当前元素值)` —— 要么从当前元素重新开始，要么接在前面的子数组后面
- 全程维护全局最大值 `maxSum`

#### 代码实现

```
public class Solution {
    public int maxSubArray(int[] nums) {
        int currentMax = nums[0];
        int maxSum = nums[0];
        for (int i = 1; i < nums.length; i++) {
            currentMax = Math.max(nums[i], currentMax + nums[i]);
            maxSum = Math.max(maxSum, currentMax);
        }
        return maxSum;
    }

    // 本地测试
    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] nums = {-2, 1, -3, 4, -1, 2, 1, -5, 4};
        System.out.println(solution.maxSubArray(nums)); // 输出 6（对应子数组 [4,-1,2,1]）
    }
}
```

#### 复杂度分析

- 时间复杂度：O(n)，单次遍历
- 空间复杂度：O(1)，仅用两个变量，无额外数组空间

#### 面试考点 & 避坑

1. 考点：动态规划思想、空间优化能力
2. 坑点：全负数数组的边界处理（不能返回0，必须返回最大的那个负数）；初始值必须设为数组首元素而非0
3. 延伸：进阶问法「返回最大子数组的起止下标」，需额外记录位置变量

---

### 3. LeetCode 283. 移动零

#### 题目描述

给定数组 `nums`，将所有 0 移动到数组末尾，同时保持非零元素的相对顺序；必须原地操作，不得复制额外数组。

#### 核心思路

**快慢双指针法**：

- 慢指针 `nonZeroIndex`：指向下一个非零元素应该放置的位置
- 快指针遍历数组：遇到非零元素，就将其赋值到慢指针位置，慢指针后移
- 遍历完成后，慢指针之后的所有位置统一置为0

#### 代码实现

```
public class Solution {
    public void moveZeroes(int[] nums) {
        int nonZeroIndex = 0;
        // 第一步：把所有非零元素按顺序移到前面
        for (int num : nums) {
            if (num != 0) {
                nums[nonZeroIndex++] = num;
            }
        }
        // 第二步：末尾剩余位置全部置0
        for (int i = nonZeroIndex; i < nums.length; i++) {
            nums[i] = 0;
        }
    }

    // 本地测试
    public static void main(String[] args) {
        Solution solution = new Solution();
        int[] nums = {0, 1, 0, 3, 12};
        solution.moveZeroes(nums);
        for (int num : nums) {
            System.out.print(num + " "); // 输出 1 3 12 0 0
        }
    }
}
```

#### 复杂度分析

- 时间复杂度：O(n)，两次线性遍历，总操作数为 n
- 空间复杂度：O(1)，原地操作，无额外空间

#### 面试考点 & 避坑

1. 考点：数组原地操作、双指针技巧
2. 坑点：必须保证非零元素的相对顺序；禁止用交换法频繁交换（会产生多余操作）
3. 延伸：变种题「移除指定数值的元素」（LeetCode 27），完全相同的双指针思路

---

## 二、链表篇（2道）

>
> 先给出 LeetCode 标准链表节点定义

```
// 单链表节点定义
public class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}
```

---

### 4. LeetCode 206. 反转链表

#### 题目描述

给定单链表的头节点 `head`，反转链表并返回反转后的头节点。

#### 核心思路

**迭代法（面试首选，空间最优）**：三指针遍历

- `pre`：指向已反转部分的头节点，初始为 null
- `cur`：指向当前待反转节点，初始为 head
- 每次循环：先保存 `cur.next`，再将 `cur.next` 指向 `pre`，然后 `pre` 和 `cur` 同步后移
- 循环结束后 `pre` 就是新的头节点

#### 代码实现

```
public class Solution {
    // 迭代解法 - 推荐
    public ListNode reverseList(ListNode head) {
        ListNode pre = null;
        ListNode cur = head;
        while (cur != null) {
            ListNode next = cur.next; // 保存下一个节点
            cur.next = pre;          // 反转指针
            pre = cur;               // pre 前移
            cur = next;              // cur 前移
        }
        return pre;
    }

    // 递归解法 - 理解即可
    public ListNode reverseListRecursive(ListNode head) {
        if (head == null || head.next == null) {
            return head;
        }
        ListNode newHead = reverseListRecursive(head.next);
        head.next.next = head;
        head.next = null;
        return newHead;
    }

    // 本地测试
    public static void main(String[] args) {
        Solution solution = new Solution();
        // 构造链表 1->2->3->4->5
        ListNode head = new ListNode(1, new ListNode(2, new ListNode(3, new ListNode(4, new ListNode(5)))));
        ListNode reversed = solution.reverseList(head);
        // 打印反转结果
        while (reversed != null) {
            System.out.print(reversed.val + " "); // 输出 5 4 3 2 1
            reversed = reversed.next;
        }
    }
}
```

#### 复杂度分析

- 迭代法：时间 O(n)，空间 O(1)
- 递归法：时间 O(n)，空间 O(n)（递归栈深度）

#### 面试考点 & 避坑

1. 考点：链表指针操作、迭代与递归两种写法
2. 坑点：空链表/单节点链表的边界处理；反转后尾节点必须指向 null，否则形成环
3. 延伸：变种题「反转链表前N个节点」「K个一组反转链表」，核心都是三指针思路的扩展

---

### 5. LeetCode 21. 合并两个有序链表

#### 题目描述

将两个升序链表合并为一个新的升序链表并返回，新链表由给定两个链表的节点拼接而成。

#### 核心思路

**虚拟头节点 + 双指针遍历**：

- 新建虚拟头节点 `dummy`，避免单独处理头节点的边界问题
- 两个指针分别遍历两个链表，每次取值更小的节点接到结果链表上，对应指针后移
- 其中一个链表遍历完后，直接将另一个链表剩余部分拼接到结果末尾

#### 代码实现

```
public class Solution {
    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        ListNode dummy = new ListNode(-1);
        ListNode cur = dummy;

        while (list1 != null && list2 != null) {
            if (list1.val <= list2.val) {
                cur.next = list1;
                list1 = list1.next;
            } else {
                cur.next = list2;
                list2 = list2.next;
            }
            cur = cur.next;
        }
        // 拼接剩余部分
        cur.next = list1 == null ? list2 : list1;
        return dummy.next;
    }

    // 本地测试
    public static void main(String[] args) {
        Solution solution = new Solution();
        ListNode l1 = new ListNode(1, new ListNode(2, new ListNode(4)));
        ListNode l2 = new ListNode(1, new ListNode(3, new ListNode(4)));
        ListNode merged = solution.mergeTwoLists(l1, l2);
        while (merged != null) {
            System.out.print(merged.val + " "); // 输出 1 1 2 3 4 4
            merged = merged.next;
        }
    }
}
```

#### 复杂度分析

- 时间复杂度：O(m+n)，m、n 分别为两个链表的长度
- 空间复杂度：O(1)，仅新建一个虚拟头节点，无额外空间

#### 面试考点 & 避坑

1. 考点：链表归并逻辑、虚拟头节点技巧
2. 坑点：其中一个链表为空的情况；两个链表值相等时的处理
3. 延伸：「合并K个有序链表」的进阶问法，基于本题用分治思想扩展

---

需要我继续出**第二组5道题**（栈/队列 + 二叉树），还是针对某道题讲解变种题/优化思路？