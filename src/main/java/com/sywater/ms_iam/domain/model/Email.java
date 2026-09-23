//package com.sywater.ms_iam.domain.model;
//
//import com.sywater.ms_iam.domain.exception.InvalidCredentialsException;
//
//import java.util.Locale;
//import java.util.regex.Pattern;
//
//public record Email(String value) {
//    private static final Pattern FORMAT = Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
//    public Email {
//        if (value == null) throw new InvalidCredentialsException();
//        value = value.trim().toLowerCase(Locale.ROOT);
//        if (value.length() > 320 || !FORMAT.matcher(value).matches()) throw new InvalidCredentialsException();
//    }
//}
//
