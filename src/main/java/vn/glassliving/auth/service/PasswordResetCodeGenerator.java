package vn.glassliving.auth.service;

@FunctionalInterface
public interface PasswordResetCodeGenerator {
    String generate();
}
