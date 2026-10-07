package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.AddOn;
import java.util.List;

public interface AddOnService {
    List<AddOn> getCatalogue() throws TelecomException;
    List<AddOn> getSubscriptionAddOns(int subscriptionId,int customerId) throws TelecomException;
    void setActive(int subscriptionId,int addOnId,int customerId,boolean active) throws TelecomException;
}
