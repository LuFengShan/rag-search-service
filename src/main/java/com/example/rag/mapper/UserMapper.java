package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.rag.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * 用户数据访问层
 * <p>
 * 继承自 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。
 * 额外定义了用户相关的查询方法。
 * </p>
 *
 * <h3>表结构</h3>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>UUID</td><td>主键，用户唯一标识</td></tr>
 *   <tr><td>username</td><td>VARCHAR</td><td>用户名，唯一约束</td></tr>
 *   <tr><td>email</td><td>VARCHAR</td><td>邮箱，唯一约束</td></tr>
 *   <tr><td>password</td><td>VARCHAR</td><td>密码（BCrypt 加密）</td></tr>
 *   <tr><td>role</td><td>VARCHAR</td><td>角色：ADMIN/MAINTAINER/USER</td></tr>
 *   <tr><td>created_at</td><td>TIMESTAMP</td><td>创建时间</td></tr>
 *   <tr><td>updated_at</td><td>TIMESTAMP</td><td>更新时间</td></tr>
 * </table>
 *
 * @see com.example.rag.entity.User 用户实体
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户 Optional 对象
     */
    default Optional<User> findByUsername(String username) {
        User user = selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        return Optional.ofNullable(user);
    }

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱地址
     * @return 用户 Optional 对象
     */
    default Optional<User> findByEmail(String email) {
        User user = selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        return Optional.ofNullable(user);
    }

    /**
     * 检查用户名是否已存在
     *
     * @param username 用户名
     * @return true 如果存在，false 否则
     */
    default boolean existsByUsername(String username) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }

    /**
     * 检查邮箱是否已存在
     *
     * @param email 邮箱地址
     * @return true 如果存在，false 否则
     */
    default boolean existsByEmail(String email) {
        return selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) > 0;
    }

    /**
     * 根据角色分页查询用户
     *
     * @param page 分页参数
     * @param role 用户角色
     * @return 分页用户列表
     */
    default IPage<User> findByRole(Page<User> page, User.Role role) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getRole, role));
    }
}