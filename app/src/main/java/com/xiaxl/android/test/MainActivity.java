package com.xiaxl.android.test;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.xiaxl.android.test.event.MessageEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 注册EventBus
        EventBus.getDefault().register(this);
        //
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // 初始化UI
        initUI();
    }

    @Override
    protected void onDestroy() {
        // 取消EventBus注册
        EventBus.getDefault().unregister(this);
        //
        super.onDestroy();
    }

    /**
     * 初始化UI
     */
    private void initUI() {
        // 点击按钮发出事件
        findViewById(R.id.button01).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //在任意线程,调用发送事件
                EventBus.getDefault().post(new MessageEvent(0, "123456"));
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onMessageEventPosting(MessageEvent event) {
        Log.d("xiaxl: ", "---onMessageEventPosting---");
        Log.d("xiaxl: ", event.toString());
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEventMain(MessageEvent event) {
        Log.d("xiaxl: ", "---onMessageEventMain---");
        Log.d("xiaxl: ", event.toString());
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onMessageEventBackground(MessageEvent event) {
        Log.d("xiaxl: ", "---onMessageEventBackground---");
        Log.d("xiaxl: ", event.toString());
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onMessageEventAsync(MessageEvent event) {
        Log.d("xiaxl: ", "---onMessageEventAsync---");
        Log.d("xiaxl: ", event.toString());
    }

}
