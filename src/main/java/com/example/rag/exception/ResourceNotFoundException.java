
package com.example.rag.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s 未找到，%s: '%s'", resourceName, fieldName, fieldValue));
    }
}
