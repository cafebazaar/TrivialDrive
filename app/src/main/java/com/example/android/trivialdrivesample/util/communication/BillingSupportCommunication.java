package com.example.android.trivialdrivesample.util.communication;


import com.example.android.trivialdrivesample.util.IabResult;

public interface BillingSupportCommunication {
    void onBillingSupportResult(int response);
    void remoteExceptionHappened(IabResult result);
}
