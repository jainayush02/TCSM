package com.amdocs.telecom.dao;

import com.amdocs.telecom.model.AddOn;
import java.sql.SQLException;
import java.util.List;

public interface AddOnDAO {
    List<AddOn> findActive() throws SQLException;
    List<AddOn> findBySubscription(int subscriptionId) throws SQLException;
}
