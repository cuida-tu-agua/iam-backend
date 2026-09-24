package com.save_water.iam.domain.exception;


// domain/exception/InvalidCredentialsException.java
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}