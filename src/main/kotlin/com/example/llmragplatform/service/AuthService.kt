package com.example.llmragplatform.service

import com.example.llmragplatform.exception.InvalidCredentialsException
import com.example.llmragplatform.generated.model.AuthTokenResponse
import com.example.llmragplatform.security.JwtTokenService
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
/**
 * ユーザー認証を行い、JWT 発行レスポンスへ変換するサービス。
 */
class AuthService(
    private val userDetailsService: UserDetailsService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
) {

    /**
     * ユーザー名とパスワードを検証し、JWT を発行する。
     *
     * @param username 認証対象のユーザー名。
     * @param password 認証対象の平文パスワード。
     * @return JWT と有効期限を含むトークン発行レスポンス。
     */
    fun issueToken(username: String, password: String): AuthTokenResponse {
        // ユーザー名から認証対象ユーザーを取得する。
        val userDetails = try {
            userDetailsService.loadUserByUsername(username)
        } catch (_: UsernameNotFoundException) {
            // ユーザーが存在しない場合は認証失敗として扱う。
            throw InvalidCredentialsException()
        }

        // 平文パスワードが保存済みパスワードと一致するかを検証する。
        if (!passwordEncoder.matches(password, userDetails.password)) {
            throw InvalidCredentialsException()
        }

        // authority 一覧から ROLE_ 接頭辞を外した API 用ロール一覧を作る。
        val roles = userDetails.authorities
            .map { authority -> authority.authority.removePrefix("ROLE_") }
            .filter(String::isNotBlank)

        // 認証済みユーザー向けの JWT access token を生成する。
        val accessToken = jwtTokenService.generateAccessToken(
            username = userDetails.username,
            roles = roles
        )

        // 発行した token 情報を API レスポンス形式へ詰め替えて返す。
        return AuthTokenResponse()
            .accessToken(accessToken.token)
            .tokenType("Bearer")
            .expiresAt(OffsetDateTime.ofInstant(accessToken.expiresAt, ZoneOffset.UTC))
            .username(userDetails.username)
            .roles(roles)
    }
}
