package me.asikeida.ashore.api.resp;

/**
 * 参数校验失败的字段级明细，作为校验错误响应的 result 列表项。
 *
 * @param field 出错字段名
 * @param message 该字段未满足的约束描述
 */
public record FieldViolation(String field, String message) {
}
