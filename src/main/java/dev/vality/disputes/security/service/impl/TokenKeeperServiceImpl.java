package dev.vality.disputes.security.service.impl;

import dev.vality.disputes.exception.TokenKeeperException;
import dev.vality.disputes.security.service.TokenKeeperService;
import dev.vality.token.keeper.AuthData;
import dev.vality.token.keeper.TokenAuthenticatorSrv;
import dev.vality.token.keeper.TokenSourceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.thrift.TException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenKeeperServiceImpl implements TokenKeeperService {

    private static final String bearerPrefix = "Bearer ";
    private final TokenAuthenticatorSrv.Iface tokenKeeperClient;

    @Override
    public AuthData getAuthData() {
        log.debug("Retrieving auth info");
        try {
            var token = getBearerToken();
            return tokenKeeperClient.authenticate(
                    token.orElseThrow(() -> new TokenKeeperException("Token not found!")), new TokenSourceContext());
        } catch (TException e) {
            throw new TokenKeeperException("Error while call token keeper: ", e);
        }
    }

    private Optional<String> getBearerToken() {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (ObjectUtils.isEmpty(attributes)
                || ObjectUtils.isEmpty(attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION))) {
            return Optional.empty();
        }
        var token = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION).substring(bearerPrefix.length());
        return Optional.of(token);
    }
}
