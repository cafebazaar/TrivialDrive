package com.example.android.trivialdrivesample.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import com.example.android.trivialdrivesample.util.communication.BillingSupportCommunication;
import com.example.android.trivialdrivesample.util.communication.OnConnectListener;
import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import static com.example.android.trivialdrivesample.util.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_ERROR_BASE;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_MISSING_TOKEN;
import static com.example.android.trivialdrivesample.util.IabHelper.RESPONSE_BUY_INTENT;
import static com.example.android.trivialdrivesample.util.IabHelper.getResponseDesc;

public class BroadcastIAB extends IAB {

    public static final String PACKAGE_NAME_KEY = "packageName";
    public static final String API_VERSION_KEY = "apiVersion";
    public static final String SECURE_KEY = "secure";

    public static final String SUBSCRIPTION_SUPPORT_KEY = "subscriptionSupport";
    public static final String SKU_KEY = "sku";
    public static final String ITEM_TYPE_KEY = "itemType";
    public static final String DEVELOPER_PAYLOAD_KEY = "developerPayload";
    public static final String TOKEN_KEY = "token";

    private static final String bazaarBaseAction = "com.farsitel.bazaar.";
    private static final String bazaarPostAction = ".iab";

    private static final int BAZAAR_VERSION_CODE_WITH_BROADCAST = 801301;

    public static final String pingAction = bazaarBaseAction + "ping";
    public static final String billingSupport = bazaarBaseAction + "billingSupport";
    public static final String purchaseAction = bazaarBaseAction + "purchase";
    public static final String skuDetailAction = bazaarBaseAction + "skuDetail";
    public static final String getPurchaseAction = bazaarBaseAction + "getPurchase";
    public static final String consumeAction = bazaarBaseAction + "consume";

    public static final String receivePingAction = pingAction + bazaarPostAction;
    public static final String receiveBillingSupport = billingSupport + bazaarPostAction;
    public static final String receivePurchaseAction = purchaseAction + bazaarPostAction;
    public static final String receiveSkuDetailAction = skuDetailAction + bazaarPostAction;
    public static final String receiveGetPurchaseAction = getPurchaseAction + bazaarPostAction;
    public static final String receiveConsumeAction = consumeAction + bazaarPostAction;
    private final Context context;
    private final String signatureBase64;

    private int requestCode;

    private AbortableCountDownLatch consumePurchaseLatch;
    private int consumePurchaseResponse;

    private AbortableCountDownLatch getSkuDetailLatch;
    private Bundle skuDetailBundle;

    private AbortableCountDownLatch getPurchaseLatch;
    private Bundle getPurchaseBundle;

    private IABReceiverCommunicator iabReceiver = null;
    private WeakReference<OnConnectListener> connectListenerWeakReference;
    private WeakReference<BillingSupportCommunication> billingSupportWeakReference;
    private WeakReference<Activity> launchPurchaseActivityWeakReference;

    BroadcastIAB(Context context, IABLogger logger, String mSignatureBase64) {
        super(logger);
        this.context = context;
        this.signatureBase64 = mSignatureBase64 != null ? mSignatureBase64 : "secureBroadcastKey";
    }

    @Override
    boolean connect(Context context, OnConnectListener listener) {

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(BAZAAR_PACKAGE_NAME, 0);
            int versionCode;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                versionCode = (int) pInfo.getLongVersionCode();
            } else {
                versionCode = pInfo.versionCode;
            }

            if (versionCode > BAZAAR_VERSION_CODE_WITH_BROADCAST) {
                createIABReceiver();
                registerBroadcast();
                trySendPingToBazaar();
                connectListenerWeakReference = new WeakReference<>(listener);

                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }

        return false;
    }

    private void createIABReceiver() {
        iabReceiver = new IABReceiverCommunicator() {
            @Override
            public void onNewBroadcastReceived(Intent intent) {
                logger.logDebug("new message received in broadcast");
                String action = intent.getAction();
                if (action == null) {
                    logger.logError("action is null");
                    return;
                }

                if (!signatureBase64.equals(intent.getStringExtra(SECURE_KEY))) {
                    logger.logError("broadcastSecure key is not valid");
                    return;
                }

                if (disposed()) {
                    return;
                }

                switch (action) {
                    case receivePingAction:
                        OnConnectListener listener = safeGetFromWeakReference(connectListenerWeakReference);
                        mSetupDone = true;
                        if (listener != null) {
                            listener.connected();
                        }
                        break;
                    case receivePurchaseAction:
                        handleLaunchPurchaseResponse(intent.getExtras());
                        break;

                    case receiveBillingSupport:
                        logger.logDebug("billingSupport message received in broadcast");
                        handleBillingSupport(intent.getExtras());
                        break;

                    case receiveConsumeAction:
                        consumePurchaseResponse = getResponseCodeFromIntent(intent);
                        if (consumePurchaseLatch != null) {
                            consumePurchaseLatch.countDown();
                        }
                        break;

                    case receiveSkuDetailAction:
                        skuDetailBundle = intent.getExtras();
                        if (getSkuDetailLatch != null) {
                            getSkuDetailLatch.countDown();
                        }
                        break;
                    case receiveGetPurchaseAction:
                        getPurchaseBundle = intent.getExtras();
                        if (getPurchaseLatch != null) {
                            getPurchaseLatch.countDown();
                        }
                        break;
                }
            }
        };
    }

    private void handleLaunchPurchaseResponse(Bundle extras) {
        int response = getResponseCodeFromBundle(extras);
        if (response != BILLING_RESPONSE_RESULT_OK) {
            logger.logError("Unable to buy item, Error response: " + getResponseDesc(response));
            flagEndAsync();
            IabResult result = new IabResult(response, "Unable to buy item");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
            return;
        }

        Intent intent = extras.getParcelable(RESPONSE_BUY_INTENT);
        logger.logDebug("Launching buy intent Request code: " + requestCode);

        Activity activity = safeGetFromWeakReference(launchPurchaseActivityWeakReference);
        if (activity == null) {
            return;
        }

        activity.startActivityForResult(intent, requestCode);
    }

    private void handleBillingSupport(Bundle bundle) {

        mSubscriptionsSupported = bundle.getBoolean(SUBSCRIPTION_SUPPORT_KEY);
        BillingSupportCommunication billingListener = safeGetFromWeakReference(billingSupportWeakReference);
        if (billingListener != null) {
            billingListener.onBillingSupportResult(getResponseCodeFromBundle(bundle));
        }
    }

    private <T> T safeGetFromWeakReference(WeakReference<T> onConnectListenerWeakReference) {
        if (onConnectListenerWeakReference == null) {
            return null;
        }
        return onConnectListenerWeakReference.get();
    }

    private void registerBroadcast() {
        IABReceiver.addObserver(iabReceiver);
    }

    private void trySendPingToBazaar() {
        Intent intent = getNewIntentForBroadcast();
        intent.setAction(pingAction);
        context.sendBroadcast(intent);
    }

    @NonNull
    private Intent getNewIntentForBroadcast() {
        Intent intent = new Intent();
        String bazaarPackage = BAZAAR_PACKAGE_NAME;
        intent.setPackage(bazaarPackage);
        Bundle bundle = new Bundle();
        bundle.putString(PACKAGE_NAME_KEY, context.getPackageName());
        bundle.putString(SECURE_KEY, signatureBase64);
        intent.putExtras(bundle);
        return intent;
    }

    @Override
    void isBillingSupported(int apiVersion, String packageName, BillingSupportCommunication communication) {
        billingSupportWeakReference = new WeakReference<>(communication);

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(billingSupport);
        intent.putExtra(PACKAGE_NAME_KEY, packageName);
        intent.putExtra(API_VERSION_KEY, apiVersion);
        context.sendBroadcast(intent);
    }

    @Override
    void launchPurchaseFlow(Context mContext, Activity act, String sku, String itemType, int requestCode, IabHelper.OnIabPurchaseFinishedListener listener, String extraData) {

        launchPurchaseActivityWeakReference = new WeakReference<>(act);
        this.requestCode = requestCode;

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(purchaseAction);
        intent.putExtra(SKU_KEY, sku);
        intent.putExtra(ITEM_TYPE_KEY, itemType);
        intent.putExtra(API_VERSION_KEY, apiVersion);
        intent.putExtra(DEVELOPER_PAYLOAD_KEY, extraData);
        context.sendBroadcast(intent);

        mPurchaseListener = listener;
        mPurchasingItemType = itemType;
    }

    @Override
    void consume(Context mContext, Purchase itemInfo) throws IabException {
        String token = itemInfo.getToken();
        String sku = itemInfo.getSku();
        if (token == null || token.equals("")) {
            logger.logError("Can't consume " + sku + ". No token.");
            throw new IabException(IABHELPER_MISSING_TOKEN, "PurchaseInfo is missing token for sku: "
                    + sku + " " + itemInfo);
        }

        logger.logDebug("Consuming sku: " + sku + ", token: " + token);

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(consumeAction);
        intent.putExtra(TOKEN_KEY, token);
        intent.putExtra(API_VERSION_KEY, apiVersion);
        mContext.sendBroadcast(intent);

        consumePurchaseLatch = new AbortableCountDownLatch(1);


        try {
            consumePurchaseLatch.await(60, TimeUnit.SECONDS);
            if (consumePurchaseResponse == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Successfully consumed sku: " + sku);
            } else {
                logger.logDebug("Error consuming consuming sku " + sku + ". " + getResponseDesc(consumePurchaseResponse));
                throw new IabException(consumePurchaseResponse, "Error consuming sku " + sku);
            }
        } catch (InterruptedException e) {
            throw new IabException(IABHELPER_ERROR_BASE, "Error consuming sku " + sku);
        }
    }

    @Override
    public Bundle getSkuDetails(int billingVersion, String packageName, String itemType, Bundle querySkus) throws RemoteException {

        getPurchaseBundle = null;

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(purchaseAction);
        intent.putExtra(ITEM_TYPE_KEY, itemType);
        intent.putExtra(PACKAGE_NAME_KEY, packageName);
        intent.putExtra(API_VERSION_KEY, billingVersion);
        intent.putExtras(querySkus);
        context.sendBroadcast(intent);

        getSkuDetailLatch = new AbortableCountDownLatch(1);
        try {
            getSkuDetailLatch.await();
            return skuDetailBundle;

        } catch (InterruptedException e) {
            logger.logWarn("error happened while getting sku detail for " + packageName);
        }

        return new Bundle();
    }

    @Override
    public Bundle getPurchases(int billingVersion, String packageName, String itemType, String continueToken) throws RemoteException {

        skuDetailBundle = null;

        Intent intent = getNewIntentForBroadcast();
        intent.setAction(getPurchaseAction);
        intent.putExtra(ITEM_TYPE_KEY, itemType);
        intent.putExtra(PACKAGE_NAME_KEY, packageName);
        intent.putExtra(API_VERSION_KEY, billingVersion);
        intent.putExtra(TOKEN_KEY, continueToken);
        context.sendBroadcast(intent);

        getPurchaseLatch = new AbortableCountDownLatch(1);
        try {
            getPurchaseLatch.await();
            return getPurchaseBundle;

        } catch (InterruptedException e) {
            logger.logWarn("error happened while getting sku detail for " + packageName);
        }

        return new Bundle();
    }

    @Override
    void dispose(Context context) {
        super.dispose(context);
        if (iabReceiver != null) {
            IABReceiver.removeObserver(iabReceiver);
        }
        if (consumePurchaseLatch != null) {
            consumePurchaseLatch.abort();
        }

        if (getSkuDetailLatch != null) {
            getSkuDetailLatch.abort();
        }

        if (getPurchaseLatch != null) {
            getPurchaseLatch.abort();
        }
        iabReceiver = null;
    }
}
