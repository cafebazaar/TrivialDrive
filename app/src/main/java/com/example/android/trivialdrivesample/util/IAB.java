package com.example.android.trivialdrivesample.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import com.example.android.trivialdrivesample.util.communication.BillingSupportCommunication;
import com.example.android.trivialdrivesample.util.communication.OnConnectListener;
import org.json.JSONException;
import static com.example.android.trivialdrivesample.util.IabHelper.BILLING_RESPONSE_RESULT_OK;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_BAD_RESPONSE;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_UNKNOWN_ERROR;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_UNKNOWN_PURCHASE_RESPONSE;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_USER_CANCELLED;
import static com.example.android.trivialdrivesample.util.IabHelper.IABHELPER_VERIFICATION_FAILED;
import static com.example.android.trivialdrivesample.util.IabHelper.RESPONSE_CODE;
import static com.example.android.trivialdrivesample.util.IabHelper.RESPONSE_INAPP_PURCHASE_DATA;
import static com.example.android.trivialdrivesample.util.IabHelper.RESPONSE_INAPP_SIGNATURE;
import static com.example.android.trivialdrivesample.util.IabHelper.getResponseDesc;

public abstract class IAB {

    IABLogger logger;
    int apiVersion = 3;

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

    public void flagStartAsync(String refresh_inventory) {
    }

    public void flagEndAsync() {
    }

    boolean handleActivityResult(int requestCode, int resultCode, Intent data, String mSignatureBase64) {

        IabResult result;
        // end of async purchase operation that started on launchPurchaseFlow
        flagEndAsync();

        if (data == null) {
            logger.logError("Null data in IAB activity result.");
            result = new IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
            return true;
        }

        int responseCode = getResponseCodeFromIntent(data);
        String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
        String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

        if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
            logger.logDebug("Successful resultcode from purchase activity.");
            logger.logDebug("Purchase data: " + purchaseData);
            logger.logDebug("Data signature: " + dataSignature);
            logger.logDebug("Extras: " + data.getExtras());
            logger.logDebug("Expected item type: " + mPurchasingItemType);

            if (purchaseData == null || dataSignature == null) {
                logger.logError("BUG: either purchaseData or dataSignature is null.");
                logger.logDebug("Extras: " + data.getExtras().toString());
                result = new IabResult(IABHELPER_UNKNOWN_ERROR, "IAB returned null purchaseData or dataSignature");
                if (mPurchaseListener != null)
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                return true;
            }

            Purchase purchase = null;
            try {
                purchase = new Purchase(mPurchasingItemType, purchaseData, dataSignature);
                String sku = purchase.getSku();

                // Verify signature
                if (!Security.verifyPurchase(mSignatureBase64, purchaseData, dataSignature)) {
                    logger.logError("Purchase signature verification FAILED for sku " + sku);
                    result = new IabResult(IABHELPER_VERIFICATION_FAILED, "Signature verification failed for sku " + sku);
                    if (mPurchaseListener != null)
                        mPurchaseListener.onIabPurchaseFinished(result, purchase);
                    return true;
                }
                logger.logDebug("Purchase signature successfully verified.");
            } catch (JSONException e) {
                logger.logError("Failed to parse purchase data.");
                e.printStackTrace();
                result = new IabResult(IABHELPER_BAD_RESPONSE, "Failed to parse purchase data.");
                if (mPurchaseListener != null)
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                return true;
            }

            if (mPurchaseListener != null) {
                mPurchaseListener.onIabPurchaseFinished(new IabResult(BILLING_RESPONSE_RESULT_OK, "Success"), purchase);
            }
        } else if (resultCode == Activity.RESULT_OK) {
            // result code was OK, but in-app billing response was not OK.
            logger.logDebug("Result code was OK but in-app billing response was not OK: " + getResponseDesc(responseCode));
            if (mPurchaseListener != null) {
                result = new IabResult(responseCode, "Problem purchashing item.");
                mPurchaseListener.onIabPurchaseFinished(result, null);
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            logger.logDebug("Purchase canceled - Response: " + getResponseDesc(responseCode));
            result = new IabResult(IABHELPER_USER_CANCELLED, "User canceled.");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
        } else {
            logger.logError("Purchase failed. Result code: " + Integer.toString(resultCode)
                    + ". Response: " + getResponseDesc(responseCode));
            result = new IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown purchase response.");
            if (mPurchaseListener != null) mPurchaseListener.onIabPurchaseFinished(result, null);
        }
        return true;
    }


    public abstract Bundle getSkuDetails(int billingVersion, String packageName, String itemType, Bundle querySkus) throws RemoteException;

    public abstract Bundle getPurchases(int billingVersion, String packageName, String itemType, String continueToken) throws RemoteException;
}
