package com.sywater.ms_iam.domain.exception;

/**
 * HU-028: the SMS / WhatsApp provider refused the number for the mode the user picked (it is not a mobile, it has no
 * WhatsApp...). Nothing was stored: the user can fix the number or pick the other mode. HTTP 422.
 */
public class PhoneNotReachableException extends DomainException {
    public PhoneNotReachableException(String channel) {
        super("phone.not_reachable", "The code could not be sent by " + channel + " to that number. "
                + "Check the number or choose the other mode.");
    }
}
