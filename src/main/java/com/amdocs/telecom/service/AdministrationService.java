package com.amdocs.telecom.service;

import com.amdocs.telecom.exception.TelecomException;
import com.amdocs.telecom.model.*;
import java.util.List;

public interface AdministrationService {
    void updateCustomer(Administrator admin,Customer customer) throws TelecomException;
    void setCustomerStatus(Administrator admin,int customerId,String status) throws TelecomException;
    SIMCard addSIM(Administrator admin,SIMCard sim) throws TelecomException;
    void setSIMStatus(Administrator admin,int simId,String status) throws TelecomException;
    void setSubscriptionStatus(Administrator admin,int subscriptionId,String status) throws TelecomException;
    void setPlanRules(Administrator admin,int planId,boolean allowTypeChange,int minimumDays) throws TelecomException;
    List<UsageRecord> getUsage(Administrator admin,int subscriptionId,String month) throws TelecomException;
}
