package com.example.android.trivialdrivesample.util;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import com.android.vending.billing.IInAppBillingService;
import com.example.android.trivialdrivesample.util.communication.BillingSupportCommunication;
import com.example.android.trivialdrivesample.util.communication.OnConnectListener;
import java.util.List;
import static com.example.android.trivialdrivesample.util.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_MISSING_TOKEN;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_REMOTE_EXCEPTION;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_SEND_INTENT_FAILED;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE;
import static com.example.android.trivialdrivesample.util.IabHelper.ITEM_TYPE_INAPP;
import static com.example.android.trivialdrivesample.util.IabHelper.ITEM_TYPE_SUBS;
import static com.example.android.trivialdrivesample.util.IabHelper.RESPONSE_BUY_INTENT;
import static com.example.android.trivialdrivesample.util.IabHelper.getResponseDesc;

public class ServiceIAB extends IAB {

    // Connection to the service
    private IInAppBillingService mService;
    private ServiceConnection mServiceConn;

    // Is an asynchronous operation in progress?
    // (only one at a time can be in progress)
    private boolean mAsyncInProgress = false;

    // (for logging/debugging)
    // if mAsyncInProgress == true, what asynchronous operation is in progress?
    private String mAsyncOperation = "";

    // Keys for the response from getPurchaseConfig
    private static final String INTENT_V2_SUPPORT = "INTENT_V2_SUPPORT";

    ServiceIAB(IABLogger logger) {
        super(logger);
    }

    @Override
    public boolean connect(Context context, final OnConnectListener listener) {

        // Connection to IAB service
        logger.logDebug("Starting in-app billing setup.");
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                logger.logDebug("Billing service disconnected.");
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                logger.logDebug("Billing service connected.");
                if (disposed()) {
                    return;
                }
                mSetupDone = true;
                mService = IInAppBillingService.Stub.asInterface(service);
                listener.connected();

            }
        };

        Intent serviceIntent = new Intent("ir.cafebazaar.pardakht.InAppBillingService.BIND");
        serviceIntent.setPackage(BAZAAR_PACKAGE_NAME);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> intentServices = pm.queryIntentServices(serviceIntent, 0);
        if (intentServices != null && !intentServices.isEmpty()) {
            // service available to handle that Intent
            return context.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        } else {
            return false;
        }
    }

    @Override
    void isBillingSupported(int apiVersion, String packageName, BillingSupportCommunication communication) {
        try {
            logger.logDebug("Checking for in-app billing 3 support.");

            // check for in-app billing v3 support
            int response = mService.isBillingSupported(apiVersion, packageName, ITEM_TYPE_INAPP);
            if (response != BILLING_RESPONSE_RESULT_OK) {
                mSubscriptionsSupported = false;
                communication.onBillingSupportResult(response);
                return;
            }
            logger.logDebug("In-app billing version 3 supported for " + packageName);

            // check for v3 subscriptions support
            response = mService.isBillingSupported(apiVersion, packageName, ITEM_TYPE_SUBS);
            if (response == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Subscriptions AVAILABLE.");
                mSubscriptionsSupported = true;
            } else {
                logger.logDebug("Subscriptions NOT AVAILABLE. Response: " + response);
            }

            communication.onBillingSupportResult(BILLING_RESPONSE_RESULT_OK);

        } catch (RemoteException e) {
            communication.remoteExceptionHappened(new IabResult(IABHELPER_REMOTE_EXCEPTION,
                    "RemoteException while setting up in-app billing."));
            e.printStackTrace();
        }

    }

    @Override
    void launchPurchaseFlow(Context mContext, Activity act, String sku, String itemType, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {

        flagStartAsync("launchPurchaseFlow");
        IabResult result;
        if (itemType.equals(ITEM_TYPE_SUBS) && !mSubscriptionsSupported) {
            IabResult r = new IabResult(IABHELPER_SUBSCRIPTIONS_NOT_AVAILABLE,
                    "Subscriptions are not available.");
            flagEndAsync();
            if (listener != null) {
                listener.onIabPurchaseFinished(r, null);
            }
            return;
        }

        try {
            logger.logDebug("Constructing buy intent for " + sku + ", item type: " + itemType);

            String packageName = mContext.getPackageName();

            Bundle configBundle = mService.getPurchaseConfig(apiVersion);
            if (configBundle != null && configBundle.getBoolean(INTENT_V2_SUPPORT)) {
                logger.logDebug("launchBuyIntentV2 for " + sku + ", item type: " + itemType);
                launchBuyIntentV2(mContext, act, sku, itemType, requestCode, listener, extraData);
            } else {
                logger.logDebug("launchBuyIntent for " + sku + ", item type: " + itemType);
                launchBuyIntent(mContext, act, sku, itemType, requestCode, listener, extraData);
            }
        } catch (IntentSender.SendIntentException e) {
            logger.logError("SendIntentException while launching purchase flow for sku " + sku);
            e.printStackTrace();
            flagEndAsync();

            result = new IabResult(IABHELPER_SEND_INTENT_FAILED, "Failed to send intent.");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
        } catch (RemoteException e) {
            logger.logError("RemoteException while launching purchase flow for sku " + sku);
            e.printStackTrace();
            flagEndAsync();

            result = new IabResult(IABHELPER_REMOTE_EXCEPTION, "Remote exception while starting purchase flow");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
        }
    }


    private void launchBuyIntentV2(
            Context context,
            Activity act,
            String sku,
            String itemType,
            int requestCode,
            IabHelper.OnIabPurchaseFinishedListener listener,
            String extraData
    ) throws RemoteException {
        String packageName = context.getPackageName();

        Bundle buyIntentBundle = mService.getBuyIntentV2(apiVersion, packageName, sku, itemType, extraData);
        int response = getResponseCodeFromBundle(buyIntentBundle);
        if (response != BILLING_RESPONSE_RESULT_OK) {
            logger.logError("Unable to buy item, Error response: " + getResponseDesc(response));
            flagEndAsync();
            IabResult result = new IabResult(response, "Unable to buy item");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
            return;
        }

        Intent intent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
        logger.logDebug("Launching buy intent for " + sku + ". Request code: " + requestCode);
        mPurchaseListener = listener;
        mPurchasingItemType = itemType;
        act.startActivityForResult(intent, requestCode);
    }

    private void launchBuyIntent(
            Context context,
            Activity act,
            String sku,
            String itemType,
            int requestCode,
            IabHelper.OnIabPurchaseFinishedListener listener,
            String extraData
    ) throws RemoteException, IntentSender.SendIntentException {

        String packageName = context.getPackageName();

        Bundle buyIntentBundle = mService.getBuyIntent(apiVersion, packageName, sku, itemType, extraData);
        int response = getResponseCodeFromBundle(buyIntentBundle);
        if (response != BILLING_RESPONSE_RESULT_OK) {
            logger.logError("Unable to buy item, Error response: " + getResponseDesc(response));
            flagEndAsync();
            IabResult result = new IabResult(response, "Unable to buy item");
            if (listener != null) {
                listener.onIabPurchaseFinished(result, null);
            }
            return;
        }


        PendingIntent pendingIntent = buyIntentBundle.getParcelable(RESPONSE_BUY_INTENT);
        logger.logDebug("Launching buy intent for " + sku + ". Request code: " + requestCode);
        mPurchaseListener = listener;
        mPurchasingItemType = itemType;
        act.startIntentSenderForResult(pendingIntent.getIntentSender(),
                requestCode, new Intent(),
                Integer.valueOf(0), Integer.valueOf(0),
                Integer.valueOf(0));
    }

    @Override
    void consume(Context context, Purchase itemInfo) throws IabException {
        try {
            String token = itemInfo.getToken();
            String sku = itemInfo.getSku();
            if (token == null || token.equals("")) {
                logger.logError("Can't consume " + sku + ". No token.");
                throw new IabException(IABHELPER_MISSING_TOKEN, "PurchaseInfo is missing token for sku: "
                        + sku + " " + itemInfo);
            }

            logger.logDebug("Consuming sku: " + sku + ", token: " + token);
            int response = mService.consumePurchase(apiVersion, context.getPackageName(), token);
            if (response == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Successfully consumed sku: " + sku);
            } else {
                logger.logDebug("Error consuming consuming sku " + sku + ". " + getResponseDesc(response));
                throw new IabException(response, "Error consuming sku " + sku);
            }
        } catch (RemoteException e) {
            throw new IabException(IABHELPER_REMOTE_EXCEPTION, "Remote exception while consuming. PurchaseInfo: " + itemInfo, e);
        }
    }

    @Override
    public Bundle getPurchases(int billingVersion, String packageName, String itemType, String continueToken) throws RemoteException {
        return mService.getPurchases(billingVersion, packageName, itemType, continueToken);
    }

    @Override
    public Bundle getSkuDetails(int billingVersion, String packageName, String itemType, Bundle querySkus) throws RemoteException {
        return mService.getSkuDetails(apiVersion, packageName,
                itemType, querySkus);
    }

    @Override
    public void flagStartAsync(String operation) {
        if (mAsyncInProgress) throw new IllegalStateException("Can't start async operation (" +
                operation + ") because another async operation(" + mAsyncOperation + ") is in progress.");
        mAsyncOperation = operation;
        mAsyncInProgress = true;
        logger.logDebug("Starting async operation: " + operation);
    }

    @Override
    public void flagEndAsync() {
        logger.logDebug("Ending async operation: " + mAsyncOperation);
        mAsyncOperation = "";
        mAsyncInProgress = false;
    }

    @Override
    void dispose(Context context) {
        logger.logDebug("Unbinding from service.");
        if (context != null && mService != null) {
            context.unbindService(mServiceConn);
        }

        mPurchaseListener = null;
        mServiceConn = null;
        mService = null;
        super.dispose(context);
    }
}
