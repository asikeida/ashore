package me.asikeida.ashore.api.resp;

/**
 * 响应状态契约：错误码 + 用户可读消息。
 * <p>由 {@link StatusEnum} 枚举承载预定义值；需要动态消息（如带参数的错误）时用 {@link #of(int, String)} 构造。</p>
 */
public interface Status {

    int getCode();

    String getMsg();

    static Status of(int code, String msg) {
        return new SimpleStatus(code, msg);
    }

    record SimpleStatus(int code, String msg) implements Status {
        @Override
        public int getCode() {
            return code;
        }

        @Override
        public String getMsg() {
            return msg;
        }
    }
}
