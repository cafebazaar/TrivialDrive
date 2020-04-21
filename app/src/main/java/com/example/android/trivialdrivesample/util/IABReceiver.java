package com.example.android.trivialdrivesample.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class IABReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(intent.getAction() + ".iab");
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            sendIntent.putExtras(bundle);
        }
        context.sendBroadcast(sendIntent);
    }
}
