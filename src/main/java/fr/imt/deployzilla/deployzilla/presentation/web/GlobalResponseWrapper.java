package fr.imt.deployzilla.deployzilla.presentation.web;

import com.mongodb.lang.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
public class GlobalResponseWrapper implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        // Prevents Spring Actuactor endpoints
        String controllerName = returnType.getContainingClass().getName();
        return !controllerName.contains("org.springframework.boot.actuate");
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {

        // 1. SAFETY CHECK: If the body is already an HttpResponse, return it immediately.
        // This happens when GlobalExceptionHandler returns a ResponseEntity<HttpResponse>
        // We do NOT want to wrap the error response inside a success response.
        if (body instanceof HttpResponse) {
            return body;
        }

        // 2. Handle void/null controller returns
        if (body == null) {
            return HttpResponse.success();
        }

        // Fallback success when no data and no error
        return HttpResponse.success(body);
    }

}
