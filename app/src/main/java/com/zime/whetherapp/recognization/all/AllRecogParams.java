package com.zime.whetherapp.recognization.all;

import android.app.Activity;

import com.baidu.speech.asr.SpeechConstant;
import com.zime.whetherapp.recognization.CommonRecogParams;

import java.util.Arrays;

/**
 * Created by fujiayi on 2017/6/24.
 */

public class AllRecogParams extends CommonRecogParams {


    private static final String TAG = "NluRecogParams";

    public AllRecogParams(Activity context) {
        super(context);
        stringParams.addAll(Arrays.asList(
                SpeechConstant.NLU,
                "_language",
                "_model"));

        intParams.addAll(Arrays.asList(
                SpeechConstant.DECODER,
                SpeechConstant.PROP));

        boolParams.addAll(Arrays.asList(SpeechConstant.DISABLE_PUNCTUATION, "_nlu_online"));

        // copyOfflineResource(context);
    }



}
