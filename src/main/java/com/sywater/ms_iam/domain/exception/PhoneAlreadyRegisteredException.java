package com.sywater.ms_iam.domain.exception;

public class PhoneAlreadyRegisteredException extends DomainException {
    public PhoneAlreadyRegisteredException() {
        super("auth.phone_already_registered", "This phone number is already registered.");
    }
}
