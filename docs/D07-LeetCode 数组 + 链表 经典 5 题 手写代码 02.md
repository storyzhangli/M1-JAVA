# LeetCode 经典算法题（数组+链表篇）

>
> 覆盖题目：#1 两数之和、#121 买卖股票最佳时机、#283 移动零、#206 反转链表、#21 合并两个有序链表
> 验收标准：Java 代码可编译运行、通过基础与边界测试、明确时间复杂度与空间复杂度
> 题型分类：数组操作、链表操作、哈希表、双指针、贪心

---

## 一、#1 Two Sum｜两数之和

### 1. 题目描述

给定一个整数数组 `nums` 和一个整数目标值 `target`，在数组中找出和为目标值的两个整数，返回它们的数组下标。

- 每种输入只会对应一个答案
- 不能重复使用相同的元素
- 可以按任意顺序返回答案

### 2. 解题思路

#### 解法1：暴力枚举（基础解法）

两层循环遍历所有数对，计算和是否等于 target。

- 优点：无需额外空间，逻辑简单
- 缺点：时间复杂度高，大数据量下性能差

#### 解法2：哈希表法（最优解法）

遍历数组，用哈希表存储「数值 → 下标」的映射；每遍历到一个元素，先检查 `target - 当前值` 是否存在于哈希表中，存在则直接返回两个下标，不存在则将当前值存入哈希表。

- 核心思想：空间换时间，将查找复杂度从 O(n) 降为 O(1)
- 注意：先检查再存入，避免同一个元素被重复使用

### 3. Java 实现代码

```
import java.util.HashMap;
import java.util.Map;

public class TwoSum {

    /**
     * 哈希表法：一次遍历完成
     * @param nums 输入数组
     * @param target 目标和
     * @return 两个元素的下标数组
     */
    public int[] twoSum(int[] nums, int target) {
        // key: 数值, value: 对应下标
        Map<Integer, Integer> map = new HashMap<>();
        
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            // 检查补数是否已在哈希表中
            if (map.containsKey(complement)) {
                return new int[]{map.get(complement), i};
            }
            // 不存在则存入当前值
            map.put(nums[i], i);
        }
        // 题目保证有解，此处仅作异常兜底
        throw new IllegalArgumentException("No two sum solution");
    }
}
```

### 4. 复杂度分析

| 解法     | 时间复杂度 | 空间复杂度 |
| -------- | ---------- | ---------- |
| 暴力法   | O(n²)      | O(1)       |
| 哈希表法 | O(n)       | O(n)       |

### 5. 边界测试用例

1. 空数组 / 数组长度小于2 → 抛出异常
2. 两个元素且和为 target → 返回 [0,1]
3. 重复数值：`nums = [3,3], target = 6` → 返回 [0,1]
4. 解在数组首尾：`nums = [2,7,11,15], target = 17` → 返回 [1,2]
5. 负数场景：`nums = [-1,-2,-3,-4,-5], target = -8` → 返回 [2,4]

---

## 二、#121 Best Time to Buy and Sell Stock｜买卖股票的最佳时机

### 1. 题目描述

给定一个数组 `prices`，第 i 个元素代表股票第 i 天的价格。只能选择某一天买入，并选择未来某一天卖出，计算所能获取的最大利润。不能获取利润则返回 0。

### 2. 解题思路

**贪心策略：一次遍历**
遍历价格数组，维护两个变量：

- `minPrice`：当前遍历位置之前的最低价格
- `maxProfit`：当前能获得的最大利润
每天计算「当前价格 - minPrice」，如果大于 maxProfit 则更新；同时更新 minPrice 为更低的价格。
- 核心思想：只需要记录历史最低点，每天计算当前卖出的利润即可，不需要回溯所有买入点

### 3. Java 实现代码

```
public class BestTimeToBuyAndSellStock {

    public int maxProfit(int[] prices) {
        if (prices == null || prices.length < 2) {
            return 0;
        }
        
        int minPrice = prices[0];
        int maxProfit = 0;
        
        for (int i = 1; i < prices.length; i++) {
            // 更新最大利润
            maxProfit = Math.max(maxProfit, prices[i] - minPrice);
            // 更新历史最低价格
            minPrice = Math.min(minPrice, prices[i]);
        }
        return maxProfit;
    }
}
```

### 4. 复杂度分析

| 维度       | 复杂度                   |
| ---------- | ------------------------ |
| 时间复杂度 | O(n)，一次遍历即可       |
| 空间复杂度 | O(1)，仅使用常数级别变量 |

### 5. 边界测试用例

1. 空数组 / 只有1天价格 → 返回 0
2. 价格持续下跌：`[5,4,3,2,1]` → 返回 0
3. 价格持续上涨：`[1,2,3,4,5]` → 返回 4
4. 最低点在最后一天：`[7,6,5,4,3]` → 返回 0
5. 利润出现在中间段：`[3,2,6,5,0,3]` → 返回 4

---

## 三、#283 Move Zeroes｜移动零

### 1. 题目描述

给定一个数组 `nums`，将所有 0 移动到数组末尾，同时保持非零元素的相对顺序。必须原地修改数组，不能复制额外数组。

### 2. 解题思路

**双指针法**

- 慢指针 `left`：指向下一个非零元素应该放置的位置
- 快指针 `right`：遍历整个数组
- 逻辑：快指针遇到非零元素时，将其赋值到慢指针位置，慢指针右移；遍历完成后，慢指针之后的位置全部置0。
- 优化：可以直接交换快慢指针位置的元素，减少赋值操作

### 3. Java 实现代码

```
public class MoveZeroes {

    public void moveZeroes(int[] nums) {
        if (nums == null || nums.length <= 1) {
            return;
        }
        
        int left = 0;
        // 快指针遍历数组
        for (int right = 0; right < nums.length; right++) {
            if (nums[right] != 0) {
                // 非零元素移到慢指针位置
                nums[left] = nums[right];
                left++;
            }
        }
        // 慢指针之后全部置0
        for (int i = left; i < nums.length; i++) {
            nums[i] = 0;
        }
    }
}
```

### 4. 复杂度分析

| 维度       | 复杂度                                      |
| ---------- | ------------------------------------------- |
| 时间复杂度 | O(n)，遍历数组一次 + 尾部置零一次，总体线性 |
| 空间复杂度 | O(1)，原地操作，无额外空间                  |

### 5. 边界测试用例

1. 全是零：`[0,0,0]` → 结果不变
2. 全是非零：`[1,2,3]` → 结果不变
3. 开头是零：`[0,1,0,3,12]` → `[1,3,12,0,0]`
4. 结尾是零：`[1,2,0,0]` → 结果不变
5. 中间穿插零：`[0,1,0,2,0,3]` → `[1,2,3,0,0,0]`

---

## 四、#206 Reverse Linked List｜反转链表

### 1. 题目描述

给你单链表的头节点 `head`，反转链表并返回反转后的头节点。

### 2. 解题思路

#### 解法1：迭代法（推荐，空间最优）

使用三个指针 `prev`、`cur`、`next` 遍历链表，逐个反转节点的 next 指针方向。

- prev：指向反转后链表的头节点，初始为 null
- cur：当前正在反转的节点
- next：保存下一个节点，避免反转后断链

#### 解法2：递归法

递归到链表最后一个节点，然后逐层返回并反转指针，适合逻辑理解，不适合超长链表（栈溢出风险）。

### 3. Java 实现代码

```
// 链表节点定义
class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

public class ReverseLinkedList {

    /**
     * 迭代法反转链表
     */
    public ListNode reverseList(ListNode head) {
        ListNode prev = null;
        ListNode cur = head;
        
        while (cur != null) {
            // 保存下一个节点
            ListNode next = cur.next;
            // 反转当前节点指针
            cur.next = prev;
            // 指针后移
            prev = cur;
            cur = next;
        }
        return prev;
    }

    /**
     * 递归法反转链表
     */
    public ListNode reverseListRecursive(ListNode head) {
        // 终止条件：空节点或最后一个节点
        if (head == null || head.next == null) {
            return head;
        }
        // 递归反转后面的链表
        ListNode newHead = reverseListRecursive(head.next);
        // 反转当前节点指针
        head.next.next = head;
        head.next = null;
        return newHead;
    }
}
```

### 4. 复杂度分析

| 解法   | 时间复杂度 | 空间复杂度           |
| ------ | ---------- | -------------------- |
| 迭代法 | O(n)       | O(1)                 |
| 递归法 | O(n)       | O(n)，递归调用栈深度 |

### 5. 边界测试用例

1. 空链表 → 返回 null
2. 只有一个节点 → 返回原节点
3. 两个节点 → 反转后指针正确
4. 长链表多节点 → 完整反转且顺序正确

---

## 五、#21 Merge Two Sorted Lists｜合并两个有序链表

### 1. 题目描述

将两个升序链表合并为一个新的升序链表并返回，新链表通过拼接两个链表的所有节点组成。

### 2. 解题思路

**虚拟头节点 + 双指针遍历**

- 创建虚拟头节点 `dummy`，作为合并后链表的前置锚点，避免处理头节点边界问题
- 两个指针分别遍历两个链表，每次选择值较小的节点接到合并链表后面
- 其中一个链表遍历完后，直接接上另一个链表剩余部分

### 3. Java 实现代码

```java
class ListNode {
    int val;
    ListNode next;
    ListNode() {}
    ListNode(int val) { this.val = val; }
    ListNode(int val, ListNode next) { this.val = val; this.next = next; }
}

public class MergeTwoSortedLists {

    public ListNode mergeTwoLists(ListNode list1, ListNode list2) {
        // 虚拟头节点，简化边界处理
        ListNode dummy = new ListNode(-1);
        ListNode cur = dummy;
        
        while (list1 != null && list2 != null) {
            // 选择较小的节点接上
            if (list1.val <= list2.val) {
                cur.next = list1;
                list1 = list1.next;
            } else {
                cur.next = list2;
                list2 = list2.next;
            }
            cur = cur.next;
        }
        // 接上剩余不为空的链表
        cur.next = list1 != null ? list1 : list2;
        
        return dummy.next;
    }
}
```

### 4. 复杂度分析

| 维度       | 复杂度                                            |
| ---------- | ------------------------------------------------- |
| 时间复杂度 | O(n+m)，n、m 分别为两个链表长度，每个节点遍历一次 |
| 空间复杂度 | O(1)，仅使用常数级指针，节点均为原链表拼接        |

### 5. 边界测试用例

1. 两个都为空 → 返回 null
2. 其中一个为空 → 返回另一个链表
3. 两个链表长度差异大 → 正确拼接剩余部分
4. 节点值相等 → 按顺序拼接，保持稳定
5. 都是单节点 → 正确合并排序

---

## 📌 算法学习要点总结

1. **数组类题目**：双指针、哈希表、贪心是三大核心思想；优先考虑原地操作，空间复杂度优化到 O(1)
2. **链表类题目**：虚拟头节点是简化边界处理的通用技巧；指针操作注意先保存下一个节点再修改指针，避免断链
3. **复杂度验证**：所有最优解均达到线性时间复杂度，是该类题型的性能上限
4. **工程习惯**：代码先做边界校验，再处理主逻辑；变量命名语义清晰，关键步骤加注释
