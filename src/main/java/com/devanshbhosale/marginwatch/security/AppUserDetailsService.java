package com.devanshbhosale.marginwatch.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.devanshbhosale.marginwatch.repository.UserRepository;

/**
 * Loads accounts by email. The domain User implements UserDetails, so the
 * ROLE_ADMIN / ROLE_ANALYST / ROLE_VIEWER authority and the isDemo flag
 * travel with the principal.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }
}
