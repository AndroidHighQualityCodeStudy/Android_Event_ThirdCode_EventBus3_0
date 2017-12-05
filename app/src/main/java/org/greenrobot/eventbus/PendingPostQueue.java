/*
 * Copyright (C) 2012-2016 Markus Junginger, greenrobot (http://greenrobot.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.greenrobot.eventbus;

/**
 * post队列
 */
final class PendingPostQueue {
    // 队列开头
    private PendingPost head;
    // 队列结尾
    private PendingPost tail;

    /**
     * 加入队列的尾部
     *
     * @param pendingPost
     */
    synchronized void enqueue(PendingPost pendingPost) {
        if (pendingPost == null) {
            throw new NullPointerException("null cannot be enqueued");
        }
        if (tail != null) {
            tail.next = pendingPost;
            tail = pendingPost;
        } else if (head == null) {
            head = tail = pendingPost;
        } else {
            throw new IllegalStateException("Head present, but no tail");
        }
        // 通知出队列
        notifyAll();
    }

    /**
     * 出队列
     *
     * @return
     */
    synchronized PendingPost poll() {
        // header指针移动
        PendingPost pendingPost = head;
        if (head != null) {
            head = head.next;
            if (head == null) {
                tail = null;
            }
        }
        return pendingPost;
    }

    /**
     * 等待maxMillisToWait时间，然后出队列
     *
     * @param maxMillisToWait
     * @return
     * @throws InterruptedException
     */
    synchronized PendingPost poll(int maxMillisToWait) throws InterruptedException {
        // 等待maxMillisToWait时间，然后出队列
        if (head == null) {
            wait(maxMillisToWait);
        }
        return poll();
    }

}
