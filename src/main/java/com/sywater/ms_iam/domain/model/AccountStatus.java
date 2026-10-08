package com.sywater.ms_iam.domain.model;

/** What an administrator sees of an account (HU-059). A temporary lock after failed logins is still ACTIVE. */
public enum AccountStatus { ACTIVE, BLOCKED, UNVERIFIED, DELETED }
