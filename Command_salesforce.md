Card__x card = new Card__x();
card.customer_id__c = 'CUST001';
card.created_at__c  = DateTime.now();

Database.insertImmediate(new List<Card__x>{ card });

============================

Card__x c = [SELECT Id FROM Card__x WHERE ExternalId = '2' LIMIT 1];
Database.deleteImmediate(new List<Card__x>{ c });
============================
Card__x c = [SELECT Id FROM Card__x WHERE ExternalId = '2' LIMIT 1];
c.customer_id__c = 9999;
Database.updateImmediate(new List<Card__x>{ c });






// Customers__x
Customers__x cus = new Customers__x();
cus.id__c = 2;
cus.email__c = 'long1@gmail.com';
cus.name__c = 'Long Nguyen';
cus.created_at__c = DateTime.now();
Database.insertImmediate(new List<Customers__x>{ cus });
============================
Customers__x c = [SELECT Id FROM Customers__x WHERE ExternalId = '2' LIMIT 1];
Database.deleteImmediate(new List<Customers__x>{ c });
============================
Customers__x c = [SELECT Id FROM Customers__x WHERE ExternalId = '1' LIMIT 1];
c.email__c = 'nguyenlong@gmail.com';
Database.updateImmediate(new List<Customers__x>{ c });