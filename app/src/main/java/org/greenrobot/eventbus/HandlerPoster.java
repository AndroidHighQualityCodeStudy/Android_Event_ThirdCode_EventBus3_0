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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;

/**
 * handler
 */
final class HandlerPoster extends Handler {


    // post队列(这个真有必要嘛？ handler是存在自己的消息队列的)
    private final PendingPostQueue queue;

    // 是否重复post自己的事件间隔 为什么这么重复post自己???????????????
    private final int maxMillisInsideHandleMessage;
    // eventbus的引用
    private final EventBus eventBus;
    // 没有消息了即为非active状态
    private boolean handlerActive;

    /**
     * 构造方法
     *
     * @param eventBus                     eventbus引用
     * @param looper                       线程所在looper
     * @param maxMillisInsideHandleMessage
     */
    HandlerPoster(EventBus eventBus, Looper looper, int maxMillisInsideHandleMessage) {
        super(looper);
        this.eventBus = eventBus;
        this.maxMillisInsideHandleMessage = maxMillisInsideHandleMessage;
        queue = new PendingPostQueue();
    }

    void enqueue(Subscription subscription, Object event) {
        // 获取一个PendingPost
        PendingPost pendingPost = PendingPost.obtainPendingPost(subscription, event);
        synchronized (this) {
            // 加入队列中
            queue.enqueue(pendingPost);
            // 若处于不活跃状态，则设置为活跃状态
            if (!handlerActive) {
                handlerActive = true;
                // 发送一个空消息
                if (!sendMessage(obtainMessage())) {
                    throw new EventBusException("Could not send handler message");
                }
            }
        }
    }

    @Override
    public void handleMessage(Message msg) {
        boolean rescheduled = false;
        try {
            // 开机到现在的时间
            long started = SystemClock.uptimeMillis();
            // 循环消息队列
            while (true) {
                PendingPost pendingPost = queue.poll();
                if (pendingPost == null) {
                    synchronized (this) {
                        // Check again, this time in synchronized
                        pendingPost = queue.poll();
                        if (pendingPost == null) {
                            handlerActive = false;
                            return;
                        }
                    }
                }
                // post到对应线程
                eventBus.invokeSubscriber(pendingPost);
                // 根据这个时间间隔重复post自己是为了什么?????????????????
                long timeInMethod = SystemClock.uptimeMillis() - started;
                if (timeInMethod >= maxMillisInsideHandleMessage) {
                    if (!sendMessage(obtainMessage())) {
                        throw new EventBusException("Could not send handler message");
                    }
                    rescheduled = true;
                    return;
                }
            }
        } finally {
            handlerActive = rescheduled;
        }
    }
}