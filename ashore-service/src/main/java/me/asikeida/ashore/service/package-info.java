/**
 * ashore-service：业务域层，按领域分包（user / article / comment / notify / rank / search / chatai），
 * 每个域内部自成一体（service + repository/mapper + dto），域间通过领域事件解耦。
 * MyBatis-Plus 在本模块落库；里程碑到达时才创建对应域包，不预建。
 */
package me.asikeida.ashore.service;
