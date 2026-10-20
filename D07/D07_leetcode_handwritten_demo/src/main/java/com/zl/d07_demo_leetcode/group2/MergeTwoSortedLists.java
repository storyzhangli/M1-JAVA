package com.zl.d07_demo_leetcode.group2;

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
