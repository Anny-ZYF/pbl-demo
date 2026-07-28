package com.gdmu.security;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Mapper
public interface AccountMapper {
    @Select("SELECT COUNT(*) FROM admin_users WHERE status=1")
    long adminCount();

    @Insert("""
            INSERT INTO admin_users(username,password,display_name,status,role,must_change_password,create_time,update_time)
            VALUES(#{username},#{passwordHash},#{displayName},1,'ROLE_ADMIN',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)
    int insertBootstrapAdmin(@Param("username") String username, @Param("passwordHash") String passwordHash,
                             @Param("displayName") String displayName);

    @Select("""
            SELECT id, username, password_hash AS passwordHash, display_name AS displayName,
                   role, status, must_change_password AS mustChangePassword,
                   token_version AS tokenVersion
            FROM user_account_view
            WHERE username = #{username} AND role = #{role}
            LIMIT 1
            """)
    Optional<AccountRow> findByUsernameAndRole(@Param("username") String username, @Param("role") String role);

    @Select("""
            SELECT id, username, password_hash AS passwordHash, display_name AS displayName,
                   role, status, must_change_password AS mustChangePassword,
                   token_version AS tokenVersion
            FROM user_account_view WHERE username = #{username}
            """)
    List<AccountRow> findAllByUsername(String username);

    @Insert("""
            INSERT INTO refresh_token(user_id, username, role, token_hash, token_version, expires_at, created_at)
            VALUES(#{userId}, #{username}, #{role}, #{tokenHash}, #{tokenVersion}, #{expiresAt}, CURRENT_TIMESTAMP)
            """)
    int insertRefreshToken(@Param("userId") long userId, @Param("username") String username,
                           @Param("role") String role, @Param("tokenHash") String tokenHash,
                           @Param("tokenVersion") int tokenVersion,
                           @Param("expiresAt") LocalDateTime expiresAt);

    @Select("""
            SELECT rt.id, rt.user_id AS userId, rt.username, rt.role,
                   v.display_name AS displayName, v.status,
                   v.must_change_password AS mustChangePassword,
                   v.token_version AS tokenVersion,
                   rt.expires_at AS expiresAt, rt.revoked_at AS revokedAt
            FROM refresh_token rt
            JOIN user_account_view v ON v.id = rt.user_id AND v.role = rt.role
            WHERE rt.token_hash = #{tokenHash} AND rt.token_version=v.token_version
            LIMIT 1
            FOR UPDATE
            """)
    Optional<RefreshTokenRow> findRefreshToken(String tokenHash);

    @Update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE id = #{id} AND revoked_at IS NULL")
    int revokeRefreshToken(long id);

    @Update("""
            UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP
            WHERE user_id = #{userId} AND role = #{role} AND revoked_at IS NULL
            """)
    int revokeAllRefreshTokens(@Param("userId") long userId, @Param("role") String role);

    @Update("""
            UPDATE admin_users SET password = #{hash}, must_change_password = 0, update_time = CURRENT_TIMESTAMP
              , token_version=token_version+1
            WHERE id = #{id}
            """)
    int updateAdminPassword(@Param("id") long id, @Param("hash") String hash);

    @Update("""
            UPDATE teacher_users SET password = #{hash}, must_change_password = 0, update_time = CURRENT_TIMESTAMP
              , token_version=token_version+1
            WHERE id = #{id}
            """)
    int updateTeacherPassword(@Param("id") long id, @Param("hash") String hash);

    @Update("""
            UPDATE student_users SET password = #{hash}, must_change_password = 0, update_time = CURRENT_TIMESTAMP
              , token_version=token_version+1
            WHERE id = #{id}
            """)
    int updateStudentPassword(@Param("id") long id, @Param("hash") String hash);

    @Update("UPDATE admin_users SET token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int bumpAdminTokenVersion(long id);

    @Update("UPDATE teacher_users SET token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int bumpTeacherTokenVersion(long id);

    @Update("UPDATE student_users SET token_version=token_version+1,update_time=CURRENT_TIMESTAMP WHERE id=#{id}")
    int bumpStudentTokenVersion(long id);

    @Update("UPDATE admin_users SET last_login_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int touchAdminLogin(long id);
}
