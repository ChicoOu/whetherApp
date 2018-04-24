package com.zime.whetherapp.recognization;

import android.os.Handler;
import android.os.Message;

import static com.zime.whetherapp.MainActivity.MSG_RECOG;

/**
 * Created by sony on 2018-04-22.
 */

public class CityRecogListener extends StatusRecogListener {
    private Handler handler;

    public CityRecogListener(Handler handler){
        this.handler = handler;
    }

    @Override
    public void onAsrFinalResult(String[] results, RecogResult recogResult) {
        super.onAsrFinalResult(results, recogResult);
        Message msg = new Message();
        msg.obj = results[0];
        msg.what = MSG_RECOG;

        handler.sendMessage(msg);
    }
}
