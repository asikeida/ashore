package me.asikeida.ashore.service.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局装配：扫描各业务域的 Mapper 接口
 * （约定路径 `me.asikeida.ashore.**.mapper`，新业务域落在该包下即自动注册）。
 * <p>配置归属 ashore-service（声明 MP 依赖的模块）：主应用由 web 模块的组件扫描装配，
 * service 模块自身的测试也可通过测试启动类独立装载。</p>
 */
@Configuration
@MapperScan("me.asikeida.ashore.**.mapper")
public class MybatisPlusConfig {
}
