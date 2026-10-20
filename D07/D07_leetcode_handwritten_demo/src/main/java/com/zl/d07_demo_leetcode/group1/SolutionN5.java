package com.zl.d07_demo_leetcode.group1;

public class SolutionN5 {
    // 迭代解法 - 推荐
    public ListNodeN4 reverseList(ListNodeN4 head) {
        ListNodeN4 pre = null;
        ListNodeN4 cur = head;
        while (cur != null) {
            ListNodeN4 next = cur.next; // 保存下一个节点
            cur.next = pre;          // 反转指针
            pre = cur;               // pre 前移
            cur = next;              // cur 前移
        }
        return pre;
    }

    // 递归解法 - 理解即可
    public ListNodeN4 reverseListRecursive(ListNodeN4 head) {
        if (head == null || head.next == null) {
            return head;
        }
        ListNodeN4 newHead = reverseListRecursive(head.next);
        head.next.next = head;
        head.next = null;
        return newHead;
    }

    // 本地测试
    public static void main(String[] args) {
        SolutionN5 solutionN5 = new SolutionN5();
        // 构造链表 1->2->3->4->5
        ListNodeN4 head = new ListNodeN4(1, new ListNodeN4(2, new ListNodeN4(3, new ListNodeN4(4, new ListNodeN4(5)))));
        ListNodeN4 reversed = solutionN5.reverseList(head);
        // 打印反转结果
        while (reversed != null) {
            System.out.print(reversed.val + " "); // 输出 5 4 3 2 1
            reversed = reversed.next;
        }
    }
}
