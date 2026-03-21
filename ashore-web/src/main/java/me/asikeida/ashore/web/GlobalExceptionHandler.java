package me.asikeida.ashore.web;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import me.asikeida.ashore.api.exception.BizException;
import me.asikeida.ashore.api.resp.FieldViolation;
import me.asikeida.ashore.api.resp.ResVo;
import me.asikeida.ashore.api.resp.Status;
import me.asikeida.ashore.api.resp.StatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理：把异常统一转换为 ResVo，并按错误码段匹配 HTTP 状态码。
 * <p>HTTP 状态与业务码双层语义：业务码供前端分支处理，HTTP 码供网关/监控识别。
 * 客户端可读文案进入响应体；堆栈与内部细节只进日志，不出响应。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：错误码段 -> HTTP 状态（401001 -> 401） */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ResVo<Void>> handleBiz(BizException e) {
        int httpStatus = httpStatusOf(e.getStatus().getCode());
        if (httpStatus >= 500) {
            log.error("业务异常 code={}：{}", e.getStatus().getCode(), e.getStatus().getMsg(), e);
        } else {
            // 预期内的业务拒绝（参数/未登录/无权限/不存在）不记录堆栈
            log.warn("业务异常 code={}：{}", e.getStatus().getCode(), e.getStatus().getMsg());
        }
        return ResponseEntity.status(httpStatus).body(ResVo.fail(e.getStatus()));
    }

    /** @Valid 请求体校验失败：400 + 字段级明细列表 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResVo<List<FieldViolation>>> handleBodyValidation(MethodArgumentNotValidException e) {
        List<FieldViolation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("请求体校验失败：{}", violations);
        return badRequest(violations);
    }

    /** 方法级约束（@RequestParam/@PathVariable 上的注解）校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResVo<List<FieldViolation>>> handleConstraint(ConstraintViolationException e) {
        List<FieldViolation> violations = e.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        log.warn("参数约束校验失败：{}", violations);
        return badRequest(violations);
    }

    /** 请求体不可读（JSON 语法错误、类型不匹配） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResVo<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败：{}", e.getMessage());
        return ResponseEntity.badRequest().body(ResVo.fail(StatusEnum.PARAM_ERROR));
    }

    /** 缺少必要参数（@RequestParam required 默认值）：400，防兜底误吞为 500（审查 R3） */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ResVo<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数：{}", e.getParameterName());
        return ResponseEntity.badRequest().body(ResVo.fail(StatusEnum.PARAM_ERROR));
    }

    /** 路径参数类型不匹配（如 /posts/{id} 传入非数字）：400，防兜底误吞 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ResVo<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配：{} = {}", e.getName(), e.getValue());
        return ResponseEntity.badRequest().body(ResVo.fail(StatusEnum.PARAM_ERROR));
    }

    /** 未匹配路径（Spring Boot 3.2+ 对静态资源/未知路径抛出）：404，防兜底误吞为 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResVo<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("路径不存在：{} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(404).body(ResVo.fail(StatusEnum.NOT_FOUND));
    }

    /** 请求方法不匹配（GET 端点收到 POST 等）：405，同理防兜底误吞 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResVo<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法：{}（支持 {}）", e.getMessage(), e.getSupportedHttpMethods());
        return ResponseEntity.status(405)
                .body(ResVo.fail(Status.of(405001, "不支持的请求方法")));
    }

    /** 兜底：未预期异常对外只返回统一模糊文案，堆栈只进日志（不泄漏内部实现） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResVo<Void>> handleUnexpected(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError().body(ResVo.fail(StatusEnum.ERROR));
    }

    private ResponseEntity<ResVo<List<FieldViolation>>> badRequest(List<FieldViolation> violations) {
        return ResponseEntity.badRequest().body(ResVo.fail(StatusEnum.PARAM_ERROR, violations));
    }

    /** 错误码段映射：400001/1000=400，401001/1000=401……其余按服务器错误处理 */
    private static int httpStatusOf(int code) {
        return switch (code / 1000) {
            case 400 -> 400;
            case 401 -> 401;
            case 403 -> 403;
            case 404 -> 404;
            case 503 -> 503;
            default -> 500;
        };
    }
}
