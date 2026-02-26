package me.asikeida.ashore.service;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * service 模块数据访问测试的独立启动类（仅测试源，不参与打包）。
 * 组件扫描限定在 me.asikeida.ashore.service 子树：装配本模块的
 * MybatisPlusConfig（@MapperScan）与 AuditMetaObjectHandler，
 * 不依赖 ashore-web 的主启动类（模块间依赖方向不允许反向引用）。
 */
@SpringBootApplication
public class ServiceDaoTestBootstrap {
}
