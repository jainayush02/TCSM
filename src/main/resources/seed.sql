-- Seed Initial Data for TCSMS

-- 1. Default Admin (Username: admin, Password: admin@123)
-- BCrypt hash generated at runtime; using a pre-computed hash here
INSERT INTO administrators (admin_number, username, password_hash, email, full_name, account_status)
SELECT 'ADM1001', 'admin', '$2a$10$In4VlbyKe7FC4DpR.3wnEOg8lCO/31lKQ1ayCTHfWuE4YwTeGkoqW', 'admin@telecom.com', 'System Administrator', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM administrators WHERE username = 'admin');

-- 2. Initial Tariff Plans
INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-101', '5G Premium', 'POSTPAID', 999.0, 100, -1, 1000, 30, TRUE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-101');

INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-102', '5G Standard', 'POSTPAID', 699.0, 50, 1500, 500, 30, FALSE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-102');

INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-103', 'Business Pro', 'POSTPAID', 1499.0, 200, -1, 2000, 30, TRUE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-103');

INSERT INTO telecom_plans (plan_code, plan_name, plan_type, monthly_rental, data_allowance_gb, voice_minutes, sms_allowance, validity_days, international_roaming, status)
SELECT 'PLAN-104', 'Basic', 'PREPAID', 399.0, 20, 500, 100, 28, FALSE, 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM telecom_plans WHERE plan_code = 'PLAN-104');

-- 3. Sample SIM Cards (available for new subscriptions)
INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678901', 'ESIM', '404010123456789', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678901');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678902', 'PHYSICAL_SIM', '404010123456790', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678902');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678903', 'ESIM', '404010123456791', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678903');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678904', 'PHYSICAL_SIM', '404010123456792', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678904');

INSERT INTO sim_cards (sim_number, sim_type, imsi, status)
SELECT '89910012345678905', 'ESIM', '404010123456793', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM sim_cards WHERE sim_number = '89910012345678905');

-- 4. Sample Customers (Password: Customer@123)
INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, email, mobile_number, address, city, country, username, password_hash, account_status)
SELECT 'CUST100245', 'Arjun', 'Mehta', '1995-05-15', 'arjun.mehta@example.com', '9876543210', '101 Marine Drive', 'Mumbai', 'India', 'arjunm', '$2a$10$wT0H7eFj/y55hX8Q8eLgU.8jWsqR6k1bH4x7f3Y6k9Q1qZ8xPzPWe', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_number = 'CUST100245');

INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, email, mobile_number, address, city, country, username, password_hash, account_status)
SELECT 'CUST100378', 'Sarah', 'Wilson', '1992-08-20', 'sarah.wilson@example.com', '9876543211', '42 Baker Street', 'London', 'UK', 'sarahw', '$2a$10$wT0H7eFj/y55hX8Q8eLgU.8jWsqR6k1bH4x7f3Y6k9Q1qZ8xPzPWe', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_number = 'CUST100378');

INSERT INTO customers (customer_number, first_name, last_name, date_of_birth, email, mobile_number, address, city, country, username, password_hash, account_status)
SELECT 'CUST100412', 'Omar', 'Hassan', '1988-12-10', 'omar.hassan@example.com', '9876543212', '12 King Fahd Rd', 'Riyadh', 'Saudi Arabia', 'omarh', '$2a$10$wT0H7eFj/y55hX8Q8eLgU.8jWsqR6k1bH4x7f3Y6k9Q1qZ8xPzPWe', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM customers WHERE customer_number = 'CUST100412');

-- 5. Sample Mobile Subscription
INSERT INTO mobile_subscriptions (subscription_number, customer_id, plan_id, mobile_number, sim_id, activation_date, subscription_type, status)
SELECT 'SUB10001', 1, 2, '+91-9876543210', 1, '2025-01-01', 'POSTPAID', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM mobile_subscriptions WHERE subscription_number = 'SUB10001');

-- 6. Sample Bill for Customer 1
INSERT INTO bills (bill_number, subscription_id, billing_month, plan_rental, usage_charges, tax_amount, discount, total_amount, due_date, bill_status)
SELECT 'INV-2026-08-10245', 1, '2026-08', 699.0, 320.0, 183.42, 50.0, 1152.42, '2026-08-20', 'UNPAID'
WHERE NOT EXISTS (SELECT 1 FROM bills WHERE bill_number = 'INV-2026-08-10245');

-- 7. Sample Usage Records
INSERT INTO usage_records (subscription_id, usage_type, quantity, unit, charge) VALUES (1, 'VOICE', 120.0, 'Minutes', 0.0);
INSERT INTO usage_records (subscription_id, usage_type, quantity, unit, charge) VALUES (1, 'SMS', 45.0, 'Count', 0.0);
INSERT INTO usage_records (subscription_id, usage_type, quantity, unit, charge) VALUES (1, 'DATA', 2048.0, 'MB', 150.0);
INSERT INTO usage_records (subscription_id, usage_type, quantity, unit, charge) VALUES (1, 'ROAMING', 30.0, 'Minutes', 170.0);

-- 8. Sample Complaints
INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1001A1', 1, 1, 'NETWORK', 'Slow 5G internet speeds and call drops during peak hours in South Mumbai.', 'HIGH', CURRENT_TIMESTAMP, 'RESOLVED', 'Network cell tower 4B in South Mumbai optimized and antenna reoriented. Speeds verified above 150 Mbps.'
WHERE NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1001A1');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1002B2', 1, 1, 'BILLING', 'Charged extra INR 170 for roaming when roaming was included.', 'MEDIUM', CURRENT_TIMESTAMP, 'OPEN', NULL
WHERE NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1002B2');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1003C3', 2, NULL, 'NETWORK', 'Cannot connect to 4G LTE while traveling on underground commute routes.', 'LOW', CURRENT_TIMESTAMP, 'OPEN', NULL
WHERE NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1003C3');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1004D4', 3, NULL, 'SIM', 'eSIM profile download QR code failed scanning twice.', 'HIGH', CURRENT_TIMESTAMP, 'RESOLVED', 'Fresh eSIM activation QR profile regenerated and emailed to customer. Profile successfully installed.'
WHERE NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1004D4');

INSERT INTO complaints (complaint_number, customer_id, subscription_id, category, description, priority, created_date, status, resolution)
SELECT 'CMP-1005E5', 1, 1, 'PAYMENT', 'UPI transaction deducted twice during bill payment.', 'CRITICAL', CURRENT_TIMESTAMP, 'RESOLVED', 'Duplicate transaction INR 1152.42 verified. Automatic refund initiated to original bank account via PG gateway.'
WHERE NOT EXISTS (SELECT 1 FROM complaints WHERE complaint_number = 'CMP-1005E5');
