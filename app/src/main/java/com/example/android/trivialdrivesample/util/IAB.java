package com.example.android.trivialdrivesample.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import com.example.android.trivialdrivesample.util.communication.BillingSupportCommunication;
import com.example.android.trivialdrivesample.util.communication.OnConnectListener;
import static com.example.android.trivialdrivesample.util.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static com.example.android.trivialdrivesample.util.IabHelper.RESPONSE_CODE;

public abstract class IAB {

    IABLogger logger;

    // Are subscriptions supported?
    boolean mSubscriptionsSupported = false;

    // The item type of the current purchase flow
    String mPurchasingItemType;

    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    IabHelper.OnIabPurchaseFinishedListener mPurchaseListener;

    // Is setup done?
    boolean mSetupDone = false;

    boolean mDisposed = false;

    public IAB(IABLogger logger) {
        this.logger = logger;
    }

    int getResponseCodeFromBundle(Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            logger.logDebug("Bundle with null response code, assuming OK (known issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) return ((Integer) o).intValue();
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            logger.logError("Unexpected type for bundle response code.");
            logger.logError(o.getClass().getName());
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    int getResponseCodeFromIntent(Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            logger.logError("Intent with no response code, assuming OK (known issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) return ((Integer) o).intValue();
        else if (o instanceof Long) return (int) ((Long) o).longValue();
        else {
            logger.logError("Unexpected type for intent response code.");
            logger.logError(o.getClass().getName());
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    protected boolean disposed() {
        return mDisposed;
    }

    void dispose(Context context) {
        mSetupDone = false;
        mDisposed = true;
    }

    abstract boolean connect(Context context, OnConnectListener listener);

    abstract void isBillingSupported(
            int apiVersion,
            String packageName,
            BillingSupportCommunication communication);

    abstract void launchPurchaseFlow(
            Context mContext, Activity act,
            String sku,
            String itemType,
            int requestCode,
            IabHelper.OnIabPurchaseFinishedListener listener,
            String extraData);

    abstract void consume(Context mContext, Purchase itemInfo) throws IabException;

    public abstract boolean handleActivityResult(int requestCode, int resultCode, Intent data, String mSignatureBase64);

    public void flagStartAsync(String refresh_inventory) {
    }

    public void flagEndAsync() {
    }

    public abstract Bundle getSkuDetails(int billingVersion, String packageName, String itemType, Bundle querySkus) throws RemoteException;

    public abstract Bundle getPurchases(int billingVersion, String packageName, String itemType, String continueToken) throws RemoteException;
}
