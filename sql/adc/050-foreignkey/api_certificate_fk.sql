ALTER TABLE API_CERTIFICATE ADD CONSTRAINT API_CERTIFICATE_FK1 FOREIGN KEY (USER_NO) REFERENCES USER (USER_NO);