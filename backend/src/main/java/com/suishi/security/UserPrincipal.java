package com.suishi.security;

import java.security.Principal;
import java.util.List;

public record UserPrincipal(Long id, String username, int authVersion, String channel, List<String> roles, List<String> permissions) implements Principal {
    @Override public String getName() { return username; }
}
