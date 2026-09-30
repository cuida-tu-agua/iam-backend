package com.sywater.ms_iam.domain.exception;

public class AlreadyVerifiedException extends DomainException {
  public AlreadyVerifiedException() {
    super("auth.already_verified", "The e-mail is already verified.");
  }
}
