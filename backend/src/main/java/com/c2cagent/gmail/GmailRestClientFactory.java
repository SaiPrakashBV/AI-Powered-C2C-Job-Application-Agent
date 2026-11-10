package com.c2cagent.gmail;

import org.springframework.stereotype.Component;

@Component
public class GmailRestClientFactory implements MailboxClientFactory {
    private final GmailProperties props;
    private final GmailTokenService tokens;

    public GmailRestClientFactory(GmailProperties props, GmailTokenService tokens) {
        this.props = props;
        this.tokens = tokens;
    }

    @Override
    public MailboxClient forAccount(GmailAccount account) {
        return new GmailRestMailboxClient(props.apiBaseUrl(), props.requestTimeout(),
                force -> tokens.accessToken(account.getId(), force));
    }

    /** Client using a known access token (during the OAuth callback, before the account row exists). */
    public MailboxClient withAccessToken(String accessToken) {
        return new GmailRestMailboxClient(props.apiBaseUrl(), props.requestTimeout(), force -> accessToken);
    }
}
