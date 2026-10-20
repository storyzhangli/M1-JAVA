package com.zl.d07_demo_leetcode.group2;

// 链表节点定义
class ListNodeN1 {
    int val;
    ListNodeN1 next;
    ListNodeN1() {}
    ListNodeN1(int val) { this.val = val; }
    ListNodeN1(int val, ListNodeN1 next) { this.val = val; this.next = next; }
}

public class ReverseLinkedList {

    /**
     * 迭代法反转链表
     */
    public ListNodeN1 reverseList(ListNodeN1 head) {
        ListNodeN1 prev = null;
        ListNodeN1 cur = head;
        
        while (cur != null) {
            // 保存下一个节点
            ListNodeN1 next = cur.next;
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
    public ListNodeN1 reverseListRecursive(ListNodeN1 head) {
        // 终止条件：空节点或最后一个节点
        if (head == null || head.next == null) {
            return head;
        }
        // 递归反转后面的链表
        ListNodeN1 newHead = reverseListRecursive(head.next);
        // 反转当前节点指针
        head.next.next = head;
        head.next = null;
        return newHead;
    }
}
