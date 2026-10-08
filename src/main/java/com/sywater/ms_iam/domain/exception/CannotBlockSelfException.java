package com.sywater.ms_iam.domain.exception;

/** An administrator cannot block their own account (the platform could be left without administrators). HTTP 400. */
public class CannotBlockSelfException extends DomainException {
    public CannotBlockSelfException() {
        super("user.cannot_block_self", "You cannot block your own account.");
    }
}
