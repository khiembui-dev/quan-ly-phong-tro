package vn.glassliving.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        List<FieldError> errors
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> message(String message) {
        return new ApiResponse<>(true, null, message, null);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static <T> ApiResponse<T> fail(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, null, message, errors);
    }

    public record FieldError(String field, String message) {}
}
