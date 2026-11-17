package com.plumb5.monitor.campaign;

import android.content.Context;
import androidx.fragment.app.FragmentActivity;

import com.plumb5.monitor.core.SdkPreferences;
import com.plumb5.monitor.ui.P5DialogBox;
import com.plumb5.monitor.core.SdkLogger;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LocalCampaignManager {

    private final Context context;

    public LocalCampaignManager(Context context) {
        this.context = context;
    }

    public void evaluateEvent(String eventName, Map<String, String> properties) {
        // Server-defined in-app messages (dialogs, forms, local notifications).
        try {
            com.plumb5.monitor.engage.P5InAppManager.onEvent(context, eventName, properties);
        } catch (RuntimeException e) {
            SdkLogger.error("LocalCampaignManager: in-app evaluation failed", e);
        }
        // Example local trigger logic
        if ("ADD_TO_CART".equalsIgnoreCase(eventName)) {
            if (canShowCampaign("discount_001", 3, 24)) {
                triggerDiscountCampaign("discount_001");
            }
        }
    }

    private boolean canShowCampaign(String campaignId, int maxImpressions, int minIntervalHours) {
        SdkPreferences prefs = new SdkPreferences(context);
        int currentImpressions = prefs.getCampaignImpressions(campaignId);
        
        if (currentImpressions >= maxImpressions) {
            SdkLogger.debug("Campaign " + campaignId + " capped by impressions (" + currentImpressions + ")");
            return false;
        }

        long lastShown = prefs.getCampaignLastShownTime(campaignId);
        long diffMs = System.currentTimeMillis() - lastShown;
        long minIntervalMs = TimeUnit.HOURS.toMillis(minIntervalHours);

        if (diffMs < minIntervalMs) {
            SdkLogger.debug("Campaign " + campaignId + " capped by interval. Next show in " + 
                    TimeUnit.MILLISECONDS.toHours(minIntervalMs - diffMs) + "h");
            return false;
        }

        return true;
    }

    private void triggerDiscountCampaign(String campaignId) {
        SdkLogger.debug("LocalCampaignManager: Triggering discount campaign: " + campaignId);
        if (context instanceof FragmentActivity) {
            FragmentActivity activity = (FragmentActivity) context;
            String html = "<html><body><h1>Special Offer!</h1><p>Use code PLUMB5 for 10% off.</p><button onclick='Plumb5.dismiss()'>Close</button></body></html>";
            
            P5DialogBox.newInstance(html).show(activity.getSupportFragmentManager(), "campaign_dialog");
            
            // Record the impression
            new SdkPreferences(context).incrementCampaignImpressions(campaignId);
        }
    }
}
