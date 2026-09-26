# OTP / 2FA JVM tests (no Android SDK needed)

RFC 6238 vectors (SHA1 / SHA256 / SHA512, 8 digit), otpauth:// parsing (label, issuer, digits, period,
algorithm, hotp rejection), Google-Authenticator-style code grouping, and the "Issuer: account" label rules.

    kotlinc app/src/main/java/com/babasitaram/pro/Totp.kt \
            app/src/main/java/com/babasitaram/pro/OtpDisplay.kt \
            tests/otp/TotpTest.kt -include-runtime -d /tmp/otp-test.jar
    java -jar /tmp/otp-test.jar        # prints ALL PASS
