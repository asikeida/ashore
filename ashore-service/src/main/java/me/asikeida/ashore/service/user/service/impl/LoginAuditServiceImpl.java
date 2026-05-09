package me.asikeida.ashore.service.user.service.impl;

import me.asikeida.ashore.api.context.ReqContext;
import me.asikeida.ashore.service.user.repository.entity.UserLoginAuditDO;
import me.asikeida.ashore.service.user.repository.mapper.UserLoginAuditMapper;
import me.asikeida.ashore.service.user.service.LoginAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 登录审计实现。客户端 IP / User-Agent / requestId 取自 {@link ReqContext}
 * （RequestIdFilter 已写入），审计插入失败仅告警不抛出——审计是旁路，不能拖垮登录。
 */
@Service
public class LoginAuditServiceImpl implements LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditServiceImpl.class);

    private final UserLoginAuditMapper auditMapper;

    public LoginAuditServiceImpl(UserLoginAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    public void record(Long userId, String account, boolean success, String failReason) {
        try {
            UserLoginAuditDO audit = new UserLoginAuditDO();
            audit.setUserId(userId);
            audit.setAccount(account);
            audit.setSuccess(success);
            audit.setFailReason(failReason);
            audit.setIp(ReqContext.clientIp());
            // 列宽 varchar(255)，超长 UA 截断而非让整条审计静默丢失（审查 R9）
            String userAgent = ReqContext.userAgent();
            audit.setUserAgent(userAgent == null ? null
                    : userAgent.length() > 250 ? userAgent.substring(0, 250) : userAgent);
            audit.setRequestId(ReqContext.requestId());
            auditMapper.insert(audit);
        } catch (Exception e) {
            log.warn("登录审计写入失败（不影响登录流程）account={} success={}: {}",
                    account, success, e.getMessage());
        }
    }
}
