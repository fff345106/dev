package com.example.hello.service;

public interface AppInvitationCodeVerifier {

    VerificationResult verifyAndConsume(String code);

    record VerificationResult(VerificationStatus status, String message) {
        public static VerificationResult consumed() {
            return new VerificationResult(VerificationStatus.CONSUMED, null);
        }

        public static VerificationResult invalid(String message) {
            return new VerificationResult(VerificationStatus.INVALID, message);
        }

        public static VerificationResult used(String message) {
            return new VerificationResult(VerificationStatus.USED, message);
        }

        public static VerificationResult unavailable(String message) {
            return new VerificationResult(VerificationStatus.UNAVAILABLE, message);
        }
    }

    enum VerificationStatus {
        CONSUMED,
        INVALID,
        USED,
        UNAVAILABLE
    }
}
