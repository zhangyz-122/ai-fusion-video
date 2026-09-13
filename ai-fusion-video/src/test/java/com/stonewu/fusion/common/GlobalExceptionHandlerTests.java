package com.stonewu.fusion.common;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SW-T06-02 / SW-T06-03：业务异常语义状态与参数类型不匹配的 HTTP 状态映射。
 */
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionWith403CodeMapsToForbidden() {
        ResponseEntity<CommonResult<?>> response =
                handler.handleBusinessException(new BusinessException(403, "无权访问该项目内容"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo(403);
        assertThat(response.getBody().getMsg()).isEqualTo("无权访问该项目内容");
    }

    @Test
    void businessExceptionWith404CodeMapsToNotFound() {
        ResponseEntity<CommonResult<?>> response =
                handler.handleBusinessException(new BusinessException(404, "剧本分集不存在: 999999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getCode()).isEqualTo(404);
        assertThat(response.getBody().getMsg()).isEqualTo("剧本分集不存在: 999999");
    }

    @Test
    void businessExceptionWith400CodeMapsToBadRequest() {
        ResponseEntity<CommonResult<?>> response =
                handler.handleBusinessException(new BusinessException(400, "该分集暂无可导出的对白"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo(400);
        assertThat(response.getBody().getMsg()).isEqualTo("该分集暂无可导出的对白");
    }

    @Test
    void businessExceptionWithDefaultCodeStays500() {
        ResponseEntity<CommonResult<?>> response =
                handler.handleBusinessException(new BusinessException("系统内部错误"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo(500);
        assertThat(response.getBody().getMsg()).isEqualTo("系统内部错误");
    }

    @Test
    void businessExceptionWithNonErrorStatusFallsBackTo500() {
        ResponseEntity<CommonResult<?>> successCoded =
                handler.handleBusinessException(new BusinessException(200, "异常文案"));
        ResponseEntity<CommonResult<?>> redirectCoded =
                handler.handleBusinessException(new BusinessException(302, "异常文案"));

        assertThat(successCoded.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(redirectCoded.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void methodArgumentTypeMismatchMapsToBadRequest() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "secondsPerLine", methodParameter(), null);

        CommonResult<?> result = handler.handleMethodArgumentTypeMismatchException(e);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("secondsPerLine");
        ResponseStatus status = GlobalExceptionHandler.class
                .getMethod("handleMethodArgumentTypeMismatchException", MethodArgumentTypeMismatchException.class)
                .getAnnotation(ResponseStatus.class);
        assertThat(status.value()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private MethodParameter methodParameter() throws NoSuchMethodException {
        return new MethodParameter(
                GlobalExceptionHandlerTests.class.getDeclaredMethod("sampleEndpoint", Integer.class), 0);
    }

    @SuppressWarnings("unused")
    private void sampleEndpoint(Integer secondsPerLine) {
        // 仅用于构造 MethodParameter，无业务逻辑
    }
}
