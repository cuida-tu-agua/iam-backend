//package com.sywater.ms_iam.domain.model;
//
//public final class PasswordPolicy {
//    private PasswordPolicy() {}
//    public static void validate(String raw) {
//        boolean ok = raw != null && raw.length() >= 8 && raw.length() <= 100
//                && raw.chars().anyMatch(Character::isUpperCase)
//                && raw.chars().anyMatch(Character::isDigit)
//                && raw.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
//        if (!ok) throw new WeakPasswordException();
//    }
//}