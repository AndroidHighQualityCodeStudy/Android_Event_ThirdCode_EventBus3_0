package com.xiaxl.android.test.event;

/**
 * 定义一个事件类
 */
public class MessageEvent {

    public int mEventType = 0;
    public Object mEventData = null;

    //
    public MessageEvent(int type, Object data) {
        this.mEventType = type;
        this.mEventData = data;
    }

    @Override
    public String toString() {
        //
        StringBuffer sb = new StringBuffer();
        sb.append(" mEventType: ");
        sb.append(mEventType);
        sb.append(" mEventData: ");
        sb.append(mEventData);
        return sb.toString();
    }
}
